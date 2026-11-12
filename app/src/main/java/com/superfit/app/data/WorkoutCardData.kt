package com.superfit.app.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.superfit.app.domain.ActivityPatterns
import com.superfit.app.domain.CardChip
import com.superfit.app.domain.CardCount
import com.superfit.app.domain.CardLines
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.GeminiClient
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone

/**
 * Fills in a workout card when its share screen opens: the heart-rate line from Health Connect
 * (read now, never stored), the line that stands out against the user's earlier workouts (sleep
 * first, when it's proven), the journey footer, and for a voice-logged strength workout its reps,
 * body areas and week. Anything that can't be read is left out and the card stays as it was.
 * The AI lines are written separately, only when the user picks their chip ([aiLine]).
 */
object WorkoutCardData {
    private const val TAG = "WorkoutCardData"
    private const val MAX_SAMPLES = 20_000
    private const val DAY = 86_400_000L
    private const val LINES_VERSION = "v3"

    suspend fun enrich(context: Context, card: ShareCard.Activity): ShareCard.Activity {
        if (card.workout == null && card.voice == null) return card
        val app = context.applicationContext
        val repository = DataRepository(app, HealthConnectManager(app))
        val zone = ZoneId.systemDefault()
        val profile = safe("profile") { repository.getProfile() }
        val bmr = profile?.let { PhysiologyEngine.calculateBmr(it) } ?: 0.0
        val imported = safe("imported workouts") { repository.getImportedWorkoutViews(bmr) }.orEmpty()
        val voiceLogs = safe("voice workouts") { repository.getAllWorkoutEntries() }.orEmpty()
        val start = card.workout?.startTime ?: card.voice!!.timestamp
        val weekCount = weekCount(start, card.workout?.title, imported, voiceLogs, zone)
        val journey = profile?.let { safe("journey") { journey(app, repository, it, zone) } }

        var out = card.copy(journey = journey, weekCount = weekCount)
        val workout = card.workout
        if (workout != null) {
            val primary = workout.merged.primary
            val samples = readHeartRate(repository.healthConnectManager.healthConnectClient, primary.sourcePackage, primary.startTime, primary.endTime)
            val heart = samples?.let { WorkoutHeart.line(it, primary.startTime, primary.endTime, profile?.currentAge()) }
            val moment = safe("moments") {
                val sleep = repository.getAllSleepTelemetry()
                    .mapNotNull { s -> runCatching { kotlinx.datetime.LocalDate.parse(s.date) }.getOrNull()?.let { it to s.sleepDurationSeconds / 3600.0 } }
                    .filter { it.second > 0 }
                    .toMap()
                SleepLink.line(workout, imported, sleep, zone.toKotlinTimeZone()) ?: WorkoutMoments.line(workout, imported, zone.toKotlinTimeZone())
            }
            out = ShareCards.withDetails(out, heart, moment)
        } else {
            val entry = card.voice!!
            if (entry.workoutType == "Strength" || ActivityPatterns.exercises(entry.description).isNotEmpty()) {
                safe("strength card") { strength(app, entry, imported, voiceLogs, zone) }?.let { out = ShareCards.withStrength(out, it) }
            }
            out = out.copy(moment = if (weekCount.n >= 3) weekCount.phrase().replaceFirstChar { it.uppercase() } else null)
        }
        return out.copy(fairLine = CardLines.fairLine(out.moment, weekCount))
    }

    /**
     * The coach line or the roast for [card], written once and kept. Null when the AI can't be
     * reached or its line fails the checks twice; the card then shares without it.
     */
    suspend fun aiLine(context: Context, card: ShareCard.Activity, chip: CardChip): String? {
        if (chip != CardChip.COACH && chip != CardChip.ROAST) return null
        val prefs = ShareCardPrefs.of(context.applicationContext)
        val style = if (chip == CardChip.COACH) CardLines.coachStyle(CoachStyle.current) else CoachStyle.ROAST
        // LINES_VERSION goes up when the prompts change, so older saved lines are written again.
        val key = listOf(LINES_VERSION, chip.key, cardKey(card), style.name).joinToString("|")
        prefs.savedLine(key)?.let { return it }
        val facts = CardLines.facts(card, card.weekCount)
        // v3: runs, walks, swims and rides are counted apart from other workouts.
        val system = (if (chip == CardChip.COACH) CardLines.COACH_SYSTEM else CardLines.ROAST_SYSTEM).trimIndent() + style.instruction()
        val maxWords = if (chip == CardChip.COACH) CardLines.COACH_MAX_WORDS else CardLines.ROAST_MAX_WORDS
        repeat(2) {
            val raw = try {
                GeminiClient.generateContent(prompt = CardLines.prompt(facts), systemInstructionText = system, isJson = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Writing the card line failed", e)
                return null
            }
            CardLines.accept(raw, facts, maxWords)?.let {
                prefs.saveLine(key, it)
                return it
            }
        }
        return null
    }

    private fun cardKey(card: ShareCard.Activity): String =
        card.workout?.merged?.primary?.id ?: card.voice?.let { "voice_${it.id}_${it.timestamp}" } ?: card.dateLine

    /**
     * How many of this kind the user did in the week (Monday on) of [start], up to and including it:
     * runs, walks, swims and rides each count apart; voice logs and every other type count as workouts.
     */
    private fun weekCount(start: Long, title: String?, imported: List<WorkoutView>, voice: List<WorkoutEntryEntity>, zone: ZoneId): CardCount {
        val noun = CardCount.nounFor(title)
        val monday = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY)
        val from = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val i = imported.count { CardCount.nounFor(it.title) == noun && it.startTime in from..start }
        val v = if (noun == "workout") voice.count { it.timestamp in from..start } else 0
        return CardCount((i + v).coerceAtLeast(1), noun)
    }

    /** The journey line for a roast card ("Day 34 of my journey"), or null when no goal is set. */
    suspend fun dayJourney(context: Context): String? {
        val app = context.applicationContext
        val repository = DataRepository(app, HealthConnectManager(app))
        val profile = safe("profile") { repository.getProfile() } ?: return null
        return safe("journey") { journey(app, repository, profile, ZoneId.systemDefault()) }
    }

    /** "Day 34 of my journey", with goal progress only when the user allows weight on cards. Null without a goal. */
    private suspend fun journey(context: Context, repository: DataRepository, profile: UserProfileEntity, zone: ZoneId): String? {
        val prefs = getUserSharedPrefs(context)
        val goalSet = GoalDatePref.hasGoal(AndroidPrefs(prefs), profile)
        if (!goalSet) return null
        val today = LocalDate.now(zone)
        val weighIns = InsightsData.weighIns(repository.getAllWeightEntries(), zone)
        val firstMeal = repository.getAllNutritionEntries().minOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val startDate = GoalForecast.startDate(
            profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toKotlinLocalDate() },
            weighIns = weighIns.map { it.first.toKotlinLocalDate() to it.second },
            firstLogged = firstMeal?.toKotlinLocalDate(),
            today = today.toKotlinLocalDate()
        ).toJavaLocalDate()
        val day = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0) + 1
        val lose = profile.targetWeightKg < profile.startingWeightKg
        val dir = if (lose) -1.0 else 1.0
        val current = weighIns.lastOrNull()?.second ?: profile.weightKg
        val total = abs(profile.targetWeightKg - profile.startingWeightKg)
        val done = ((current - profile.startingWeightKg) * dir).coerceIn(0.0, total)
        val reached = (profile.targetWeightKg - current) * dir <= 0
        return ShareJourney.line(day, done, total, lose, reached, ShareCardPrefs.of(context).showWeight)
    }

    /** Reps and exercises from the words, body areas from the recap's strength tags, and the week strip. */
    private suspend fun strength(
        context: Context,
        entry: WorkoutEntryEntity,
        imported: List<WorkoutView>,
        voice: List<WorkoutEntryEntity>,
        zone: ZoneId
    ): StrengthCardDetail {
        val day = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
        val monday = day.with(DayOfWeek.MONDAY)
        val lastWeek = voice.filter { it.workoutType == "Strength" && it.timestamp in (entry.timestamp - 7 * DAY)..entry.timestamp }
        val tags = MonthlyRecapData.strengthTags(context, (lastWeek + entry).distinctBy { MonthlyRecapData.tagKey(it) })
        val mine = tags[MonthlyRecapData.tagKey(entry)]
        // Health Connect strength workouts don't say what they worked, so only voice logs count here.
        val others = lastWeek.filter { MonthlyRecapData.tagKey(it) != MonthlyRecapData.tagKey(entry) }
            .flatMap { tags[MonthlyRecapData.tagKey(it)]?.first.orEmpty() }.toSet()
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val week = (0..6).map { i ->
            val d = monday.plusDays(i.toLong())
            val strengthDay = voice.any { it.workoutType == "Strength" && dateOf(it.timestamp) == d } ||
                imported.any { it.title == "Strength workout" && dateOf(it.startTime) == d }
            val cardioDay = voice.any { it.workoutType != "Strength" && dateOf(it.timestamp) == d } ||
                imported.any { it.title != "Strength workout" && dateOf(it.startTime) == d && (it.title != "Walk" || it.minutes >= 30) }
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
            dayIndex = ChronoUnit.DAYS.between(monday, day).toInt().coerceIn(0, 6)
        )
    }

    private suspend fun <T> safe(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read $what", e)
        null
    }

    /** Heart-rate readings the workout's own app recorded during it; null when not allowed or on failure. */
    private suspend fun readHeartRate(client: HealthConnectClient?, sourcePackage: String, start: Long, end: Long): List<HeartSample>? {
        client ?: return null
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null
            val out = mutableListOf<HeartSample>()
            var token: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)),
                        dataOriginFilter = setOf(DataOrigin(sourcePackage)),
                        pageToken = token
                    )
                )
                response.records.forEach { record ->
                    record.samples.forEach { out += HeartSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
                }
                token = response.pageToken
            } while (token != null && out.size < MAX_SAMPLES)
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading heart rate failed", e)
            null
        }
    }
}
