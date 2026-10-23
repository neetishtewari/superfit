package com.superfit.app.domain

import org.json.JSONObject

/** The weekly note at the top of Diet Quality: one thing going well and at most two to change. */
data class DietNote(val good: String, val fixes: List<String>) {
    fun encode(): String = JSONObject().apply {
        put("good", good)
        put("fixes", org.json.JSONArray(fixes))
    }.toString()

    companion object {
        fun decode(raw: String?): DietNote? {
            if (raw.isNullOrBlank()) return null
            val o = runCatching { JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) }.getOrNull() ?: return null
            val good = o.optString("good", "").trim()
            val fixes = o.optJSONArray("fixes")?.let { a -> (0 until a.length()).map { a.optString(it, "").trim() } }.orEmpty()
                .filter { it.isNotEmpty() }.take(2)
            if (good.isEmpty() && fixes.isEmpty()) return null
            return DietNote(good, fixes)
        }
    }
}

/**
 * The AI calls behind the user's own nutritionist's advice: reading any numbers out of it, and
 * phrasing the weekly Diet Quality note. The app works out every fact; the AI only reads and words.
 */
object NutritionistNote {

    private const val TARGETS_INSTRUCTION =
        "A person pasted advice from their nutritionist. Find any daily nutrition targets it states as numbers.\n" +
            "Return a raw JSON object {\"kcal\": Double or null, \"protein\": Double or null, \"carbs\": Double or null, " +
            "\"fat\": Double or null, \"fibre\": Double or null}, in kcal and grams per day.\n" +
            "Only use numbers the advice actually gives for a whole day; never estimate or invent one. " +
            "A range means its middle. Per-meal numbers are not daily targets. If none are given, all are null."

    /** Daily targets the advice names, or null when it names none. */
    suspend fun readTargets(advice: String): AdviceTargets? {
        if (advice.isBlank()) return null
        val json = GeminiClient.generateContent(
            prompt = "Advice: \"${advice.take(1500)}\"",
            systemInstructionText = TARGETS_INSTRUCTION,
            isJson = true
        )
        return AdviceTargets.decode(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    }

    private const val NOTE_INSTRUCTION =
        "You are an experienced, warm nutritionist reviewing a client's last 7 days of eating. Your philosophy: let people " +
            "live a normal life, only correct what really matters for their goal, and never scare them over small things.\n" +
            "You get the facts the app worked out. Use only these facts and the foods named; never invent numbers or foods.\n" +
            "Return a raw JSON object {\"good\": String, \"fixes\": [String]}:\n" +
            "- \"good\": one sentence on what is going well, naming real foods from their week.\n" +
            "- \"fixes\": at most 2 sentences, each one specific, doable change that would matter most, naming foods and amounts " +
            "where the facts show them. Use an empty list if nothing important needs changing.\n" +
            "If their own nutritionist's advice is given, follow it and never contradict it. Plain words, at most 25 words each, English."

    /** The facts the note is written from, in plain lines. */
    fun facts(metrics: DietQualityMetrics): String = buildString {
        append("Diet Quality score: ${metrics.overallScore} / 100\n")
        metrics.breakdown.forEach { append("- ${it.label}: ${it.points}/${it.maxPoints}. ${it.detail}\n") }
        val foods = metrics.allRatedItems.filter { it.rated }.take(12)
        if (foods.isNotEmpty()) {
            append("Foods this week (times logged, verdict, the rater's reason):\n")
            foods.forEach { append("- ${it.foodText}: ${it.weeklyFrequency}x, ${it.verdict.label}. ${it.scoreRationale}\n") }
        }
    }

    suspend fun write(metrics: DietQualityMetrics): DietNote? {
        if (!metrics.hasEnoughData) return null
        val json = GeminiClient.generateContent(
            prompt = PersonalNutrition.promptContext() + facts(metrics),
            systemInstructionText = NOTE_INSTRUCTION,
            isJson = true
        )
        return DietNote.decode(json)
    }
}
