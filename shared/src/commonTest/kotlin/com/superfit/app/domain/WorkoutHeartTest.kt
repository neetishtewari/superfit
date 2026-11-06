package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutHeartTest {

    private val start = 1_000_000L
    private fun samples(vararg bpm: Int, everySeconds: Int = 30) =
        bpm.mapIndexed { i, b -> HeartSample(start + i * everySeconds * 1000L, b) }

    @Test
    fun maxHeartRateFromAge() {
        assertEquals(187, WorkoutHeart.maxHeartRate(30))
        assertNull(WorkoutHeart.maxHeartRate(0))
    }

    @Test
    fun effortBands() {
        assertEquals(Effort.EASY, WorkoutHeart.effort(120, 187))
        assertEquals(Effort.STEADY, WorkoutHeart.effort(135, 187))
        assertEquals(Effort.HARD, WorkoutHeart.effort(155, 187))
        assertEquals(Effort.MAX, WorkoutHeart.effort(170, 187))
    }

    @Test
    fun tooFewReadingsGiveNoLine() {
        val few = samples(*IntArray(10) { 140 })
        assertNull(WorkoutHeart.line(few, start, start + 600_000, 30))
    }

    @Test
    fun lineFindsPeakAndMinutesPerBand() {
        // 20 minutes easy, then 10 minutes max, a reading every 30 s.
        val bpm = IntArray(40) { 110 } + IntArray(20) { 175 } + intArrayOf(180)
        val s = samples(*bpm)
        val line = WorkoutHeart.line(s, start, s.last().timeMillis, age = 30)
        assertNotNull(line)
        line!!
        assertEquals(180, line.peakBpm)
        // The peak's point sits in the hard part of the line.
        assertTrue(line.bpm[line.peakIndex] >= 175)
        assertEquals(20, line.effortMinutes[Effort.EASY])
        assertEquals(10, line.effortMinutes[Effort.MAX])
        assertNull(line.effortMinutes[Effort.STEADY])
        assertTrue(line.x.all { it in 0f..1f })
        assertEquals(line.bpm.size, line.efforts.size)
    }

    @Test
    fun noAgeMeansOneColourAndNoBands() {
        val s = samples(*IntArray(30) { 140 + it })
        val line = WorkoutHeart.line(s, start, s.last().timeMillis, age = null)!!
        assertTrue(line.efforts.isEmpty())
        assertTrue(line.effortMinutes.isEmpty())
    }

    @Test
    fun longGapsDoNotCountAsTime() {
        val s = listOf(HeartSample(0, 170), HeartSample(10 * 60_000L, 170), HeartSample(10 * 60_000L + 60_000L, 170))
        assertEquals(mapOf(Effort.MAX to 1), WorkoutHeart.effortMinutes(s, 187))
    }

    @Test
    fun readingsOutsideTheWorkoutAreDropped() {
        val inside = samples(*IntArray(25) { 130 })
        val outside = listOf(HeartSample(start - 60_000, 200), HeartSample(start + 10_000_000, 200))
        val line = WorkoutHeart.line(inside + outside, start, inside.last().timeMillis, 30)!!
        assertEquals(130, line.peakBpm)
    }
}
