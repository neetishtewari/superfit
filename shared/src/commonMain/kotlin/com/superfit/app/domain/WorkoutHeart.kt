package com.superfit.app.domain

import kotlin.math.roundToInt

/** One heart-rate reading during a workout. */
data class HeartSample(val timeMillis: Long, val bpm: Int)

/** How hard the heart is working, as a share of the user's estimated maximum heart rate. */
enum class Effort(val label: String) {
    EASY("Easy"),
    STEADY("Steady"),
    HARD("Hard"),
    MAX("Max")
}

/**
 * A workout's heart rate, smoothed for a small line on a share card. [x] runs from 0 (start) to
 * 1 (end); [efforts] is empty when the user's age is unknown, so the line is drawn in one colour.
 */
data class HeartLine(
    val x: List<Float>,
    val bpm: List<Int>,
    val efforts: List<Effort>,
    val peakBpm: Int,
    /** Index into [x]/[bpm] of the point that holds the highest reading. */
    val peakIndex: Int,
    /** Whole minutes in each effort band; bands under a minute are left out. Empty without an age. */
    val effortMinutes: Map<Effort, Int>
)

object WorkoutHeart {

    /** Fewer readings than this make a line that says nothing, so the card stays as it was. */
    const val MIN_SAMPLES = 20

    /** Points on the drawn line. */
    const val POINTS = 60

    /** A gap longer than this between readings (watch off the wrist) isn't counted as time in a band. */
    private const val MAX_GAP_MILLIS = 60_000L

    /** Estimated maximum heart rate (Tanaka: 208 - 0.7 x age); null for an age that can't be right. */
    fun maxHeartRate(age: Int): Int? = if (age in 10..100) (208 - 0.7 * age).roundToInt() else null

    /** Easy below 70% of max, Steady 70-80%, Hard 80-90%, Max 90% and up. */
    fun effort(bpm: Int, maxHr: Int): Effort {
        val share = bpm.toDouble() / maxHr
        return when {
            share >= 0.9 -> Effort.MAX
            share >= 0.8 -> Effort.HARD
            share >= 0.7 -> Effort.STEADY
            else -> Effort.EASY
        }
    }

    /**
     * The line for a workout from [startMillis] to [endMillis], or null with too few readings.
     * Readings outside the workout or with impossible values are dropped.
     */
    fun line(samples: List<HeartSample>, startMillis: Long, endMillis: Long, age: Int?, points: Int = POINTS): HeartLine? {
        if (endMillis <= startMillis) return null
        val usable = samples
            .filter { it.timeMillis in startMillis..endMillis && it.bpm in 30..230 }
            .sortedBy { it.timeMillis }
        if (usable.size < MIN_SAMPLES) return null
        val maxHr = age?.let(::maxHeartRate)
        val span = (endMillis - startMillis).toDouble()

        // Average the readings in equal slices of the workout; slices without readings are skipped.
        val buckets = usable.groupBy { ((it.timeMillis - startMillis) / span * points).toInt().coerceIn(0, points - 1) }
            .entries.sortedBy { it.key }.associate { it.key to it.value }
        val x = buckets.keys.map { (it + 0.5f) / points }
        val bpm = buckets.values.map { list -> list.map { it.bpm }.average().roundToInt() }
        val peak = usable.maxBy { it.bpm }
        val peakBucket = ((peak.timeMillis - startMillis) / span * points).toInt().coerceIn(0, points - 1)
        val peakIndex = buckets.keys.indexOf(peakBucket).coerceAtLeast(0)

        val efforts = if (maxHr != null) bpm.map { effort(it, maxHr) } else emptyList()
        val minutes = if (maxHr != null) effortMinutes(usable, maxHr) else emptyMap()
        return HeartLine(x, bpm, efforts, peak.bpm, peakIndex, minutes)
    }

    /** Each reading counts until the next one (up to a minute), in that reading's band. */
    fun effortMinutes(sorted: List<HeartSample>, maxHr: Int): Map<Effort, Int> {
        val millis = LinkedHashMap<Effort, Long>()
        sorted.zipWithNext().forEach { (a, b) ->
            val gap = b.timeMillis - a.timeMillis
            if (gap in 1..MAX_GAP_MILLIS) {
                val band = effort(a.bpm, maxHr)
                millis[band] = (millis[band] ?: 0L) + gap
            }
        }
        return Effort.values()
            .mapNotNull { band -> millis[band]?.let { band to (it / 60_000.0).roundToInt() } }
            .filter { it.second >= 1 }
            .toMap()
    }
}
