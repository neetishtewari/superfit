package com.superfit.app.domain

/** One answer from the internet: the HTTP status and the text that came back. */
class HttpReply(val status: Int, val body: String)

class OpenFoodFactsException(message: String) : Exception(message)

/** What a barcode scan found. */
sealed interface BarcodeResult {
    data class Found(val food: PackagedFood) : BarcodeResult

    /** The database knows the product (often its [name]) but has no calories for it. */
    data class NoNutrition(val name: String?) : BarcodeResult

    data object NotFound : BarcodeResult

    /** Not a shop barcode at all, such as a stock sticker's code with letters in it. */
    data object NotAProductCode : BarcodeResult

    /** What to tell the person when nothing can be logged from the barcode; null when [Found]. */
    val message: String?
        get() = when (this) {
            is Found -> null
            is NoNutrition ->
                (name?.let { "Found $it, but there's no nutrition info for it yet." } ?: "This product has no nutrition info yet.") +
                    " Snap its nutrition label instead."
            NotFound -> "We couldn't find this product. Snap its nutrition label instead."
            NotAProductCode -> "That isn't the product's barcode. Scan the one with numbers under it, or snap the label."
        }
}

/**
 * Looks up a packet by its barcode in Open Food Facts, the same way on every phone; each app
 * makes the request itself ([get]). Only the barcode number is sent.
 */
object OpenFoodFacts {
    private val BARCODE = Regex("^\\d{8,14}$")

    fun url(barcode: String): String =
        "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments,serving_quantity"

    /** Null when the product isn't known or has no calories; throws when offline or the database fails. */
    suspend fun lookup(barcode: String, get: suspend (url: String) -> HttpReply): PackagedFood? =
        (find(barcode, get) as? BarcodeResult.Found)?.food

    /** What the database says about [barcode]; throws when offline or the database fails. */
    suspend fun find(barcode: String, get: suspend (url: String) -> HttpReply): BarcodeResult {
        if (!isProductCode(barcode)) return BarcodeResult.NotAProductCode
        var known: BarcodeResult.NoNutrition? = null
        for (code in alternateForms(barcode)) {
            val reply = get(url(code))
            when (reply.status) {
                200 -> {
                    PackagedFoodParser.fromOpenFoodFacts(reply.body)?.let { return BarcodeResult.Found(it) }
                    if (PackagedFoodParser.isKnownProduct(reply.body)) {
                        known = known ?: BarcodeResult.NoNutrition(PackagedFoodParser.productName(reply.body))
                    }
                }
                404 -> Unit
                else -> throw OpenFoodFactsException("Open Food Facts returned HTTP ${reply.status}")
            }
        }
        return known ?: BarcodeResult.NotFound
    }

    /** Shop barcodes (EAN, UPC, GTIN) are 8 to 14 digits. */
    fun isProductCode(code: String): Boolean = BARCODE.matches(code)

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
