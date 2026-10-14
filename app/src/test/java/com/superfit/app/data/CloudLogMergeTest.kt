package com.superfit.app.data

import com.superfit.app.data.CloudLogMerge.CloudDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLogMergeTest {

    @Test
    fun phoneOnlyItemsAreUploadedAndNothingIsRestored() {
        val plan = CloudLogMerge.plan(listOf(100L, 200L, 300L), emptyList())

        assertEquals(setOf(100L, 200L, 300L), plan.uploadTimestamps)
        assertTrue(plan.restoreTimestamps.isEmpty())
        assertTrue(plan.staleDocs.isEmpty())
    }

    @Test
    fun cloudOnlyItemsAreRestoredToAnEmptyPhone() {
        val cloud = listOf(CloudDoc("100", 100L), CloudDoc("200", 200L))

        val plan = CloudLogMerge.plan(emptyList(), cloud)

        assertEquals(setOf(100L, 200L), plan.restoreTimestamps)
        assertTrue(plan.uploadTimestamps.isEmpty())
    }

    @Test
    fun oldSingleDocumentBackupIsMergedNotUsedToReplaceThePhone() {
        // The old bug: every meal was saved to document "0", so the cloud held only the latest one.
        val cloud = listOf(CloudDoc("0", 900L))

        val plan = CloudLogMerge.plan(listOf(100L, 200L, 900L), cloud)

        assertTrue(plan.restoreTimestamps.isEmpty())
        assertEquals(setOf(100L, 200L, 900L), plan.uploadTimestamps)
        assertEquals(listOf(CloudDoc("0", 900L)), plan.staleDocs)
    }

    @Test
    fun itemOnlyUnderAnOldNameIsRestoredAndReuploadedUnderItsNewName() {
        val plan = CloudLogMerge.plan(emptyList(), listOf(CloudDoc("7", 700L)))

        assertEquals(setOf(700L), plan.restoreTimestamps)
        assertEquals(setOf(700L), plan.uploadTimestamps)
        assertEquals(listOf(CloudDoc("7", 700L)), plan.staleDocs)
    }

    @Test
    fun alreadyInSyncNeedsNoWork() {
        val cloud = listOf(CloudDoc("100", 100L), CloudDoc("200", 200L))

        val plan = CloudLogMerge.plan(listOf(100L, 200L), cloud)

        assertTrue(plan.restoreTimestamps.isEmpty())
        assertTrue(plan.uploadTimestamps.isEmpty())
        assertTrue(plan.staleDocs.isEmpty())
    }

    @Test
    fun documentsWithoutATimestampAreIgnored() {
        val plan = CloudLogMerge.plan(listOf(100L), listOf(CloudDoc("0", 0L)))

        assertTrue(plan.restoreTimestamps.isEmpty())
        assertEquals(setOf(100L), plan.uploadTimestamps)
        assertTrue(plan.staleDocs.isEmpty())
    }
}
