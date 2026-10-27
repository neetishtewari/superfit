package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** "AI coach style": how the daily tip and the chat talk to the user. One roomy row per style. */
@Composable
fun CoachStyleSettings(viewModel: DashboardViewModel) {
    val style by viewModel.coachStyle.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "AI coach style",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        CoachStyleOptions(selected = style, onSelect = viewModel::setCoachStyle)
    }
}

/** The three styles as rows; with [showSamples] each shows an example line (sign-up and the one-time chooser). */
@Composable
fun CoachStyleOptions(selected: CoachStyle, onSelect: (CoachStyle) -> Unit, showSamples: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoachStyle.entries.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) SuperfitTheme.colors.brandSoft else SuperfitTheme.colors.fieldBg)
                    .then(if (isSelected) Modifier.border(1.5.dp, NeonMint, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    colors = RadioButtonDefaults.colors(selectedColor = NeonMint)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = option.label,
                        color = SuperfitTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = option.blurb + if (option == CoachStyle.ROAST) " Roasts your habits, never you." else "",
                        color = SuperfitTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                    if (showSamples) {
                        Text(
                            text = "\u201C${option.sample}\u201D",
                            color = SuperfitTheme.colors.textPrimary,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The one-time "Pick your coach" for existing users: Roast me starts selected; "Not now" keeps Supportive. */
@Composable
fun CoachStyleChooserDialog(onChoose: (CoachStyle) -> Unit) {
    var picked by remember { mutableStateOf(CoachStyle.ROAST) }
    AlertDialog(
        onDismissRequest = { onChoose(CoachStyle.current) },
        containerColor = SuperfitTheme.colors.cardBg,
        title = { Text("Pick your coach", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "New: choose how your AI coach talks to you. It also sets the voice of your monthly recap. Same advice, different voice.",
                    color = SuperfitTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
                CoachStyleOptions(selected = picked, onSelect = { picked = it }, showSamples = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(picked) }) { Text("Save", fontWeight = FontWeight.Bold, color = NeonMint) }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(CoachStyle.current) }) { Text("Not now", color = SuperfitTheme.colors.textSecondary) }
        }
    )
}
