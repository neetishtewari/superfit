package com.superfit.app.ui.components

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.superfit.app.ui.dashboard.SpeechRecognizerHelper

// Android's voice typing box. The text box, mic and card styles live in shared code.

/**
 * Opens the phone's voice typing box and hands back what was said.
 * Returns the function to call when the mic is tapped.
 */
@Composable
fun rememberVoiceTyping(prompt: String, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onText)
    }
    return {
        try {
            launcher.launch(SpeechRecognizerHelper.systemDialogIntent(context).putExtra(RecognizerIntent.EXTRA_PROMPT, prompt))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice typing isn't available. Please type it.", Toast.LENGTH_SHORT).show()
        }
    }
}
