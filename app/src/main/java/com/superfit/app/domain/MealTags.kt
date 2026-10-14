package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject
import java.util.Locale

/**
 * What kind of food a logged meal is, judged by the AI when the meal is logged. Diet Quality and
 * the coach use these instead of guessing from words in the meal's name.
 */
data class MealTags(
    /** 1 = whole foods or home-style cooking, 2 = processed, 3 = ultra-processed or fast food. */
    val processing: Int,
    val fried: Boolean,
    val addedSugar: Boolean,
    /** Rough servings of vegetables or fruit (a serving is about a cup of sabzi, salad or a piece of fruit). */
    val vegFruitServings: Double,
    val fiberG: Double,
    /** Something most coaches would call a treat: fast food, sweets, desserts, fried snacks, sugary drinks, alcohol. */
    val treat: Boolean,
    /** Short generic dish name, e.g. "burger", "biryani", "dal". */
    val foodType: String,
    /** True when worked out from the meal's name in the app, not by the AI. */
    val estimated: Boolean = false
) {
    fun encode(): String = listOf(
        "v1",
        "p=$processing",
        "fr=${if (fried) 1 else 0}",
        "sg=${if (addedSugar) 1 else 0}",
        "vf=${String.format(Locale.US, "%.1f", vegFruitServings)}",
        "fb=${String.format(Locale.US, "%.1f", fiberG)}",
        "tr=${if (treat) 1 else 0}",
        "ty=${foodType.replace(Regex("[|=\\n]"), " ").trim().take(30)}"
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): MealTags? {
            if (raw.isNullOrBlank() || !raw.startsWith("v1|")) return null
            val fields = raw.split('|').drop(1).mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            return MealTags(
                processing = fields["p"]?.toIntOrNull()?.coerceIn(1, 3) ?: return null,
                fried = fields["fr"] == "1",
                addedSugar = fields["sg"] == "1",
                vegFruitServings = fields["vf"]?.toDoubleOrNull() ?: 0.0,
                fiberG = fields["fb"]?.toDoubleOrNull() ?: 0.0,
                treat = fields["tr"] == "1",
                foodType = fields["ty"].orEmpty()
            )
        }

        /** Reads the tag fields from the AI's meal JSON. Returns null if the AI left them out. */
        fun fromJson(json: JSONObject): MealTags? {
            if (!json.has("processing")) return null
            return MealTags(
                processing = json.optInt("processing", 2).coerceIn(1, 3),
                fried = json.optBoolean("fried", false),
                addedSugar = json.optBoolean("addedSugar", false),
                vegFruitServings = json.optDouble("vegFruitServings", 0.0).takeIf { !it.isNaN() }?.coerceIn(0.0, 10.0) ?: 0.0,
                fiberG = json.optDouble("fiber", 0.0).takeIf { !it.isNaN() }?.coerceIn(0.0, 100.0) ?: 0.0,
                treat = json.optBoolean("treat", false),
                foodType = json.optString("foodType", "").lowercase().trim()
            )
        }

        /** The instructions added to every meal-parsing prompt so the AI returns tags too. */
        const val PROMPT_FIELDS =
            "- \"processing\": Int (1 = whole foods or home-style cooking from basic ingredients, such as dal, roti, sabzi, rice, eggs, grilled chicken, fruit, curd; " +
                "2 = processed, such as white bread, cheese, packaged cereal, restaurant curries heavy in cream or butter; " +
                "3 = ultra-processed or fast food, such as burgers, pizza, chips, instant noodles, packaged sweets, soft drinks)\n" +
                "- \"fried\": Boolean (true if deep-fried or mostly fried)\n" +
                "- \"addedSugar\": Boolean (true if it has a noticeable amount of added sugar)\n" +
                "- \"vegFruitServings\": Double (servings of vegetables or fruit; about a cup of cooked vegetables, a bowl of salad or one piece of fruit is 1)\n" +
                "- \"fiber\": Double (estimated grams of fibre)\n" +
                "- \"treat\": Boolean (true for fast food, desserts, sweets, fried snacks, sugary drinks or alcohol)\n" +
                "- \"foodType\": String (the main dish as a short generic name in lowercase, one or two words, e.g. \"burger\", \"pizza\", \"biryani\", \"dal\", \"salad\")\n"

        private val friedWords = Regex(
            "\\b(fried|fries|pakoras?|pakodas?|samosas?|bhature|bhaturas?|puris?|pooris?|vadas?|kachoris?|bondas?|nuggets?|tempura|chips|crisps)\\b",
            RegexOption.IGNORE_CASE
        )
        private val fibreWords = Regex(
            "\\b(dal|daal|rajma|chana|chole|chickpeas?|lentils?|beans|oats|oatmeal|sprouts|quinoa|millet|bajra|jowar|ragi|brown rice|whole ?wheat|salad)\\b",
            RegexOption.IGNORE_CASE
        )

        /**
         * A best guess from the meal's name, for meals logged before tags existed or when the AI
         * couldn't be reached. Rougher than the AI's tags, and marked [estimated].
         */
        fun estimate(entry: NutritionEntryEntity): MealTags {
            val text = entry.foodText
            val treatCategory = CoachPatternEngine.treatCategory(text)
            val keywordScore = FoodQualityEngine.keywordScore(text, entry.proteinG >= 14.0)
            val processing = when {
                treatCategory != null -> 3
                keywordScore >= 9 -> 1
                keywordScore >= 6 -> 2
                else -> 3
            }
            val vegFruit = if (CoachPatternEngine.isVegOrFruit(text)) 1.0 else 0.0
            val fibre = vegFruit * 3.0 + (if (fibreWords.containsMatchIn(text)) 5.0 else 1.0)
            return MealTags(
                processing = processing,
                fried = friedWords.containsMatchIn(text),
                addedSugar = treatCategory == "dessert" || treatCategory == "sugary drink",
                vegFruitServings = vegFruit,
                fiberG = fibre,
                treat = treatCategory != null,
                foodType = treatCategory ?: text.lowercase().split(Regex("\\s+")).filter { it.any(Char::isLetter) }.take(2).joinToString(" "),
                estimated = true
            )
        }

        /** The AI's tags when the meal has them, otherwise a guess from its name. */
        fun of(entry: NutritionEntryEntity): MealTags = decode(entry.qualityTags) ?: estimate(entry)
    }
}
