package com.superfit.app.domain

import com.superfit.app.data.ImportedWorkoutEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ShareOfferTest {

    private val day = 24L * 3600 * 1000
    private val today = LocalDate.fromEpochDays(100)

    private fun workout(id: String, type: Int, startDay: Long, minutes: Long, hour: Long = 7): WorkoutView {
        val start = startDay * day + hour * 3_600_000L
        val w = ImportedWorkoutEntity(
            id = id, sourcePackage = "com.sec.android.app.shealth", exerciseType = type,
            startTime = start, endTime = start + minutes * 60_000L,
            distanceMeters = 3000.0, totalKcal = 200.0, avgHeartRate = 120, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf(id)), "Samsung Health", bmr = 1700.0)
    }

    private fun pick(list: List<WorkoutView>, runs: Boolean = true, walkMin: Int? = 30, handled: Set<String> = emptySet()) =
        ShareOffer.pick(list, today, runs, walkMin, handled, TimeZone.UTC)

    @Test
    fun anyRunCounts() {
        assertEquals("r", pick(listOf(workout("r", ExerciseTypes.RUNNING, 100, 12)))?.merged?.primary?.id)
    }

    @Test
    fun shortWalksDoNot() {
        assertNull(pick(listOf(workout("w", ExerciseTypes.WALKING, 100, 25))))
        assertEquals("w", pick(listOf(workout("w", ExerciseTypes.WALKING, 100, 25)), walkMin = 20)?.merged?.primary?.id)
        assertNull(pick(listOf(workout("w", ExerciseTypes.WALKING, 100, 90)), walkMin = null))
    }

    @Test
    fun yesterdaysCountsButNotOlder() {
        assertEquals("y", pick(listOf(workout("y", ExerciseTypes.RUNNING, 99, 30, hour = 19)))?.merged?.primary?.id)
        assertNull(pick(listOf(workout("old", ExerciseTypes.RUNNING, 98, 30))))
    }

    @Test
    fun onlyTheNewestAndNothingOnceItIsHandled() {
        val older = workout("a", ExerciseTypes.RUNNING, 99, 30)
        val newer = workout("b", ExerciseTypes.WALKING, 100, 40)
        assertEquals("b", pick(listOf(older, newer))?.merged?.primary?.id)
        assertNull(pick(listOf(older, newer), handled = setOf("b")))
    }

    @Test
    fun runsCanBeTurnedOff() {
        assertNull(pick(listOf(workout("r", ExerciseTypes.RUNNING, 100, 30)), runs = false))
    }

    @Test
    fun otherWorkoutsAreNotOffered() {
        assertNull(pick(listOf(workout("c", ExerciseTypes.BIKING, 100, 60))))
    }
}
