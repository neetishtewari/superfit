package com.superfit.app.ui

import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.TodayLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The home-screen widget's numbers, kept by Swift (WidgetBridge.swift) where the widget can read
 * them: today's date, calorie target and what has been eaten, like Android's WidgetSnapshotStore.
 */
interface IosWidget {
    fun publish(date: String, targetKcal: Int, eatenKcal: Int)

    /** Signed out: the widget goes back to "Open Superfit to start", with no one's numbers. */
    fun clear()
}

/** Today's numbers for the widget, rounded like the Home ring's "left" so the two never differ by 1 kcal. */
internal fun IosWidget.publish(date: String, state: DashboardUiState.Success) {
    val target = state.macroTargets.calories.toInt()
    val left = (state.macroTargets.calories - state.caloriesEaten).toInt()
    publish(date, target, target - left)
}

/**
 * What the widget's mic or camera asked for, handed from Swift (after it checked the link came
 * from Superfit's own widget) to Today.
 */
object IosLaunch {
    private val _pending = MutableStateFlow<TodayLaunch?>(null)
    internal val pending: StateFlow<TodayLaunch?> = _pending.asStateFlow()

    fun voice() {
        _pending.value = TodayLaunch.VOICE
    }

    fun camera() {
        _pending.value = TodayLaunch.CAMERA
    }

    internal fun handled() {
        _pending.value = null
    }

    private val _recap = MutableStateFlow<String?>(null)

    /** A month's recap to open (yyyy-MM), from the reminder on the 1st. */
    internal val recap: StateFlow<String?> = _recap.asStateFlow()

    fun recap(month: String) {
        _recap.value = month
    }

    internal fun recapHandled() {
        _recap.value = null
    }
}
