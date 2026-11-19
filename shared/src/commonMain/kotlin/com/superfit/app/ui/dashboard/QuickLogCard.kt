package com.superfit.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.BrandGreenDeep
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.ThemeTextSecondary

// Home's log buttons: "Snap your plate" and "Say what you ate" side by side on Food,
// one wide "Say your workout" on Activity. Each mic logs only its own kind, which is faster.
@Composable
fun VoiceQuickLogCard(
    logType: String,
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
    // Soft green tile with a solid green icon circle; turns solid deep green while listening.
    val colors = com.superfit.app.theme.SuperfitTheme.colors
    val titleColor = if (listening || colors.isDark) Color.White else colors.textPrimary
    val hintColor = if (listening || colors.isDark) Color.White.copy(alpha = 0.9f) else colors.textSecondary
    val iconCircle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size((44 * scale).dp)
                .clip(CircleShape)
                .background(if (listening) Color.White else NeonMint),
            contentAlignment = Alignment.Center
        ) {
            if (processing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = if (listening) BrandGreenDeep else Color.White, strokeWidth = 2.5.dp)
            } else {
                Icon(icon, contentDescription = iconDescription, tint = if (listening) BrandGreenDeep else Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (vertical) {
                // Half-width buttons: shrink the title a little rather than leave one word alone on a second line.
                val titleStyle = MaterialTheme.typography.titleMedium.copy(color = titleColor)
                BasicText(
                    text = title,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = titleStyle.fontSize)
                )
            } else {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            }
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = hintColor)
        }
    }
    val shape = RoundedCornerShape(20.dp)
    val content = Modifier
        .clip(shape)
        .background(if (listening) BrandGreenDeep else colors.selectedBg)
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
