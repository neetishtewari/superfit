package com.superfit.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/**
 * Settings › Daily reminder, with Android's words: on or off, and the time. [allowed] is false when
 * the phone's own settings block Superfit's notifications, with a line that opens them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsCard(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    allowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onTime: (hour: Int, minute: Int) -> Unit,
    onOpenPhoneSettings: () -> Unit
) {
    var pickTime by remember { mutableStateOf(false) }
    SettingsCard {
        Text(text = "Daily reminder", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
        Text(
            text = "A nudge to log your meals. If you've already logged, you get a coach tip instead.",
            fontSize = 12.sp,
            color = SuperfitTheme.colors.textSecondary
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Remind me",
                fontWeight = FontWeight.Medium,
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enabled && allowed,
                onCheckedChange = { on -> if (on && !allowed && enabled) onOpenPhoneSettings() else onEnabled(on) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonMint,
                    uncheckedThumbColor = SuperfitTheme.colors.textTertiary,
                    uncheckedTrackColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f)
                )
            )
        }
        if (enabled && !allowed) {
            Text(
                text = "Notifications are turned off for Superfit on this phone, so the reminder can't appear. Tap here to turn them on.",
                color = NeonMint,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPhoneSettings).padding(vertical = 4.dp)
            )
        }
        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { pickTime = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time", color = SuperfitTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(clockText(hour, minute), color = NeonMint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    if (pickTime) {
        val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = SuperfitTheme.colors.cardBg,
            title = { Text("Reminder time", color = SuperfitTheme.colors.textPrimary) },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(picker.hour, picker.minute)
                    pickTime = false
                }) { Text("Save", color = NeonMint, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pickTime = false }) { Text("Cancel", color = SuperfitTheme.colors.textSecondary) }
            }
        )
    }
}

/** "09:00 PM", as Android's reminder setting shows it. */
fun clockText(hour: Int, minute: Int): String {
    val shown = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour >= 12) "PM" else "AM"
    return "${shown.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
}
