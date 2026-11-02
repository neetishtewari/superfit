package com.superfit.app.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Timing and number count-up for the recap when it plays on its own like a story. */
object RecapAutoplay {

    /** How long a card stays up: long enough to read its line, never a rush and never a drag. */
    fun durationMs(line: String, title: String = ""): Long =
        (4_500L + (line.length + title.length) * 28L).coerceIn(5_500L, 10_000L)

    /** In the saved video: each card's time, so the whole video stays under about 45 seconds. */
    fun videoCardMs(cards: Int): Long = if (cards <= 0) 0L else (45_000L / cards).coerceIn(2_500L, 4_000L)

    /** How long a card's numbers take to count up in the video, as in the app. */
    const val VIDEO_REVEAL_MS = 1_100L

    /** The swipe from one card to the next at the end of each card. */
    const val VIDEO_SLIDE_MS = 450L

    /** The count-up is drawn 12 times a second; the video itself runs at 24. */
    const val VIDEO_STEP_MS = 1_000L / 12

    /**
     * What one moment of the video shows: [card], with its numbers [reveal] of the way up (0-1,
     * before easing), and [slide] of the way into the swipe to the next card (0 = no swipe).
     */
    data class VideoShot(val card: Int, val reveal: Float, val slide: Float)

    fun videoShot(timeMs: Long, cardMs: Long, cards: Int): VideoShot {
        val card = (timeMs / cardMs).toInt().coerceIn(0, (cards - 1).coerceAtLeast(0))
        val local = timeMs - card * cardMs
        val stepped = local / VIDEO_STEP_MS * VIDEO_STEP_MS
        val reveal = (stepped.toFloat() / VIDEO_REVEAL_MS).coerceIn(0f, 1f)
        val slideStart = cardMs - VIDEO_SLIDE_MS
        val slide = if (card < cards - 1 && local >= slideStart) ((local - slideStart).toFloat() / VIDEO_SLIDE_MS).coerceIn(0f, 1f) else 0f
        return VideoShot(card, reveal, slide)
    }

    /** A big number split so it can count up: "1.4" -> 1.4 with one decimal, "12,450" -> 12450 grouped. */
    data class Number(val prefix: String, val value: Double, val decimals: Int, val grouped: Boolean, val suffix: String)

    private val NUMBER = Regex("^([^0-9]*?)(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(.*)$")

    /** Null when the text has no number to count, such as "7 am" times (kept as they are). */
    fun parse(text: String): Number? {
        val m = NUMBER.find(text.trim()) ?: return null
        val (prefix, whole, frac, suffix) = m.destructured
        if (suffix.trim().isNotEmpty() && !suffix.trim().first().isLetter() && suffix.trim().first() != '%') return null
        val digits = whole.replace(",", "")
        val value = (if (frac.isEmpty()) digits else "$digits.$frac").toDoubleOrNull() ?: return null
        return Number(prefix, value, frac.length, whole.contains(','), suffix)
    }

    /** The number at [fraction] (0-1) of the way up, written the way the card writes it. */
    fun format(n: Number, fraction: Float): String {
        val f = fraction.coerceIn(0f, 1f)
        val v = if (f >= 1f) n.value else n.value * f
        val body = if (n.decimals > 0) {
            String.format(Locale.US, "%.${n.decimals}f", v)
        } else {
            val whole = v.roundToInt()
            if (n.grouped) String.format(Locale.US, "%,d", whole) else whole.toString()
        }
        return n.prefix + body + n.suffix
    }

    /** True when counting up would only flash zero (tiny numbers count from 0 too fast to read). */
    fun worthCounting(n: Number): Boolean = abs(n.value) >= 2
}
