package com.superfit.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.superfit.app.data.Prefs
import com.superfit.app.domain.RecapAutoplay
import com.superfit.app.domain.SetupItem
import com.superfit.app.ui.recap.RecapHost
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.YearMonth
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * The recap's music and video on iPhone, done by Swift (RecapBridge.swift). The music is the same
 * original monthly track as Android's, as m4a. The video is made on the phone with Apple's own
 * encoder, nothing uploaded, and only leaves where the person shares it.
 */
interface IosRecapMedia {
    /** Plays [month]'s track (1-12) quietly, fading in; the phone's silent switch keeps it quiet. */
    fun playMusic(month: Int)
    fun pauseMusic()
    fun stopMusic()

    /** Starts a video of [width] x [height] at [fps]. False when the phone can't. */
    fun videoStart(width: Int, height: Int, fps: Int): Boolean

    /** Adds one frame (BGRA pixels, [width] * 4 bytes a row). Waits while the encoder catches up. */
    fun videoAppend(bgra: NSData, frame: Int): Boolean

    /**
     * Finishes the video, lays [month]'s track under it (looped, faded) and opens the share sheet.
     * Answers true once the sheet is up, false when the video couldn't be made.
     */
    fun videoFinish(month: Int, durationMs: Long, fileName: String, done: (Boolean) -> Unit)
    fun videoCancel()

    /** Whether both the microphone and speech recognition are allowed. */
    fun micAllowed(): Boolean

    /** Asks for the microphone and speech recognition (or opens Settings once refused). */
    fun requestMic(done: (Boolean) -> Unit)

    /** Answers whether a Superfit widget is on the Home Screen. */
    fun widgetPlaced(done: (Boolean) -> Unit)
}

/** The shared recap screen's iPhone side. */
class IosRecapHost(
    private val media: IosRecapMedia,
    private val prefs: Prefs,
    private val month: YearMonth,
    private val onOpenSettings: () -> Unit,
    private val notice: (String) -> Unit
) : RecapHost {
    override val healthName = "Apple Health"

    override var musicMuted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    private val track get() = month.month.ordinal + 1

    override fun toggleMusic(): Boolean {
        musicMuted = !musicMuted
        prefs.edit { putBoolean(KEY_MUTED, musicMuted) }
        if (musicMuted) media.pauseMusic() else media.playMusic(track)
        return musicMuted
    }

    override fun playMusic() {
        if (!musicMuted) media.playMusic(track)
    }

    override fun pauseMusic() = media.pauseMusic()

    fun release() = media.stopMusic()

    override suspend fun shareVideo(stories: Int, frame: suspend (Int, Float) -> ImageBitmap, onProgress: (Float) -> Unit) {
        require(stories > 0)
        check(media.videoStart(WIDTH, HEIGHT, FPS)) { "No video encoder" }
        try {
            val cardMs = RecapAutoplay.videoCardMs(stories)
            val totalMs = cardMs * stories
            val totalFrames = (totalMs * FPS / 1000).toInt()
            val pictures = Pictures(stories, frame)
            onProgress(0.01f)
            for (i in 0 until totalFrames) {
                val shot = RecapAutoplay.videoShot(i * 1000L / FPS, cardMs, stories)
                val pixels = pictures.at(shot.card, shot.reveal, shot.slide)
                val ok = withContext(Dispatchers.Default) { media.videoAppend(pixels.toNSData(), i) }
                check(ok) { "Frame $i not added" }
                onProgress(0.01f + 0.94f * (i + 1) / totalFrames)
            }
            val name = "superfit-${month.month.name.lowercase()}-${month.year}.mp4"
            val shared = suspendCancellableCoroutine { cont -> media.videoFinish(track, totalMs, name) { cont.resume(it) } }
            check(shared) { "Video not finished" }
            onProgress(1f)
        } catch (e: Exception) {
            media.videoCancel()
            throw e
        }
    }

    @Composable
    override fun rememberSetupFix(onChanged: () -> Unit): (SetupItem) -> Unit = remember(onChanged) {
        { item ->
            when (item) {
                SetupItem.MIC -> media.requestMic { onChanged() }
                SetupItem.WIDGET -> notice("Touch and hold your Home Screen, tap Edit, then Add Widget, and pick Superfit.")
                SetupItem.HEALTH_CONNECT, SetupItem.LOG_DAILY -> onOpenSettings()
            }
        }
    }

    /**
     * Each video frame, drawn on demand: the card's numbers counting up, then the swipe to the next
     * card. Keeps only the pictures it still needs, so a long recap doesn't fill memory.
     */
    private class Pictures(val cards: Int, val capture: suspend (Int, Float) -> ImageBitmap) {
        private var current: Triple<Int, Float, Image>? = null
        private var next: Pair<Int, Image>? = null
        private val info = ImageInfo(WIDTH, HEIGHT, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        private val out = Bitmap().apply { allocPixels(info) }
        private val canvas = Canvas(out)
        private var backdrop = 0xFF000000.toInt()

        private suspend fun picture(card: Int, reveal: Float): Image {
            current?.let { (c, r, img) -> if (c == card && r == reveal) return img }
            val eased = FastOutSlowInEasing.transform(reveal)
            val img = next?.takeIf { it.first == card && reveal == 0f }?.second ?: grab(card, eased)
            current = Triple(card, reveal, img)
            return img
        }

        private suspend fun grab(card: Int, reveal: Float): Image {
            val bitmap = capture(card, reveal).asSkiaBitmap()
            // The captured card has rounded corners; fill behind them with the card's own colour.
            val c = bitmap.getColor(bitmap.width / 2, minOf(8, bitmap.height - 1))
            backdrop = if ((c ushr 24) < 255) 0xFF000000.toInt() else c
            return Image.makeFromBitmap(bitmap)
        }

        suspend fun at(card: Int, reveal: Float, slide: Float): ByteArray {
            val from = picture(card, reveal)
            canvas.clear(backdrop)
            if (slide <= 0f) {
                draw(from, 0f)
            } else {
                val to = next?.takeIf { it.first == card + 1 }?.second ?: grab(card + 1, 0f).also { next = card + 1 to it }
                val x = FastOutSlowInEasing.transform(slide) * WIDTH
                draw(from, -x)
                draw(to, WIDTH - x)
            }
            return out.readPixels(info, WIDTH * 4, 0, 0) ?: error("No pixels")
        }

        private fun draw(image: Image, x: Float) {
            canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeXYWH(x, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                null,
                true
            )
        }
    }

    private companion object {
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val FPS = 24
        const val KEY_MUTED = "recap_music_muted"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
