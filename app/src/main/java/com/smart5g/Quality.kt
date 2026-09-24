package com.smart5g

data class UseCaseGrade(
    val name: String,
    val suitable: Boolean,
    val rating: String,
    val detail: String
)

data class Score(
    val total: Int,
    val grade: String,
    val summary: String,
    val parts: Map<String, Int>,
    val useCases: List<UseCaseGrade> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * Smart5G Quality Score (0-100).
 * Combines throughput, latency, jitter, and cellular RSRP.
 * Also derives use-case suitability and actionable placement recommendations.
 */
object Quality {
    private fun c(x: Double) = x.coerceIn(0.0, 100.0)

    fun score(s: Signal?, p: Perf?, rsrp: Int? = s?.rsrp, rsrpSpread: Int? = null): Score? {
        val m = linkedMapOf<String, Pair<Double, Double>>() // name -> (score, weight)
        p?.down?.let { m["Download"] = c(it) to .30 }               // 100 Mbps = 100
        p?.up?.let { m["Upload"] = c(it * 2) to .15 }               // 50 Mbps = 100
        p?.latMs?.let { m["Latency"] = c(100 - it / 1.5) to .20 }   // 150 ms = 0
        p?.jitterMs?.let { m["Jitter"] = c(100 - it * 4) to .10 }   // 25 ms = 0
        rsrp?.let { m["Signal"] = c((it + 120) * 2.0) to .25 }      // -120 dBm = 0, -70 dBm = 100

        if (m.isEmpty()) return null
        val totalWeight = m.values.sumOf { it.second }
        val totalScore = (m.values.sumOf { it.first * it.second } / totalWeight).toInt().coerceIn(0, 100)

        val grade = when {
            totalScore >= 85 -> "Excellent (5G Ultra)"
            totalScore >= 70 -> "Good Performance"
            totalScore >= 50 -> "Fair / Moderate"
            else -> "Poor / Degraded"
        }

        val summary = when {
            totalScore >= 85 -> "Exceptional cellular performance with low latency and high bandwidth."
            totalScore >= 70 -> "Solid connection capable of smooth HD/4K streaming and daily demands."
            totalScore >= 50 -> "Acceptable for web browsing and standard streaming, but may buffer under load."
            else -> "Weak connection. Prone to packet drops and high latency."
        }

        // Use case suitability
        val useCases = mutableListOf<UseCaseGrade>()
        val down = p?.down ?: 0.0
        val up = p?.up ?: 0.0
        val lat = p?.latMs ?: 999.0
        val jit = p?.jitterMs ?: 999.0
        val sig = rsrp ?: -130

        // 1. 4K / 8K Video Streaming
        val streamPass = down >= 25.0 && lat <= 120.0
        useCases += UseCaseGrade(
            name = "4K / UHD Streaming",
            suitable = streamPass,
            rating = if (down >= 50.0) "Optimal" else if (streamPass) "Supported" else "Buffering likely",
            detail = "Down: ${"%.1f".format(down)} Mbps (requires 25+ Mbps)"
        )

        // 2. Video Calls / Remote Work
        val callPass = down >= 8.0 && up >= 3.0 && lat <= 80.0 && jit <= 20.0
        useCases += UseCaseGrade(
            name = "Work & Video Calls",
            suitable = callPass,
            rating = if (callPass) "Smooth HD" else "Potential lag / stutter",
            detail = "Up: ${"%.1f".format(up)} Mbps, Ping: ${"%.0f".format(lat)} ms"
        )

        // 3. Online Gaming
        val gamePass = lat <= 45.0 && jit <= 10.0
        useCases += UseCaseGrade(
            name = "Online Gaming",
            suitable = gamePass,
            rating = if (lat <= 28.0 && jit <= 5.0) "Pro Grade" else if (gamePass) "Good" else "High Latency",
            detail = "Ping: ${"%.0f".format(lat)} ms, Jitter: ${"%.1f".format(jit)} ms"
        )

        // 4. 5G Home Router / CPE Placement
        val routerPass = totalScore >= 72 && sig >= -96
        useCases += UseCaseGrade(
            name = "5G Router Placement",
            suitable = routerPass,
            rating = if (routerPass) "Ideal Spot" else "Suboptimal Spot",
            detail = if (routerPass) "Great location for fixed wireless CPE" else "Signal penetration too low"
        )

        // Actionable placement recommendations
        val recs = mutableListOf<String>()
        if (rsrpSpread != null && rsrpSpread > 10) {
            recs += "Signal fluctuates by $rsrpSpread dB in this location. Obstacles or reflective glass cause multipath fading; try placing router away from interior walls."
        }
        if (sig < -100) {
            recs += "Cellular RSRP is weak ($sig dBm). Move closer to a window with an unobstructed line toward the nearest cellular tower."
        }
        if (jit > 15.0) {
            recs += "High packet jitter detected (${"%.1f".format(jit)} ms). Cellular base station is likely under congestion."
        }
        if (up < 2.0 && p?.up != null) {
            recs += "Uplink is restricted (${"%.1f".format(up)} Mbps). 5G uplink uses lower transmit power; orient device toward open space."
        }
        if (recs.isEmpty() && totalScore >= 75) {
            recs += "This room is prime for work, gaming, and 5G Home Internet gateway positioning."
        }

        return Score(
            total = totalScore,
            grade = grade,
            summary = summary,
            parts = m.mapValues { it.value.first.toInt() },
            useCases = useCases,
            recommendations = recs
        )
    }
}
