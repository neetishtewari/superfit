package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachExportGoalLabelTest {

    @Test
    fun `report uses the same goal names`() {
        assertEquals("Tone up", CoachExportBuilder.goalLabel("MAINTAIN"))
    }
}
