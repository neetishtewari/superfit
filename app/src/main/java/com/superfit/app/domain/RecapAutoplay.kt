package com.superfit.app.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Timing and number count-up for the recap when it plays on its own like a story. */
object RecapAutoplay {

    /** How long a card stays up: long enough to read its line, never a rush and never a drag. */
    fun durationMs(line: String, title: String = ""): Long =
        (4_500L + (line.length + title.length) * 28L).coerceIn(5_500L, 10_000L)

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
