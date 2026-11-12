package com.superfit.app.data

import com.superfit.app.domain.BarcodeResult
import com.superfit.app.domain.HttpReply
import com.superfit.app.domain.OpenFoodFacts
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks up a packet by its barcode in Open Food Facts. Only the barcode number is sent. */
object OpenFoodFactsClient {

    private const val USER_AGENT = "Superfit Android - https://github.com/neetishtewari/superfit"

    /** What the database says about [barcode]; throws when offline. */
    suspend fun find(barcode: String): BarcodeResult = withContext(Dispatchers.IO) {
        OpenFoodFacts.find(barcode) { url -> fetch(url) }.also {
            if (it !is BarcodeResult.Found) android.util.Log.i("FoodCamera", "Barcode $barcode: $it")
        }
    }

    private fun fetch(url: String): HttpReply {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val status = conn.responseCode
            HttpReply(status, if (status == HttpURLConnection.HTTP_OK) conn.inputStream.bufferedReader().use { it.readText() } else "")
        } finally {
            conn.disconnect()
        }
    }
}
