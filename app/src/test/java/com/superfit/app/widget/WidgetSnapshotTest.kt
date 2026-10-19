package com.superfit.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSnapshotTest {

    private val today = "2026-10-19"

    @Test
    fun `calories left with thousands separators`() {
        assertEquals(WidgetText("1,240 kcal left", "of 2,100"), widgetText(WidgetSnapshot(today, 2100, 860), today))
    }

    @Test
    fun `over the target`() {
        assertEquals("180 kcal over", widgetText(WidgetSnapshot(today, 2000, 2180), today).headline)
    }

    @Test
    fun `exactly on target`() {
        assertEquals("0 kcal left", widgetText(WidgetSnapshot(today, 2000, 2000), today).headline)
    }

    @Test
    fun `a new day starts from the full target`() {
        assertEquals("2,000 kcal left", widgetText(WidgetSnapshot("2026-10-18", 2000, 1500), today).headline)
    }

    @Test
    fun `no target yet`() {
        assertEquals(WidgetText("Open Superfit to start", null), widgetText(null, today))
        assertEquals(WidgetText("Open Superfit to start", null), widgetText(WidgetSnapshot(today, 0, 0), today))
    }
}
