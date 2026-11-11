package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@OptIn(ExperimentalTime::class)
class SleepNightTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val date = LocalDate(2026, 11, 12)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(2026, 11, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun span(fromDay: Int, fromHour: Int, toDay: Int, toHour: Int, toMinute: Int = 0) =
        SleepSpan(at(fromDay, fromHour), at(toDay, toHour, toMinute))

    @Test
    fun aNightThatStartedBeforeMidnightCountsForTheMorningItEnded() {
        // 23:00 to 01:00, a short wake, then 01:30 to 07:00: one night of 7.5 hours.
        val night = SleepNight.summarise(listOf(span(11, 23, 12, 1), SleepSpan(at(12, 1, 30), at(12, 7))), date, zone)!!

        assertEquals(7.5 * 3600, night.sleepDurationSeconds.toDouble())
        assertEquals((7.5 * 3600 * 0.2).toLong(), night.deepSleepDurationSeconds)
        assertEquals(93, night.readinessScore)
        assertEquals("2026-11-12", night.date)
    }

    @Test
    fun theWatchAndASleepAppRecordingTheSameNightCountItOnce() {
        val watch = span(11, 23, 12, 7)
        val app = span(12, 0, 12, 7, 30)

        assertEquals(8.5 * 3600, SleepNight.summarise(listOf(watch, app), date, zone)!!.sleepDurationSeconds.toDouble())
    }

    @Test
    fun anAfternoonNapIsAddedToThatDay() {
        val night = span(11, 23, 12, 6)
        val nap = span(12, 14, 12, 15)

        assertEquals(8L * 3600, SleepNight.summarise(listOf(night, nap), date, zone)!!.sleepDurationSeconds)
    }

    @Test
    fun theNightBeforeDoesNotCountForToday() {
        assertNull(SleepNight.summarise(listOf(span(10, 23, 11, 7)), date, zone))
    }

    @Test
    fun nothingRecordedMeansNoNight() {
        assertNull(SleepNight.summarise(emptyList(), date, zone))
        assertNull(SleepNight.summarise(listOf(SleepSpan(at(12, 7), at(12, 7))), date, zone))
    }

    @Test
    fun aLongNightIsCappedAtFullyRested() {
        assertEquals(100, SleepNight.summarise(listOf(span(11, 21, 12, 8)), date, zone)!!.readinessScore)
    }
}
