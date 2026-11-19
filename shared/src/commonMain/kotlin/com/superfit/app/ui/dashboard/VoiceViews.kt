package com.superfit.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.SuperfitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One line at the top of Today: what was logged, or why it couldn't be. */
data class LogBanner(val message: String, val isError: Boolean)

/** What Today watches when this phone has no speech recognition: nothing ever changes. */
class VoiceIdle {
    val ui: StateFlow<VoiceUi> = MutableStateFlow(VoiceUi())
    val notice: StateFlow<String?> = MutableStateFlow(null)
    val micOff: StateFlow<Boolean> = MutableStateFlow(false)
}

/** Android's floating result banner: green dot when logged, amber when not (red is only for delete). */
@Composable
fun LogResultBanner(banner: LogBanner?, modifier: Modifier = Modifier) {
    // Keeps the last words on screen while the banner slides away.
    var shown by remember { mutableStateOf(banner) }
    if (banner != null) shown = banner
    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .systemBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            .zIndex(99f)
    ) {
        val colors = SuperfitTheme.colors
        val accent = if (shown?.isError == true) SolarAmber else NeonGreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, accent, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                text = shown?.message.orEmpty(),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The microphone or speech recognition was refused: say why it's needed, and where to turn it on. */
@Composable
fun MicOffDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Microphone is off for Superfit") },
        text = {
            Text(
                "Superfit needs the microphone and speech recognition to hear what you ate or did. Nothing is recorded or kept. " +
                    "You can turn them on in Settings, or type your meal instead."
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
