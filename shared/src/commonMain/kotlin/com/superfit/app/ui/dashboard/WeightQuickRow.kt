package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.domain.Decimals
import com.superfit.app.theme.ThemeCardBgTranslucent
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** A small Home row to log today's weight without going to the History tab. */
@Composable
fun WeightQuickRow(
    currentWeightKg: Double,
    weightEntries: List<WeightEntryEntity>,
    onLogWeight: (Double, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val last = weightEntries.maxByOrNull { it.timestamp }
    val shownWeight = last?.weightKg ?: currentWeightKg
    val whenText = last?.let { lastLoggedText(it.timestamp) } ?: "No weigh-ins yet"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Weight  ${Decimals.one(shownWeight)} kg",
                color = ThemeTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(text = whenText, color = ThemeTextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text("Log weight", fontWeight = FontWeight.Bold)
        }
    }

    if (showDialog) {
        WeighInDialog(
            currentWeight = shownWeight,
            onDismiss = { showDialog = false },
            onConfirm = { weight ->
                onLogWeight(weight, "")
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun lastLoggedText(timestamp: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val day = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).date
    return when (val days = day.daysUntil(Clock.System.todayIn(zone))) {
        0 -> "Logged today"
        1 -> "Logged yesterday"
        else -> "Logged $days days ago"
    }
}
