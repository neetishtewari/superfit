package com.superfit.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.SuperfitTheme

/** Settings › Privacy: the switch for anonymous usage and crash reports (see UsageEvents). */
@Composable
fun UsageReportsCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(enabled) }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Share anonymous usage and crash reports",
                    fontWeight = FontWeight.Bold,
                    color = SuperfitTheme.colors.textPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Helps fix crashes and see which features people use. Never includes what you eat, your weight, health data, photos, or your name or email.",
                    fontSize = 12.sp,
                    color = SuperfitTheme.colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    onChange(it)
                }
            )
        }
    }
}
