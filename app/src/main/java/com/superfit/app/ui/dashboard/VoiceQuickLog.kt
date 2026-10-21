package com.superfit.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.theme.*
import java.time.LocalDate

// Voice Speech Recognition Helper class
class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChange: (Boolean) -> Unit,
    /**
     * Called when the in-app recognizer can't run on this phone (no speech service, or the
     * phone's own service refuses to connect, seen on some OPPO/OnePlus builds). The screen
     * then opens the phone's standard voice-typing dialog instead.
     */
    private val onNeedsSystemDialog: () -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null

    /** True once the service said it's listening; a client error after that is just a stop, not a broken service. */
    private var startedListening = false

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        startedListening = true
                        onListeningStateChange(true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onListeningStateChange(false)
                    }

                    override fun onError(error: Int) {
                        onListeningStateChange(false)
                        if (error !in USER_ERRORS) {
                            AppAnalytics.recordError(IllegalStateException("Speech recognizer error $error"))
                        }
                        if (error in SERVICE_ERRORS && !startedListening) {
                            // The phone's speech service is broken or missing; don't leave the
                            // user stuck on an error, try the system dialog instead.
                            destroy()
                            onNeedsSystemDialog()
                            return
                        }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                            SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand audio. Try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy."
                            SpeechRecognizer.ERROR_SERVER -> "Server connection error."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
                            SpeechRecognizer.ERROR_CLIENT -> "Voice input stopped. Try again."
                            else -> "Speech error: $error"
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            onResult(text)
                        } else {
                            onError("Could not understand speech.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        return speechRecognizer!!
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onNeedsSystemDialog()
            return
        }

        try {
            speechRecognizer?.cancel()
            startedListening = false
            val recognizer = getOrCreateRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Speed up recognition response: finish 800ms after speech ends instead of waiting 3000ms
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            AppAnalytics.recordError(e)
            destroy()
            onNeedsSystemDialog()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
        onListeningStateChange(false)
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
        onListeningStateChange(false)
    }

    companion object {
        /** Ordinary outcomes (nothing said, not understood), not worth a crash report. */
        private val USER_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        )

        /** The speech service failed before listening: client error, server disconnected (11), language unsupported (12, 13). */
        private val SERVICE_ERRORS = setOf(SpeechRecognizer.ERROR_CLIENT, 11, 12, 13)

        /** The phone's standard voice-typing dialog, the fallback when the in-app recognizer can't run. */
        fun systemDialogIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you ate or did")
        }
    }
}

// Voice Quick Log UI Composable Card
@Composable
fun VoiceQuickLogCard(
    logType: String,
    onLogTypeChange: (String) -> Unit,
    isListening: Boolean,
    isProcessing: Boolean,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the food camera. Null on phones without a camera, which hides the button. */
    onCameraClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // FOOD / ACTIVITY tab: picks which cards show below. The mic handles both either way.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (logType == "MEAL") ThemeTextPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onLogTypeChange("MEAL") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FOOD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (logType == "MEAL") ThemeSecondaryAccent else ThemeTextTertiary,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (logType == "WORKOUT") ThemeTextPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onLogTypeChange("WORKOUT") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ACTIVITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (logType == "WORKOUT") ThemeSecondaryAccent else ThemeTextTertiary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pulse animated scale for listening indicator
                val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
                val scale by if (isListening) {
                    infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Scale"
                    )
                } else {
                    remember { mutableStateOf(1f) }
                }

                val glowBg = if (isListening) {
                    HyperViolet.copy(alpha = 0.3f)
                } else {
                    ThemeTextPrimary.copy(alpha = 0.05f)
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(glowBg)
                        .clickable(enabled = !isProcessing) {
                            if (isListening) onStopClick() else onMicClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ThemeSecondaryAccent,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size((36.dp.value * scale).dp)
                                .clip(CircleShape)
                                .background(if (isListening) NeonMint else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Quick Voice Log Microphone",
                                tint = if (isListening) Color.Black else ThemeTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when {
                            isListening -> "Listening..."
                            isProcessing -> "Working it out…"
                            else -> "Quick log"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            isListening -> NeonMint
                            isProcessing -> ThemeSecondaryAccent
                            else -> ThemeTextPrimary
                        }
                    )
                    Text(
                        text = when {
                            isListening -> "Say what you ate or did..."
                            isProcessing -> "Logging food, exercise or both"
                            logType == "MEAL" && onCameraClick != null -> "Tap the mic and say what you ate or did, or snap a photo"
                            else -> "Tap the mic and say what you ate or did"
                        },
                        fontSize = 11.sp,
                        color = ThemeTextSecondary
                    )
                }

                if (logType == "MEAL" && onCameraClick != null) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(ThemeTextPrimary.copy(alpha = 0.05f))
                            .clickable(enabled = !isListening && !isProcessing, onClick = onCameraClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Log a meal with the camera",
                            tint = ThemeTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
