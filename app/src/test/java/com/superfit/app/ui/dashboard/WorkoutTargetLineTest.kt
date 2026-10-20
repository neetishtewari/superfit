package com.superfit.app.ui.dashboard

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTargetLineTest {

    @Test
    fun showsWhereTheTargetComesFrom() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals(
                "2,100 + 275 from your run = 2,375 today",
                workoutTargetLine(targetCalories = 2375.4, workoutKcal = 275.2, source = "from your run")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun restDaysShowNothingExtra() {
        assertNull(workoutTargetLine(targetCalories = 2100.0, workoutKcal = 0.0, source = "from your workouts"))
    }
}
