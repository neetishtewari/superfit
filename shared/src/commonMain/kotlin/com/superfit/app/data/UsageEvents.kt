package com.superfit.app.data

/**
 * Anonymous usage counts sent from shared screens. Each app plugs in its own reporter (Android:
 * AppAnalytics, which stays off in the dev app and when the person turns usage reports off).
 * Events carry only how something was done, never food, numbers, health data, names or email.
 */
object UsageEvents {
    var report: (event: String, params: Map<String, String>) -> Unit = { _, _ -> }

    /** A meal item swapped for a different food, by picking a choice or typing/saying a name. */
    fun mealItemChanged(typed: Boolean) = report("meal_item_changed", mapOf("how" to if (typed) "typed" else "choice"))

    /** A meal item's amount typed in, or switched between household units and g/ml ([how]: typed, switch). */
    fun mealAmountChanged(how: String) = report("meal_amount_changed", mapOf("how" to how))
}
