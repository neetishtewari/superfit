package com.superfit.app.domain

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.json.JSONObject

/** What the coach card shows: one headline, one thing to do, and the numbers behind it. */
data class CoachInsight(
    val headline: String,
    val action: String,
    val why: List<String>,
    val focusId: String,
    val focusKind: FindingKind,
    /** Set when the AI couldn't be reached and the plain summary is shown instead. */
    val note: String? = null
) {
    /** One-line text for notifications and older screens. */
    fun asText(): String = listOf(headline, action).filter { it.isNotBlank() }.joinToString(" ")
}

/** What the coach said on a given day, so it can follow up instead of repeating itself. */
data class CoachMemoryEntry(
    val date: LocalDate,
    val focusId: String,
    val focusKind: FindingKind,
    val headline: String
)

object CoachMemory {
    private const val KEEP = 14

    fun decode(raw: String?): List<CoachMemoryEntry> =
        raw.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val kind = runCatching { FindingKind.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            CoachMemoryEntry(date, parts[1], kind, parts[3])
        }

    fun encode(entries: List<CoachMemoryEntry>): String =
        entries.joinToString("\n") { e ->
            listOf(e.date.toString(), e.focusId, e.focusKind.name, e.headline.replace('\t', ' ').replace('\n', ' '))
                .joinToString("\t")
        }

    /** Adds [entry], replacing anything already saved for the same day, and keeps the newest [KEEP]. */
    fun remember(existing: List<CoachMemoryEntry>, entry: CoachMemoryEntry): List<CoachMemoryEntry> =
        (existing.filter { it.date != entry.date } + entry).sortedByDescending { it.date }.take(KEEP)
}

class CoachingEngine {

    suspend fun generateDailyInsight(
        profile: UserProfileEntity,
        macroTargets: MacroTargets,
        maintenanceCalories: Double?,
        historyNutrition: List<NutritionEntryEntity>,
        historyWorkouts: List<WorkoutEntryEntity>,
        historyActivity: List<ActivityTelemetryEntity>,
        historySleep: List<SleepTelemetryEntity>,
        historyWeights: List<WeightEntryEntity>,
        memory: List<CoachMemoryEntry>,
        notes: List<CoachNote> = emptyList(),
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): CoachInsight {
        val report = CoachPatternEngine.analyze(
            today = today,
            now = now,
            weightKg = profile.weightKg,
            goal = profile.goal,
            targets = macroTargets,
            maintenanceCalories = maintenanceCalories,
            nutrition = historyNutrition,
            workouts = historyWorkouts,
            activity = historyActivity,
            sleep = historySleep,
            weights = historyWeights,
            targetWeightKg = profile.targetWeightKg
        )
        val plans = CoachNotes.planFindings(notes, historyNutrition, historyWorkouts, today)
        val focus = chooseFocus(report, memory, today, plans)
        val support = report.findings.firstOrNull { it.kind == FindingKind.WIN && it.id != focus.id }
        val why = whyLines(focus, report)

        val prompt = PersonalNutrition.adviceLine() + buildPrompt(profile, report, focus, support, memory, today, now, historyNutrition, notes)
        return try {
            val response = GeminiClient.generateContent(
                prompt = prompt,
                systemInstructionText = SYSTEM_INSTRUCTION,
                isJson = true
            )
            parseResponse(response, focus, why)
        } catch (e: Exception) {
            android.util.Log.e("CoachingEngine", "Coach AI call failed, showing the plain summary", e)
            fallbackInsight(focus, why, note = GeminiClient.sanitizeError(e))
        }
    }

    companion object {

        val SYSTEM_INSTRUCTION = """
            You are the coach inside Superfit, a fitness and nutrition app. You write the one short message the user reads on their home screen today.
            The app has already studied the user's last few weeks of logs and picked THE FOCUS: the single most useful thing to talk about today. Your job is to say it the way a good personal coach texts a client: warm, direct, specific and in plain everyday words.

            Rules:
            1. Talk about THE FOCUS. Use its numbers and say briefly why it matters for this user's goal, using the "why it matters" note. Do not switch to a different topic.
            2. Only use numbers that are given to you. Never invent or recalculate numbers.
            3. Days without logs are unknown, not days without food. Never say or imply the user is under-eating or skipping meals because something wasn't logged.
            4. Don't recite today's totals. Mention today only when it's needed for the action.
            5. If THE FOCUS is a win, celebrate it specifically and don't add a criticism.
            6. Don't repeat your recent messages. If THE FOCUS was also a recent topic, say it's still going on and give a different, more concrete tip than before.
            7. Avoid jargon such as bioavailable, thermic, macronutrients, glycemic, telemetry.
            8. The action is one concrete thing for the rest of today, or for tomorrow if it's after 9 PM. Prefer foods the user already eats.
            9. Respect what you know about the user: never suggest something an injury, dislike or diet day rules out, and adapt the action instead (a walk instead of a run for a sore knee).

            Reply with JSON only, in this exact shape:
            {"headline": "1 or 2 sentences, at most 40 words, may put one key phrase in **bold**", "action": "one sentence, at most 25 words, no bold"}
        """.trimIndent()

        private const val REPEAT_PENALTY = 25

        private val areaLabels = mapOf(
            "protein" to "your protein",
            "calories" to "your calories",
            "treats" to "treat meals",
            "veg" to "vegetables and fruit",
            "fibre" to "your fibre",
            "steps" to "your steps",
            "sleep" to "your sleep",
            "workouts" to "your workouts",
            "weight" to "your weight trend"
        )

        /**
         * Picks what to talk about today. The highest-priority finding wins, but one that was
         * the focus yesterday is marked down so the coach doesn't nag about the same thing every
         * day, and a concern that has since cleared up becomes a follow-up win.
         */
        fun chooseFocus(
            report: CoachReport,
            memory: List<CoachMemoryEntry>,
            today: LocalDate,
            plans: List<CoachFinding> = emptyList()
        ): CoachFinding {
            val past = memory.filter { it.date < today }.sortedByDescending { it.date }
            val yesterday = past.firstOrNull { it.date == today.minusDays(1) }?.focusId
            val dayBefore = past.firstOrNull { it.date == today.minusDays(2) }?.focusId

            val candidates = report.findings + followUps(report, past, today) + plans
            if (candidates.isEmpty()) return gettingStarted(report)

            return candidates.maxBy { f ->
                var score = f.priority
                if (f.id == yesterday) score -= REPEAT_PENALTY
                if (f.id == yesterday && f.id == dayBefore) score -= REPEAT_PENALTY
                score
            }
        }

        /** Concerns the coach raised in the last two weeks that the latest logs no longer show. */
        fun followUps(report: CoachReport, past: List<CoachMemoryEntry>, today: LocalDate): List<CoachFinding> {
            val recentConcerns = past
                .filter { it.focusKind == FindingKind.CONCERN && ChronoUnit.DAYS.between(it.date, today) <= 14 }
            return recentConcerns
                .groupBy { areaOf(it.focusId) }
                .mapNotNull { (area, entries) ->
                    val label = areaLabels[area] ?: return@mapNotNull null
                    if (area !in report.checkedAreas) return@mapNotNull null
                    if (report.findings.any { it.area == area && it.kind == FindingKind.CONCERN }) return@mapNotNull null
                    val flaggedOn = entries.maxOf { it.date }
                    val id = "followup_$area"
                    // Only celebrate once per concern.
                    if (past.any { it.focusId == id && it.date >= flaggedOn }) return@mapNotNull null
                    val currentWin = report.findings.firstOrNull { it.area == area && it.kind == FindingKind.WIN }
                    val daysAgo = ChronoUnit.DAYS.between(flaggedOn, today)
                    val whenText = if (daysAgo <= 1) "Yesterday" else "$daysAgo days ago"
                    CoachFinding(
                        id = id,
                        area = area,
                        kind = FindingKind.WIN,
                        priority = 65,
                        fact = "$whenText I flagged $label. " +
                            (currentWin?.fact ?: "Your latest logs no longer show that pattern."),
                        meaning = "You acted on it, and it shows. That's exactly how lasting habits are built.",
                        suggestion = currentWin?.suggestion ?: "Keep doing what you changed."
                    )
                }
        }

        fun areaOf(focusId: String): String = focusId.substringBefore('_')

        private fun gettingStarted(report: CoachReport): CoachFinding {
            val t = report.today
            return CoachFinding(
                id = "getting_started",
                area = "logging",
                kind = FindingKind.LOGGING,
                priority = 0,
                fact = "There isn't enough history yet to spot patterns (${report.loggedDaysLastWeek} of the last 7 days have logs). " +
                    "Today so far: ${t.caloriesSoFar} kcal and ${t.proteinSoFar}g protein.",
                meaning = report.targetMeanings.first(),
                suggestion = "Log every meal for the next few days and I'll start spotting your patterns."
            )
        }

        fun whyLines(focus: CoachFinding, report: CoachReport): List<String> {
            val lines = mutableListOf(focus.fact, focus.meaning)
            if (focus.area in setOf("protein", "calories", "veg", "fibre")) {
                lines += "Based on ${report.completeDaysLastWeek} fully logged days in the last week. " +
                    "Days with missing meals aren't counted against you."
            }
            return lines
        }

        fun fallbackInsight(focus: CoachFinding, why: List<String>, note: String? = null) = CoachInsight(
            headline = focus.fact,
            action = focus.suggestion,
            why = why.drop(1),
            focusId = focus.id,
            focusKind = focus.kind,
            note = note
        )

        fun parseResponse(response: String, focus: CoachFinding, why: List<String>): CoachInsight {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()
            val parsed = runCatching {
                val json = JSONObject(cleaned)
                json.optString("headline").trim() to json.optString("action").trim()
            }.getOrNull()
            val (headline, action) = when {
                parsed != null && parsed.first.isNotBlank() -> parsed
                cleaned.isNotBlank() && !cleaned.startsWith("{") -> cleaned to ""
                else -> return fallbackInsight(focus, why)
            }
            return CoachInsight(
                headline = headline,
                action = action.ifBlank { focus.suggestion },
                why = why,
                focusId = focus.id,
                focusKind = focus.kind
            )
        }

        fun buildPrompt(
            profile: UserProfileEntity,
            report: CoachReport,
            focus: CoachFinding,
            support: CoachFinding?,
            memory: List<CoachMemoryEntry>,
            today: LocalDate,
            now: LocalTime,
            nutrition: List<NutritionEntryEntity>,
            notes: List<CoachNote> = emptyList()
        ): String {
            val t = report.today
            val goalText = when (profile.goal) {
                "LOSE_WEIGHT" -> "lose weight"
                "GAIN_MUSCLE" -> "build muscle"
                else -> "maintain weight and stay fit"
            }
            val meals = if (t.mealsLogged.isEmpty()) "none yet" else t.mealsLogged.joinToString { it.name.lowercase() }
            val treats = if (t.treatsToday.isEmpty()) "none" else t.treatsToday.joinToString()
            val dayName = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
            val recentMessages = memory.filter { it.date < today }.sortedByDescending { it.date }.take(5)
                .joinToString("\n") { "- ${it.date.format(dayName)}: ${it.headline}" }
                .ifBlank { "- none yet" }

            return buildString {
                appendLine("Current time: ${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))}")
                appendLine("User: goal is to $goalText. Age ${profile.age}, ${profile.weightKg.toInt()} kg.")
                appendLine()
                appendLine("THE FOCUS (${focus.kind.name.lowercase()}): ${focus.fact}")
                appendLine("Why it matters: ${focus.meaning}")
                appendLine("A tip you can use or improve on: ${focus.suggestion}")
                if (support != null && focus.kind != FindingKind.WIN) {
                    appendLine()
                    appendLine("Also going well (mention only if it fits naturally in a few words): ${support.fact}")
                }
                appendLine()
                appendLine("Today so far: ${t.caloriesSoFar} kcal, ${t.proteinSoFar}g protein (${t.proteinStillNeeded}g more to reach the target). Meals logged today: $meals. Treats today: $treats.")
                appendLine("Logging: ${report.completeDaysLastWeek} of the last 7 days look fully logged, ${report.loggedDaysLastWeek} have some logs.")
                appendLine()
                appendLine("What their targets mean:")
                report.targetMeanings.forEach { appendLine("- $it") }
                appendLine()
                appendLine("Foods they often eat: ${commonFoods(nutrition, today).ifBlank { "not enough logs yet" }}")
                appendLine()
                appendLine("What you know about them from your chats (respect these):")
                appendLine(CoachNotes.promptLines(notes, today).ifBlank { "- nothing yet" })
                appendLine()
                appendLine("Your recent messages (newest first):")
                appendLine(recentMessages)
            }
        }

        /** The user's most-logged foods over the last two weeks, so tips fit what they actually eat. */
        fun commonFoods(nutrition: List<NutritionEntryEntity>, today: LocalDate): String {
            val cutoff = today.minusDays(14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            return nutrition.filter { it.timestamp >= cutoff }
                .groupingBy { it.foodText.trim().lowercase().take(40) }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .take(10)
                .joinToString { it.key }
        }

        /** Short summary of the latest patterns, for the coach chat. */
        fun chatContext(report: CoachReport): String = buildString {
            appendLine("What the app has noticed over the last few weeks:")
            if (report.findings.isEmpty()) appendLine("- Not enough logs yet to see patterns.")
            report.findings.take(5).forEach { appendLine("- ${it.fact}") }
            appendLine("- ${report.completeDaysLastWeek} of the last 7 days look fully logged; days with missing meals are unknown, not days without food.")
            appendLine("What their targets mean:")
            report.targetMeanings.forEach { appendLine("- $it") }
        }
    }
}
