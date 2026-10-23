package com.superfit.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One step on a smart scale, as its app saved it to Health Connect: weight plus whatever else the
 * scale measured at the same moment. [id] is the weight record's Health Connect id.
 */
data class ScaleReading(
    val id: String,
    val time: Instant,
    val weightKg: Double,
    val source: String,
    val bodyFatPct: Double? = null,
    val leanKg: Double? = null,
    val boneKg: Double? = null,
    val bmrKcal: Double? = null
)

/** A single body measurement from Health Connect, before it's matched to a weigh-in. */
data class BodyValue(val time: Instant, val source: String, val value: Double)

/** What we know of the user's own body from readings they accepted, to recognise their next one. */
data class Baseline(val weightKg: Double?, val bodyFatPct: Double?)

enum class ReadingMatch { MINE, ASK, NOT_ME }

/**
 * Smart scale readings: grouping, telling the user's readings apart from someone else's on the same
 * scale, and the Body card. Health Connect can't say which person in the scale app a reading belongs
 * to, so a reading far from the user's own weight or body fat is asked about instead of added.
 */
object BodyReadings {

    /** Further than this from the user's recent weight: ask. Whichever of the two is smaller. */
    const val WEIGHT_GAP_KG = 5.0
    const val WEIGHT_GAP_SHARE = 0.07

    /** Further than this many body fat points from the user's last accepted reading: ask. */
    const val BODY_FAT_GAP = 4.0

    /** A body fat reading older than this no longer sets the calorie target. */
    const val BODY_FAT_VALID_DAYS = 45L

    private val PAIR_WINDOW: Duration = Duration.ofMinutes(10)

    /** Puts body fat, lean mass, bone mass and BMR saved by the same app within minutes onto their weigh-in. */
    fun group(
        weights: List<ScaleReading>,
        bodyFat: List<BodyValue>,
        lean: List<BodyValue>,
        bone: List<BodyValue>,
        bmr: List<BodyValue>
    ): List<ScaleReading> = weights.sortedBy { it.time }.map { w ->
        fun near(list: List<BodyValue>) = list
            .filter { it.source == w.source && Duration.between(it.time, w.time).abs() <= PAIR_WINDOW }
            .minByOrNull { Duration.between(it.time, w.time).abs() }?.value
        w.copy(bodyFatPct = near(bodyFat), leanKg = near(lean), boneKg = near(bone), bmrKcal = near(bmr))
    }

    /**
     * MINE when the reading sits close to the user's own weight and, when both are known, body fat.
     * The first reading that carries body fat is always asked about, so a partner's reading can't
     * become the baseline by stepping on the scale first.
     */
    fun classify(r: ScaleReading, baseline: Baseline): ReadingMatch {
        val w = baseline.weightKg
        if (w != null) {
            val gap = minOf(WEIGHT_GAP_KG, w * WEIGHT_GAP_SHARE)
            if (abs(r.weightKg - w) > gap) return ReadingMatch.ASK
        }
        val fat = r.bodyFatPct ?: return ReadingMatch.MINE
        val known = baseline.bodyFatPct ?: return ReadingMatch.ASK
        return if (abs(fat - known) > BODY_FAT_GAP) ReadingMatch.ASK else ReadingMatch.MINE
    }

    /**
     * Like [classify], but a reading that isn't clearly the user's and looks like one they already
     * said wasn't them (a partner on the same scale) is set aside without asking again.
     */
    fun decide(r: ScaleReading, baseline: Baseline, notMe: List<ScaleReading>): ReadingMatch {
        val match = classify(r, baseline)
        if (match == ReadingMatch.MINE) return match
        val looksLikeSomeoneElse = notMe.any { other ->
            abs(other.weightKg - r.weightKg) <= 2.0 &&
                (r.bodyFatPct == null || other.bodyFatPct == null || abs(other.bodyFatPct - r.bodyFatPct) <= 3.0)
        }
        return if (looksLikeSomeoneElse) ReadingMatch.NOT_ME else ReadingMatch.ASK
    }

    /** Resting calories from lean mass (Katch-McArdle), which counts muscle that a weight-only formula can't. */
    fun leanMassBmr(weightKg: Double, bodyFatPct: Double): Double = 370.0 + 21.6 * weightKg * (1 - bodyFatPct / 100.0)

    fun bmi(weightKg: Double, heightCm: Double): Double? =
        if (heightCm <= 0) null else weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    /** The Body card: latest of each measurement the user's scale sends, and the change over 4 weeks. */
    fun summary(mine: List<ScaleReading>, latestWeightKg: Double?, heightCm: Double, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BodySummary {
        val sorted = mine.sortedBy { it.time }
        val monthAgo = today.minusDays(28).atStartOfDay(zone).toInstant()

        fun latest(f: (ScaleReading) -> Double?) = sorted.lastOrNull { f(it) != null }?.let { it to f(it)!! }
        fun change(f: (ScaleReading) -> Double?): Double? {
            val now = latest(f) ?: return null
            val then = sorted.lastOrNull { f(it) != null && !it.time.isAfter(monthAgo) }
                ?: sorted.firstOrNull { f(it) != null && it.time.isBefore(now.first.time) && Duration.between(it.time, now.first.time).toDays() >= 14 }
                ?: return null
            return now.second - f(then)!!
        }

        val fat = latest { it.bodyFatPct }
        val fatChange = change { it.bodyFatPct }
        val weightChange = change { it.weightKg }
        val leanChange = change { it.leanKg ?: it.bodyFatPct?.let { f -> it.weightKg * (1 - f / 100) } }
        val weight = latestWeightKg ?: sorted.lastOrNull()?.weightKg

        return BodySummary(
            bodyFatPct = fat?.second,
            bodyFatChange = fatChange,
            leanKg = latest { it.leanKg }?.second,
            leanChange = if (latest { it.leanKg } != null) change { it.leanKg } else null,
            boneKg = latest { it.boneKg }?.second,
            scaleBmr = latest { it.bmrKcal }?.second,
            bmi = weight?.let { bmi(it, heightCm) },
            source = sorted.lastOrNull { it.bodyFatPct != null }?.source,
            measuredOn = fat?.first?.time?.atZone(zone)?.toLocalDate(),
            insight = insight(weightChange, fatChange, leanChange)
        )
    }

    /** "Down 2.1 kg in 4 weeks, body fat down 1.4 points: mostly fat, not muscle." */
    fun insight(weightChange: Double?, fatChange: Double?, leanChange: Double?): String? {
        if (weightChange == null || fatChange == null || leanChange == null) return null
        if (abs(weightChange) < 0.5) {
            return if (fatChange <= -0.8) "Weight steady while body fat fell ${fmt(-fatChange)} points: you're swapping fat for muscle." else null
        }
        val fatKg = weightChange - leanChange
        val dir = if (weightChange < 0) "Down" else "Up"
        val head = "$dir ${fmt(abs(weightChange))} kg in about 4 weeks, body fat ${if (fatChange <= 0) "down" else "up"} ${fmt(abs(fatChange))} points"
        val share = if (weightChange != 0.0) fatKg / weightChange else 0.0
        return when {
            weightChange < 0 && share >= 0.7 -> "$head: that's mostly fat, not muscle."
            weightChange < 0 && share < 0.4 -> "$head: a lot of it is muscle. More protein and some strength work help keep it."
            weightChange > 0 && share <= 0.3 -> "$head: that's mostly muscle."
            weightChange > 0 && share >= 0.7 -> "$head: that's mostly fat."
            else -> "$head."
        }
    }

    fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
}

data class BodySummary(
    val bodyFatPct: Double?,
    val bodyFatChange: Double?,
    val leanKg: Double?,
    val leanChange: Double?,
    val boneKg: Double?,
    val scaleBmr: Double?,
    val bmi: Double?,
    /** The scale app the latest body fat came from, as people know it ("FitDays"). */
    val source: String?,
    val measuredOn: LocalDate?,
    val insight: String?
) {
    val hasScaleData: Boolean get() = bodyFatPct != null || leanKg != null || boneKg != null || scaleBmr != null
}

/**
 * The user's latest accepted body fat reading, which [PhysiologyEngine.calculateBmr] uses for the
 * calorie target. Loaded from the phone at start-up and updated when a reading is accepted.
 */
object MeasuredBody {
    @Volatile var bodyFatPct: Double? = null

    @Volatile var measuredOn: LocalDate? = null

    fun currentBodyFat(today: LocalDate = LocalDate.now()): Double? {
        val on = measuredOn ?: return null
        return bodyFatPct?.takeIf { !on.isBefore(today.minusDays(BodyReadings.BODY_FAT_VALID_DAYS)) && it in 3.0..70.0 }
    }
}
