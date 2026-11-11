package com.superfit.app.ui.camera

import com.superfit.app.domain.Nutrients
import com.superfit.app.domain.PackagedFood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PhotoLogTest {
    private val biscuits = PackagedFood("Biscuits", Nutrients(480.0, 7.0, 70.0, 20.0, 2.0), 25.0, null)

    @Test
    fun knownBarcode_showsThePacketToCheck() = runTest {
        val photoLog = PhotoLog(this, lookup = { biscuits })
        photoLog.onBarcode("8901063010000")
        testScheduler.advanceUntilIdle()
        assertEquals(PhotoLogState.PackResult(biscuits), photoLog.state.value)
        assertEquals(PhotoKind.BARCODE, photoLog.kind)
    }

    @Test
    fun unknownBarcode_offersTheLabelInstead() = runTest {
        val photoLog = PhotoLog(this, lookup = { null })
        photoLog.onBarcode("8901063010000")
        testScheduler.advanceUntilIdle()
        val failed = assertIs<PhotoLogState.Failed>(photoLog.state.value)
        assertEquals(PhotoKind.LABEL, failed.retake)
    }

    @Test
    fun offline_retriesTheBarcode_andCloseClearsIt() = runTest {
        val photoLog = PhotoLog(this, lookup = { error("offline") })
        photoLog.onBarcode("8901063010000")
        testScheduler.advanceUntilIdle()
        assertEquals(PhotoKind.BARCODE, assertIs<PhotoLogState.Failed>(photoLog.state.value).retake)
        photoLog.close()
        assertEquals(PhotoLogState.Idle, photoLog.state.value)
    }
}
