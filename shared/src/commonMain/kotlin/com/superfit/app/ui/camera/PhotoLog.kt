package com.superfit.app.ui.camera

import com.superfit.app.domain.AiPhoto
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.FoodPhotoParser
import com.superfit.app.domain.PackagedFood
import com.superfit.app.domain.ParsedNutritionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the iPhone's camera is used for: the meal itself, a packet's barcode, or its nutrition label. */
enum class PhotoKind { MEAL, BARCODE, LABEL }

sealed interface PhotoLogState {
    data object Idle : PhotoLogState
    data class Working(val message: String) : PhotoLogState
    data class MealResult(val result: ParsedNutritionResult) : PhotoLogState
    data class PackResult(val food: PackagedFood) : PhotoLogState

    /** [retake] opens the camera again, in the kind that fits (the label when a barcode wasn't found). */
    data class Failed(val message: String, val retake: PhotoKind) : PhotoLogState
}

/**
 * Logging food with the camera where the phone's own camera screens take the picture or read the
 * barcode (iPhone), with the same wording and AI as Android's food camera: a meal photo or label
 * goes to the AI, a barcode to Open Food Facts, and the person checks the result before anything
 * is saved. The photo itself is never stored.
 */
class PhotoLog(
    private val scope: CoroutineScope,
    private val lookup: suspend (barcode: String) -> PackagedFood?,
    private val parser: FoodPhotoParser = FoodPhotoParser()
) {
    private val _state = MutableStateFlow<PhotoLogState>(PhotoLogState.Idle)
    val state: StateFlow<PhotoLogState> = _state.asStateFlow()

    /** How the last result was made, for saving it (packets get their quality tags afterwards). */
    var kind: PhotoKind = PhotoKind.MEAL
        private set

    private var job: Job? = null

    fun onPhoto(kind: PhotoKind, photo: AiPhoto) {
        if (_state.value is PhotoLogState.Working) return
        this.kind = kind
        _state.value = PhotoLogState.Working(if (kind == PhotoKind.LABEL) "Reading the label…" else "Reading your meal…")
        job = scope.launch {
            _state.value = try {
                if (kind == PhotoKind.LABEL) {
                    parser.parseLabel(photo)?.let { PhotoLogState.PackResult(it) }
                        ?: PhotoLogState.Failed("We couldn't read this label. Try a straight, close-up photo.", PhotoKind.LABEL)
                } else {
                    val result = parser.parseMeal(photo)
                    if (result.foodText == "invalid") {
                        PhotoLogState.Failed("We couldn't see any food. Try again with more light.", PhotoKind.MEAL)
                    } else {
                        PhotoLogState.MealResult(result)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("PhotoLog", "Reading photo failed", e)
                PhotoLogState.Failed(com.superfit.app.domain.Ai.friendlyError(e), kind)
            }
        }
    }

    fun onBarcode(code: String) {
        if (_state.value is PhotoLogState.Working) return
        kind = PhotoKind.BARCODE
        _state.value = PhotoLogState.Working("Looking up product…")
        job = scope.launch {
            _state.value = try {
                lookup(code)?.let { PhotoLogState.PackResult(it) }
                    ?: PhotoLogState.Failed("We couldn't find this product. Snap its nutrition label instead.", PhotoKind.LABEL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("PhotoLog", "Barcode lookup failed", e)
                PhotoLogState.Failed("Couldn't reach the product database. Check your connection and try again.", PhotoKind.BARCODE)
            }
        }
    }

    /** Closes the result or the message without saving anything. */
    fun close() {
        job?.cancel()
        job = null
        _state.value = PhotoLogState.Idle
    }
}

/**
 * The phone's own camera screens, as each app provides them (iPhone: Apple's camera and barcode
 * scanner). [take] answers once: a photo (already shrunk) for a meal or label, a barcode's number,
 * or nothing when the person cancelled; [onError] says why it couldn't open.
 */
interface PhotoCamera {
    fun isAvailable(): Boolean

    fun take(kind: PhotoKind, onPhoto: (AiPhoto) -> Unit, onBarcode: (String) -> Unit, onError: (String) -> Unit)
}
