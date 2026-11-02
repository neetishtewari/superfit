package com.superfit.app.ui.recap

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.content.FileProvider
import com.superfit.app.domain.RecapAutoplay
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns the recap's story cards into one short MP4 with the month's music, made on the phone with
 * Android's own encoders (no library, nothing uploaded). Each card holds, then softly fades into the
 * next; the music loops to fit and fades out at the end.
 */
object RecapVideo {
    const val WIDTH = 720
    const val HEIGHT = 1280
    private const val FPS = 24
    private const val VIDEO_BITRATE = 4_000_000
    private const val AUDIO_BITRATE = 128_000
    private const val TIMEOUT_US = 10_000L

    /** One card ready for the encoder: the picture as I420 (Y, then U, then V). */
    class Frame internal constructor(internal val yuv: ByteArray)

    /** Scales a captured card to video size and converts it once, so the picture can be dropped. */
    fun frameOf(card: Bitmap): Frame {
        val soft = if (card.config == Bitmap.Config.HARDWARE) card.copy(Bitmap.Config.ARGB_8888, false) else card
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // The captured card has rounded corners; fill behind them with the card's own colour.
        canvas.drawColor(soft.getPixel(soft.width / 2, min(8, soft.height - 1)).let { if (Color.alpha(it) < 255) Color.BLACK else it })
        canvas.drawBitmap(soft, null, Rect(0, 0, WIDTH, HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(WIDTH * HEIGHT)
        out.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        out.recycle()
        return Frame(toI420(pixels))
    }

    private fun toI420(argb: IntArray): ByteArray {
        val frameSize = WIDTH * HEIGHT
        val yuv = ByteArray(frameSize * 3 / 2)
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4
        for (j in 0 until HEIGHT) {
            for (i in 0 until WIDTH) {
                val c = argb[j * WIDTH + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                yuv[j * WIDTH + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    yuv[vIndex++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    /**
     * Writes the video to [out]. [onProgress] goes from 0 to 1 while it works. Runs off the main
     * thread; throws when the phone can't encode, so the caller can say so.
     */
    suspend fun make(context: Context, frames: List<Frame>, music: Int, out: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.Default) {
        require(frames.isNotEmpty())
        val cardMs = RecapAutoplay.videoCardMs(frames.size)
        val totalMs = cardMs * frames.size
        val audio = runCatching { encodeAudio(context, music, totalMs) }.getOrNull()
        onProgress(0.1f)
        out.delete()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            encodeVideo(frames, cardMs, totalMs, muxer, audio) { onProgress(0.1f + 0.9f * it) }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        out
    }

    private class Encoded(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

    private fun encodeVideo(frames: List<Frame>, cardMs: Long, totalMs: Long, muxer: MediaMuxer, audio: Encoded?, onProgress: (Float) -> Unit) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val totalFrames = (totalMs * FPS / 1000).toInt()
        val blend = ByteArray(frames[0].yuv.size)
        var frameIndex = 0
        var inputDone = false
        var videoTrack = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        if (frameIndex >= totalFrames) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs(frameIndex), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val yuv = frameAt(frames, frameIndex, cardMs, blend)
                            val image = codec.getInputImage(idx) ?: error("No input image")
                            writeI420(image, yuv)
                            codec.queueInputBuffer(idx, 0, yuv.size, ptsUs(frameIndex), 0)
                            frameIndex++
                            onProgress(frameIndex / totalFrames.toFloat())
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(codec.outputFormat)
                        val audioTrack = audio?.let { muxer.addTrack(it.format) }
                        muxer.start()
                        muxing = true
                        if (audio != null && audioTrack != null) {
                            audio.samples.forEach { (bytes, i) -> muxer.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), i) }
                        }
                    }
                    out >= 0 -> {
                        val buf = codec.getOutputBuffer(out)!!
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && muxing) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buf, info)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun ptsUs(frame: Int): Long = frame * 1_000_000L / FPS

    /** The picture for one frame: a card, or two cards mid-fade. */
    private fun frameAt(frames: List<Frame>, index: Int, cardMs: Long, blend: ByteArray): ByteArray {
        val t = index * 1000L / FPS
        val card = (t / cardMs).toInt().coerceAtMost(frames.lastIndex)
        val local = t - card * cardMs
        val fadeStart = cardMs - RecapAutoplay.VIDEO_FADE_MS
        if (card == frames.lastIndex || local < fadeStart) return frames[card].yuv
        val a = ((local - fadeStart) * 256 / RecapAutoplay.VIDEO_FADE_MS).toInt().coerceIn(0, 256)
        val from = frames[card].yuv
        val to = frames[card + 1].yuv
        for (k in blend.indices) {
            blend[k] = (((from[k].toInt() and 0xff) * (256 - a) + (to[k].toInt() and 0xff) * a) shr 8).toByte()
        }
        return blend
    }

    /** Copies an I420 picture into the encoder's own layout, whatever its strides. */
    private fun writeI420(image: android.media.Image, yuv: ByteArray) {
        val planes = image.planes
        val chromaW = WIDTH / 2
        val chromaH = HEIGHT / 2
        for (p in 0 until 3) {
            val plane = planes[p]
            val buf = plane.buffer
            val w = if (p == 0) WIDTH else chromaW
            val h = if (p == 0) HEIGHT else chromaH
            val start = when (p) {
                0 -> 0
                1 -> WIDTH * HEIGHT
                else -> WIDTH * HEIGHT + chromaW * chromaH
            }
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (row in 0 until h) {
                val src = start + row * w
                if (pixelStride == 1) {
                    buf.position(row * rowStride)
                    buf.put(yuv, src, w)
                } else {
                    val base = row * rowStride
                    for (col in 0 until w) buf.put(base + col * pixelStride, yuv[src + col])
                }
            }
        }
    }

    /** The month's track, decoded, looped to [totalMs], faded in and out, and encoded as AAC. */
    private fun encodeAudio(context: Context, music: Int, totalMs: Long): Encoded {
        val (pcm, rate, channels) = decode(context, music)
        val total = (totalMs * rate / 1000).toInt() * channels
        val fadeIn = rate * channels / 4
        val fadeOut = rate * channels * 3 / 2
        val looped = ShortArray(total) { i ->
            val v = pcm[i % pcm.size] * 0.8f
            val gain = when {
                i < fadeIn -> i / fadeIn.toFloat()
                i > total - fadeOut -> (total - i) / fadeOut.toFloat()
                else -> 1f
            }
            (v * gain).toInt().toShort()
        }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        var pos = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val pts = (pos / channels).toLong() * 1_000_000L / rate
                        val n = min(buf.capacity() / 2, total - pos)
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(looped, pos, n)
                            codec.queueInputBuffer(idx, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    out >= 0 -> {
                        val buf = codec.getOutputBuffer(out)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(bytes)
                            samples += bytes to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return Encoded(outFormat ?: error("No audio format"), samples)
    }

    /** Decodes a bundled Ogg track to 16-bit PCM. */
    private fun decode(context: Context, music: Int): Triple<ShortArray, Int, Int> {
        val extractor = MediaExtractor()
        context.resources.openRawResourceFd(music).use { afd -> extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        extractor.selectTrack(track)
        val inFormat = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFormat, null, null, 0)
        codec.start()
        var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val chunks = mutableListOf<ShortArray>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(idx)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    out >= 0 -> {
                        val buf = codec.getOutputBuffer(out)!!
                        if (info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            chunks += shorts
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        val pcm = ShortArray(chunks.sumOf { it.size })
        var at = 0
        chunks.forEach {
            it.copyInto(pcm, at)
            at += it.size
        }
        require(pcm.isNotEmpty())
        return Triple(pcm, rate, channels)
    }

    /** Hands the video to the share sheet: Instagram, WhatsApp, or save it. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your month"))
    }
}
