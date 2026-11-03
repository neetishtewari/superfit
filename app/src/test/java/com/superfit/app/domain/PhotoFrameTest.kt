package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFrameTest {

    private val cardW = 1080
    private val cardH = 1920

    @Test
    fun cannotZoomOutPastFillingTheCard() {
        val f = PhotoFrame().moved(0.3f, 0f, 0f, 4000, 3000, cardW, cardH)
        assertEquals(1f, f.scale, 0.0001f)
        assertFalse(f.adjusted)
    }

    @Test
    fun zoomStopsAtFourTimes() {
        val f = PhotoFrame().moved(10f, 0f, 0f, 4000, 3000, cardW, cardH)
        assertEquals(PhotoFrame.MAX_SCALE, f.scale, 0.0001f)
    }

    @Test
    fun landscapePhotoMovesSidewaysButNotUpAtFill() {
        // A 4:3 landscape photo covering a 9:16 card is much wider than the card, as tall as it.
        val f = PhotoFrame().moved(1f, 5f, 5f, 4000, 3000, cardW, cardH)
        val (w, _) = PhotoFrame.drawnSize(4000, 3000, cardW, cardH, 1f)
        assertEquals((w - cardW) / 2f / cardW, f.offsetX, 0.0001f)
        assertEquals(0f, f.offsetY, 0.0001f)
        assertTrue(f.adjusted)
    }

    @Test
    fun zoomedPhotoCanMoveBothWaysWithinItsEdges() {
        val f = PhotoFrame(scale = 2f).moved(1f, -5f, -5f, 1080, 1920, cardW, cardH)
        assertEquals(-0.5f, f.offsetX, 0.0001f)
        assertEquals(-0.5f, f.offsetY, 0.0001f)
    }

    @Test
    fun resetKeepsStatsPlace() {
        val f = PhotoFrame(scale = 3f, offsetX = 0.2f, offsetY = 0.1f, statsTop = true).reset()
        assertEquals(PhotoFrame(statsTop = true), f)
    }
}
