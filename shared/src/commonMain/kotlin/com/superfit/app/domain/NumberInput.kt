package com.superfit.app.domain

/** Keeps number boxes tidy: digits only, and at most two decimal places where decimals are allowed. */
object NumberInput {
    const val MAX_DECIMALS = 2

    /**
     * What the box should hold after the person typed [typed], or null to keep the old value.
     * A comma becomes a dot so "63,5" works on keyboards that type commas.
     */
    fun clean(typed: String, allowDecimals: Boolean): String? {
        val text = typed.trim().replace(',', '.')
        val pattern = if (allowDecimals) Regex("""\d*(\.\d{0,$MAX_DECIMALS})?""") else Regex("""\d*""")
        return text.takeIf { pattern.matches(it) }
    }
}
