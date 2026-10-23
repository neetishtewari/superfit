package com.superfit.app.data

import android.content.Context
import com.superfit.app.domain.AdviceTargets
import com.superfit.app.domain.DietNote
import com.superfit.app.domain.PersonalNutrition
import org.json.JSONArray

/**
 * The user's own nutritionist's advice, the targets from it they confirmed, and the foods they
 * marked "Fine for me", kept in their preferences on the phone. The keys start with "custom_" so
 * the dashboard recalculates targets and Diet Quality as soon as one changes.
 */
object NutritionAdviceStore {

    private const val KEY_ADVICE = "custom_nutritionist_advice"
    private const val KEY_TARGETS = "custom_nutritionist_targets"
    private const val KEY_FINE_FOODS = "custom_fine_foods"
    private const val KEY_NOTE = "diet_note"
    private const val KEY_NOTE_STAMP = "diet_note_stamp"

    /** Loads everything into [PersonalNutrition]. Call when the signed-in user may have changed. */
    fun load(context: Context) {
        val prefs = getUserSharedPrefs(context)
        PersonalNutrition.advice = prefs.getString(KEY_ADVICE, null)?.takeIf { it.isNotBlank() }
        PersonalNutrition.targets = AdviceTargets.decode(prefs.getString(KEY_TARGETS, null))
        PersonalNutrition.approvedFoods = prefs.getString(KEY_FINE_FOODS, null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrNull()
        }.orEmpty()
    }

    fun advice(context: Context): String = getUserSharedPrefs(context).getString(KEY_ADVICE, "").orEmpty()

    fun saveAdvice(context: Context, text: String) {
        val clean = text.trim().take(2000)
        PersonalNutrition.advice = clean.takeIf { it.isNotEmpty() }
        getUserSharedPrefs(context).edit().putString(KEY_ADVICE, clean).remove(KEY_NOTE_STAMP).apply()
    }

    fun targets(): AdviceTargets? = PersonalNutrition.targets

    /** Uses [targets] in place of the app's own (null stops using them). */
    fun applyTargets(context: Context, targets: AdviceTargets?) {
        PersonalNutrition.targets = targets?.takeIf { !it.isEmpty }
        getUserSharedPrefs(context).edit().apply {
            if (targets == null || targets.isEmpty) remove(KEY_TARGETS) else putString(KEY_TARGETS, targets.encode())
        }.apply()
    }

    fun setFineForMe(context: Context, key: String, fine: Boolean) {
        val k = PersonalNutrition.key(key)
        if (k.isEmpty()) return
        val updated = if (fine) PersonalNutrition.approvedFoods + k else PersonalNutrition.approvedFoods - k
        PersonalNutrition.approvedFoods = updated
        getUserSharedPrefs(context).edit()
            .putString(KEY_FINE_FOODS, JSONArray(updated.sorted()).toString())
            .remove(KEY_NOTE_STAMP)
            .apply()
    }

    /** The saved weekly note when it was written for [stamp] (today plus the score), else null. */
    fun note(context: Context, stamp: String): DietNote? {
        val prefs = getUserSharedPrefs(context)
        if (prefs.getString(KEY_NOTE_STAMP, null) != stamp) return null
        return DietNote.decode(prefs.getString(KEY_NOTE, null))
    }

    /** The last note written, whatever it was written for; shown while a fresh one is on its way. */
    fun lastNote(context: Context): DietNote? = DietNote.decode(getUserSharedPrefs(context).getString(KEY_NOTE, null))

    fun saveNote(context: Context, stamp: String, note: DietNote) {
        getUserSharedPrefs(context).edit().putString(KEY_NOTE, note.encode()).putString(KEY_NOTE_STAMP, stamp).apply()
    }
}
