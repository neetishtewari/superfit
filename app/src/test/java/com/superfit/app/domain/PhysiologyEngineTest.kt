package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysiologyEngineTest {

    @Test
    fun testBmrCalculationMale() {
        val bmrMale = PhysiologyEngine.calculateBmr(
            weightKg = 80.0,
            heightCm = 180.0,
            age = 30,
            isMale = true
        )
        // Mifflin-St Jeor: 10 * 80 + 6.25 * 180 - 5 * 30 + 5 = 800 + 1125 - 150 + 5 = 1780.0
        assertEquals(1780.0, bmrMale, 0.01)
    }

    @Test
    fun testBmrCalculationFemale() {
        val bmrFemale = PhysiologyEngine.calculateBmr(
            weightKg = 60.0,
            heightCm = 165.0,
            age = 25,
            isMale = false
        )
        // Mifflin-St Jeor: 10 * 60 + 6.25 * 165 - 5 * 25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        assertEquals(1345.25, bmrFemale, 0.01)
    }

    @Test
    fun testTdeeCalculation() {
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 450.0)
        assertEquals(2230.0, tdee, 0.01)
    }

    @Test
    fun testReadinessScoreOptimum() {
        // 8 hours sleep, 1.6 hours deep sleep (exactly 20%)
        val score = PhysiologyEngine.calculateReadinessScore(28800, 5760)
        assertEquals(100, score)
    }

    @Test
    fun testReadinessScoreSuboptimal() {
        // 4 hours sleep, 0 deep sleep
        val score = PhysiologyEngine.calculateReadinessScore(14400, 0)
        // sleepRatio = 0.5 -> 35 points, deepSleepQuality = 0 -> 0 points. Total = 35
        assertEquals(35, score)
    }

    @Test
    fun testMacroTargetsShiftBasedOnReadiness() {
        val profile = UserProfileEntity(
            id = 0,
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            isMale = true,
            activityMultiplier = 1.2
        )

        // Scenario 1: Perfect readiness (100) -> higher protein target
        val targetsHighReadiness = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = 2500.0,
            readinessScore = 100
        )
        // Protein should be 80 * 2.2 = 176g
        assertEquals(176.0, targetsHighReadiness.proteinG, 0.01)

        // Scenario 2: Zero readiness (0) -> lower protein target
        val targetsLowReadiness = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = 2500.0,
            readinessScore = 0
        )
        // Protein should be 80 * 1.6 = 128g
        assertEquals(128.0, targetsLowReadiness.proteinG, 0.01)
    }
}
