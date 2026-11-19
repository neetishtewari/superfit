package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import kotlin.math.roundToInt

/** One suggestion under the meal box: a whole past meal, or one food from past meals. */
data class MealSuggestion(
    /** What the list shows, e.g. "2 roti, dal and salad" or "paneer bhurji". */
    val text: String,
    val kind: Kind,
    /** For a whole meal, its calories last time it was logged (logging it again reuses them). */
    val kcal: Int? = null,
    /** Where in [text] the typed letters matched, so the list can highlight them. */
    val matchStart: Int = 0,
    val matchLength: Int = 0,
    /** The whole box after picking this suggestion. */
    val result: String
) {
    enum class Kind { MEAL, FOOD }
}

/**
 * Finishes what the person is typing from their own past meals, like a search box does. Built
 * once from all logged meals; [suggest] then runs on every letter, on the phone, with no AI.
 *
 * Two kinds, both only from the person's own logs (there is no built-in food list):
 * - whole past meals whose text has a word starting with what was typed. Picking one fills the
 *   box; logging it again is an exact repeat, so it keeps last time's numbers.
 * - single foods (the item names stored with each meal), matched against only the part being
 *   typed now, after the last comma, "and", "with" or "+". Picking one swaps that part for the
 *   food, keeps any amount typed before it, and adds ", " for the next food.
 */
class MealSuggester private constructor(
    private val meals: List<Entry>,
    private val foods: List<Entry>
) {
    private class Entry(val text: String, val score: Double, val kcal: Int? = null) {
        val folded = fold(text)
        val key get() = folded.key

        /** The part of [text] that key letters [from] until [from] + [length] came from. */
        fun span(from: Int, length: Int): Pair<Int, Int> {
            if (length <= 0 || from >= folded.origin.size) return 0 to 0
            val start = folded.origin[from]
            val end = folded.origin[minOf(from + length, folded.origin.size) - 1] + 1
            return start to end - start
        }
    }

    fun suggest(typed: String, max: Int = MAX): List<MealSuggestion> {
        if (typed.isBlank() || max <= 0) return emptyList()
        val segment = currentSegment(typed)

        val mealQuery = key(typed)
        val mealHits = if (letters(mealQuery) < MIN_LETTERS) {
            emptyList()
        } else {
            rank(meals, mealQuery).filter { it.first.key != mealQuery }
        }

        // The part being typed now, without its amount ("2 ro" looks for "ro"). If nothing
        // starts that way (the person typed a unit, "2 katori da"), try just its last word.
        val foodHits = mutableListOf<Pair<Entry, Int>>()
        var replaceFrom = segment.queryStart
        val partQuery = key(typed.substring(segment.queryStart))
        if (letters(partQuery) >= MIN_LETTERS) foodHits += rank(foods, partQuery).filter { it.first.key != partQuery }
        if (foodHits.isEmpty()) {
            val lastWord = lastWordStart(typed, segment.queryStart)
            val wordQuery = key(typed.substring(lastWord))
            if (lastWord > segment.queryStart && letters(wordQuery) >= MIN_LETTERS) {
                foodHits += rank(foods, wordQuery).filter { it.first.key != wordQuery && it.first.key != partQuery }
                replaceFrom = lastWord
            }
        }
        val foodQueryLength = key(typed.substring(replaceFrom)).length

        val mealKeys = mealHits.map { it.first.key }.toSet()
        val foodsLeft = foodHits.filter { it.first.key !in mealKeys }
        val mealCount = minOf(mealHits.size, maxOf(MEALS_WHEN_BOTH, max - foodsLeft.size), max)
        val foodCount = minOf(foodsLeft.size, max - mealCount)

        val head = typed.substring(0, replaceFrom)
        return mealHits.take(mealCount).map { (meal, at) ->
            val (start, length) = meal.span(at, mealQuery.length)
            MealSuggestion(
                text = meal.text,
                kind = MealSuggestion.Kind.MEAL,
                kcal = meal.kcal,
                matchStart = start,
                matchLength = length,
                result = meal.text
            )
        } + foodsLeft.take(foodCount).map { (food, at) ->
            val (start, length) = food.span(at, foodQueryLength)
            MealSuggestion(
                text = food.text,
                kind = MealSuggestion.Kind.FOOD,
                matchStart = start,
                matchLength = length,
                result = head + inSentence(food.text, head) + ", "
            )
        }
    }

    /** Entries whose text starts with [query], then ones with a later word starting with it, best first. */
    private fun rank(entries: List<Entry>, query: String): List<Pair<Entry, Int>> =
        entries.mapNotNull { e ->
            val at = when {
                e.key.startsWith(query) -> 0
                else -> e.key.indexOf(" $query").takeIf { it >= 0 }?.plus(1)
            } ?: return@mapNotNull null
            e to at
        }.sortedWith(compareBy<Pair<Entry, Int>> { if (it.second == 0) 0 else 1 }.thenByDescending { it.first.score })

    private data class Segment(val queryStart: Int)

    /** Where the food being typed now begins, after the last separator and any leading amount. */
    private fun currentSegment(typed: String): Segment {
        var start = 0
        SEPARATOR.findAll(typed).forEach { start = it.range.last + 1 }
        val amount = AMOUNT.find(typed.substring(start))
        return Segment(start + (amount?.range?.last?.plus(1) ?: 0))
    }

    /** "Paneer bhurji" reads "paneer bhurji" after other words; names like "KFC" stay as they are. */
    private fun inSentence(food: String, head: String): String =
        if (head.isBlank() || food.length < 2 || !food[1].isLowerCase()) food else food[0].lowercaseChar() + food.substring(1)

    private fun lastWordStart(typed: String, from: Int): Int {
        var i = typed.length
        while (i > from && !typed[i - 1].isWhitespace()) i--
        return i
    }

    companion object {
        const val MAX = 4
        private const val MIN_LETTERS = 2
        private const val MEALS_WHEN_BOTH = 2
        private val SEPARATOR = Regex("""\s*(?:[,;+&]|\b(?:and|with|aur|e|com|y|con)\b)\s*""", RegexOption.IGNORE_CASE)
        private val AMOUNT = Regex("""^\s*(?:\d+(?:[.,/]\d+)?|½|¼|¾)\s*(?:x\s*)?""", RegexOption.IGNORE_CASE)

        val EMPTY = MealSuggester(emptyList(), emptyList())

        /**
         * Builds the suggestion lists from all meals. [scores] maps each meal's text (as
         * [FoodPredictor] groups it) to its score, so often, recently and usually-at-this-hour
         * eaten meals come first.
         */
        fun build(entries: List<NutritionEntryEntity>, scores: Map<String, Double>, nowMs: Long): MealSuggester {
            val usable = entries.filter { it.calories > 0 && it.foodText.isNotBlank() && it.foodText != "invalid" }
            val meals = usable.groupBy { it.foodText.trim().lowercase() }.map { (lowered, group) ->
                val latest = group.maxBy { it.timestamp }
                val text = group.groupBy { it.foodText.trim() }.maxBy { it.value.size }.key
                Entry(text, scores[lowered] ?: group.size.toDouble(), latest.calories.roundToInt())
            }.distinctBy { it.key }

            val foodUses = mutableMapOf<String, MutableList<Pair<String, Long>>>()
            for (meal in usable) {
                val items = MealItems.decode(meal.items)?.items ?: continue
                for (item in items) {
                    val name = item.name.trim()
                    if (name.isBlank()) continue
                    foodUses.getOrPut(key(name)) { mutableListOf() } += name to meal.timestamp
                }
            }
            val foods = foodUses.filterKeys { letters(it) >= MIN_LETTERS }.map { (k, uses) ->
                val text = uses.groupBy { it.first }.maxBy { it.value.size }.key
                val days = ((nowMs - uses.maxOf { it.second }) / DAY_MS).coerceAtLeast(0.0)
                Entry(text, uses.size * (1.0 + 1.0 / (1.0 + days)))
            }
            return MealSuggester(meals, foods)
        }

        private const val DAY_MS = 1000.0 * 60 * 60 * 24

        /** Lower case, accents and punctuation removed, single spaces: what matching compares. */
        internal fun key(text: String): String = fold(text).key

        private class Folded(val key: String, val origin: IntArray)

        /** [key] for [text], with where in [text] each of its letters came from. */
        private fun fold(text: String): Folded {
            val sb = StringBuilder(text.length)
            val origin = ArrayList<Int>(text.length)
            text.forEachIndexed { i, raw ->
                val ch = raw.lowercaseChar().let { FOLD[it] ?: it }
                if (ch.isLetterOrDigit()) {
                    sb.append(ch)
                    origin += i
                } else if (sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                    origin += i
                }
            }
            if (sb.isNotEmpty() && sb.last() == ' ') {
                sb.setLength(sb.length - 1)
                origin.removeAt(origin.size - 1)
            }
            return Folded(sb.toString(), origin.toIntArray())
        }

        private fun letters(text: String) = text.count { it.isLetter() }

        private val FOLD: Map<Char, Char> = buildMap {
            fun add(chars: String, to: Char) = chars.forEach { put(it, to) }
            add("àáâãäåāă", 'a')
            add("çćč", 'c')
            add("èéêëēė", 'e')
            add("ìíîïī", 'i')
            add("ñń", 'n')
            add("òóôõöøō", 'o')
            add("ùúûüū", 'u')
            add("ýÿ", 'y')
            add("śš", 's')
            add("źżž", 'z')
        }
    }
}
