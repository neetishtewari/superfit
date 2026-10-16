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
    /** True for a meal the AI hasn't tagged yet; see [untagged]. */
    val estimated: Boolean = false,
    /** Lots of oil, ghee or butter you can't see, such as restaurant gravies or ghee-laden parathas. */
    val hiddenOil: Boolean = false,
    /** The AI's one-line reason this meal is good or not, shown as the food's "Why". */
    val why: String? = null,
    /** The AI's suggested healthier swap in the same cuisine, or null when the meal is fine as it is. */
    val swap: MealSwap? = null
) {
    /** True when the AI also gave its reason and swap, which meals tagged before those existed lack. */
    val hasAdvice: Boolean get() = why != null

    /** The same meal at a different portion size. */
    fun scaled(factor: Double): MealTags = copy(
        vegFruitServings = (vegFruitServings * factor).coerceIn(0.0, 10.0),
        fiberG = (fiberG * factor).coerceIn(0.0, 100.0),
        swap = swap?.let { it.copy(kcalSaved = (it.kcalSaved * factor).toInt()) }
    )

    fun encode(): String = listOf(
        "v1",
        "p=$processing",
        "fr=${if (fried) 1 else 0}",
        "sg=${if (addedSugar) 1 else 0}",
        "vf=${String.format(Locale.US, "%.1f", vegFruitServings)}",
        "fb=${String.format(Locale.US, "%.1f", fiberG)}",
        "tr=${if (treat) 1 else 0}",
        "ty=${clean(foodType, 30)}"
    ).plus(
        listOfNotNull(
            if (hiddenOil) "oil=1" else null,
            why?.let { "why=${clean(it, 160)}" },
            swap?.let { "sw=${clean(it.alternative, 80)}" },
            swap?.let { "sv=${it.kcalSaved}" },
            swap?.let { "sb=${clean(it.benefit, 80)}" }
        )
    ).joinToString("|")

    companion object {
        private fun clean(text: String, max: Int) = text.replace(Regex("[|=\\n]"), " ").trim().take(max)

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
                foodType = fields["ty"].orEmpty(),
                hiddenOil = fields["oil"] == "1",
                why = fields["why"]?.takeIf { it.isNotBlank() },
                swap = fields["sw"]?.takeIf { it.isNotBlank() }?.let { alt ->
                    MealSwap(alt, fields["sv"]?.toIntOrNull() ?: 0, fields["sb"].orEmpty())
                }
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
                foodType = json.optString("foodType", "").lowercase().trim(),
                hiddenOil = json.optBoolean("hiddenOil", false),
                why = json.optString("why", "").trim().takeIf { it.isNotEmpty() && it != "null" },
                swap = json.optJSONObject("swap")?.let { sw ->
                    val alternative = sw.optString("alternative", "").trim()
                    if (alternative.isEmpty()) null
                    else MealSwap(alternative, sw.optInt("kcalSaved", 0).coerceIn(0, 2000), sw.optString("benefit", "").trim())
                }
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
                "- \"foodType\": String (the main dish as a short generic name in lowercase, one or two words, e.g. \"burger\", \"pizza\", \"biryani\", \"dal\", \"salad\")\n" +
                "- \"hiddenOil\": Boolean (true if it carries a lot of oil, ghee or butter that is easy to miss, not counting deep-frying, such as restaurant gravies, butter chicken, dal makhani, ghee-laden parathas or oily sabzi)\n" +
                "- \"why\": String (one plain sentence, at most 15 words, on what makes this meal good or not for health)\n" +
                "- \"swap\": Object or null. If the meal is fried, sugary, oily, processed or a treat, suggest one realistic healthier swap this person would enjoy: " +
                "the same cuisine and region, the same kind of meal (a breakfast for a breakfast, a snack for a snack, a sweet for a sweet) and at least as much protein. " +
                "{\"alternative\": String (short, e.g. \"moong dal chilla with curd\"), \"kcalSaved\": Int (calories saved, 0 if about the same), \"benefit\": String (at most 10 words)}. " +
                "Use null when the meal is already a healthy choice.\n" +
                "Write all text in English, even when the meal is described in Hindi or another language or script.\n"

        /**
         * Stand-in for a meal the AI hasn't tagged yet (logged offline, or before tags existed).
         * It claims nothing: no veg, no fibre, not a treat. Scores and the coach leave such meals
         * out where it matters, and the daily backfill tags them.
         */
        fun untagged(): MealTags = MealTags(
            processing = 2, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 0.0,
            treat = false, foodType = "", estimated = true
        )

        /** The AI's tags when the meal has them, otherwise [untagged]. */
        fun of(entry: NutritionEntryEntity): MealTags = decode(entry.qualityTags) ?: untagged()
    }
}

/** A healthier swap for a meal, suggested by the AI. */
data class MealSwap(val alternative: String, val kcalSaved: Int, val benefit: String)
