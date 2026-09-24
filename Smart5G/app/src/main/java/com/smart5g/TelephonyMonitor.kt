package com.smart5g

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Real-time Cellular Signal snapshot.
 * Any metric unavailable from modem hardware or driver is null.
 */
data class Signal(
    val net: String,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val operator: String?,
    val pci: Int? = null,
    val ci: Long? = null,
    val tac: Int? = null,
    val bandOrArfcn: String? = null,
    val isWifiActive: Boolean = false,
    val simInfo: String? = null
) {
    /** Signal quality classification based on 3GPP RSRP ranges */
    val qualityLevel: SignalQuality
        get() = when {
            rsrp == null -> SignalQuality.UNKNOWN
            rsrp >= -85 -> SignalQuality.EXCELLENT
            rsrp >= -98 -> SignalQuality.GOOD
            rsrp >= -110 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }

    val is5G: Boolean
        get() = net.contains("5G")
}

enum class SignalQuality(val label: String, val colorHex: Long) {
    EXCELLENT("Excellent", 0xFF2E7D32),
    GOOD("Good", 0xFF388E3C),
    FAIR("Fair", 0xFFF57C00),
    POOR("Poor (Dead zone)", 0xFFD32F2F),
    UNKNOWN("Unavailable", 0xFF757575)
}

@SuppressLint("MissingPermission")
class TelephonyMonitor(private val ctx: Context) {
    private val tm = ctx.getSystemService(TelephonyManager::class.java)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val sm = ctx.getSystemService(SubscriptionManager::class.java)

    @Volatile private var nsa = false
    private var callback31: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    fun hasPerms(): Boolean = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** Checks whether Wi-Fi is currently routing network traffic */
    fun isWifiConnected(): Boolean {
        val net = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 5G NSA detection callback initialization with lifecycle teardown support */
    fun start() {
        if (!hasPerms()) return
        val executor = ContextCompat.getMainExecutor(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback31 == null) {
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(i: TelephonyDisplayInfo) {
                    nsa = i.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                            i.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                }
            }
            tm?.registerTelephonyCallback(executor, cb)
            callback31 = cb
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && phoneStateListener == null) {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onDisplayInfoChanged(i: TelephonyDisplayInfo) {
                    nsa = i.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                            i.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                }
            }
            @Suppress("DEPRECATION")
            tm?.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            phoneStateListener = listener
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback31 != null) {
            (callback31 as? TelephonyCallback)?.let { tm?.unregisterTelephonyCallback(it) }
            callback31 = null
        }
        if (phoneStateListener != null) {
            @Suppress("DEPRECATION")
            tm?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        }
    }

    fun flow(everyMs: Long = 1500): Flow<Signal> = flow {
        while (true) {
            emit(read())
            delay(everyMs)
        }
    }

    private suspend fun cells(): List<CellInfo> {
        if (tm == null || !hasPerms()) return emptyList()
        val executor = ContextCompat.getMainExecutor(ctx)

        return withTimeoutOrNull(2000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                suspendCancellableCoroutine { continuation ->
                    tm.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cells: MutableList<CellInfo>) {
                            if (continuation.isActive) continuation.resume(cells)
                        }
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (continuation.isActive) continuation.resume(tm.allCellInfo ?: emptyList())
                        }
                    })
                }
            } else {
                tm.allCellInfo ?: emptyList()
            }
        } ?: tm.allCellInfo ?: emptyList()
    }

    private fun Int.valid(): Int? = takeIf { it != Int.MAX_VALUE && it != 0 }
    private fun Long.valid(): Long? = takeIf { it != Long.MAX_VALUE && it != 0L }

    suspend fun read(): Signal {
        val wifiActive = isWifiConnected()
        if (tm == null || !hasPerms()) {
            return Signal("Permissions required", null, null, null, null, isWifiActive = wifiActive)
        }

        val all = cells()
        val registeredCells = all.filter { it.isRegistered }
        val reg = registeredCells.firstOrNull { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it is CellInfoNr }
            ?: registeredCells.firstOrNull { it is CellInfoLte }
            ?: registeredCells.firstOrNull()

        var rsrp: Int? = null
        var rsrq: Int? = null
        var sinr: Int? = null
        var pci: Int? = null
        var ci: Long? = null
        var tac: Int? = null
        var bandInfo: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && reg is CellInfoNr) {
            val sig = reg.cellSignalStrength as? CellSignalStrengthNr
            rsrp = sig?.ssRsrp?.valid()
            rsrq = sig?.ssRsrq?.valid()
            sinr = sig?.ssSinr?.valid()

            val id = reg.cellIdentity as? CellIdentityNr
            pci = id?.pci?.valid()
            ci = id?.nci?.takeIf { it != Long.MAX_VALUE && it != 0L }
            tac = id?.tac?.valid()
            val arfcn = id?.nrarfcn?.valid()
            if (arfcn != null) bandInfo = "NR-ARFCN: $arfcn"
        } else if (reg is CellInfoLte) {
            val sig = reg.cellSignalStrength
            rsrp = sig.rsrp.valid()
            rsrq = sig.rsrq.valid()
            sinr = sig.rssnr.valid()

            val id = reg.cellIdentity
            pci = id.pci.valid()
            ci = id.ci.toLong().valid()?.toLong()
            tac = id.tac.valid()
            val earfcn = id.earfcn.valid()
            if (earfcn != null) bandInfo = "EARFCN: $earfcn"
        } else if (reg is CellInfoWcdma) {
            val sig = reg.cellSignalStrength
            rsrp = sig.dbm.valid()
            val id = reg.cellIdentity
            ci = id.cid.toLong().valid()?.toLong()
            tac = id.lac.valid()
            bandInfo = "3G UMTS"
        } else if (reg is CellInfoGsm) {
            val sig = reg.cellSignalStrength
            rsrp = sig.dbm.valid()
            val id = reg.cellIdentity
            ci = id.cid.toLong().valid()?.toLong()
            tac = id.lac.valid()
            bandInfo = "2G GSM"
        }

        val dataNetType = try { tm.dataNetworkType } catch (_: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
        val net = when {
            nsa -> "5G (NSA)"
            dataNetType == TelephonyManager.NETWORK_TYPE_NR -> "5G (SA)"
            dataNetType == TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            dataNetType in listOf(
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS
            ) -> "3G (HSPA/UMTS)"
            dataNetType in listOf(
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS
            ) -> "2G (EDGE/GSM)"
            reg != null -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && reg is CellInfoNr) "5G NR" else "Cellular"
            else -> "No Cellular Signal"
        }

        val operator = tm.networkOperatorName.takeIf { it.isNotBlank() }
            ?: tm.simOperatorName.takeIf { it.isNotBlank() }

        val activeSubs = if (hasPerms()) {
            runCatching { sm?.activeSubscriptionInfoList }.getOrNull()
        } else null
        val simInfo = if (!activeSubs.isNullOrEmpty()) {
            activeSubs.joinToString(" • ") { sub ->
                val slot = sub.simSlotIndex + 1
                val name = sub.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: sub.displayName?.toString() ?: "Carrier"
                "SIM $slot: $name"
            }
        } else null

        return Signal(
            net = net,
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            operator = operator,
            pci = pci,
            ci = ci,
            tac = tac,
            bandOrArfcn = bandInfo,
            isWifiActive = wifiActive,
            simInfo = simInfo
        )
    }

}
