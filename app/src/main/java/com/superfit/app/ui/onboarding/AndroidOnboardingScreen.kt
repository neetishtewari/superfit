package com.superfit.app.ui.onboarding

import android.speech.tts.TextToSpeech
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.data.userPrefs
import com.superfit.app.ui.launchHealthConnectPermissions
import java.util.Locale

/** The shared sign-up steps with Android's Health Connect sheet and spoken guidance. */
@Composable
fun AndroidOnboardingScreen(
    healthConnectManager: HealthConnectManager,
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Text to speech for "Read aloud".
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) runCatching { engine?.language = Locale.US }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }
    val voiceGuide = remember(tts) {
        tts?.let { engine ->
            object : OnboardingVoiceGuide {
                override fun speak(text: String) = speakUtterance(engine, text)

                override fun stop() {
                    engine.stop()
                }
            }
        }
    }

    // Android's autofill would offer saved passwords in the number boxes.
    val view = LocalView.current
    DisposableEffect(view) {
        val original = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { view.importantForAutofill = original }
    }

    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        pending?.invoke(granted.isNotEmpty())
        pending = null
    }

    OnboardingScreen(
        model = viewModel.model,
        prefs = userPrefs(context),
        onConnectHealth = { done ->
            pending = done
            launchHealthConnectPermissions(context, launcher, healthConnectManager.permissions + healthConnectManager.bodyPermissions)
        },
        onOnboardingComplete = onOnboardingComplete,
        onBack = onBack,
        voiceGuide = voiceGuide
    )
}
