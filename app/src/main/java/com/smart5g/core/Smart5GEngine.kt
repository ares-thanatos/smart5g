package com.smart5g.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

// ---------- Inputs (every field nullable: null = not measured, never a fake value) ----------

data class RadioSample(val rsrp: Int? = null, val rsrq: Int? = null, val sinr: Int? = null)

data class InternetResult(
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val latencyMs: Double? = null,
    val packetLossPct: Double? = null,
)

/** samples = radio readings collected during monitoring / a scan at one spot. */
data class ScoreInput(val samples: List<RadioSample>, val internet: InternetResult?)

// ---------- Outputs ----------

enum class Confidence { HIGH, MEDIUM, LOW }

enum class Component(val weight: Double, val title: String) {
    RADIO(30.0, "Radio quality"),
    SPEED(30.0, "Download speed"),
    LATENCY(15.0, "Latency"),
    STABILITY(15.0, "Stability"),
    UPLOAD(10.0, "Upload speed"),
}

/** One scored component plus the real measurement behind it (for "Why this score?"). */
data class Part(val score: Double, val raw: String)

data class ScoreResult(
    val score: Int,                       // 0..100
    val label: String,                    // Excellent / Good / Fair / Poor
    val confidence: Confidence,
    val parts: Map<Component, Part>,      // only components that were actually measured
    val availableWeight: Double,          // e.g. 90 when upload wasn't tested
    val why: List<String>,                // strengths
    val reducing: List<String>,           // what is lowering the score
    val fingerprint: String,              // e.g. "EXCELLENT / STABLE"
)

// ---------- Normalisation: practical telecom thresholds, clamped to 0..100 ----------

object Normalize {
    fun linear(v: Double, zeroAt: Double, hundredAt: Double) =
        ((v - zeroAt) / (hundredAt - zeroAt) * 100).coerceIn(0.0, 100.0)

    fun rsrp(dBm: Double) = linear(dBm, -125.0, -80.0)      // <=-125 unusable, >=-80 excellent
    fun rsrq(dB: Double) = linear(dB, -20.0, -10.0)          // <=-20 poor, >=-10 excellent
    fun sinr(dB: Double) = linear(dB, -5.0, 20.0)            // <=-5 poor, >=20 excellent
    fun latency(ms: Double) = linear(ms, 150.0, 20.0)        // >=150 ms poor, <=20 ms excellent
    fun loss(pct: Double) = linear(pct, 5.0, 0.0)            // >=5% poor
    /** Log scale so 20->100 Mbps matters more than 400->480 Mbps. */
    fun throughput(mbps: Double, capMbps: Double) =
        (ln(1 + mbps.coerceAtLeast(0.0)) / ln(1 + capMbps) * 100).coerceIn(0.0, 100.0)
}

// ---------- Scoring ----------

object ScoreEngine {
    private const val MIN_SAMPLES_FOR_STABILITY = 5

    fun compute(input: ScoreInput): ScoreResult? {
        val parts = linkedMapOf<Component, Part>()
        val s = input.samples
        val net = input.internet

        radio(s)?.let { parts[Component.RADIO] = it }
        net?.downMbps?.let { parts[Component.SPEED] = Part(Normalize.throughput(it, 300.0), "${it.fmt()} Mbps") }
        latency(net)?.let { parts[Component.LATENCY] = it }
        stability(s)?.let { parts[Component.STABILITY] = it }
        net?.upMbps?.let { parts[Component.UPLOAD] = Part(Normalize.throughput(it, 50.0), "${it.fmt()} Mbps") }

        if (parts.isEmpty()) return null // UI shows "Not enough data"

        // Missing components are removed and weights re-normalised over what was measured.
        val available = parts.keys.sumOf { it.weight }
        val score = (parts.entries.sumOf { it.key.weight * it.value.score } / available)
            .coerceIn(0.0, 100.0)

        val why = parts.filter { it.value.score >= 75 }.map { (c, p) -> "${c.title}: ${p.raw}" }
        val reducing = parts.filter { it.value.score <= 50 }.map { (c, p) -> "${c.title} is weak (${p.raw})" }

        return ScoreResult(
            score = score.toInt(),
            label = label(score),
            confidence = confidence(available, s.size, net?.downMbps != null),
            parts = parts,
            availableWeight = available,
            why = why,
            reducing = reducing,
            fingerprint = fingerprint(parts),
        )
    }

    fun label(score: Double) = when {
        score >= 85 -> "Excellent"; score >= 70 -> "Good"; score >= 50 -> "Fair"; else -> "Poor"
    }

    /** Radio = weighted mean of the metrics that exist (RSRP 40 / RSRQ 20 / SINR 40), each counted once. */
    private fun radio(s: List<RadioSample>): Part? {
        val rsrp = s.mapNotNull { it.rsrp?.toDouble() }.avg()
        val rsrq = s.mapNotNull { it.rsrq?.toDouble() }.avg()
        val sinr = s.mapNotNull { it.sinr?.toDouble() }.avg()
        val items = listOfNotNull(
            rsrp?.let { Triple(Normalize.rsrp(it), 0.4, "RSRP ${it.fmt()} dBm") },
            rsrq?.let { Triple(Normalize.rsrq(it), 0.2, "RSRQ ${it.fmt()} dB") },
            sinr?.let { Triple(Normalize.sinr(it), 0.4, "SINR ${it.fmt()} dB") },
        )
        if (items.isEmpty()) return null
        val w = items.sumOf { it.second }
        return Part(items.sumOf { it.first * it.second } / w, items.joinToString(", ") { it.third })
    }

    /** Latency, blended with packet loss only when loss was really measured. */
    private fun latency(net: InternetResult?): Part? {
        val ms = net?.latencyMs ?: return null
        val lat = Normalize.latency(ms)
        val loss = net.packetLossPct
        return if (loss == null) Part(lat, "${ms.fmt()} ms")
        else Part((lat + Normalize.loss(loss)) / 2, "${ms.fmt()} ms, ${loss.fmt()}% loss")
    }

    /** Needs enough samples; RSRP std-dev 0 dB -> 100, 8 dB -> 0. SINR: 0 -> 100, 10 dB -> 0. */
    private fun stability(s: List<RadioSample>): Part? {
        if (s.size < MIN_SAMPLES_FOR_STABILITY) return null
        val rsrpSd = s.mapNotNull { it.rsrp?.toDouble() }.takeIf { it.size >= MIN_SAMPLES_FOR_STABILITY }?.std()
        val sinrSd = s.mapNotNull { it.sinr?.toDouble() }.takeIf { it.size >= MIN_SAMPLES_FOR_STABILITY }?.std()
        val scores = listOfNotNull(
            rsrpSd?.let { 100 - Normalize.linear(it, 0.0, 8.0) },
            sinrSd?.let { 100 - Normalize.linear(it, 0.0, 10.0) },
        )
        if (scores.isEmpty()) return null
        val sd = rsrpSd ?: sinrSd!!
        return Part(scores.average(), "variation ±${sd.fmt()} dB over ${s.size} samples")
    }

    /** Confidence is separate from the score: coverage of metrics + sample count, capped without a speed test. */
    private fun confidence(availableWeight: Double, samples: Int, speedTested: Boolean): Confidence {
        val coverage = availableWeight / 100.0
        val sampleFactor = when { samples >= 10 -> 1.0; samples >= 5 -> 0.7; else -> 0.4 }
        val c = 0.6 * coverage + 0.4 * sampleFactor
        return when {
            c >= 0.8 && speedTested -> Confidence.HIGH
            c >= 0.55 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    private fun fingerprint(parts: Map<Component, Part>): String {
        val overall = parts.entries.sumOf { it.key.weight * it.value.score } / parts.keys.sumOf { it.weight }
        val stab = parts[Component.STABILITY]?.score
        val stabText = when { stab == null -> "STABILITY UNKNOWN"; stab >= 70 -> "STABLE"; else -> "UNSTABLE" }
        return "${label(overall).uppercase()} / $stabText"
    }
}

// ---------- Connection change alerts ----------

data class ChangeAlert(val from: Int, val to: Int, val improved: Boolean, val reason: String)

object ChangeDetector {
    /** Only fires on a large move, and only when both scores are at least Medium confidence. */
    fun detect(prev: ScoreResult?, curr: ScoreResult, threshold: Int = 15): ChangeAlert? {
        if (prev == null || prev.confidence == Confidence.LOW || curr.confidence == Confidence.LOW) return null
        val delta = curr.score - prev.score
        if (abs(delta) < threshold) return null
        val biggest = Component.entries
            .filter { it in prev.parts && it in curr.parts }
            .maxByOrNull { abs(curr.parts.getValue(it).score - prev.parts.getValue(it).score) }
        val reason = biggest?.let {
            val better = curr.parts.getValue(it).score > prev.parts.getValue(it).score
            "${it.title} ${if (better) "improved" else "decreased"} significantly."
        } ?: "Overall conditions changed."
        return ChangeAlert(prev.score, curr.score, delta > 0, reason)
    }
}

// ---------- Best Measured Spot ----------

data class Spot(val name: String, val result: ScoreResult, val downMbps: Double?, val latencyMs: Double?)

data class Recommendation(val best: Spot, val qualityGain: Int, val downloadGainMbps: Double?)

object BestSpot {
    /** "Best MEASURED spot": highest score among spots the user actually scanned. Null if data can't support advice. */
    fun pick(spots: List<Spot>): Spot? =
        spots.filter { it.result.confidence != Confidence.LOW }.maxByOrNull { it.result.score }

    /** Recommend only when a measured spot is clearly better than the current one. Nothing is extrapolated. */
    fun recommend(current: Spot, spots: List<Spot>, minGain: Int = 8): Recommendation? {
        val best = pick(spots) ?: return null
        if (best.name == current.name || current.result.confidence == Confidence.LOW) return null
        val gain = best.result.score - current.result.score
        if (gain < minGain) return null
        val dl = if (best.downMbps != null && current.downMbps != null) best.downMbps - current.downMbps else null
        return Recommendation(best, gain, dl?.takeIf { it > 0 })
    }
}

// ---------- helpers ----------

private fun List<Double>.avg(): Double? = if (isEmpty()) null else average()
private fun List<Double>.std(): Double {
    val m = average()
    return sqrt(sumOf { (it - m) * (it - m) } / size)
}
private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
