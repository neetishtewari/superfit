package com.superfit.app.domain

/** One answer from the internet: the HTTP status and the text that came back. */
class HttpReply(val status: Int, val body: String)

class OpenFoodFactsException(message: String) : Exception(message)

/**
 * Looks up a packet by its barcode in Open Food Facts, the same way on every phone; each app
 * makes the request itself ([get]). Only the barcode number is sent.
 */
object OpenFoodFacts {
    private val BARCODE = Regex("^\\d{8,14}$")

    fun url(barcode: String): String =
        "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments,serving_quantity"

    /** Null when the product isn't known or has no calories; throws when offline or the database fails. */
    suspend fun lookup(barcode: String, get: suspend (url: String) -> HttpReply): PackagedFood? {
        if (!BARCODE.matches(barcode)) return null
        for (code in alternateForms(barcode)) {
            val reply = get(url(code))
            when (reply.status) {
                200 -> PackagedFoodParser.fromOpenFoodFacts(reply.body)?.let { return it }
                404 -> Unit
                else -> throw OpenFoodFactsException("Open Food Facts returned HTTP ${reply.status}")
            }
        }
        return null
    }

    /**
     * A 12-digit UPC-A code is the same product as its 13-digit EAN form with a leading 0, and
     * the database may only have one of the two.
     */
    fun alternateForms(barcode: String): List<String> = when {
        barcode.length == 12 -> listOf(barcode, "0$barcode")
        barcode.length == 13 && barcode.startsWith("0") -> listOf(barcode, barcode.drop(1))
        else -> listOf(barcode)
    }
}
