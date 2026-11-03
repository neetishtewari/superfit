package com.superfit.app.domain

/** What the user can add to a workout card. Nothing is on a card unless the user picks it. */
enum class CardChip(val key: String, val label: String) {
    STANDOUT("standout", "Stand-out"),
    COACH("coach", "Coach"),
    ROAST("roast", "Roast"),
    JOURNEY("journey", "Journey");

    companion object {
        fun of(key: String): CardChip? = entries.firstOrNull { it.key == key.trim() }

        /** "standout,coach" from the saved setting; unknown keys are dropped. */
        fun parse(saved: String?): Set<CardChip> = saved.orEmpty().split(',').mapNotNull { of(it) }.toSet().let(::exclusive)

        fun encode(chips: Set<CardChip>): String = entries.filter { it in chips }.joinToString(",") { it.key }

        /** Picking [chip] turns it on or off; the coach line and the roast never sit on a card together. */
        fun toggle(chips: Set<CardChip>, chip: CardChip): Set<CardChip> = when {
            chip in chips -> chips - chip
            chip == COACH -> chips - ROAST + COACH
            chip == ROAST -> chips - COACH + ROAST
            else -> chips + chip
        }

        /** A saved set with both coach and roast (it can't be made in the app, but be safe) keeps the coach line. */
        private fun exclusive(chips: Set<CardChip>): Set<CardChip> = if (COACH in chips && ROAST in chips) chips - ROAST else chips

        /** Chip order on the share screen; Roast me users see Roast before Coach. */
        fun order(style: CoachStyle): List<CardChip> =
            if (style == CoachStyle.ROAST) listOf(STANDOUT, ROAST, COACH, JOURNEY) else entries.toList()
    }
}

/** One day on the week strip of a strength card. */
enum class WeekMark { NONE, CARDIO, STRENGTH }

/**
 * The strength card for a voice-logged workout: reps added up from what was said, the exercises,
 * and which parts of the body were worked today and over the last 7 days.
 */
data class StrengthCardDetail(
    /** Reps added up from the words ("20 push-ups and 50 curls" = 70), else the reps the app stored; null if none. */
    val reps: Int?,
    /** Up to three exercises with their reps, for the small list; [moreExercises] counts the rest. */
    val exercises: List<Pair<String, Int>>,
    val moreExercises: Int,
    /** Minutes, when the user said how long it took. */
    val minutes: Int?,
    /** Upper body, lower body and core worked today (full body counts as all three). */
    val today: Set<BodyArea>,
    /** Areas worked on another day of the last 7. */
    val thisWeek: Set<BodyArea>,
    /** Monday to Sunday of the workout's week. */
    val week: List<WeekMark>,
    /** Index into [week] of the workout's day. */
    val dayIndex: Int
) {
    companion object {
        /** The three areas the card shows. */
        val SHOWN = listOf(BodyArea.UPPER, BodyArea.LOWER, BodyArea.CORE)

        /** Full body covers all three shown areas. */
        fun expand(areas: Set<BodyArea>): Set<BodyArea> = if (BodyArea.FULL in areas) SHOWN.toSet() else areas.intersect(SHOWN.toSet())

        fun build(
            said: String,
            storedReps: Int,
            minutes: Int?,
            todayAreas: Set<BodyArea>,
            weekAreas: Set<BodyArea>,
            week: List<WeekMark>,
            dayIndex: Int
        ): StrengthCardDetail {
            val exercises = ActivityPatterns.exercises(said)
            val spokenReps = exercises.sumOf { it.second }.takeIf { it > 0 }
            val today = expand(todayAreas)
            return StrengthCardDetail(
                reps = spokenReps ?: storedReps.takeIf { it > 0 },
                exercises = exercises.take(3),
                moreExercises = (exercises.size - 3).coerceAtLeast(0),
                minutes = minutes?.takeIf { it > 0 },
                today = today,
                thisWeek = expand(weekAreas) - today,
                week = week,
                dayIndex = dayIndex
            )
        }
    }
}

/** The card's journey footer: "Day 34 of my journey", with goal progress only when the user allows it. */
object ShareJourney {

    fun line(dayNumber: Long, kgDone: Double, kgTotal: Double, lose: Boolean, reached: Boolean, showWeight: Boolean): String {
        val day = "Day $dayNumber of my journey"
        if (!showWeight || kgTotal < 0.5) return day
        if (reached) return "$day · goal reached"
        val word = if (lose) "down" else "up"
        return "$day · ${kg(kgDone)} of ${kg(kgTotal)} kg $word"
    }

    private fun kg(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }
}
