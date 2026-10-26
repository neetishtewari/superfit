package com.superfit.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.superfit.app.data.getUserSharedPrefs

/** Asks Android to place the Superfit widget, and remembers whether the Home tip was dismissed. */
object WidgetPin {
    private const val KEY_TIP_DISMISSED = "widget_tip_dismissed"
    const val TIP_MIN_MEALS = 3

    private fun provider(context: Context) = ComponentName(context, SuperfitWidgetReceiver::class.java)

    /** False on launchers that can't add a widget from inside an app. */
    fun canPin(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun isPlaced(context: Context): Boolean = count(context) > 0

    /** How many Superfit widgets are on the home screen. */
    fun count(context: Context): Int =
        AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context)).size

    /** Shows Android's own "Add to home screen" prompt. */
    fun requestPin(context: Context): Boolean =
        runCatching {
            AppWidgetManager.getInstance(context).requestPinAppWidget(provider(context), null, null)
        }.getOrDefault(false)

    fun isTipDismissed(context: Context): Boolean =
        getUserSharedPrefs(context).getBoolean(KEY_TIP_DISMISSED, false)

    fun dismissTip(context: Context) {
        getUserSharedPrefs(context).edit().putBoolean(KEY_TIP_DISMISSED, true).apply()
    }

    fun shouldShowTip(context: Context, mealsLogged: Int): Boolean =
        mealsLogged >= TIP_MIN_MEALS && !isTipDismissed(context) && canPin(context) && !isPlaced(context)
}
