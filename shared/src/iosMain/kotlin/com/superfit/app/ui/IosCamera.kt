package com.superfit.app.ui

import com.superfit.app.domain.AiPhoto
import com.superfit.app.domain.HttpReply
import com.superfit.app.domain.OpenFoodFacts
import com.superfit.app.domain.OpenFoodFactsException
import com.superfit.app.domain.PackagedFood
import com.superfit.app.ui.camera.PhotoCamera
import com.superfit.app.ui.camera.PhotoKind
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * The iPhone's camera screens and the one web request they need, made by Swift (CameraBridge.swift):
 * Apple's camera for meal and label photos, Apple's live barcode scanner (VisionKit) for packets.
 */
interface IosCamera {
    fun isAvailable(): Boolean

    /**
     * [kind] is "meal", "label" or "barcode". Answers once with a shrunk JPEG photo, or the
     * barcode's number, or an error's description; all null when the person cancelled.
     */
    fun take(kind: String, done: (NSData?, String?, String?) -> Unit)

    /** Fetches [url]: the text on success, both null when the page doesn't exist (404), or an error. */
    fun fetch(url: String, done: (String?, String?) -> Unit)
}

/** Shared code's [PhotoCamera] on top of the Swift camera screens. */
class IosPhotoCamera(private val ios: IosCamera) : PhotoCamera {
    override fun isAvailable(): Boolean = ios.isAvailable()

    override fun take(kind: PhotoKind, onPhoto: (AiPhoto) -> Unit, onBarcode: (String) -> Unit, onError: (String) -> Unit) {
        val name = when (kind) {
            PhotoKind.MEAL -> "meal"
            PhotoKind.LABEL -> "label"
            PhotoKind.BARCODE -> "barcode"
        }
        ios.take(name) { jpeg, barcode, error ->
            when {
                error != null -> onError(error)
                jpeg != null -> onPhoto(AiPhoto(jpeg.toByteArray()))
                barcode != null -> onBarcode(barcode)
            }
        }
    }

    /** A packet's barcode in Open Food Facts, through Swift's web request. */
    suspend fun lookup(barcode: String): PackagedFood? = OpenFoodFacts.lookup(barcode) { url ->
        suspendCancellableCoroutine { c ->
            ios.fetch(url) { body, error ->
                when {
                    error != null -> c.resumeWithException(OpenFoodFactsException(error))
                    body == null -> c.resume(HttpReply(404, ""))
                    else -> c.resume(HttpReply(200, body))
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
