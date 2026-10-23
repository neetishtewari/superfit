package com.superfit.app.ui.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.ScaleReading
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

private fun whenOf(reading: ScaleReading): String {
    val time = reading.time.atZone(ZoneId.systemDefault())
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)
    val day = time.toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> "today at $clock"
        today.minusDays(1) -> "yesterday at $clock"
        else -> "${DateTimeFormatter.ofPattern("d MMM").format(day)} at $clock"
    }
}
