package com.superfit.app.ui.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.LocalFormat
import com.superfit.app.domain.ScaleReading
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * "Is this you?" for a scale reading that's far from the user's own weight or body fat, so a
 * partner's weigh-in on the same scale doesn't land in their history.
 */
@Composable
fun ScaleReadingDialog(reading: ScaleReading, onMe: () -> Unit, onNotMe: () -> Unit, onLater: () -> Unit) {
    val fat = reading.bodyFatPct?.let { " · ${BodyReadings.fmt(it)}% body fat" } ?: ""
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Is this you?") },
        text = {
            Text(
                "${BodyReadings.fmt(reading.weightKg)} kg$fat from ${reading.source}, ${whenOf(reading)}.\n\n" +
                    "It's quite different from your recent readings. If someone else uses the same scale, " +
                    "tap Not me and Superfit will leave out their readings from now on."
            )
        },
        confirmButton = {
            TextButton(onClick = onMe) { Text("Yes, it's me", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onNotMe) { Text("Not me") }
        }
    )
}

@OptIn(ExperimentalTime::class)
private fun whenOf(reading: ScaleReading): String {
    val zone = TimeZone.currentSystemDefault()
    val time = Instant.fromEpochMilliseconds(reading.timeMs).toLocalDateTime(zone)
    val clock = LocalFormat.shortClock(time.time)
    val today = Clock.System.todayIn(zone)
    return when (time.date) {
        today -> "today at $clock"
        today.minus(1, DateTimeUnit.DAY) -> "yesterday at $clock"
        else -> "${LocalFormat.dayMonth(time.date)} at $clock"
    }
}
