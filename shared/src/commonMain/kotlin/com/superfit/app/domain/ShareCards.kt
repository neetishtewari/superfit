package com.superfit.app.domain

import com.superfit.app.data.WorkoutEntryEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        val roast: Boolean = false,
        /** Every number the day has, sleep included, to pick the ones a roast talks about. */
        val pool: List<CardStat> = tiles,
        /** What's on a roast card: the roast, the numbers that back it up, the journey line. */
        val chips: Set<DayChip> = DayChip.DEFAULTS,
        /** "Day 34 of my journey", when a goal is set. */
        val journey: String? = null,
        /** A short version of a long roast, for a photo card; null until written. */
        val shortRoast: String? = null,
        /** The numbers the AI says the roast is about, by label; null until it answers (word matching till then). */
        val picked: List<String>? = null
    ) : ShareCard {
        /** The roast, when this is a roast card and the Roast chip is on. */
        val shownRoast: String? get() = coachLine?.takeIf { roast && DayChip.ROAST in chips && it.isNotBlank() }

        /** A roast card with the roast off is just the day's numbers. */
        val asDay: Daily get() = if (roast && shownRoast == null) copy(roast = false, coachLine = null) else this

        /** The numbers under a roast: only the ones it talks about, and only when Numbers is on. */
        val roastTiles: List<CardStat> get() = when {
            DayChip.NUMBERS !in chips || shownRoast == null -> emptyList()
            picked != null -> picked.mapNotNull { label -> pool.firstOrNull { it.label == label } }.take(RoastCard.MAX_TILES)
            else -> RoastCard.matching(shownRoast, pool)
        }

        val shownJourney: String? get() = journey?.takeIf { roast && DayChip.JOURNEY in chips }
    }

    data class Activity(
        override val dateLine: String,
        val heading: String,
        val bigValue: String,
        val bigUnit: String,
        val subtitle: String?,
        val stats: List<CardStat>,
        /** The Health Connect workout behind the card, so the share screen can read its heart rate. */
        val workout: WorkoutView? = null,
        /** Heart rate through the workout; null when there are too few readings. */
        val heart: HeartLine? = null,
        /** The one line that stands out about this workout, when something does. */
        val moment: String? = null,
        /** What the user picked to add (stand-out line, coach line or roast, journey). */
        val chips: Set<CardChip> = emptySet(),
        /** The voice-logged workout behind the card, for its strength card and AI lines. */
        val voice: WorkoutEntryEntity? = null,
        /** Reps, exercises, body areas and the week, for a voice-logged strength workout. */
        val strength: StrengthCardDetail? = null,
        /** "Day 34 of my journey", when a goal is set. */
        val journey: String? = null,
        /** The AI coach's line and the roast, once written (only when their chip is picked). */
        val coachLine: String? = null,
        val roastLine: String? = null,
        /** The fair, true line a roast ends with. */
        val fairLine: String? = null,
        /** Workouts this week up to this one (walks not counted), for the AI lines' facts. */
        /** How many of this kind (runs, walks, swims, rides or other workouts) so far this week, this one included. */
        val weekCount: CardCount? = null
    ) : ShareCard {
        val showMoment: Boolean get() = CardChip.STANDOUT in chips && moment != null
        val shownHeart: HeartLine? get() = heart?.takeIf { CardChip.HEART in chips }
        val shownCoach: String? get() = coachLine?.takeIf { CardChip.COACH in chips }
        val shownRoast: String? get() = roastLine?.takeIf { CardChip.ROAST in chips }
        val shownJourney: String? get() = journey?.takeIf { CardChip.JOURNEY in chips }
    }

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
@OptIn(ExperimentalTime::class)
object ShareCards {

    private fun at(millis: Long, zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)

    private fun dateAndTime(t: LocalDateTime) = "${LocalFormat.dayLine(t.date)} · ${LocalFormat.clock(t.time)}"

    private fun two(n: Long) = n.toString().padStart(2, '0')

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
        coachLine: String?,
        sleepSeconds: Long = 0
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
            if (steps > 0) add(CardStat("STEPS", LocalFormat.integer(steps.toLong())))
            if (workout != null) add(CardStat("WORKOUT", workout.value, note = workout.note))
        }
        // Sleep isn't on the day card, but a roast about oversleeping can show it.
        val sleep = sleepSeconds.takeIf { it >= 60 * 60 }?.let {
            val minutes = (it / 60).toInt()
            CardStat("SLEEP", "${minutes / 60}h ${minutes % 60}m", note = "last night")
        }
        return ShareCard.Daily(
            dateLine = LocalFormat.dayLine(date),
            streakDays = streakDays,
            tiles = tiles,
            pool = tiles + listOfNotNull(sleep),
            coachLine = coachLine?.let(::plain)?.takeIf { it.isNotBlank() }
        )
    }

    /** The day's longest imported workout, or else the first voice-logged one. */
    fun dayWorkout(imported: List<WorkoutView>, voiceLogged: List<WorkoutEntryEntity>, zone: TimeZone = TimeZone.currentSystemDefault()): DayWorkout? {
        imported.maxByOrNull { it.minutes }?.let { w ->
            val value = w.distanceKm?.let { Decimals.one(it) + " km" } ?: "${w.minutes} min"
            return DayWorkout(value, "${partOfDay(w.startTime, zone)} ${w.noun}")
        }
        voiceLogged.firstOrNull()?.let { e ->
            return DayWorkout("${e.caloriesBurned.roundToInt()} kcal", e.description)
        }
        return null
    }

    fun activity(w: WorkoutView, zone: TimeZone = TimeZone.currentSystemDefault()): ShareCard.Activity {
        val start = at(w.startTime, zone)
        val hasDistance = w.distanceKm != null
        val stats = buildList {
            if (hasDistance) add(CardStat("TIME", duration(w.merged.durationMillis)))
            w.paceSecondsPerKm?.let { add(CardStat("PACE", pace(it), "/km")) }
            w.speedKmh?.let { add(CardStat("SPEED", Decimals.one(it), "km/h")) }
            w.avgHeartRate?.let { add(CardStat("AVG HEART RATE", "$it", "bpm")) }
            w.exerciseKcal?.let { add(CardStat("BURNED", "${it.roundToInt()}", "kcal")) }
        }
        return ShareCard.Activity(
            dateLine = dateAndTime(start),
            // The workout's own name; only a workout of no known kind is named by the time of day.
            heading = (if (w.title == "Workout") "${partOfDay(w.startTime, zone)} workout" else w.title).let(LocalFormat::uppercase),
            bigValue = w.distanceKm?.let { Decimals.fixed(it, 2) } ?: "${w.minutes}",
            bigUnit = if (hasDistance) "km" else "min",
            subtitle = null,
            stats = stats,
            workout = w
        )
    }

    /**
     * A voice-logged strength workout leads with its reps (or minutes) and says what was worked;
     * calories move into the stats.
     */
    fun withStrength(card: ShareCard.Activity, detail: StrengthCardDetail): ShareCard.Activity {
        val kcal = card.voice?.caloriesBurned?.roundToInt()?.takeIf { it > 0 }
        val (big, unit) = when {
            detail.reps != null -> "${detail.reps}" to "reps"
            detail.minutes != null -> "${detail.minutes}" to "min"
            else -> return card.copy(strength = detail, heading = "STRENGTH")
        }
        val stats = buildList {
            if (detail.reps != null) detail.minutes?.let { add(CardStat("TIME", "$it", "min")) }
            kcal?.let { add(CardStat("BURNED", "$it", "kcal")) }
        }
        // The exercise list replaces the words they said when it could be read from them.
        val subtitle = if (detail.exercises.isNotEmpty()) null else card.subtitle
        return card.copy(strength = detail, heading = "STRENGTH", bigValue = big, bigUnit = unit, stats = stats, subtitle = subtitle)
    }

    /** Adds the heart-rate line (and its peak, next to the average) and the stand-out line. */
    fun withDetails(card: ShareCard.Activity, heart: HeartLine?, moment: String?): ShareCard.Activity {
        val stats = if (heart == null || card.stats.any { it.label == "PEAK" }) {
            card.stats
        } else {
            val peak = CardStat("PEAK", "${heart.peakBpm}", "bpm")
            val at = card.stats.indexOfFirst { it.label == "AVG HEART RATE" }
                .let { if (it >= 0) it + 1 else card.stats.indexOfFirst { s -> s.label == "BURNED" }.takeIf { i -> i >= 0 } ?: card.stats.size }
            card.stats.toMutableList().apply { add(at, peak) }
        }
        return card.copy(stats = stats, heart = heart, moment = moment)
    }

    /** A voice-logged workout has what the user said and its calories, nothing more. */
    fun activity(e: WorkoutEntryEntity, zone: TimeZone = TimeZone.currentSystemDefault()): ShareCard.Activity {
        val start = at(e.timestamp, zone)
        return ShareCard.Activity(
            dateLine = dateAndTime(start),
            heading = "${partOfDay(e.timestamp, zone)} workout".let(LocalFormat::uppercase),
            bigValue = "${e.caloriesBurned.roundToInt()}",
            bigUnit = "kcal",
            subtitle = e.description,
            stats = emptyList(),
            voice = e
        )
    }

    fun best(b: PersonalBest, isNew: Boolean, today: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): ShareCard.Best {
        val date = b.workout?.let { at(it.startTime, zone).date } ?: b.periodStart ?: today
        val dateLine = when (b.kind.period) {
            BestPeriod.WEEK -> "Week of " + LocalFormat.dayMonth(date)
            BestPeriod.MONTH -> LocalFormat.monthYear(date)
            else -> LocalFormat.dayLine(date)
        }
        // Week and month bests read fine without the date of the one they beat.
        val datedPrevious = b.kind.period == BestPeriod.WORKOUT || b.kind.period == BestPeriod.DAY
        val previousDate = b.previousTime?.takeIf { datedPrevious }
            ?.let { " · " + LocalFormat.dayMonth(at(it, zone).date) }
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
                        w.distanceKm?.let { Decimals.fixed(it, 2) + " km run" },
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
                bigValue = Decimals.fixed(b.value, 2),
                bigUnit = "km",
                detail = b.workout?.let { w ->
                    listOfNotNull(duration(w.merged.durationMillis), w.paceSecondsPerKm?.let { pace(it) + " /km" }).joinToString(" · ")
                } ?: "running and walking",
                previous = prev?.let { Decimals.fixed(it, 2) + " km" + previousDate },
                gain = prev?.let { "+" + Decimals.fixed(abs(b.value - it), 2) + " km" },
                podium = b.rank > 1
            )
        }
    }

    /** "Morning", "Afternoon", "Evening" or "Night", the way runners name workouts. */
    fun partOfDay(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        when (at(millis, zone).hour) {
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
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    private fun pace(secondsPerKm: Long): String = "${secondsPerKm / 60}:${two(secondsPerKm % 60)}"

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

    /** Until the short version is written, a photo shows the roast's opening sentences that fit. */
    fun photoRoast(text: String): String = fitRoast(text, RoastCard.PHOTO_MAX_CHARS)

    /** Whole sentences that fit in [max]; a first sentence that's too long is cut at a word. */
    fun fitRoast(text: String, max: Int = ROAST_MAX): String {
        if (text.length <= max) return text
        val sentences = Regex("""(?<=[.!?])\s+""").split(text)
        val kept = StringBuilder()
        for (s in sentences) {
            val next = if (kept.isEmpty()) s else "$kept $s"
            if (next.length > max) break
            kept.clear().append(next)
        }
        if (kept.isNotEmpty()) return kept.toString()
        return text.take(max - 1).substringBeforeLast(' ').trimEnd(',', ';', ':', '-', '—') + "…"
    }

    private fun plain(text: String): String = text.replace("**", "").trim()
}
