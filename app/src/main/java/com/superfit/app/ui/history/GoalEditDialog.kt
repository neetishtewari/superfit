package com.superfit.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.ui.components.SuperfitField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Start weight, goal weight and an optional "reach it by" date for the Journey forecast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditDialog(
    startingWeight: Double,
    targetWeight: Double,
    targetDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, LocalDate?) -> Unit
) {
    var startInput by remember { mutableStateOf(com.superfit.app.domain.BodyReadings.fmt(startingWeight)) }
    var targetInput by remember { mutableStateOf(com.superfit.app.domain.BodyReadings.fmt(targetWeight)) }
    var date by remember { mutableStateOf(targetDate) }
    var picking by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SuperfitField(
                    value = startInput,
                    onValueChange = {
                        startInput = it
                        isError = false
                    },
                    label = { Text("Starting weight (kg)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                SuperfitField(
                    value = targetInput,
                    onValueChange = {
                        targetInput = it
                        isError = false
                    },
                    label = { Text("Goal weight (kg)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Reach it by (optional). With a date, Journey tells you whether you're ahead or behind.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) {
                        Text(date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) ?: "Pick a date")
                    }
                    if (date != null) TextButton(onClick = { date = null }) { Text("No date") }
                }
                if (isError) {
                    Text("Please enter valid weights.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = startInput.toDoubleOrNull()
                val t = targetInput.toDoubleOrNull()
                if (s != null && t != null && s > 20.0 && t > 20.0) onConfirm(s, t, date) else isError = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (picking) {
        val tomorrowUtc = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= tomorrowUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}
