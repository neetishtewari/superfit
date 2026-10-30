package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.util.Locale
import org.json.JSONObject

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
    /** A real treat in a real amount: desserts, mithai, fried snacks, fast food, sugary drinks, alcohol. */
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
    val swap: MealSwap? = null,
    /** The AI's 1 to 10 rating as a nutritionist, weighing the amount, the person's goal and their own advice. */
    val rating: Int? = null,
    /** Servings of fruit, part of [vegFruitServings]. */
    val fruitServings: Double = 0.0,
    /** How many different vegetables and fruits are in it, for a colourful plate. */
    val vegKinds: Int = 0,
    /** [PersonalNutrition.adviceVersion] when the meal was rated, so new advice re-rates recent meals. */
    val adviceVersion: String? = null,
    /** The AI's plain verdict for the person: great, good, portion, treat or avoid (see [FoodVerdict]). */
    val verdict: String? = null,
    /** Calories of treat food in it (the gulab jamun, not the dal rice), so a small bite isn't a treat. Null for older meals. */
    val treatKcal: Int? = null
) {
    /** True when the AI also gave its reason and swap, which meals tagged before those existed lack. */
    val hasAdvice: Boolean get() = why != null

    /** The same meal at a different portion size. */
    fun scaled(factor: Double): MealTags = copy(
        vegFruitServings = (vegFruitServings * factor).coerceIn(0.0, 10.0),
        fiberG = (fiberG * factor).coerceIn(0.0, 100.0),
        fruitServings = (fruitServings * factor).coerceIn(0.0, 10.0),
        swap = swap?.let { it.copy(kcalSaved = (it.kcalSaved * factor).toInt()) },
        treatKcal = treatKcal?.let { (it * factor).toInt().coerceAtLeast(0) }
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
            swap?.let { "sb=${clean(it.benefit, 80)}" },
            rating?.let { "r=$it" },
            if (fruitServings > 0) "fs=${String.format(Locale.US, "%.1f", fruitServings)}" else null,
            if (vegKinds > 0) "vk=$vegKinds" else null,
            adviceVersion?.let { "av=${clean(it, 8)}" },
            verdict?.let { "vd=${clean(it, 10)}" },
            treatKcal?.let { "tk=$it" }
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
                },
                rating = fields["r"]?.toIntOrNull()?.coerceIn(1, 10),
                fruitServings = fields["fs"]?.toDoubleOrNull() ?: 0.0,
                vegKinds = fields["vk"]?.toIntOrNull() ?: 0,
                adviceVersion = fields["av"],
                verdict = fields["vd"]?.takeIf { it in FoodVerdict.KEYS },
                treatKcal = fields["tk"]?.toIntOrNull()?.coerceAtLeast(0)
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
                    if (alternative.isEmpty()) {
                        null
                    } else {
                        MealSwap(alternative, sw.optInt("kcalSaved", 0).coerceIn(0, 2000), sw.optString("benefit", "").trim())
                    }
                },
                rating = json.optInt("rating", 0).takeIf { it in 1..10 },
                fruitServings = json.optDouble("fruitServings", 0.0).takeIf { !it.isNaN() }?.coerceIn(0.0, 10.0) ?: 0.0,
                vegKinds = json.optInt("vegKinds", 0).coerceIn(0, 15),
                adviceVersion = if (json.has("rating")) PersonalNutrition.adviceVersion() else null,
                verdict = json.optString("verdict", "").trim().lowercase().takeIf { it in FoodVerdict.KEYS },
                treatKcal = if (json.has("treatKcal")) json.optInt("treatKcal", 0).coerceIn(0, 5000) else null
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
                "- \"treat\": Boolean (true only for a real treat in a real amount: desserts, mithai, fried snacks, fast food, sugary drinks or alcohol. " +
                "Small amounts are not treats: a square or two of dark chocolate, a bite of mithai, tea or coffee with a little sugar, roasted chana, chana chaat, a home thali)\n" +
                "- \"treatKcal\": Int (calories from treat food in this meal: desserts, mithai, chocolate, ice cream, biscuits, fried snacks, fast food, sugary drinks or alcohol, " +
                "even in small amounts. Count only that part: for dal rice and one gulab jamun, only the gulab jamun. 0 when there is none; tea or coffee with a little sugar is 0)\n" +
                "- \"foodType\": String (the main dish as a short generic name in lowercase, one or two words, e.g. \"burger\", \"pizza\", \"biryani\", \"dal\", \"salad\")\n" +
                "- \"hiddenOil\": Boolean (true if it carries a lot of oil, ghee or butter that is easy to miss, not counting deep-frying, such as restaurant gravies, butter chicken, dal makhani, ghee-laden parathas or oily sabzi)\n" +
                "- \"why\": String (one plain sentence, at most 15 words, on what makes this meal good or not for health)\n" +
                "- \"swap\": Object or null. If the meal rates 5 or lower, suggest one realistic healthier swap this person would enjoy: " +
                "the same cuisine and region, the same kind of meal (a breakfast for a breakfast, a snack for a snack, a sweet for a sweet) and at least as much protein. " +
                "{\"alternative\": String (short, e.g. \"moong dal chilla with curd\"), \"kcalSaved\": Int (calories saved, 0 if about the same), \"benefit\": String (at most 10 words)}. " +
                "Use null when the meal is already a healthy choice.\n" +
                "- \"fruitServings\": Double (the part of vegFruitServings that is fruit)\n" +
                "- \"vegKinds\": Int (how many different vegetables and fruits are in it)\n" +
                "- \"rating\": Int 1-10. Rate it as an experienced nutritionist who lets people live a normal life and only corrects what matters for their goal. " +
                "Weigh the amount: 2 rotis with sabzi is balanced, 6 rotis is a heavy carb load, more so for weight loss. " +
                "Normal home food (dal rice, roti sabzi, curd rice, a home thali) starts around 7; more for good protein and vegetables, less for very large portions or a lot of ghee or oil. " +
                "Don't mark a home meal down for being a little low in protein; protein is judged separately over the day. " +
                "Judge supplements and packaged foods by what they do for this person: whey or a protein shake that helps reach the protein goal is about 8. " +
                "Treats and fast food are 2-4, fried snacks 3-4, sugary drinks 2. " +
                "Use the person's goal, targets, own nutritionist's advice and approved foods given with the meal; their nutritionist's advice outranks these rules. " +
                "The \"why\" sentence should explain the rating in plain words, mentioning the amount when it matters.\n" +
                "- \"verdict\": String, one of: \"great\" (a choice that helps their goal), \"good\" (fine, normal food), " +
                "\"portion\" (a good food, but this amount is too much for their goal), \"treat\" (fine now and then, even while working on the goal), " +
                "\"avoid\" (best skipped completely until their goal is reached, because it works directly against it: for example sugary soft drinks, " +
                "regular alcohol or deep-fried snacks when losing weight). Use \"avoid\" sparingly, never for normal home food, " +
                "never for a food on their approved list, and follow their nutritionist's advice.\n" +
                "Write all text in English, even when the meal is described in Hindi or another language or script.\n"

        /**
         * Stand-in for a meal the AI hasn't tagged yet (logged offline, or before tags existed).
         * It claims nothing: no veg, no fibre, not a treat. Scores and the coach leave such meals
         * out where it matters, and the daily backfill tags them.
         */
        fun untagged(): MealTags = MealTags(
            processing = 2,
            fried = false,
            addedSugar = false,
            vegFruitServings = 0.0,
            fiberG = 0.0,
            treat = false,
            foodType = "",
            estimated = true
        )

        /** The AI's tags when the meal has them, otherwise [untagged]. */
        fun of(entry: NutritionEntryEntity): MealTags = decode(entry.qualityTags) ?: untagged()
    }
}

/** A healthier swap for a meal, suggested by the AI. */
data class MealSwap(val alternative: String, val kcalSaved: Int, val benefit: String)

/** The plain words shown for a food instead of a number. */
enum class FoodVerdict(val key: String, val label: String) {
    GREAT("great", "Great"),
    GOOD("good", "Good"),
    PORTION("portion", "Watch the portion"),
    TREAT("treat", "Treat"),

    /** Treat food in a small amount (a square of chocolate): set by the app, never by the AI. */
    SMALL_TREAT("small_treat", "Small treat"),
    AVOID("avoid", "Avoid for now");

    companion object {
        /** The verdicts the AI may give. */
        val KEYS = entries.filter { it != SMALL_TREAT }.map { it.key }.toSet()

        /**
         * The AI's verdict when it gave one; older ratings are put into words from their number.
         * A treat below [Treats.REAL_TREAT_KCAL] of treat food is a Small treat, given [treatKcal].
         * A food the user marked "Fine for me" is always at least Good.
         */
        fun of(tags: MealTags, rating: Int, approved: Boolean, treatKcal: Double? = null): FoodVerdict {
            val fromAi = entries.firstOrNull { it.key == tags.verdict }
            val v = fromAi ?: when {
                tags.treat -> TREAT
                rating >= 8 -> GREAT
                rating >= 6 -> GOOD
                else -> PORTION
            }
            val sized = if (v == TREAT && treatKcal != null && treatKcal < Treats.REAL_TREAT_KCAL) SMALL_TREAT else v
            return if (approved && sized !in setOf(GREAT, GOOD)) GOOD else sized
        }
    }
}
