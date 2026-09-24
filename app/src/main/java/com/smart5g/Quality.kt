package com.smart5g

import com.smart5g.core.Confidence
import com.smart5g.core.InternetResult
import com.smart5g.core.RadioSample
import com.smart5g.core.ScoreEngine
import com.smart5g.core.ScoreInput
import com.smart5g.core.ScoreResult

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
    val rawParts: Map<String, String> = emptyMap(),
    val confidence: Confidence = Confidence.LOW,
    val fingerprint: String = "",
    val why: List<String> = emptyList(),
    val reducing: List<String> = emptyList(),
    val useCases: List<UseCaseGrade> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val coreResult: ScoreResult? = null
)

/**
 * Smart5G Quality Score (0-100).
 * Grounded in telecom standards via ScoreEngine (empirical weighting, logarithmic throughput scaling,
 * stability variance analysis, and confidence rating).
 */
object Quality {
    fun score(
        samples: List<RadioSample>,
        p: Perf?,
        rsrpSpread: Int? = null
    ): Score? {
        if (samples.isEmpty() && p == null) return null

        val internet = p?.let {
            InternetResult(
                downMbps = it.down,
                upMbps = it.up,
                latencyMs = it.latMs,
                packetLossPct = null
            )
        }

        val input = ScoreInput(samples = samples, internet = internet)
        val core = ScoreEngine.compute(input) ?: return null

        val totalScore = core.score
        val grade = core.label

        val summary = when {
            totalScore >= 85 -> "Exceptional cellular performance with low latency and high bandwidth."
            totalScore >= 70 -> "Solid connection capable of smooth HD/4K streaming and daily demands."
            totalScore >= 50 -> "Acceptable for web browsing and standard streaming, but may buffer under load."
            else -> "Weak connection. Prone to packet drops and high latency."
        }

        val parts = core.parts.mapKeys { it.key.title }.mapValues { it.value.score.toInt() }
        val rawParts = core.parts.mapKeys { it.key.title }.mapValues { it.value.raw }

        // Use case suitability
        val useCases = mutableListOf<UseCaseGrade>()
        val down = p?.down ?: 0.0
        val up = p?.up ?: 0.0
        val lat = p?.latMs ?: 999.0
        val jit = p?.jitterMs ?: 999.0
        val latestRsrp = samples.lastOrNull { it.rsrp != null }?.rsrp ?: -130

        // 1. 4K / UHD Streaming
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
        val routerPass = totalScore >= 72 && latestRsrp >= -96
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
        if (latestRsrp < -100) {
            recs += "Cellular RSRP is weak ($latestRsrp dBm). Move closer to a window with an unobstructed line toward the nearest cellular tower."
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
            parts = parts,
            rawParts = rawParts,
            confidence = core.confidence,
            fingerprint = core.fingerprint,
            why = core.why,
            reducing = core.reducing,
            useCases = useCases,
            recommendations = recs,
            coreResult = core
        )
    }

    fun score(s: Signal?, p: Perf?, rsrp: Int? = s?.rsrp, rsrpSpread: Int? = null): Score? {
        val sample = RadioSample(
            rsrp = rsrp ?: s?.rsrp,
            rsrq = s?.rsrq,
            sinr = s?.sinr
        )
        return score(listOf(sample), p, rsrpSpread)
    }
}
