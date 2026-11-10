package com.superfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteCheckTest {
    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, RouteCheck.distanceMetres(18.0, 73.0, 19.0, 73.0), 50.0)
    }

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, RouteCheck.distanceMetres(18.52, 73.85, 18.52, 73.85), 0.001)
    }
}
