package com.superfit.app.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.WorkoutView
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.SuperfitTheme
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One-time card on the Activity view offering to bring in workouts from other apps. */
@Composable
fun WorkoutConnectCard(onConnect: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ElectricCyan.copy(alpha = 0.5f), SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Bring in your workouts from Samsung Health, Strava and other apps.",
                fontWeight = FontWeight.Bold,
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 14.sp
            )
            Text(
                text = "Runs, walks, rides and swims appear here on their own, each counted once, " +
                    "and your calorie target grows on workout days.",
                fontSize = 12.sp,
                color = SuperfitTheme.colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNotNow) {
                    Text("Not now", color = SuperfitTheme.colors.textSecondary)
                }
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A workout from another app. Long-press to hide it in Superfit (it stays in the other app). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportedWorkoutRow(workout: WorkoutView, onHide: () -> Unit, showDay: Boolean = false, onShare: (() -> Unit)? = null) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SuperfitTheme.colors.textPrimary.copy(alpha = 0.03f))
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${workout.title} · ${if (showDay) dayAndTimeOf(workout.startTime) else timeOf(workout.startTime)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuperfitTheme.colors.textPrimary
                )
                Text(text = "${workout.detailLine} · ${workout.sourceName}", fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                workout.exerciseKcal?.let {
                    Text(
                        text = "${it.toInt()} kcal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
                if (onShare != null) {
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share workout",
                            tint = SuperfitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Hide") },
                onClick = {
                    menuOpen = false
                    onHide()
                }
            )
        }
    }
}

/** Asked after a voice-logged workout when another app recorded one in the last few hours. */
@Composable
fun DuplicateWorkoutDialog(prompt: DuplicateWorkoutPrompt, onKeepImported: () -> Unit, onKeepBoth: () -> Unit) {
    val imported = prompt.imported
    AlertDialog(
        onDismissRequest = onKeepBoth,
        title = { Text("Same workout as your ${imported.sourceName} ${imported.noun}?") },
        text = {
            Text(
                "You just logged \"${prompt.voiceDescription}\". ${imported.sourceName} recorded a " +
                    "${imported.noun} at ${timeOf(imported.startTime)} (${imported.detailLine}). " +
                    "If they're the same, keep just one so it isn't counted twice."
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepImported) { Text("Keep ${imported.sourceName}'s") }
        },
        dismissButton = {
            TextButton(onClick = onKeepBoth) { Text("Keep both") }
        }
    )
}

/** "2,100 + 275 from your run = 2,375 today", so the target never changes without a reason. */
fun workoutTargetLine(targetCalories: Double, workoutKcal: Double, source: String): String? {
    val added = workoutKcal.toInt()
    if (added < 1) return null
    val total = targetCalories.toInt()
    val fmt = NumberFormat.getIntegerInstance()
    return "${fmt.format(total - added)} + ${fmt.format(added)} $source = ${fmt.format(total)} today"
}

private fun dayAndTimeOf(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "${time.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${timeOf(millis)}"
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
