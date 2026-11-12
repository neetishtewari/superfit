package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@OptIn(ExperimentalTime::class)
class ReminderPlanTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 11, 12)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(today, kotlinx.datetime.LocalTime(hour, minute)).toInstant(zone)

    private fun notes(nowHour: Int, eaten: Int, tip: String? = null, style: CoachStyle = CoachStyle.SUPPORTIVE) =
        ReminderPlan.notes(at(nowHour), zone, today, 21, 0, eaten, 2000, tip, style)

    @Test
    fun beforeReminderTime_todayAndSixMoreDays() {
        val notes = notes(nowHour = 10, eaten = 0)
        assertEquals(ReminderPlan.DAYS_AHEAD, notes.size)
        assertEquals("reminder-2026-11-12", notes.first().id)
        assertEquals(at(21).epochSeconds, notes.first().atEpochSeconds)
    }

    @Test
    fun afterReminderTime_todayIsSkipped() {
        val notes = notes(nowHour = 22, eaten = 0)
        assertEquals(ReminderPlan.DAYS_AHEAD - 1, notes.size)
        assertEquals("reminder-2026-11-13", notes.first().id)
    }

    @Test
    fun underLogged_asksToCompleteTheLog() {
        val note = notes(nowHour = 10, eaten = 900).first()
        assertEquals("📝 Complete your food log?", note.title)
        assertTrue(note.body.contains("900 / 2000"))
    }

    @Test
    fun fullDay_withTip_sendsTheCoachTip() {
        val note = notes(nowHour = 10, eaten = 1800, tip = "**Protein** short. Add curd.").first()
        assertEquals(CoachStyle.SUPPORTIVE.tipTitle(), note.title)
        assertEquals("Protein short. Add curd.", note.body)
    }

    @Test
    fun fullDay_withoutTip_sendsTheFallback() {
        val note = notes(nowHour = 10, eaten = 1800).first()
        assertEquals(CoachStyle.SUPPORTIVE.completeDayFallback(1800), note.title to note.body)
    }

    @Test
    fun comingDays_areLogReminders() {
        val tomorrow = notes(nowHour = 10, eaten = 1800, tip = "tip")[1]
        assertTrue(tomorrow.body.contains("0 / 2000"))
    }
}
