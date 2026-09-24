package com.smart5g

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

class VM(app: Application) : AndroidViewModel(app) {
    val mon = TelephonyMonitor(app)
    val repo = RoomRepository(app)

    val signal = MutableStateFlow<Signal?>(null)
    val signalHistory = MutableStateFlow<List<Int>>(emptyList())
    val perf = MutableStateFlow<Perf?>(null)
    val score = MutableStateFlow<Score?>(null)
    val speedProgress = MutableStateFlow(SpeedTestProgress())
    val running = MutableStateFlow(false)
    val statusText = MutableStateFlow("")
    val rooms = MutableStateFlow<List<RoomResult>>(repo.loadRooms())
    val isDarkMode = MutableStateFlow<Boolean?>(null) // null = system default

    private var monitorJob: Job? = null

    fun startMonitor() {
        if (monitorJob != null || !mon.hasPerms()) return
        mon.start()
        monitorJob = viewModelScope.launch {
            mon.flow().collect { s ->
                signal.value = s
                s.rsrp?.let { r ->
                    val current = signalHistory.value
                    signalHistory.value = (current + r).takeLast(30)
                }
                perf.value?.let { p ->
                    score.value = Quality.score(s, p)
                }
            }
        }
    }

    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        mon.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitor()
    }

    fun runSpeedTest() {
        if (running.value) return
        running.value = true
        statusText.value = "Running speed test..."
        viewModelScope.launch {
            try {
                val p = SpeedTest.runFullTest(testSec = 5) { prog ->
                    speedProgress.value = prog
                }
                perf.value = p
                score.value = Quality.score(signal.value, p)
            } finally {
                running.value = false
                statusText.value = ""
            }
        }
    }

    fun testRoom(name: String) {
        if (running.value) return
        running.value = true
        val roomLabel = name.trim().ifBlank { "Room ${rooms.value.size + 1}" }
        statusText.value = "Starting survey for $roomLabel..."

        viewModelScope.launch {
            try {
                val rs = mutableListOf<Int>()
                var lastSig: Signal? = null

                if (mon.hasPerms()) {
                    for (i in 1..4) {
                        statusText.value = "Sampling cellular signal ($i/4)..."
                        val s = mon.read()
                        lastSig = s
                        signal.value = s
                        s.rsrp?.let { rs.add(it) }
                        delay(1200)
                    }
                }

                statusText.value = "Running throughput & latency benchmark..."
                val p = SpeedTest.runFullTest(testSec = 4) { prog ->
                    speedProgress.value = prog
                }

                val med = rs.sorted().getOrNull(rs.size / 2)
                val spread = if (rs.size >= 2) rs.max() - rs.min() else null
                val sc = Quality.score(lastSig, p, med, spread)

                val newRoom = RoomResult(
                    name = roomLabel,
                    score = sc?.total,
                    grade = sc?.grade,
                    rsrp = med,
                    spread = spread,
                    perf = p,
                    networkType = lastSig?.net,
                    operator = lastSig?.operator
                )

                rooms.value = repo.saveRoom(newRoom)
                perf.value = p
                score.value = sc
            } finally {
                running.value = false
                statusText.value = ""
            }
        }
    }

    fun deleteRoom(id: String) {
        rooms.value = repo.deleteRoom(id)
    }

    fun clearRooms() {
        rooms.value = repo.clearAll()
    }

    fun toggleDarkMode(systemDark: Boolean) {
        val current = isDarkMode.value ?: systemDark
        isDarkMode.value = !current
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: VM = viewModel()
            val systemDark = isSystemInDarkTheme()
            val userDark by vm.isDarkMode.collectAsState()
            val darkActive = userDark ?: systemDark

            val colorScheme = if (darkActive) {
                darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    onPrimary = Color(0xFF003258),
                    primaryContainer = Color(0xFF00497D),
                    onPrimaryContainer = Color(0xFFD1E4FF),
                    secondary = Color(0xFF4DD0E1),
                    background = Color(0xFF0F141C),
                    surface = Color(0xFF1B232E),
                    surfaceVariant = Color(0xFF263242),
                    onSurface = Color(0xFFE2E8F0)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF006493),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFC8E6FF),
                    onPrimaryContainer = Color(0xFF001E31),
                    secondary = Color(0xFF006874),
                    background = Color(0xFFF6F8FA),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurface = Color(0xFF1E293B)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                App(vm = vm, isDark = darkActive, onToggleDark = { vm.toggleDarkMode(systemDark) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM, isDark: Boolean, onToggleDark: () -> Unit) {
    var granted by remember { mutableStateOf(vm.mon.hasPerms()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = vm.mon.hasPerms()
    }

    LaunchedEffect(granted) {
        if (granted) {
            vm.startMonitor()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val signal by vm.signal.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Smart5G", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    signal?.net?.let { net ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (signal?.is5G == true) {
                                if (isDark) Color(0xFF1B5E20).copy(alpha = 0.6f) else Color(0xFFE8F5E9)
                            } else {
                                if (isDark) Color(0xFF0D47A1).copy(alpha = 0.6f) else Color(0xFFE3F2FD)
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (signal?.is5G == true) Color(0xFF4CAF50) else Color(0xFF2196F3))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    net,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (signal?.is5G == true) Color(0xFF4CAF50) else Color(0xFF64B5F6)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onToggleDark) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    Triple("Dashboard", Icons.Default.Dashboard, 0),
                    Triple("Room Survey", Icons.Default.MeetingRoom, 1),
                    Triple("Signal Info", Icons.Default.NetworkCheck, 2)
                )
                tabs.forEach { (title, icon, idx) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (signal?.isWifiActive == true) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Connected to Wi-Fi",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800),
                                fontSize = 14.sp
                            )
                            Text(
                                "To benchmark real 5G cellular speed and signal coverage, turn off Wi-Fi in Android Settings.",
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFFD7CCC8) else Color(0xFF5D4037)
                            )
                        }
                    }
                }
            }

            if (!granted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Permissions Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cellular modem telemetry (RSRP, RSRQ, SINR, 5G band identification) requires phone and location access.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                launcher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Grant Required Permissions")
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> DashboardScreen(vm, isDark)
                1 -> RoomSurveyScreen(vm, isDark)
                2 -> SignalDetailsScreen(vm, isDark)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun DashboardScreen(vm: VM, isDark: Boolean) {
    val signal by vm.signal.collectAsState()
    val perf by vm.perf.collectAsState()
    val score by vm.score.collectAsState()
    val progress by vm.speedProgress.collectAsState()
    val isRunning by vm.running.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val history by vm.signalHistory.collectAsState()

    SignalMeterCard(signal, isDark)

    if (history.size >= 3) {
        SignalTimelineCard(history, isDark)
    }

    SpeedTestCard(
        perf = perf,
        progress = progress,
        isRunning = isRunning,
        statusText = statusText,
        isDark = isDark,
        onRunTest = { vm.runSpeedTest() }
    )

    score?.let { sc ->
        QualityScoreCard(sc, isDark)
    }
}

@Composable
fun SignalMeterCard(s: Signal?, isDark: Boolean) {
    val rsrp = s?.rsrp
    val quality = s?.qualityLevel ?: SignalQuality.UNKNOWN

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Cellular Signal (Live)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        s?.operator ?: "Searching carrier...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    s?.simInfo?.let { sim ->
                        Text(sim, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(quality.colorHex).copy(alpha = if (isDark) 0.3f else 0.15f)
                ) {
                    Text(
                        quality.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(quality.colorHex),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            val rsrpProgress = if (rsrp != null) ((rsrp + 130).toFloat() / 60f).coerceIn(0f, 1f) else 0f
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Signal Strength (RSRP)", fontSize = 13.sp)
                    Text(rsrp?.let { "$it dBm" } ?: "Unavailable", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { rsrpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(quality.colorHex),
                    trackColor = if (isDark) Color(0xFF2A3644) else Color(0xFFE0E0E0)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF263242) else Color(0xFFEEEEEE))
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricPill(label = "Network", value = s?.net ?: "N/A")
                MetricPill(label = "RSRQ", value = s?.rsrq?.let { "$it dB" } ?: "N/A")
                MetricPill(label = "SINR", value = s?.sinr?.let { "$it dB" } ?: "N/A")
            }
        }
    }
}

@Composable
fun SignalTimelineCard(history: List<Int>, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Signal Stability Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val curr = history.lastOrNull()
                val minR = history.minOrNull() ?: 0
                val maxR = history.maxOrNull() ?: 0
                Text(
                    "Min: $minR | Max: $maxR dBm",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(10.dp))

            // Canvas Line Chart
            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = if (isDark) Color(0xFF2A374A) else Color(0xFFE2E8F0)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            ) {
                val w = size.width
                val h = size.height
                val count = history.size
                if (count < 2) return@Canvas

                val minVal = -125f
                val maxVal = -65f

                // Draw background grid lines
                drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

                val path = Path()
                val fillPath = Path()

                val stepX = w / (count - 1)

                history.forEachIndexed { i, valDbm ->
                    val normY = 1f - ((valDbm.toFloat() - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val x = i * stepX
                    val y = normY * h

                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo((count - 1) * stepX, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f))
                    )
                )

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Current point marker
                val lastVal = history.last()
                val lastY = (1f - ((lastVal.toFloat() - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)) * h
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(w, lastY))
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(w, lastY))
            }
        }
    }
}

@Composable
fun SpeedometerGauge(
    speedMbps: Double,
    isDark: Boolean,
    stageText: String
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speedMbps.toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "SpeedGauge"
    )

    // Map 0 to 500+ Mbps to 0.0..1.0 using logarithmic curve
    val progressFraction = when {
        animatedSpeed <= 0f -> 0f
        animatedSpeed < 10f -> (animatedSpeed / 10f) * 0.2f
        animatedSpeed < 50f -> 0.2f + ((animatedSpeed - 10f) / 40f) * 0.25f
        animatedSpeed < 100f -> 0.45f + ((animatedSpeed - 50f) / 50f) * 0.2f
        animatedSpeed < 300f -> 0.65f + ((animatedSpeed - 100f) / 200f) * 0.2f
        else -> 0.85f + ((animatedSpeed - 300f) / 700f).coerceIn(0f, 1f) * 0.15f
    }.coerceIn(0f, 1f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = if (isDark) Color(0xFF263242) else Color(0xFFE2E8F0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val w = size.width
                val h = size.height
                val strokeW = 16.dp.toPx()
                val startAngle = 150f
                val sweepTotal = 240f

                // Draw background arc
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    size = Size(w, h)
                )

                // Draw active progress arc with gradient
                if (progressFraction > 0.01f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF00B0FF),
                                primaryColor,
                                Color(0xFF7C4DFF)
                            )
                        ),
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * progressFraction,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        size = Size(w, h)
                    )
                }

                // Needle tip dot
                val currentAngle = (startAngle + sweepTotal * progressFraction) * (PI.toFloat() / 180f)
                val radius = w / 2f
                val dotX = center.x + radius * cos(currentAngle)
                val dotY = center.y + radius * sin(currentAngle)
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(dotX, dotY))
                drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = Offset(dotX, dotY))
            }

            // Center Speed Value & Unit
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f".format(animatedSpeed),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Mbps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                if (stageText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stageText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedTestCard(
    perf: Perf?,
    progress: SpeedTestProgress,
    isRunning: Boolean,
    statusText: String,
    isDark: Boolean,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Performance Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isRunning) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            progress.stage.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Animated Radial Speedometer
            AnimatedVisibility(visible = isRunning) {
                SpeedometerGauge(
                    speedMbps = progress.currentMbps,
                    isDark = isDark,
                    stageText = progress.stage.displayName
                )
            }

            // Metric boxes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBox(
                        icon = Icons.Default.ArrowDownward,
                        label = "Download",
                        value = (if (isRunning && progress.downloadMbps != null) progress.downloadMbps else perf?.down)
                            ?.let { "%.1f Mbps".format(it) } ?: "--",
                        isDark = isDark
                    )
                    MetricBox(
                        icon = Icons.Default.ArrowUpward,
                        label = "Upload",
                        value = (if (isRunning && progress.uploadMbps != null) progress.uploadMbps else perf?.up)
                            ?.let { "%.1f Mbps".format(it) } ?: "--",
                        isDark = isDark
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBox(
                        icon = Icons.Default.Timer,
                        label = "Ping / Latency",
                        value = (if (isRunning && progress.pingMs != null) progress.pingMs else perf?.latMs)
                            ?.let { "%.0f ms".format(it) } ?: "--",
                        isDark = isDark
                    )
                    MetricBox(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        label = "Jitter",
                        value = (if (isRunning && progress.jitterMs != null) progress.jitterMs else perf?.jitterMs)
                            ?.let { "%.1f ms".format(it) } ?: "--",
                        isDark = isDark
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onRunTest,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(if (isRunning) Icons.Default.HourglassBottom else Icons.Default.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Running Benchmark..." else "Start Speed Test")
            }
        }
    }
}

@Composable
fun MetricBox(icon: ImageVector, label: String, value: String, isDark: Boolean) {
    Surface(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xFF242E3D) else Color(0xFFF1F5F9)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun QualityScoreCard(score: Score, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Smart5G Quality Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(score.grade, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    shape = CircleShape,
                    color = if (score.total >= 75) {
                        if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                    } else if (score.total >= 50) {
                        if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) else Color(0xFFFFF3E0)
                    } else {
                        if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${score.total}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (score.total >= 75) Color(0xFF4CAF50) else if (score.total >= 50) Color(0xFFFF9800) else Color(0xFFEF5350)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(score.summary, fontSize = 12.sp, color = if (isDark) Color(0xFFCBD5E1) else Color.DarkGray)
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                score.parts.forEach { (name, partScore) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, fontSize = 10.sp, color = Color.Gray)
                        Text("$partScore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF263242) else Color(0xFFEEEEEE))
            Spacer(Modifier.height(12.dp))

            Text("Use-Case Readiness", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                score.useCases.forEach { uc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (uc.suitable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (uc.suitable) Color(0xFF4CAF50) else Color(0xFFEF5350),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(uc.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(uc.rating, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (uc.suitable) Color(0xFF4CAF50) else Color(0xFFEF5350))
                    }
                }
            }

            if (score.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = if (isDark) Color(0xFF263242) else Color(0xFFEEEEEE))
                Spacer(Modifier.height(12.dp))
                Text("Coverage & Placement Advice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                score.recommendations.forEach { rec ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(rec, fontSize = 12.sp, color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF37474F))
                    }
                }
            }
        }
    }
}

@Composable
fun RoomSurveyScreen(vm: VM, isDark: Boolean) {
    val rooms by vm.rooms.collectAsState()
    val isRunning by vm.running.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val context = LocalContext.current
    var roomName by remember { mutableStateOf("") }

    val presets = listOf("Living Room", "Home Office", "Master Bedroom", "Kitchen", "Balcony", "Basement")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Survey Room Coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Map 5G signal quality across each room in your home/office to locate dead zones and optimal 5G router placement.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(12.dp))

            Text("Quick Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { p ->
                    FilterChip(
                        selected = roomName == p,
                        onClick = { roomName = p },
                        label = { Text(p, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("Room name or label") },
                placeholder = { Text("e.g. Study Room, Corner Window") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    vm.testRoom(roomName)
                    roomName = ""
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(if (isRunning) Icons.Default.HourglassBottom else Icons.Default.AddLocationAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Benchmarking Room..." else "Test This Room (~20s)")
            }

            if (isRunning && statusText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (rooms.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Surveyed Rooms (${rooms.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = {
                    val intent = vm.repo.createShareIntent(rooms)
                    context.startActivity(Intent.createChooser(intent, "Share Coverage Survey"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share Report", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.clearRooms() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color.Gray)
                }
            }
        }

        val best = rooms.maxByOrNull { it.score ?: -1 }
        if (best != null && rooms.size > 1) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B3E25) else Color(0xFFE8F5E9)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Recommended 5G CPE / Router Spot: ${best.name}",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                            fontSize = 14.sp
                        )
                        Text(
                            "Achieved ${best.score ?: 0}/100 quality score with ${best.perf.down?.let { "%.1f Mbps".format(it) } ?: "highest"} download speed.",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                        )
                    }
                }
            }
        }

        rooms.sortedByDescending { it.score ?: -1 }.forEachIndexed { idx, r ->
            val isBest = r.id == best?.id && rooms.size > 1
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isBest) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("#${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (isBest) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("⭐ BEST", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(r.formattedDate, fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if ((r.score ?: 0) >= 75) {
                                    if (isDark) Color(0xFF1B5E20).copy(alpha = 0.5f) else Color(0xFFE8F5E9)
                                } else {
                                    if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) else Color(0xFFFFF3E0)
                                }
                            ) {
                                Text(
                                    "${r.score ?: 0}/100",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if ((r.score ?: 0) >= 75) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                            }
                            IconButton(onClick = { vm.deleteRoom(r.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = if (isDark) Color(0xFF263242) else Color(0xFFF0F0F0))
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Download", fontSize = 10.sp, color = Color.Gray)
                            Text(r.perf.down?.let { "%.1f Mbps".format(it) } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Upload", fontSize = 10.sp, color = Color.Gray)
                            Text(r.perf.up?.let { "%.1f Mbps".format(it) } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Ping / Latency", fontSize = 10.sp, color = Color.Gray)
                            Text(r.perf.latMs?.let { "%.0f ms".format(it) } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("RSRP (Median)", fontSize = 10.sp, color = Color.Gray)
                            Text(r.rsrp?.let { "$it dBm" } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    r.spread?.let { sp ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Signal Variance: $sp dB ${if (sp > 10) "(High fluctuation across walls)" else "(Stable signal)"}",
                            fontSize = 11.sp,
                            color = if (sp > 10) Color(0xFFFF9800) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignalDetailsScreen(vm: VM, isDark: Boolean) {
    val signal by vm.signal.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Cellular Hardware & Tower Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Direct low-level diagnostic values reported by the device radio baseband.",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(14.dp))

            DetailRow("Active SIM / Subscription", signal?.simInfo)
            DetailRow("Carrier / Operator", signal?.operator)
            DetailRow("Network Technology", signal?.net)
            DetailRow("Reference Signal Received Power (RSRP)", signal?.rsrp?.let { "$it dBm" })
            DetailRow("Reference Signal Received Quality (RSRQ)", signal?.rsrq?.let { "$it dB" })
            DetailRow("Signal to Interference plus Noise (SINR)", signal?.sinr?.let { "$it dB" })
            DetailRow("Physical Cell ID (PCI)", signal?.pci?.toString())
            DetailRow("Cell Identity (CI)", signal?.ci?.toString())
            DetailRow("Tracking Area Code (TAC)", signal?.tac?.toString())
            DetailRow("Frequency / ARFCN", signal?.bandOrArfcn)
            DetailRow("Wi-Fi Transport Active", if (signal?.isWifiActive == true) "Yes" else "No")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B232E) else Color(0xFFF8F9FA))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("3GPP 5G Signal Quality Reference", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SignalScaleItem("≥ -85 dBm", "Excellent (Ultra fast speeds near base station)", Color(0xFF4CAF50))
            SignalScaleItem("-86 to -98 dBm", "Good (Standard reliable coverage)", Color(0xFF66BB6A))
            SignalScaleItem("-99 to -110 dBm", "Fair (Edge of cell or indoor wall loss)", Color(0xFFFF9800))
            SignalScaleItem("< -110 dBm", "Poor (High packet drop rate / dead zone)", Color(0xFFEF5350))
        }
    }
}

@Composable
fun DetailRow(key: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value ?: "Unavailable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (value == null) Color.Gray else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SignalScaleItem(range: String, desc: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(range, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
        Spacer(Modifier.width(8.dp))
        Text(desc, fontSize = 11.sp, color = Color.Gray)
    }
}
