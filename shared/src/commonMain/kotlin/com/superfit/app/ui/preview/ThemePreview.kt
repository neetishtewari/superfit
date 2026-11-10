package com.superfit.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.Decimals
import com.superfit.app.domain.FitnessGoals
import com.superfit.app.theme.HyperViolet
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeBgStart
import com.superfit.app.ui.components.FieldActionButton
import com.superfit.app.ui.components.GoalPicker
import com.superfit.app.ui.components.IconBadge
import com.superfit.app.ui.components.MicButton
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitBrandHeader
import com.superfit.app.ui.components.SuperfitCard
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.ui.dashboard.SegmentedPills

/**
 * A stand-in screen drawn with Superfit's shared theme and the shared buttons, text boxes and
 * cards, so the iPhone shows real Superfit pieces before the actual screens move to shared code.
 */
@Composable
fun ThemePreview(darkTheme: Boolean) {
    SuperfitTheme(darkTheme = darkTheme) {
        val colors = SuperfitTheme.colors
        var taps by remember { mutableIntStateOf(0) }
        var meal by remember { mutableStateOf("") }
        var tab by remember { mutableStateOf("food") }
        var goal by remember { mutableStateOf(FitnessGoals.all.first().key) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeBgStart)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SuperfitBrandHeader()
            SegmentedPills(
                options = listOf("food" to "Food", "activity" to "Activity"),
                selected = tab,
                onSelect = { tab = it }
            )
            SuperfitCard {
                Text("Today", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text("1,240 of 1,804 kcal", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                Text("Protein ${Decimals.one(72.46)} g", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            SuperfitField(
                value = meal,
                onValueChange = { meal = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("2 roti and dal") },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MicButton(onClick = {})
                        FieldActionButton(Icons.Default.Add, "Add", enabled = meal.isNotBlank(), onClick = { meal = "" })
                    }
                }
            )
            PrimaryButton(text = if (taps == 0) "Tell me what you ate" else "Tapped $taps", onClick = { taps++ })
            SuperfitCard(tint = colors.coachSoft) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBadge(Icons.Default.AutoAwesome, background = colors.coachSoft, tint = HyperViolet)
                    Text("Your AI coach", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                }
            }
            GoalPicker(selected = goal, onSelect = { goal = it.key })
        }
    }
}
