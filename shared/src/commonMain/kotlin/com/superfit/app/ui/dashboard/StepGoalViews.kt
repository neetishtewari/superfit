package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.Decimals
import com.superfit.app.domain.HealthApp
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StepGoalReason
import com.superfit.app.domain.StepGoalResult
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeBrandSoft
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import com.superfit.app.ui.components.SuperfitField

private fun steps(n: Int) = Decimals.grouped(n.toLong())

/** Shown on Home the day the step goal goes up, until dismissed. */
@Composable
fun StepGoalRaisedNote(goal: StepGoalResult, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ThemeBrandSoft)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("New step goal: ${steps(goal.goal)}", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 14.sp)
            Text(
                "You reached your last goal on ${goal.daysReached} days last week.",
                color = ThemeTextSecondary,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = ThemeTextSecondary)
        }
    }
}

/** What today's step goal is and why, with the choice of setting your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepGoalSheet(goal: StepGoalResult, onSetOwn: (Int?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.White.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your step goal: ${steps(goal.goal)}", style = MaterialTheme.typography.titleLarge, color = ThemeTextPrimary)
            stepGoalExplanation(goal).forEach { line ->
                Text(line, color = ThemeTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            StepGoalChooser(goal = goal, onSetOwn = onSetOwn)
        }
    }
}

/** Plain lines explaining the goal, for the sheet. */
fun stepGoalExplanation(goal: StepGoalResult): List<String> {
    val lines = mutableListOf<String>()
    when (goal.reason) {
        StepGoalReason.OWN -> lines += "You set this goal yourself."
        StepGoalReason.STARTER -> lines += "A starting goal for your age. Once there are about 5 days of steps from ${HealthApp.name}, it changes to fit how much you usually walk."
        else -> {
            goal.usual?.let { lines += "You usually walk about ${steps(it)} steps a day." }
            if (goal.reason == StepGoalReason.DOWN) lines += "It was reset lower to fit a quieter stretch. It will build back up."
            if (goal.goal < goal.healthyAim) {
                lines += "It goes up ${steps(goal.stepUp)} when you reach it on ${StepGoal.DAYS_TO_RISE} days in a week. It's checked each Monday."
            } else if (goal.atHealthyLevel) {
                lines += "You're already walking more than the healthy level for your age, so your goal stays there. Every step above it is a bonus."
            }
        }
    }
    lines += "For your age, about ${steps(goal.healthyAim)} steps a day gives most of the health benefit of walking."
    return lines
}

/** Automatic goal or the person's own number. Used in the Home sheet and in Settings. */
@Composable
fun StepGoalChooser(goal: StepGoalResult, onSetOwn: (Int?) -> Unit) {
    var mode by remember(goal.reason) { mutableStateOf(if (goal.reason == StepGoalReason.OWN) "OWN" else "AUTO") }
    var text by remember(goal.goal) { mutableStateOf(goal.goal.toString()) }
    SegmentedPills(
        options = listOf("AUTO" to "Automatic", "OWN" to "My own number"),
        selected = mode,
        onSelect = { key ->
            mode = key
            if (key == "AUTO" && goal.reason == StepGoalReason.OWN) onSetOwn(null)
        }
    )
    if (mode == "OWN") {
        val value = text.filter { it.isDigit() }.toIntOrNull()
        val valid = value != null && value in StepGoal.OWN_MIN..StepGoal.OWN_MAX
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SuperfitField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Steps a day", color = ThemeTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = text.isNotEmpty() && !valid,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { value?.let { onSetOwn(it) } },
                enabled = valid && !(goal.reason == StepGoalReason.OWN && value == goal.goal),
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.White)
            ) { Text("Save") }
        }
        Text(
            "Between ${steps(StepGoal.OWN_MIN)} and ${steps(StepGoal.OWN_MAX)}. Useful if a doctor gave you a number. Your own goal doesn't change by itself.",
            color = ThemeTextSecondary,
            fontSize = 12.sp
        )
    } else {
        Text("Recommended. Fits how much you walk now and grows with you.", color = ThemeTextSecondary, fontSize = 12.sp)
    }
}
