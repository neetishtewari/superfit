package com.superfit.app.ui.dashboard

import com.superfit.app.domain.LogKind
import com.superfit.app.domain.SpokenInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's speech recognition, as each app provides it (Apple's Speech framework on iPhone).
 * It listens through pauses, sends the words heard so far, and finishes by itself a moment after
 * the last word or after a minute. Every callback comes on the main thread; after [start] exactly
 * one of onHeard or onError comes, unless [cancel] is called first.
 */
interface VoiceRecognizer {
    /** False when this phone can't recognise speech at all, which hides the mic buttons. */
    fun isAvailable(): Boolean

    /**
     * Asks for the microphone and speech recognition the first time, then listens. [onHeard] gets
     * each stretch of speech as its guesses, best first. [onError] gets [MIC_OFF] when either was
     * refused, [NOTHING_HEARD] when nothing was said, or a description of what went wrong.
     */
    fun start(onLiveText: (String) -> Unit, onChecking: () -> Unit, onHeard: (List<List<String>>) -> Unit, onError: (String) -> Unit)

    /** Stops listening and keeps what was said (a short wait for the final words). */
    fun stop()

    /** Stops listening and drops what was said. */
    fun cancel()

    companion object {
        const val MIC_OFF = "mic_off"
        const val NOTHING_HEARD = "nothing_heard"
    }
}

/** What Today's mic buttons show while the phone listens. */
data class VoiceUi(
    val listening: Boolean = false,
    /** Stopped listening; waiting a moment for the corrected words. */
    val checking: Boolean = false,
    val liveText: String = "",
    /** Which mic is listening: Food's logs meals, Activity's workouts. */
    val kind: LogKind = LogKind.ANY
)

/**
 * Today's voice logging, the same on every phone: one mic listens at a time, and what was heard
 * (with the phone's other guesses, so the AI can pick the one that makes sense as food) is logged
 * as a meal or a workout depending on which mic was tapped.
 */
class VoiceLog(private val recognizer: VoiceRecognizer, private val onHeard: (SpokenInput, LogKind) -> Unit) {
    private val _ui = MutableStateFlow(VoiceUi())
    val ui: StateFlow<VoiceUi> = _ui.asStateFlow()

    /** A short line for the banner when listening went wrong, until [dismissNotice]. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** The microphone or speech recognition is off for Superfit: explain and offer Settings. */
    private val _micOff = MutableStateFlow(false)
    val micOff: StateFlow<Boolean> = _micOff.asStateFlow()

    val available: Boolean get() = recognizer.isAvailable()

    // Each start gets its own number, so a late answer from an earlier session is ignored.
    private var session = 0

    fun start(kind: LogKind) {
        if (_ui.value.listening) return
        val id = ++session
        _notice.value = null
        _ui.value = VoiceUi(listening = true, kind = kind)
        recognizer.start(
            onLiveText = { text -> if (id == session) _ui.value = _ui.value.copy(liveText = text) },
            onChecking = { if (id == session) _ui.value = _ui.value.copy(checking = true) },
            onHeard = { stretches ->
                if (id == session) {
                    _ui.value = VoiceUi()
                    val heard = SpokenInput.fromStretches(stretches)
                    if (heard == null) _notice.value = NOTHING_HEARD_LINE else onHeard(heard, kind)
                }
            },
            onError = { error ->
                if (id == session) {
                    _ui.value = VoiceUi()
                    when (error) {
                        VoiceRecognizer.MIC_OFF -> _micOff.value = true
                        VoiceRecognizer.NOTHING_HEARD -> _notice.value = NOTHING_HEARD_LINE
                        else -> _notice.value = "Couldn't hear that. Try again, or type it instead."
                    }
                }
            }
        )
    }

    fun stop() {
        if (_ui.value.listening) recognizer.stop()
    }

    /** Leaving Today or signing out: stop without logging anything. */
    fun cancel() {
        if (!_ui.value.listening) return
        session++
        _ui.value = VoiceUi()
        recognizer.cancel()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun dismissMicOff() {
        _micOff.value = false
    }

    companion object {
        const val NOTHING_HEARD_LINE = "Didn't catch anything. Tap the mic and say what you ate or did."
    }
}
