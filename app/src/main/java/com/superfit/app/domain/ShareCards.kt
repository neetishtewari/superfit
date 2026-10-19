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
        val coachLine: String?
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
        val gain: String?
    ) : ShareCard
}

/** Builds share cards from what the app already shows. Anything missing is left out, never shown as zero. */
object ShareCards {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
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
        val date = b.workout?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: today
        val previousDate = b.previousTime?.let { " · " + Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(shortDate) }.orEmpty()
        val prev = b.previousValue
        return when (b.kind) {
            BestKind.FASTEST_RUN_PACE -> ShareCard.Best(
                dateLine = date.format(dayFormat),
                isNew = isNew,
                label = b.kind.label,
                bigValue = pace(b.value.roundToLong()),
                bigUnit = "/km",
                detail = b.workout?.distanceKm?.let { String.format(Locale.US, "on a %.2f km run", it) },
                previous = prev?.let { pace(it.roundToLong()) + " /km" + previousDate },
                gain = prev?.let { "${(it - b.value).roundToInt()} s faster" }
            )
            BestKind.LONGEST_STREAK -> ShareCard.Best(
                dateLine = date.format(dayFormat),
                isNew = isNew,
                label = b.kind.label,
                bigValue = "${b.value.roundToInt()}",
                bigUnit = "days",
                detail = "meals logged day after day",
                previous = prev?.let { "${it.roundToInt()} days" },
                gain = prev?.let { "+${(b.value - it).roundToInt()} days" }
            )
            else -> ShareCard.Best(
                dateLine = date.format(dayFormat),
                isNew = isNew,
                label = b.kind.label,
                bigValue = String.format(Locale.US, "%.2f", b.value),
                bigUnit = "km",
                detail = b.workout?.let { w ->
                    listOfNotNull(duration(w.merged.durationMillis), w.paceSecondsPerKm?.let { pace(it) + " /km" }).joinToString(" · ")
                },
                previous = prev?.let { String.format(Locale.US, "%.2f km", it) + previousDate },
                gain = prev?.let { String.format(Locale.US, "+%.2f km", abs(b.value - it)) }
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
    private fun plain(text: String): String = text.replace("**", "").trim()
}
