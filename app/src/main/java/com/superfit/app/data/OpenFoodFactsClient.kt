package com.superfit.app.data

import com.superfit.app.domain.PackagedFood
import com.superfit.app.domain.PackagedFoodParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks up a packet by its barcode in Open Food Facts. Only the barcode number is sent. */
object OpenFoodFactsClient {

    private const val USER_AGENT = "Superfit Android - https://github.com/neetishtewari/superfit"
    private val BARCODE = Regex("^\\d{8,14}$")

    /** Null when the product isn't known or has no calories; throws when offline. */
    suspend fun lookup(barcode: String): PackagedFood? = withContext(Dispatchers.IO) {
        if (!BARCODE.matches(barcode)) return@withContext null
        // A 12-digit UPC-A code is the same product as its 13-digit EAN form with a leading 0,
        // and the database may only have one of the two.
        alternateForms(barcode).firstNotNullOfOrNull { fetch(it) }
    }

    internal fun alternateForms(barcode: String): List<String> = when {
        barcode.length == 12 -> listOf(barcode, "0$barcode")
        barcode.length == 13 && barcode.startsWith("0") -> listOf(barcode, barcode.drop(1))
        else -> listOf(barcode)
    }

    private fun fetch(barcode: String): PackagedFood? {
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments,serving_quantity")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> PackagedFoodParser.fromOpenFoodFacts(conn.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null.also { android.util.Log.i("FoodCamera", "Barcode $barcode not in Open Food Facts") }
                else -> throw IOException("Open Food Facts returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
