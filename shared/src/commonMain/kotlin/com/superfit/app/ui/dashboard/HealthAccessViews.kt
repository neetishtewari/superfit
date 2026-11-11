package com.superfit.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.HealthApp
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.IconBadge
import com.superfit.app.ui.components.SuperfitCard

/** What Superfit reads from the phone's health app, said the same way before and after connecting. */
fun healthReadsLine(): String = "Superfit reads your steps, active calories, walking distance and sleep from ${HealthApp.name}. " +
    "It never changes anything there."

/**
 * Asks to connect the phone's health app, explaining first what it's for, as Apple asks apps to do
 * before its own access sheet appears. Shown on Today's Activity side until the sheet is answered.
 */
@Composable
fun ConnectHealthCard(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SuperfitTheme.colors
    SuperfitCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Rounded.MonitorHeart, background = colors.brandSoft, tint = colors.primaryAccent, size = 40)
            Text(
                "Bring in your steps and sleep",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                healthReadsLine() + " Your step goal and coach use them.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            HealthConnectButton(onConnect)
        }
    }
}

/** Settings' health app section: connect it, or how to change what Superfit may read. */
@Composable
fun HealthSettingsSection(asked: Boolean, onConnect: () -> Unit) {
    val colors = SuperfitTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(healthReadsLine(), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (asked) {
            Text(
                "To change what Superfit can read, open the Health app, tap your picture, then Apps › Superfit.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        } else {
            HealthConnectButton(onConnect)
        }
    }
}

@Composable
private fun HealthConnectButton(onConnect: () -> Unit) {
    Button(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SuperfitTheme.colors.primaryAccent, contentColor = Color.White)
    ) {
        Text("Connect ${HealthApp.name}", fontWeight = FontWeight.SemiBold)
    }
}
