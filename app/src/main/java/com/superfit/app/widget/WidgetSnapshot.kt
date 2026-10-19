package com.superfit.app.widget

import android.content.Context
import com.superfit.app.data.getUserSharedPrefs
import java.text.NumberFormat
import java.util.Locale

/** Today's calorie target and what has been eaten, saved by the dashboard for the widget. */
data class WidgetSnapshot(val date: String, val targetKcal: Int, val eatenKcal: Int)

object WidgetSnapshotStore {
    private const val KEY_DATE = "widget_date"
    private const val KEY_TARGET = "widget_target"
    private const val KEY_EATEN = "widget_eaten"

    /** Returns true when the snapshot changed, so the widget only redraws when it needs to. */
    fun save(context: Context, snapshot: WidgetSnapshot): Boolean {
        if (load(context) == snapshot) return false
        getUserSharedPrefs(context).edit()
            .putString(KEY_DATE, snapshot.date)
            .putInt(KEY_TARGET, snapshot.targetKcal)
            .putInt(KEY_EATEN, snapshot.eatenKcal)
            .apply()
        return true
    }

    fun load(context: Context): WidgetSnapshot? {
        val prefs = getUserSharedPrefs(context)
        val date = prefs.getString(KEY_DATE, null) ?: return null
        return WidgetSnapshot(date, prefs.getInt(KEY_TARGET, 0), prefs.getInt(KEY_EATEN, 0))
    }
}

data class WidgetText(val headline: String, val subline: String?)

/** "1,240 kcal left" / "of 2,100". A snapshot from an earlier day means nothing eaten yet today. */
fun widgetText(snapshot: WidgetSnapshot?, today: String): WidgetText {
    if (snapshot == null || snapshot.targetKcal <= 0) return WidgetText("Open Superfit to start", null)
    val eaten = if (snapshot.date == today) snapshot.eatenKcal else 0
    val left = snapshot.targetKcal - eaten
    val number = NumberFormat.getIntegerInstance(Locale.US)
    val headline = if (left >= 0) "${number.format(left)} kcal left" else "${number.format(-left)} kcal over"
    return WidgetText(headline, "of ${number.format(snapshot.targetKcal)}")
}
