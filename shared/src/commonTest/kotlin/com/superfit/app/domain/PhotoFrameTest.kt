package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoFrameTest {

    private val cardW = 1080
    private val cardH = 1920

    @Test
    fun zoomsOutToALittleBelowTheWholePhotoFitting() {
        // 4:3 landscape on a 9:16 card: covering needs height, fitting needs width.
        val f = PhotoFrame().moved(0.01f, 0f, 0f, 4000, 3000, cardW, cardH)
        val fit = (cardW / 4000f) / (cardH / 3000f)
        assertEquals(fit * PhotoFrame.BELOW_FIT, f.scale, 0.0001f)
        assertTrue(f.adjusted)
        assertTrue(f.showsFill(4000, 3000, cardW, cardH))
    }

    @Test
    fun smallerPhotoStaysOnTheCardWhenDragged() {
        val f = PhotoFrame(scale = 0.75f).moved(1f, 5f, -5f, 1080, 1920, cardW, cardH)
        // Three quarters of the card's size, so it can move an eighth of the card each way and no further.
        assertEquals(0.125f, f.offsetX, 0.0001f)
        assertEquals(-0.125f, f.offsetY, 0.0001f)
    }

    @Test
    fun fillOnlyShowsWhenThePhotoIsSmallerThanTheCard() {
        assertFalse(PhotoFrame().showsFill(4000, 3000, cardW, cardH))
        assertFalse(PhotoFrame(scale = 2f).showsFill(4000, 3000, cardW, cardH))
        assertTrue(PhotoFrame(scale = 0.9f).showsFill(1080, 1920, cardW, cardH))
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
        val f = PhotoFrame(scale = 3f, offsetX = 0.2f, offsetY = 0.1f, statsTop = true, fill = PhotoFill.COLOUR).reset()
        assertEquals(PhotoFrame(statsTop = true, fill = PhotoFill.COLOUR), f)
    }
}
