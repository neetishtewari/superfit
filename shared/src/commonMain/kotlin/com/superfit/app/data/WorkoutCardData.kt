package com.superfit.app.data

import com.superfit.app.domain.ActivityPatterns
import com.superfit.app.domain.Ai
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.BodyArea
import com.superfit.app.domain.CardChip
import com.superfit.app.domain.CardCount
import com.superfit.app.domain.CardLines
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.HeartSample
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.domain.ShareJourney
import com.superfit.app.domain.SleepLink
import com.superfit.app.domain.StrengthCardDetail
import com.superfit.app.domain.WeekMark
import com.superfit.app.domain.WorkoutHeart
import com.superfit.app.domain.WorkoutMoments
import com.superfit.app.domain.WorkoutView
import com.superfit.app.domain.currentAge
import com.superfit.app.ui.history.ProgressLog
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Fills in a workout card when its share screen opens, the same on Android and iPhone: the
 * heart-rate line from the phone's health app ([heartRate], read now, never stored), the line that
 * stands out against the person's earlier workouts (sleep first, when it's proven), the journey
 * footer, and for a voice-logged strength workout its reps, body areas and week. Anything that
 * can't be read is left out and the card stays as it was. The AI lines are written separately,
 * only when the person picks their chip ([aiLine]).
 */
class WorkoutCardData(
    private val log: LogRepository,
    /** The signed-in account's settings (goal, strength tags). */
    private val userPrefs: Prefs,
    private val share: ShareCardPrefs,
    /** Workouts from other apps as Today shows them (empty while the import is off). */
    private val importedViews: suspend (bmr: Double) -> List<WorkoutView>,
    /** Heart-rate readings the workout's own app recorded during it; null when not allowed. */
    private val heartRate: suspend (sourcePackage: String, start: Long, end: Long) -> List<HeartSample>?,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {

    suspend fun enrich(card: ShareCard.Activity): ShareCard.Activity {
        if (card.workout == null && card.voice == null) return card
        val zone = zone()
        val profile = safe("profile") { log.getProfile() }
        val bmr = profile?.let { PhysiologyEngine.calculateBmr(it) } ?: 0.0
        val imported = safe("imported workouts") { importedViews(bmr) }.orEmpty()
        val voiceLogs = safe("voice workouts") { log.getAllWorkoutEntries() }.orEmpty()
        val start = card.workout?.startTime ?: card.voice!!.timestamp
        val weekCount = weekCount(start, card.workout?.title, imported, voiceLogs, zone)
        val journey = profile?.let { safe("journey") { journey(it, zone) } }

        var out = card.copy(journey = journey, weekCount = weekCount)
        val workout = card.workout
        if (workout != null) {
            val primary = workout.merged.primary
            val samples = safe("heart rate") { heartRate(primary.sourcePackage, primary.startTime, primary.endTime) }
            val heart = samples?.let { WorkoutHeart.line(it, primary.startTime, primary.endTime, profile?.currentAge()) }
            val moment = safe("moments") {
                val sleep = log.getAllSleepTelemetry()
                    .mapNotNull { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it to s.sleepDurationSeconds / 3600.0 } }
                    .filter { it.second > 0 }
                    .toMap()
                SleepLink.line(workout, imported, sleep, zone) ?: WorkoutMoments.line(workout, imported, zone)
            }
            out = ShareCards.withDetails(out, heart, moment)
        } else {
            val entry = card.voice!!
            if (entry.workoutType == "Strength" || ActivityPatterns.exercises(entry.description).isNotEmpty()) {
                safe("strength card") { strength(entry, imported, voiceLogs, zone) }?.let { out = ShareCards.withStrength(out, it) }
            }
            out = out.copy(moment = if (weekCount.n >= 3) weekCount.phrase().replaceFirstChar { it.uppercase() } else null)
        }
        return out.copy(fairLine = CardLines.fairLine(out.moment, weekCount))
    }

    /**
     * The coach line or the roast for [card], written once and kept. Null when the AI can't be
     * reached or its line fails the checks twice; the card then shares without it.
     */
    suspend fun aiLine(card: ShareCard.Activity, chip: CardChip): String? {
        if (chip != CardChip.COACH && chip != CardChip.ROAST) return null
        val style = if (chip == CardChip.COACH) CardLines.coachStyle(CoachStyle.current) else CoachStyle.ROAST
        // LINES_VERSION goes up when the prompts change, so older saved lines are written again.
        val key = listOf(LINES_VERSION, chip.key, cardKey(card), style.name).joinToString("|")
        share.savedLine(key)?.let { return it }
        val facts = CardLines.facts(card, card.weekCount)
        // v3: runs, walks, swims and rides are counted apart from other workouts.
        val system = (if (chip == CardChip.COACH) CardLines.COACH_SYSTEM else CardLines.ROAST_SYSTEM).trimIndent() + style.instruction()
        val maxWords = if (chip == CardChip.COACH) CardLines.COACH_MAX_WORDS else CardLines.ROAST_MAX_WORDS
        repeat(2) {
            val raw = try {
                Ai.generate(prompt = CardLines.prompt(facts), systemInstructionText = system, isJson = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Writing the card line failed", e)
                return null
            }
            CardLines.accept(raw, facts, maxWords)?.let {
                share.saveLine(key, it)
                return it
            }
        }
        return null
    }

    /** The journey line for a roast card ("Day 34 of my journey"), or null when no goal is set. */
    suspend fun dayJourney(): String? {
        val profile = safe("profile") { log.getProfile() } ?: return null
        return safe("journey") { journey(profile, zone()) }
    }

    private fun cardKey(card: ShareCard.Activity): String =
        card.workout?.merged?.primary?.id ?: card.voice?.let { "voice_${it.id}_${it.timestamp}" } ?: card.dateLine

    @OptIn(ExperimentalTime::class)
    private fun dateOf(ms: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date

    private fun mondayOf(day: LocalDate): LocalDate = day.minus(DatePeriod(days = day.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber))

    /**
     * How many of this kind the person did in the week (Monday on) of [start], up to and including
     * it: runs, walks, swims and rides each count apart; voice logs and every other type count as workouts.
     */
    @OptIn(ExperimentalTime::class)
    private fun weekCount(start: Long, title: String?, imported: List<WorkoutView>, voice: List<WorkoutEntryEntity>, zone: TimeZone): CardCount {
        val noun = CardCount.nounFor(title)
        val from = mondayOf(dateOf(start, zone)).atStartOfDayIn(zone).toEpochMilliseconds()
        val i = imported.count { CardCount.nounFor(it.title) == noun && it.startTime in from..start }
        val v = if (noun == "workout") voice.count { it.timestamp in from..start } else 0
        return CardCount((i + v).coerceAtLeast(1), noun)
    }

    /** "Day 34 of my journey", with goal progress only when the person allows weight on cards. Null without a goal. */
    private suspend fun journey(profile: UserProfileEntity, zone: TimeZone): String? {
        if (!GoalDatePref.hasGoal(userPrefs, profile)) return null
        val today = Now.today()
        val weighIns = ProgressLog.weighIns(log.getAllWeightEntries(), zone)
        val firstMeal = log.getAllNutritionEntries().minOfOrNull { it.timestamp }?.let { dateOf(it, zone) }
        val startDate = GoalForecast.startDate(
            profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let { dateOf(it, zone) },
            weighIns = weighIns,
            firstLogged = firstMeal,
            today = today
        )
        val day = startDate.daysUntil(today).coerceAtLeast(0) + 1L
        val lose = profile.targetWeightKg < profile.startingWeightKg
        val dir = if (lose) -1.0 else 1.0
        val current = weighIns.lastOrNull()?.second ?: profile.weightKg
        val total = abs(profile.targetWeightKg - profile.startingWeightKg)
        val done = ((current - profile.startingWeightKg) * dir).coerceIn(0.0, total)
        val reached = (profile.targetWeightKg - current) * dir <= 0
        return ShareJourney.line(day, done, total, lose, reached, share.showWeight)
    }

    /** Reps and exercises from the words, body areas from the recap's strength tags, and the week strip. */
    private suspend fun strength(
        entry: WorkoutEntryEntity,
        imported: List<WorkoutView>,
        voice: List<WorkoutEntryEntity>,
        zone: TimeZone
    ): StrengthCardDetail {
        val day = dateOf(entry.timestamp, zone)
        val monday = mondayOf(day)
        val lastWeek = voice.filter { it.workoutType == "Strength" && it.timestamp in (entry.timestamp - 7 * DAY)..entry.timestamp }
        val tags: Map<String, Pair<Set<BodyArea>, Int?>> = StrengthTags.load(userPrefs, (lastWeek + entry).distinctBy { StrengthTags.key(it) })
        val mine = tags[StrengthTags.key(entry)]
        // Health apps' strength workouts don't say what they worked, so only voice logs count here.
        val others = lastWeek.filter { StrengthTags.key(it) != StrengthTags.key(entry) }
            .flatMap { tags[StrengthTags.key(it)]?.first.orEmpty() }.toSet()
        val week = (0..6).map { i ->
            val d = monday.plus(DatePeriod(days = i))
            val strengthDay = voice.any { it.workoutType == "Strength" && dateOf(it.timestamp, zone) == d } ||
                imported.any { it.title == "Strength workout" && dateOf(it.startTime, zone) == d }
            val cardioDay = voice.any { it.workoutType != "Strength" && dateOf(it.timestamp, zone) == d } ||
                imported.any { it.title != "Strength workout" && dateOf(it.startTime, zone) == d && (it.title != "Walk" || it.minutes >= 30) }
            when {
                strengthDay -> WeekMark.STRENGTH
                cardioDay -> WeekMark.CARDIO
                else -> WeekMark.NONE
            }
        }
        return StrengthCardDetail.build(
            said = entry.description,
            storedReps = entry.repsCount,
            minutes = mine?.second,
            todayAreas = mine?.first.orEmpty(),
            weekAreas = others,
            week = week,
            dayIndex = monday.daysUntil(day).coerceIn(0, 6)
        )
    }

    private suspend fun <T> safe(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, "Couldn't read $what", e)
        null
    }

    private companion object {
        const val TAG = "WorkoutCardData"
        const val DAY = 86_400_000L
        const val LINES_VERSION = "v3"
    }
}
