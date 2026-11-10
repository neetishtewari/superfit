package com.superfit.app.ui.dashboard

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android's voice typing box for one short answer (shared screens get it through
 * LocalVoiceTyping). Shows a short note if the phone has no voice typing.
 */
@Composable
fun androidVoiceTyping(prompt: String, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { latest(it) }
    }
    return {
        try {
            launcher.launch(SpeechRecognizerHelper.systemDialogIntent(context).putExtra(RecognizerIntent.EXTRA_PROMPT, prompt))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice typing isn't available. Please type it.", Toast.LENGTH_SHORT).show()
        }
    }
}
