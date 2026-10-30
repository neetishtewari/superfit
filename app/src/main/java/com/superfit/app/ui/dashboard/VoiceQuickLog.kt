package com.superfit.app.ui.dashboard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.SpeechLanguages
import com.superfit.app.domain.SpokenInput
import com.superfit.app.theme.*
import java.time.LocalDate

// Voice Speech Recognition Helper class
/**
 * One voice-logging session: listens until the person taps Stop, keeps going through natural
 * pauses ("two rotis... and dal"), and finishes by itself after a longer silence or a minute.
 * Android's recognizer ends each stretch of speech on its own, so we restart it and join the
 * pieces. Prefers Google's speech service, then the phone's default, then the phone's standard
 * voice-typing dialog. Listens in the phone's own languages, so it works for any language.
 *
 * When the person stops talking, the speech service takes a second look at the whole sentence and
 * corrects its quick live guesses ("jaipuri chane" becomes "cheh poori aur chane"). The session
 * waits briefly for that corrected answer instead of logging the live guess.
 */
class SpeechRecognizerHelper(
    private val context: Context,
    /** What was heard: the best guess plus the phone's other versions, for the AI to choose from. */
    private val onResult: (SpokenInput) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChange: (Boolean) -> Unit,
    /**
     * Called when no in-app recognizer can run on this phone (no speech service, or every
     * service refuses to connect). The screen then opens the phone's voice-typing dialog.
     */
    private val onNeedsSystemDialog: () -> Unit = {},
    /** What's been heard so far in this session, for showing on screen while listening. */
    private val onLiveText: (String) -> Unit = {},
    /** True while waiting for the speech service's corrected answer after the person stopped talking. */
    private val onChecking: (Boolean) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    /** Try Google's service first; after it fails, the phone's default. */
    private var useGoogleService = true

    /** Ask for the phone's languages; dropped if the service says it can't do them. */
    private var useLanguageHint = true

    /** A session is running: the mic button shows Stop. */
    private var active = false

    /** The person tapped Stop; finish with whatever comes back. */
    private var stopRequested = false

    /** True once the service said it's listening; a client error after that is just a stop, not a broken service. */
    private var startedListening = false

    /** Each finished stretch of speech, as the service's guesses for it, best first. */
    private val heardSegments = mutableListOf<List<String>>()
    private var partial: String? = null
    private var sessionStartedAt = 0L
    private var lastSpeechAt = 0L

    private fun fullText(): String = (heardSegments.map { it.first() } + listOfNotNull(partial)).joinToString(" ").trim()

    /**
     * Finishes [FINISH_SILENCE_MS] after the last word. Without this the session waited for the
     * speech service's own "no speech" timeout after each pause, which took 5 to 10 seconds.
     */
    private val silenceCheck = object : Runnable {
        override fun run() {
            if (!active || stopRequested || fullText().isBlank()) return
            val quietFor = SystemClock.elapsedRealtime() - lastSpeechAt
            if (quietFor >= FINISH_SILENCE_MS) requestFinish() else handler.postDelayed(this, FINISH_SILENCE_MS - quietFor)
        }
    }

    private fun heardSpeech() {
        lastSpeechAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(silenceCheck)
        handler.postDelayed(silenceCheck, FINISH_SILENCE_MS)
    }

    private fun createRecognizer(): SpeechRecognizer {
        val app = context.applicationContext
        val google = if (useGoogleService) googleRecognitionService(app) else null
        AppAnalytics.setSpeechService(google?.packageName ?: "default")
        val recognizer = if (google != null) {
            SpeechRecognizer.createSpeechRecognizer(app, google)
        } else {
            SpeechRecognizer.createSpeechRecognizer(app)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                startedListening = true
            }

            override fun onBeginningOfSpeech() {
                heardSpeech()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!active) return
                if (error in LANGUAGE_ERRORS && useLanguageHint && !startedListening && heardSegments.isEmpty() && partial == null) {
                    // This service can't listen in the phone's language; let it pick its own.
                    useLanguageHint = false
                    releaseRecognizer()
                    listenOnce()
                    return
                }
                if (error in SERVICE_ERRORS && !startedListening && heardSegments.isEmpty() && partial == null) {
                    AppAnalytics.recordError(IllegalStateException("Speech service error $error (google=$useGoogleService)"))
                    releaseRecognizer()
                    if (useGoogleService && googleRecognitionService(context.applicationContext) != null) {
                        // Google's service failed on this phone; try the phone's own.
                        useGoogleService = false
                        listenOnce()
                    } else {
                        endSession()
                        onNeedsSystemDialog()
                    }
                    return
                }
                partial?.let { heardSegments += listOf(it) }
                partial = null
                if (error in SILENCE_ERRORS && !stopRequested && heardSegments.isEmpty() && withinLimits()) {
                    // Nothing said yet: keep waiting rather than giving up after a short silence.
                    handler.post { if (active) listenOnce() }
                    return
                }
                if (error !in SILENCE_ERRORS && error != SpeechRecognizer.ERROR_CLIENT) {
                    AppAnalytics.recordError(IllegalStateException("Speech recognizer error $error"))
                }
                if (heardSegments.isNotEmpty()) {
                    finishWithText()
                    return
                }
                endSession()
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand audio. Try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy."
                    SpeechRecognizer.ERROR_SERVER -> "Server connection error."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
                    SpeechRecognizer.ERROR_CLIENT -> if (stopRequested) return else "Voice input stopped. Try again."
                    else -> "Speech error: $error"
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                if (!active) return
                val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(partial)
                // Services that send live words already timed the silence from the last one.
                val hadLiveWords = partial != null
                partial = null
                if (guesses.isNotEmpty()) {
                    heardSegments += guesses
                    if (!hadLiveWords) heardSpeech()
                    onLiveText(fullText())
                }
                if (!stopRequested && withinLimits()) {
                    // A pause ended this stretch; keep listening for the rest of the sentence.
                    // Restarting on the next tick avoids "recognizer busy" on some services.
                    handler.post { if (active) listenOnce() }
                } else if (heardSegments.isNotEmpty()) {
                    finishWithText()
                } else {
                    endSession()
                    onError("Could not understand speech.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        partial = it
                        heardSpeech()
                        onLiveText(fullText())
                    }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        return recognizer
    }

    /** Under a minute in total, and spoken to within the last few seconds. */
    private fun withinLimits(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return now - sessionStartedAt < MAX_SESSION_MS && now - lastSpeechAt < FINISH_SILENCE_MS
    }

    private fun listenOnce() {
        try {
            startedListening = false
            val recognizer = speechRecognizer ?: createRecognizer().also { speechRecognizer = it }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Several guesses, so the AI can pick the one that makes sense as food or exercise.
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, SpokenInput.MAX_VERSIONS)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (useLanguageHint) addLanguages(this, context)
                // Allow natural pauses before a stretch ends. Many services ignore these, which is
                // why the session restarts listening after each stretch anyway.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, FINISH_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            AppAnalytics.recordError(e)
            releaseRecognizer()
            endSession()
            onNeedsSystemDialog()
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onNeedsSystemDialog()
            return
        }
        releaseRecognizer()
        heardSegments.clear()
        partial = null
        stopRequested = false
        sessionStartedAt = SystemClock.elapsedRealtime()
        lastSpeechAt = sessionStartedAt
        active = true
        onListeningStateChange(true)
        onLiveText("")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (active) stopListening() }, MAX_SESSION_MS)
        listenOnce()
    }

    /** The person tapped Stop: log what was heard so far. */
    fun stopListening() {
        if (!active) {
            onListeningStateChange(false)
            return
        }
        requestFinish()
    }

    /**
     * Stopped talking or tapped Stop. Live words are quick guesses; ask the service for its
     * corrected answer and log that, waiting at most [STOP_GRACE_MS] for it.
     */
    private fun requestFinish() {
        if (stopRequested) return
        stopRequested = true
        if (partial == null && heardSegments.isNotEmpty()) {
            // Everything heard is already corrected; nothing to wait for.
            finishWithText()
            return
        }
        if (partial != null) onChecking(true)
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
        // Some services never answer a stop; don't leave the mic spinning.
        handler.postDelayed({ if (active) finishWithText() }, STOP_GRACE_MS)
    }

    private fun finishWithText() {
        partial?.let { heardSegments += listOf(it) }
        partial = null
        val heard = SpokenInput.fromStretches(heardSegments)
        endSession()
        if (heard != null) onResult(heard) else onError("No speech input detected.")
    }

    private fun endSession() {
        active = false
        onChecking(false)
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        onListeningStateChange(false)
    }

    private fun releaseRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
    }

    fun destroy() {
        active = false
        onChecking(false)
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        onListeningStateChange(false)
    }

    companion object {
        /** Finish by itself after this long without hearing anything new. */
        private const val FINISH_SILENCE_MS = 2_000L

        /** Safety net for a session left running. */
        private const val MAX_SESSION_MS = 60_000L

        /** How long to wait for final words after Stop before logging what we have. */
        private const val STOP_GRACE_MS = 1_500L

        /** Ordinary silence outcomes, not worth a crash report. */
        private val SILENCE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        )

        /** The speech service failed before listening: client error, server disconnected (11), language unsupported (12, 13). */
        private val SERVICE_ERRORS = setOf(SpeechRecognizer.ERROR_CLIENT, 11, 12, 13)

        /** Language not supported (12) or not available right now (13). */
        private val LANGUAGE_ERRORS = setOf(12, 13)

        /** The languages to listen in: the phone's own, with the region from the SIM when that helps. */
        private fun speechLanguages(context: Context): SpeechLanguages.Choice? = try {
            val locales = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it] } }
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val country = telephony?.simCountryIso?.takeIf { it.isNotBlank() } ?: telephony?.networkCountryIso
            SpeechLanguages.choose(locales, country)
        } catch (e: Exception) {
            null
        }

        /**
         * Listen in the phone's main language, and on Android 14+ also detect and switch to the
         * phone's other languages mid-sentence. Services that don't support this ignore it.
         */
        private fun addLanguages(intent: Intent, context: Context) {
            val choice = speechLanguages(context) ?: return
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, choice.primary)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, choice.primary)
            if (choice.others.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val all = arrayListOf(choice.primary) + choice.others
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, ArrayList(all))
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, ArrayList(all))
            }
        }

        /**
         * Google's speech service when it's installed. Some phones (OnePlus, OPPO, Xiaomi) default
         * to their own service, which is less accurate for food names and ignores pause settings.
         */
        private val GOOGLE_SPEECH_PACKAGES = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.tts"
        )

        private fun googleRecognitionService(context: Context): ComponentName? = try {
            val services = context.packageManager
                .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .mapNotNull { it.serviceInfo }
            GOOGLE_SPEECH_PACKAGES.firstNotNullOfOrNull { pkg ->
                services.firstOrNull { it.packageName == pkg }?.let { ComponentName(it.packageName, it.name) }
            }
        } catch (e: Exception) {
            null
        }

        /** The phone's standard voice-typing dialog, the fallback when the in-app recognizer can't run. */
        fun systemDialogIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, SpokenInput.MAX_VERSIONS)
            addLanguages(this, context)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you ate or did")
        }
    }
}

// Home's log buttons: "Snap your plate" and "Say what you ate" side by side on Food,
// one wide "Say your workout" on Activity. Each mic logs only its own kind, which is faster.
@Composable
fun VoiceQuickLogCard(
    logType: String,
    onLogTypeChange: (String) -> Unit,
    isListening: Boolean,
    isProcessing: Boolean,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Words heard so far while listening. */
    liveText: String = "",
    /** Stopped listening; waiting a moment for the speech service's corrected words. */
    isChecking: Boolean = false,
    /** Opens the food camera. Null on phones without a camera, which hides the button. */
    onCameraClick: (() -> Unit)? = null
) {
    val showCamera = logType == "MEAL" && onCameraClick != null
    val micAction = { if (isListening) onStopClick() else onMicClick() }
    val micTitle = when {
        isChecking -> "Checking what you said…"
        isListening -> "Listening…"
        isProcessing -> "Working it out…"
        logType == "MEAL" -> "Say what you ate"
        else -> "Say your workout"
    }
    val micHint = when {
        isChecking -> "One moment"
        isListening -> "Tap when you're done"
        isProcessing && logType == "MEAL" -> "Logging your meal"
        isProcessing -> "Logging your workout"
        logType == "MEAL" && showCamera -> "Best after a meal"
        logType == "MEAL" -> "e.g. \u201C2 roti and dal\u201D"
        else -> "e.g. \u201C20 push-ups and 50 dumbbell curls\u201D"
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showCamera) {
                LogButton(
                    title = "Snap your plate",
                    hint = "Best while you eat",
                    icon = Icons.Default.PhotoCamera,
                    iconDescription = "Log a meal with the camera",
                    enabled = !isListening && !isProcessing,
                    onClick = onCameraClick!!,
                    vertical = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            LogButton(
                title = micTitle,
                hint = micHint,
                icon = Icons.Default.Mic,
                iconDescription = if (isListening) "Stop listening" else "Log by voice",
                enabled = !isProcessing,
                onClick = micAction,
                vertical = showCamera,
                listening = isListening,
                processing = isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        AnimatedVisibility(visible = isListening && liveText.isNotBlank()) {
            Text(
                text = "\u201C$liveText\u201D",
                style = MaterialTheme.typography.bodyMedium,
                color = ThemeTextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Food / Activity picks which cards show below.
        SegmentedPills(
            options = listOf("MEAL" to "Food", "WORKOUT" to "Activity"),
            selected = logType,
            onSelect = onLogTypeChange
        )
    }
}

@Composable
private fun LogButton(
    title: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    processing: Boolean = false
) {
    val pulse = rememberInfiniteTransition(label = "MicPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "Scale"
    )
    val iconCircle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size((44 * scale).dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (processing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = BrandGreenDeep, strokeWidth = 2.5.dp)
            } else {
                Icon(icon, contentDescription = iconDescription, tint = BrandGreenDeep, modifier = Modifier.size(24.dp))
            }
        }
    }
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (vertical) {
                // Half-width buttons: shrink the title a little rather than leave one word alone on a second line.
                val titleStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                BasicText(
                    text = title,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = titleStyle.fontSize)
                )
            } else {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
    }
    val shape = RoundedCornerShape(20.dp)
    val content = Modifier
        .clip(shape)
        .background(if (listening) BrandGreenDeep else NeonMint)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(14.dp)
    if (vertical) {
        Column(modifier = modifier.then(content), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            iconCircle()
            texts()
        }
    } else {
        Row(
            modifier = modifier.then(content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            iconCircle()
            texts()
        }
    }
}

/** Pill-shaped switch used for Food / Activity and the Progress tabs. */
@Composable
fun SegmentedPills(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(ThemeFieldBg)
            .padding(4.dp)
    ) {
        options.forEach { (key, label) ->
            val isOn = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    // Solid brand green for the chosen option: a card-coloured pill was hard to see in dark mode.
                    .background(if (isOn) NeonMint else Color.Transparent)
                    .clickable { onSelect(key) }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOn) Color.White else ThemeTextSecondary,
                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}
