package com.superfit.app.data

import com.superfit.app.domain.BodyValue
import com.superfit.app.domain.MeasuredBody
import com.superfit.app.domain.ScaleReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ScaleImportTest {
    private val day = 24L * 3600 * 1000
    private val now = 20_000L * day

    private class Source(var samples: BodySamples?) : HealthSource {
        override fun isAvailable() = true
        override suspend fun requestAccess() = true
        override suspend fun readActivity(date: LocalDate): ActivityTelemetryEntity? = null
        override suspend fun readDistanceMeters(date: LocalDate): Double? = null
        override suspend fun readSleep(date: LocalDate): SleepTelemetryEntity? = null
        override suspend fun readWorkouts(fromMs: Long, toMs: Long, bmr: Double): List<HealthWorkout>? = null
        override suspend fun readBody(sinceMs: Long) = samples
    }

    private val added = mutableListOf<Pair<Double, String>>()

    private fun importer(prefs: Prefs = MemoryPrefs()) = ScaleImport(
        prefs = prefs,
        weighIns = { emptyList() },
        profileWeightKg = { 80.0 },
        addWeight = { kg, _, note -> added += kg to note },
        zone = { TimeZone.UTC }
    )

    private fun weighIn(id: String, kg: Double, daysAgo: Long) = ScaleReading(id, now - daysAgo * day, kg, "Withings")

    @Test
    fun aWeighInCloseToYourOwnGoesStraightIntoTheLog() = runTest {
        val scale = importer()
        scale.importNew(Source(BodySamples(listOf(weighIn("a", 79.4, 1)), emptyList(), emptyList())), now)

        assertEquals(listOf(79.4 to "from Withings"), added)
        assertNull(scale.nextPending())
        assertEquals(listOf("a"), scale.mine().map { it.id })
    }

    @Test
    fun someoneElsesWeighInIsAskedAboutFirst() = runTest {
        val scale = importer()
        scale.importNew(Source(BodySamples(listOf(weighIn("p", 58.0, 1)), emptyList(), emptyList())), now)

        assertEquals(emptyList(), added)
        assertEquals("p", scale.nextPending()?.id)

        scale.answer("p", isMe = false)
        assertNull(scale.nextPending())
        assertEquals(emptyList(), added)
    }

    @Test
    fun bodyFatFromTheSameScaleSetsTheCalorieTarget() = runTest {
        val prefs = MemoryPrefs()
        val scale = importer(prefs)
        val reading = weighIn("a", 79.8, 0)
        scale.importNew(
            Source(BodySamples(listOf(reading), listOf(BodyValue(reading.timeMs, "Withings", 22.5)), emptyList())),
            now
        )
        // The first reading with body fat is always asked about, so a partner can't set the baseline.
        scale.answer("a", isMe = true)

        assertEquals(22.5, MeasuredBody.bodyFatPct)
        MeasuredBody.bodyFatPct = null
        importer(prefs).load()
        assertEquals(22.5, MeasuredBody.bodyFatPct)
        MeasuredBody.bodyFatPct = null
        MeasuredBody.measuredOn = null
    }

    @Test
    fun aFailedReadChangesNothing() = runTest {
        val scale = importer()
        scale.importNew(Source(null), now)

        assertEquals(emptyList(), added)
        assertEquals(emptyList(), scale.mine())
    }
}
