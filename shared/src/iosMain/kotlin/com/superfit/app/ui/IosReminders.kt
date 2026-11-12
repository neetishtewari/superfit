package com.superfit.app.ui

import com.superfit.app.data.Prefs
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.ReminderNote
import com.superfit.app.domain.ReminderPlan
import com.superfit.app.ui.dashboard.CoachingInsightState
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.TodayModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The iPhone's notification schedule for Superfit's evening reminder, kept by Swift (ReminderBridge.swift). */
interface IosReminders {
    /** Asks to show notifications (Apple's prompt appears only the first time); answers whether they're allowed. */
    fun ask(done: (Boolean) -> Unit)

    /** Replaces every scheduled Superfit reminder with [notes]. */
    fun schedule(notes: List<ReminderNote>)

    /** Removes every scheduled Superfit reminder (reminder off, or signed out). */
    fun cancelAll()
}

/**
 * Keeps the iPhone's evening reminders in step with Today: written ahead from today's numbers and
 * tip (ReminderPlan), and written again whenever they or the reminder settings change.
 */
class ReminderSync(private val ios: IosReminders, private val prefs: Prefs, private val today: TodayModel) {
    private val settingsChanged = MutableStateFlow(0)

    private val _allowed = MutableStateFlow(true)

    /** False when the person has turned Superfit's notifications off in the phone's Settings. */
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    /** Follows Today while this account is signed in. */
    suspend fun run() {
        if (ReminderPlan.enabled(prefs)) checkAllowed()
        combine(today.state, today.coaching, settingsChanged) { state, coaching, _ -> state to coaching }
            .collect { (state, coaching) -> if (state is DashboardUiState.Success) update(state, coaching) }
    }

    /** Asks again (also brings back the switch after a trip to the phone's Settings). */
    fun checkAllowed() {
        ios.ask { _allowed.value = it }
    }

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(ReminderPlan.KEY_ENABLED, on) }
        if (on) checkAllowed()
        settingsChanged.value++
    }

    fun setTime(hour: Int, minute: Int) {
        prefs.edit {
            putInt(ReminderPlan.KEY_HOUR, hour)
            putInt(ReminderPlan.KEY_MINUTE, minute)
        }
        settingsChanged.value++
    }

    private fun update(state: DashboardUiState.Success, coaching: CoachingInsightState) {
        if (!ReminderPlan.enabled(prefs)) return ios.cancelAll()
        // Rounded like the Home ring and the widget.
        val target = state.macroTargets.calories.toInt()
        val eaten = target - (state.macroTargets.calories - state.caloriesEaten).toInt()
        val tip = (coaching as? CoachingInsightState.Success)?.insight?.asText()
        ios.schedule(
            ReminderPlan.notes(
                now = Clock.System.now(),
                zone = TimeZone.currentSystemDefault(),
                today = LocalDate.parse(today.day),
                hour = ReminderPlan.hour(prefs),
                minute = ReminderPlan.minute(prefs),
                eatenKcal = eaten,
                targetKcal = target,
                coachTip = tip,
                style = CoachStyle.current
            )
        )
    }
}
