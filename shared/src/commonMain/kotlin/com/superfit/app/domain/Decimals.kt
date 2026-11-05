package com.superfit.app.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Writes a number with a fixed count of decimals, the same as Java's
 * String.format(Locale.US, "%.1f", x): rounding half up from the number's shortest decimal form,
 * a dot as the separator and no grouping. Works on Android and iPhone alike.
 */
object Decimals {
    fun fixed(x: Double, decimals: Int): String {
        require(decimals >= 0)
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        val negative = x < 0 || (x == 0.0 && 1.0 / x < 0)
        val digits = plainDigits(abs(x)) ?: return fallback(x, decimals)
        val dot = digits.indexOf('.')
        val whole = if (dot == -1) digits else digits.substring(0, dot)
        val frac = if (dot == -1) "" else digits.substring(dot + 1)
        val keep = (whole + frac.padEnd(decimals, '0').take(decimals)).trimStart('0').ifEmpty { "0" }
        val roundUp = frac.length > decimals && frac[decimals] >= '5'
        val rounded = if (roundUp) addOne(keep) else keep
        val padded = rounded.padStart(decimals + 1, '0')
        val intPart = padded.substring(0, padded.length - decimals)
        val text = if (decimals == 0) intPart else intPart + "." + padded.substring(padded.length - decimals)
        return if (negative) "-$text" else text
    }

    /** "%.1f" */
    fun one(x: Double): String = fixed(x, 1)

    /** A whole number with commas between thousands, like String.format(Locale.US, "%,d", n): "23,410". */
    fun grouped(n: Long): String {
        val digits = if (n < 0) (-n).toString() else n.toString()
        val text = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (n < 0) "-$text" else text
    }

    /** The number's digits without an exponent, or null when Kotlin writes it in E notation. */
    private fun plainDigits(x: Double): String? {
        val s = x.toString()
        return if (s.contains('E') || s.contains('e')) null else s
    }

    // Very large or very small numbers: never seen in the app, but still give a sensible answer.
    private fun fallback(x: Double, decimals: Int): String {
        val scale = 10.0.pow(decimals)
        val scaled = (abs(x) * scale).roundToLong()
        val whole = scaled / scale.toLong()
        val frac = (scaled % scale.toLong()).toString().padStart(decimals, '0')
        val text = if (decimals == 0) whole.toString() else "$whole.$frac"
        return if (x < 0) "-$text" else text
    }

    private fun addOne(digits: String): String {
        val chars = digits.toCharArray()
        var i = chars.size - 1
        while (i >= 0) {
            if (chars[i] == '9') {
                chars[i] = '0'
                i--
            } else {
                chars[i] = chars[i] + 1
                return chars.concatToString()
            }
        }
        return "1" + chars.concatToString()
    }
}
