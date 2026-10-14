package com.superfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeTest {

    private data class Entry(val id: Long, val timestamp: Long)

    private fun merge(cloud: List<Entry>, local: Set<Long>) =
        withoutLegacyDuplicates(cloud, local, { it.id }, { it.timestamp })

    @Test
    fun keepsEntriesWithRealIds() {
        val cloud = listOf(Entry(1, 100), Entry(2, 200))
        assertEquals(cloud, merge(cloud, setOf(100L)))
    }

    @Test
    fun dropsLegacyEntryAlreadyOnThePhone() {
        assertEquals(emptyList<Entry>(), merge(listOf(Entry(0, 100)), setOf(100L)))
    }

    @Test
    fun dropsLegacyEntryAlreadyBackedUpUnderItsRealId() {
        val cloud = listOf(Entry(0, 100), Entry(7, 100))
        assertEquals(listOf(Entry(7, 100)), merge(cloud, emptySet()))
    }

    @Test
    fun keepsLegacyEntryNobodyElseHas() {
        assertEquals(listOf(Entry(0, 300)), merge(listOf(Entry(0, 300)), setOf(100L)))
    }
}
