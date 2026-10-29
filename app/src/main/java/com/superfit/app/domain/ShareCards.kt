package com.superfit.app.domain

import com.superfit.app.data.WorkoutEntryEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One number on a card: "PROTEIN 132 g", with an optional bar and a small line under it. */
data class CardStat(
    val label: String,
    val value: String,
    val unit: String? = null,
    val note: String? = null,
    // 0..1 for a progress bar; null for none.
    val progress: Float? = null
)

/** What a share picture shows. Weight and calories eaten are never on a card. */
sealed interface ShareCard {
    val dateLine: String

    data class Daily(
        override val dateLine: String,
        val streakDays: Int,
        val tiles: List<CardStat>,
        val coachLine: String?,
        // "Roast my day": the coach line is the AI coach's roast of the day.
        val roast: Boolean = false
    ) : ShareCard

    data class Activity(
        override val dateLine: String,
        val heading: String,
        val bigValue: String,
        val bigUnit: String,
        val subtitle: String?,
        val stats: List<CardStat>
    ) : ShareCard

    data class Best(
        override val dateLine: String,
        val isNew: Boolean,
        val label: String,
        val bigValue: String,
        val bigUnit: String,
        val detail: String?,
        val previous: String?,
        val gain: String?,
        // A 2nd or 3rd best run rather than the best itself.
        val podium: Boolean = false
    ) : ShareCard
}

/** Builds share cards from what the app already shows. Anything missing is left out, never shown as zero. */
object ShareCards {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    /** The day's longest workout for the daily card's workout tile. */
    data class DayWorkout(val value: String, val note: String)

    fun daily(
        date: LocalDate,
        streakDays: Int,
        mealsToday: Int,
        proteinEatenG: Double,
        proteinGoalG: Double,
        dietScore: Int?,
        steps: Int,
        workout: DayWorkout?,
        coachLine: String?
    ): ShareCard.Daily {
        val tiles = buildList {
            // A day with no meals logged is a blank day, not a day of eating nothing.
            if (mealsToday > 0) {
                add(
                    CardStat(
                        label = "PROTEIN",
                        value = "${proteinEatenG.roundToInt()}",
                        unit = "g",
                        note = if (proteinGoalG > 0) "of ${proteinGoalG.roundToInt()} g goal" else null,
                        progress = if (proteinGoalG > 0) (proteinEatenG / proteinGoalG).toFloat().coerceIn(0f, 1f) else null
                    )
                )
                if (dietScore != null) {
                    add(CardStat("DIET QUALITY", "$dietScore", "/100", "7-day score", dietScore / 100f))
                }
            }
            if (steps > 0) add(CardStat("STEPS", NumberFormat.getIntegerInstance().format(steps)))
            if (workout != null) add(CardStat("WORKOUT", workout.value, note = workout.note))
        }
        return ShareCard.Daily(
            dateLine = date.format(dayFormat),
            streakDays = streakDays,
            tiles = tiles,
            coachLine = coachLine?.let(::plain)?.takeIf { it.isNotBlank() }
        )
    }

    /** The day's longest imported workout, or else the first voice-logged one. */
    fun dayWorkout(imported: List<WorkoutView>, voiceLogged: List<WorkoutEntryEntity>, zone: ZoneId = ZoneId.systemDefault()): DayWorkout? {
        imported.maxByOrNull { it.minutes }?.let { w ->
            val value = w.distanceKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "${w.minutes} min"
            return DayWorkout(value, "${partOfDay(w.startTime, zone)} ${w.noun}")
        }
        voiceLogged.firstOrNull()?.let { e ->
            return DayWorkout("${e.caloriesBurned.roundToInt()} kcal", e.description)
        }
        return null
    }

    fun activity(w: WorkoutView, zone: ZoneId = ZoneId.systemDefault()): ShareCard.Activity {
        val start = Instant.ofEpochMilli(w.startTime).atZone(zone)
        val hasDistance = w.distanceKm != null
        val stats = buildList {
            if (hasDistance) add(CardStat("TIME", duration(w.merged.durationMillis)))
            w.paceSecondsPerKm?.let { add(CardStat("PACE", pace(it), "/km")) }
            w.speedKmh?.let { add(CardStat("SPEED", String.format(Locale.US, "%.1f", it), "km/h")) }
            w.avgHeartRate?.let { add(CardStat("AVG HEART RATE", "$it", "bpm")) }
            w.exerciseKcal?.let { add(CardStat("BURNED", "${it.roundToInt()}", "kcal")) }
        }
        return ShareCard.Activity(
            dateLine = "${start.format(dayFormat)} · ${start.format(clock)}",
            heading = "${partOfDay(w.startTime, zone)} ${w.noun}".uppercase(Locale.getDefault()),
            bigValue = w.distanceKm?.let { String.format(Locale.US, "%.2f", it) } ?: "${w.minutes}",
            bigUnit = if (hasDistance) "km" else "min",
            subtitle = null,
            stats = stats
        )
    }

    /** A voice-logged workout has what the user said and its calories, nothing more. */
    fun activity(e: WorkoutEntryEntity, zone: ZoneId = ZoneId.systemDefault()): ShareCard.Activity {
        val start = Instant.ofEpochMilli(e.timestamp).atZone(zone)
        return ShareCard.Activity(
            dateLine = "${start.format(dayFormat)} · ${start.format(clock)}",
            heading = "${partOfDay(e.timestamp, zone)} workout".uppercase(Locale.getDefault()),
            bigValue = "${e.caloriesBurned.roundToInt()}",
            bigUnit = "kcal",
            subtitle = e.description,
            stats = emptyList()
        )
    }

    fun best(b: PersonalBest, isNew: Boolean, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ShareCard.Best {
        val date = b.workout?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: b.periodStart ?: today
        val dateLine = when (b.kind.period) {
            BestPeriod.WEEK -> "Week of " + date.format(shortDate)
            BestPeriod.MONTH -> date.format(monthFormat)
            else -> date.format(dayFormat)
        }
        // Week and month bests read fine without the date of the one they beat.
        val datedPrevious = b.kind.period == BestPeriod.WORKOUT || b.kind.period == BestPeriod.DAY
        val previousDate = b.previousTime?.takeIf { datedPrevious }
            ?.let { " · " + Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(shortDate) }
            .orEmpty()
        val prev = b.previousValue.takeIf { b.rank == 1 }
        return when {
            b.kind.isPace -> ShareCard.Best(
                dateLine = dateLine,
                isNew = isNew,
                label = b.title,
                bigValue = pace(b.value.roundToLong()),
                bigUnit = "/km",
                detail = b.workout?.let { w ->
                    listOfNotNull(
                        w.distanceKm?.let { String.format(Locale.US, "%.2f km run", it) },
                        duration(w.merged.durationMillis)
                    ).joinToString(" · ")
                },
                previous = prev?.let { pace(it.roundToLong()) + " /km" + previousDate },
                gain = prev?.let { "${(it - b.value).roundToInt()} s/km faster" },
                podium = b.rank > 1
            )
            b.kind.isSteps -> ShareCard.Best(
                dateLine = dateLine,
                isNew = isNew,
                label = b.title,
                bigValue = PersonalBests.stepsText(b.value),
                bigUnit = "steps",
                detail = null,
                previous = prev?.let { PersonalBests.stepsText(it) + " steps" + previousDate },
                gain = prev?.let { "+" + PersonalBests.stepsText(b.value - it) + " steps" }
            )
            else -> ShareCard.Best(
                dateLine = dateLine,
                isNew = isNew,
                label = b.title,
                bigValue = String.format(Locale.US, "%.2f", b.value),
                bigUnit = "km",
                detail = b.workout?.let { w ->
                    listOfNotNull(duration(w.merged.durationMillis), w.paceSecondsPerKm?.let { pace(it) + " /km" }).joinToString(" · ")
                } ?: "running and walking",
                previous = prev?.let { String.format(Locale.US, "%.2f km", it) + previousDate },
                gain = prev?.let { String.format(Locale.US, "+%.2f km", abs(b.value - it)) },
                podium = b.rank > 1
            )
        }
    }

    /** "Morning", "Afternoon", "Evening" or "Night", the way runners name workouts. */
    fun partOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        when (Instant.ofEpochMilli(millis).atZone(zone).hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }

    /** "48:12", or "1:09:40" past an hour. */
    fun duration(millis: Long): String {
        val total = (millis / 1000.0).roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    private fun pace(secondsPerKm: Long): String = String.format(Locale.US, "%d:%02d", secondsPerKm / 60, secondsPerKm % 60)

    /** The coach writes **bold**; a picture shows plain text. */

    /** The daily card with the AI coach's roast in place of the coach line. */
    fun roast(daily: ShareCard.Daily, line: String): ShareCard.Daily =
        daily.copy(coachLine = fitRoast(plain(line)).takeIf { it.isNotBlank() }, roast = true)

    /** The whole roast fits: the card shrinks the text to fit rather than cutting it. */
    const val ROAST_MAX = 600

    /** The opening line, when it's short, becomes the card's headline and the rest sits under it. */
    fun splitRoast(text: String): Pair<String, String?> {
        val parts = Regex("""(?<=[.!?])\s+""").split(text, limit = 2)
        val rest = parts.getOrNull(1)?.trim()
        return if (parts[0].length <= 90 && !rest.isNullOrEmpty()) parts[0] to rest else text to null
    }

    /** Whole sentences that fit in [ROAST_MAX]; a first sentence that's too long is cut at a word. */
    fun fitRoast(text: String): String {
        if (text.length <= ROAST_MAX) return text
        val sentences = Regex("""(?<=[.!?])\s+""").split(text)
        val kept = StringBuilder()
        for (s in sentences) {
            val next = if (kept.isEmpty()) s else "$kept $s"
            if (next.length > ROAST_MAX) break
            kept.clear().append(next)
        }
        if (kept.isNotEmpty()) return kept.toString()
        return text.take(ROAST_MAX - 1).substringBeforeLast(' ').trimEnd(',', ';', ':', '-', '—') + "…"
    }

    private fun plain(text: String): String = text.replace("**", "").trim()
}
