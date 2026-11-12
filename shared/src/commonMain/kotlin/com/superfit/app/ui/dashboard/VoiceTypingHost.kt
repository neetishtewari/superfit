package com.superfit.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.SpokenInput
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.LocalVoiceTyping

/**
 * Voice typing for one short answer (the right name of a food, a nutritionist's advice) where the
 * phone has no voice-typing box of its own, as on iPhone: a small "Listening…" window with the
 * words as they're heard. Android keeps its own Google voice-typing box.
 */
@Composable
fun ProvideVoiceTyping(recognizer: VoiceRecognizer?, onOpenPhoneSettings: () -> Unit, content: @Composable () -> Unit) {
    if (recognizer == null || !recognizer.isAvailable()) {
        content()
        return
    }
    var prompt by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var micOff by remember { mutableStateOf(false) }
    var deliver by remember { mutableStateOf<(String) -> Unit>({}) }

    fun close() {
        prompt = null
        live = ""
        checking = false
    }

    val typing: @Composable (String, (String) -> Unit) -> (() -> Unit)? = { askFor, onText ->
        val latest by rememberUpdatedState(onText)
        remember(askFor) {
            {
                deliver = { latest(it) }
                prompt = askFor
                recognizer.start(
                    onLiveText = { live = it },
                    onChecking = { checking = true },
                    onHeard = { stretches ->
                        close()
                        SpokenInput.fromStretches(stretches)?.let { deliver(it.text) }
                    },
                    onError = { error ->
                        close()
                        if (error == VoiceRecognizer.MIC_OFF) micOff = true
                    }
                )
            }
        }
    }

    CompositionLocalProvider(LocalVoiceTyping provides typing) { content() }

    prompt?.let { askFor ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (checking) "Checking what you said…" else "Listening…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(askFor, style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textSecondary)
                    if (live.isNotBlank()) {
                        Text("“$live”", style = MaterialTheme.typography.bodyLarge, color = SuperfitTheme.colors.textPrimary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { recognizer.stop() }, enabled = !checking) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = {
                    recognizer.cancel()
                    close()
                }) { Text("Cancel") }
            }
        )
    }

    if (micOff) {
        MicOffDialog(
            onOpenSettings = {
                micOff = false
                onOpenPhoneSettings()
            },
            onDismiss = { micOff = false }
        )
    }
}
