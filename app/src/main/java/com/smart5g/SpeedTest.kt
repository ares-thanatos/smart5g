package com.smart5g

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class TestStage(val displayName: String) {
    IDLE("Ready"),
    PING("Testing Latency & Jitter"),
    DOWNLOAD("Testing Download"),
    UPLOAD("Testing Upload"),
    COMPLETED("Test Complete")
}

data class SpeedTestProgress(
    val stage: TestStage = TestStage.IDLE,
    val currentMbps: Double = 0.0,
    val stageProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null
)

data class Perf(
    val down: Double?,
    val up: Double?,
    val latMs: Double?,
    val jitterMs: Double?,
    val timestamp: Long = System.currentTimeMillis()
)

/** Real measurements with configurable edge endpoints, durations, and parallel stream count */
object SpeedTest {
    private const val DEFAULT_SERVER = "https://speed.cloudflare.com"
    private const val TIMEOUT_MS = 6000

    suspend fun latency(
        baseUrl: String = DEFAULT_SERVER,
        n: Int = 8,
        onPingSample: ((Double) -> Unit)? = null
    ): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val s = mutableListOf<Double>()
        val pingUrl = if (baseUrl.contains("cloudflare")) "$baseUrl/__down?bytes=0" else baseUrl
        for (i in 0 until n) {
            if (!isActive) break
            runCatching {
                val t = System.nanoTime()
                val c = (URL(pingUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                c.inputStream.use { it.readBytes() }
                c.disconnect()
                val sampleMs = (System.nanoTime() - t) / 1e6
                s += sampleMs
                onPingSample?.invoke(sampleMs)
            }
            delay(40)
        }
        val x = s.drop(1)
        if (x.size < 2) {
            if (s.isNotEmpty()) s.average() to 0.0 else null
        } else {
            val sorted = x.sorted()
            val median = sorted[sorted.size / 2]
            val jitter = x.zipWithNext { a, b -> abs(a - b) }.average()
            median to jitter
        }
    }

    private suspend fun timed(
        sec: Int,
        threads: Int,
        onProgress: ((currentMbps: Double, stageProgress: Float) -> Unit)? = null,
        work: suspend (Long, AtomicLong) -> Unit
    ): Double? = withContext(Dispatchers.IO) {
        val bytes = AtomicLong()
        val t0 = System.nanoTime()
        val durationNs = sec * 1_000_000_000L
        val end = t0 + durationNs

        val monitorJob = launch {
            var lastBytes = 0L
            var lastTime = t0
            while (isActive && System.nanoTime() < end) {
                delay(200)
                val now = System.nanoTime()
                val currentBytes = bytes.get()
                val deltaBytes = currentBytes - lastBytes
                val deltaTimeSec = (now - lastTime) / 1e9
                if (deltaTimeSec > 0) {
                    val instantMbps = (deltaBytes * 8) / 1e6 / deltaTimeSec
                    val progress = ((now - t0).toDouble() / durationNs).coerceIn(0.0, 1.0).toFloat()
                    onProgress?.invoke(instantMbps, progress)
                }
                lastBytes = currentBytes
                lastTime = now
            }
        }

        try {
            (1..threads).map { async { work(end, bytes) } }.awaitAll()
        } finally {
            monitorJob.cancel()
        }

        val totalBytes = bytes.get()
        val totalSec = (System.nanoTime() - t0) / 1e9
        if (totalBytes == 0L || totalSec <= 0) null else (totalBytes * 8 / 1e6) / totalSec
    }

    suspend fun download(
        baseUrl: String = DEFAULT_SERVER,
        sec: Int = 6,
        threads: Int = 4,
        onProgress: ((Double, Float) -> Unit)? = null
    ): Double? = timed(sec, threads, onProgress) { end, bytes ->
        val buf = ByteArray(65536)
        val dlUrl = if (baseUrl.contains("cloudflare")) "$baseUrl/__down?bytes=25000000" else baseUrl
        while (System.nanoTime() < end && currentCoroutineContext().isActive) {
            try {
                val c = (URL(dlUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                c.inputStream.use { s ->
                    while (System.nanoTime() < end && currentCoroutineContext().isActive) {
                        val r = s.read(buf)
                        if (r < 0) break
                        bytes.addAndGet(r.toLong())
                    }
                }
                c.disconnect()
            } catch (_: Exception) {
                delay(100)
            }
        }
    }

    suspend fun upload(
        baseUrl: String = DEFAULT_SERVER,
        sec: Int = 6,
        threads: Int = 3,
        onProgress: ((Double, Float) -> Unit)? = null
    ): Double? = timed(sec, threads, onProgress) { end, bytes ->
        val payload = ByteArray(500_000)
        val upUrl = if (baseUrl.contains("cloudflare")) "$baseUrl/__up" else baseUrl
        while (System.nanoTime() < end && currentCoroutineContext().isActive) {
            try {
                val c = (URL(upUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "POST"
                    doOutput = true
                    setFixedLengthStreamingMode(payload.size)
                }
                c.outputStream.use { it.write(payload) }
                c.responseCode
                c.disconnect()
                bytes.addAndGet(payload.size.toLong())
            } catch (_: Exception) {
                delay(100)
            }
        }
    }

    suspend fun runFullTest(
        config: SpeedTestConfig = SpeedTestConfig(),
        onUpdate: (SpeedTestProgress) -> Unit
    ): Perf = withContext(Dispatchers.IO) {
        var state = SpeedTestProgress(stage = TestStage.PING, overallProgress = 0.05f)
        onUpdate(state)

        val pingResult = latency(baseUrl = config.baseUrl, n = 6) {
            state = state.copy(pingMs = it)
            onUpdate(state)
        }
        val ping = pingResult?.first
        val jitter = pingResult?.second
        state = state.copy(stage = TestStage.DOWNLOAD, pingMs = ping, jitterMs = jitter, overallProgress = 0.2f)
        onUpdate(state)

        val down = download(
            baseUrl = config.baseUrl,
            sec = config.durationSeconds,
            threads = config.streams
        ) { mbps, stageProg ->
            state = state.copy(
                stage = TestStage.DOWNLOAD,
                currentMbps = mbps,
                stageProgress = stageProg,
                overallProgress = 0.2f + stageProg * (if (config.uploadEnabled) 0.4f else 0.8f)
            )
            onUpdate(state)
        }

        val up = if (config.uploadEnabled) {
            state = state.copy(stage = TestStage.UPLOAD, downloadMbps = down, currentMbps = 0.0, stageProgress = 0f, overallProgress = 0.6f)
            onUpdate(state)

            upload(
                baseUrl = config.baseUrl,
                sec = config.durationSeconds,
                threads = (config.streams - 1).coerceAtLeast(2)
            ) { mbps, stageProg ->
                state = state.copy(
                    stage = TestStage.UPLOAD,
                    currentMbps = mbps,
                    stageProgress = stageProg,
                    overallProgress = 0.6f + stageProg * 0.4f
                )
                onUpdate(state)
            }
        } else null

        val result = Perf(down, up, ping, jitter)
        onUpdate(
            SpeedTestProgress(
                stage = TestStage.COMPLETED,
                currentMbps = 0.0,
                stageProgress = 1f,
                overallProgress = 1f,
                pingMs = ping,
                jitterMs = jitter,
                downloadMbps = down,
                uploadMbps = up
            )
        )
        result
    }
}
