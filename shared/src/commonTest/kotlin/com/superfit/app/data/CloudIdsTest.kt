package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CloudIdsTest {

    @Test
    fun newIdsAreUnique() {
        val ids = List(1000) { CloudIds.newId() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun newIdIsNeverTheLegacyId() {
        assertNotEquals(CloudIds.LEGACY_UNNUMBERED, CloudIds.newId())
    }
}
