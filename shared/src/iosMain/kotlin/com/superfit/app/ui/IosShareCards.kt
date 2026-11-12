package com.superfit.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.WorkoutCardData
import com.superfit.app.domain.AppLog
import com.superfit.app.ui.share.MAX_PHOTO_EDGE
import com.superfit.app.ui.share.SHARE_TEXT
import com.superfit.app.ui.share.ShareCardHost
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * The iPhone's photo picker and share sheet for share cards, done by Swift (ShareBridge.swift).
 * Apple's photo picker gives the app only the one photo picked, so no photo-library access is asked.
 */
interface IosShareCards {
    /**
     * Opens Apple's photo picker. Answers once with the photo as a JPEG, turned the right way up and
     * at most [maxEdge] pixels on its longest side, or null when it couldn't be read. No answer
     * when the person cancels.
     */
    fun pickPhoto(maxEdge: Int, done: (NSData?) -> Unit)

    /** Saves the PNG in the app's own temporary folder (replacing the last one) and opens the share sheet with [text]. */
    fun sharePicture(png: NSData, text: String)

    /** A short message that fades by itself, like Android's toast. */
    fun notice(message: String)
}

/** The shared share screen's iPhone side. */
class IosShareCardHost(
    private val ios: IosShareCards,
    override val prefs: ShareCardPrefs,
    override val workoutCards: WorkoutCardData?
) : ShareCardHost {

    @Composable
    override fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember(ios) {
            {
                ios.pickPhoto(MAX_PHOTO_EDGE) { jpeg ->
                    // The photo stays in memory only while the share screen is open.
                    latest(jpeg?.let { decode(it.toByteArray()) })
                }
            }
        }
    }

    override suspend fun share(picture: ImageBitmap) {
        val png = withContext(Dispatchers.Default) {
            Image.makeFromBitmap(picture.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        }
        if (png == null || png.isEmpty()) {
            notice("Couldn't make the picture. Please try again.")
            return
        }
        ios.sharePicture(png.toNSData(), SHARE_TEXT)
    }

    override fun notice(message: String) = ios.notice(message)

    private fun decode(bytes: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        AppLog.w("IosShareCards", "Couldn't read the picked photo", e)
        null
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
