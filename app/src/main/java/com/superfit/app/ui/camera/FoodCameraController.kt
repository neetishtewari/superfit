package com.superfit.app.ui.camera

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.superfit.app.data.OpenFoodFactsClient
import com.superfit.app.domain.FoodPhotoParser
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.PackagedFood
import com.superfit.app.domain.ParsedNutritionResult
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class CameraMode(val label: String) { MEAL("Meal"), BARCODE("Barcode"), LABEL("Label") }

sealed interface CameraState {
    data object Viewfinder : CameraState
    data class Working(val message: String) : CameraState
    data class MealResult(val result: ParsedNutritionResult) : CameraState
    data class PackResult(val food: PackagedFood) : CameraState
    data class Failed(val message: String) : CameraState
}

/**
 * What the food camera is doing: which mode it is in, and what happened to the last photo or
 * barcode. A ViewModel so turning the phone keeps the photo and its result; [reset] is called
 * each time the camera opens, so every opening starts fresh.
 */
class FoodCameraController : ViewModel() {

    /** Blank means Superfit's built-in AI. Set by the screen each time it opens. */
    var apiKey: String = ""

    var mode by mutableStateOf(CameraMode.MEAL)
        private set
    var state by mutableStateOf<CameraState>(CameraState.Viewfinder)
        private set
    /** The photo being read, shown frozen behind the progress and the confirm sheet. Never saved. */
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    /** A hint over the viewfinder, such as "snap the label instead". */
    var banner by mutableStateOf<String?>(null)
        private set

    // The scanner reports the same barcode many times a second; only the first one counts.
    private var scanLocked = false
    // After Retake the same packet is usually still in view. Ignore its code until the camera has
    // seen a few frames without it, or a few seconds have passed, so it isn't looked up again at once.
    private var ignoredCode: String? = null
    private var ignoreUntil = 0L
    private var lastCode: String? = null
    private var emptyFrames = 0
    private var job: Job? = null

    fun reset() {
        retake()
        mode = CameraMode.MEAL
        banner = null
        ignoredCode = null
        lastCode = null
    }

    fun changeMode(newMode: CameraMode) {
        if (state != CameraState.Viewfinder) return
        mode = newMode
        banner = null
    }

    fun onPhoto(bitmap: Bitmap) {
        if (state != CameraState.Viewfinder) return
        val photoMode = mode
        photo = bitmap
        banner = null
        state = CameraState.Working(if (photoMode == CameraMode.LABEL) "Reading the label…" else "Reading your meal…")
        job = viewModelScope.launch {
            state = try {
                val parser = FoodPhotoParser(apiKey)
                if (photoMode == CameraMode.LABEL) {
                    parser.parseLabel(bitmap)?.let { CameraState.PackResult(it) }
                        ?: CameraState.Failed("We couldn't read this label. Try a straight, close-up photo.")
                } else {
                    val result = parser.parseMeal(bitmap)
                    if (result.foodText == "invalid") CameraState.Failed("We couldn't see any food. Try again with more light.")
                    else CameraState.MealResult(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FoodCamera", "Reading photo failed", e)
                CameraState.Failed(GeminiClient.sanitizeError(e))
            }
        }
    }

    /** A scanned frame with no barcode in it. A few in a row mean the old packet is out of view. */
    fun onNoBarcode() {
        emptyFrames++
        if (emptyFrames >= 5) ignoredCode = null
    }

    /** Returns true when this barcode is the one being looked up, so the screen can vibrate once. */
    fun onBarcode(code: String, now: Long = System.currentTimeMillis()): Boolean {
        if (scanLocked || mode != CameraMode.BARCODE || state != CameraState.Viewfinder) return false
        emptyFrames = 0
        if (code == ignoredCode && now < ignoreUntil) return false
        scanLocked = true
        lastCode = code
        banner = null
        state = CameraState.Working("Looking up product…")
        job = viewModelScope.launch {
            try {
                val food = OpenFoodFactsClient.lookup(code)
                if (food != null) {
                    state = CameraState.PackResult(food)
                } else {
                    mode = CameraMode.LABEL
                    banner = "We couldn't find this product. Snap its nutrition label instead."
                    scanLocked = false
                    state = CameraState.Viewfinder
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FoodCamera", "Barcode lookup failed", e)
                state = CameraState.Failed("Couldn't reach the product database. Check your connection and try again.")
            }
        }
        return true
    }

    /** Back to the viewfinder in the same mode. */
    fun retake(now: Long = System.currentTimeMillis()) {
        job?.cancel()
        job = null
        photo = null
        scanLocked = false
        ignoredCode = lastCode
        ignoreUntil = now + 3_000L
        state = CameraState.Viewfinder
    }
}
