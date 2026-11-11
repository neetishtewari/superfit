package com.superfit.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Prefs
import com.superfit.app.domain.FitnessGoal
import com.superfit.app.domain.LocalFormat
import com.superfit.app.domain.StepGoalResult
import com.superfit.app.theme.DeleteRed
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.GoalPicker
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.SegmentedPills
import com.superfit.app.ui.dashboard.StepGoalChooser
import kotlin.math.roundToInt

/** Small heading above each group of settings. */
@Composable
fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = SuperfitTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

/** The rounded card every group of settings sits in. */
@Composable
fun SettingsCard(spacing: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}

/** Which account is signed in, so people with two accounts can tell them apart. */
@Composable
fun AccountCard(name: String?, email: String?, signInMethod: String?) {
    SettingsCard(spacing = 2) {
        Text(text = "Signed in as", fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary)
        name?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SuperfitTheme.colors.textPrimary)
        }
        Text(
            text = email ?: "Account without an email",
            fontWeight = if (name.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            color = SuperfitTheme.colors.textPrimary
        )
        signInMethod?.let { Text(text = it, fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary) }
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
}

/**
 * Your plan: the goal, the daily step goal, the nutritionist's advice (where the app has it) and the
 * person's own calorie and macro targets, saved in their settings under the same keys on both apps.
 */
@Composable
fun PlanSettingsCard(
    state: DashboardUiState,
    stepGoal: StepGoalResult?,
    prefs: Prefs,
    onGoal: (FitnessGoal) -> Unit,
    onSetOwnStepGoal: (Int?) -> Unit,
    /** True when own targets were saved, false when they were switched off. */
    onTargetsChanged: (saved: Boolean) -> Unit,
    nutritionistAdvice: (@Composable () -> Unit)? = null,
    /** Show "Targets saved" under the button (where the app has no pop-up note for it). */
    confirmInCard: Boolean = false
) {
    var savedNote by remember { mutableStateOf(false) }
    var customMacroEnabled by remember { mutableStateOf(prefs.getBoolean("custom_macro_enabled", false)) }
    var customProtein by remember { mutableStateOf(prefs.getInt("custom_protein_g", 130).toString()) }
    var customCarbs by remember { mutableStateOf(prefs.getInt("custom_carbs_g", 200).toString()) }
    var customFat by remember { mutableStateOf(prefs.getInt("custom_fat_g", 70).toString()) }
    var customCalories by remember { mutableStateOf(prefs.getInt("custom_calories", 2000).toString()) }

    SettingsCard(spacing = 14) {
        SettingTitle("Goal")
        if (state is DashboardUiState.Success) {
            val targets = state.macroTargets
            GoalPicker(
                selected = state.profile.goal,
                onSelect = onGoal,
                selectedDetail = "Today's target: ${LocalFormat.integer(targets.calories.roundToInt().toLong())} kcal · ${targets.proteinG.roundToInt()} g protein"
            )
        }

        stepGoal?.let { goal ->
            HorizontalDivider(color = SuperfitTheme.colors.glassBorder)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingTitle("Daily step goal")
                Text(
                    text = "Today's goal: ${LocalFormat.integer(goal.goal.toLong())} steps",
                    color = SuperfitTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
                StepGoalChooser(goal = goal, onSetOwn = onSetOwnStepGoal)
            }
        }

        nutritionistAdvice?.let {
            HorizontalDivider(color = SuperfitTheme.colors.glassBorder)
            it()
        }

        HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                SettingTitle("Daily targets")
                Text(
                    text = "Set your own calories and macros instead of ours",
                    color = SuperfitTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = customMacroEnabled,
                onCheckedChange = { on ->
                    customMacroEnabled = on
                    if (on) {
                        // Just opens the boxes; nothing changes until "Save targets".
                        // First time, start from the person's current plan, not generic numbers.
                        val plan = (state as? DashboardUiState.Success)?.macroTargets
                        if (plan != null && !prefs.contains("custom_calories")) {
                            customCalories = plan.calories.roundToInt().toString()
                            customProtein = plan.proteinG.roundToInt().toString()
                            customCarbs = plan.carbsG.roundToInt().toString()
                            customFat = plan.fatG.roundToInt().toString()
                        }
                    } else {
                        prefs.edit { putBoolean("custom_macro_enabled", false) }
                        onTargetsChanged(false)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonMint,
                    checkedTrackColor = NeonMint.copy(alpha = 0.3f),
                    uncheckedThumbColor = SuperfitTheme.colors.textTertiary,
                    uncheckedTrackColor = SuperfitTheme.colors.glassBorder
                )
            )
        }

        if (customMacroEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(customCalories, { customCalories = it }, "Calories (kcal)", Modifier.weight(1f))
                    NumberField(customProtein, { customProtein = it }, "Protein (g)", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(customCarbs, { customCarbs = it }, "Carbs (g)", Modifier.weight(1f))
                    NumberField(customFat, { customFat = it }, "Fat (g)", Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        prefs.edit {
                            putInt("custom_calories", customCalories.toIntOrNull() ?: 2000)
                            putInt("custom_protein_g", customProtein.toIntOrNull() ?: 130)
                            putInt("custom_carbs_g", customCarbs.toIntOrNull() ?: 200)
                            putInt("custom_fat_g", customFat.toIntOrNull() ?: 70)
                            putBoolean("custom_macro_enabled", true)
                        }
                        savedNote = true
                        onTargetsChanged(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save targets", color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (confirmInCard && savedNote) {
                    Text("Targets saved", fontSize = 12.sp, color = NeonMint, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    SuperfitField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = SuperfitTheme.colors.textSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Light, dark or Auto (follow the phone). [mode] is "system", "light" or "dark". */
@Composable
fun AppearanceCard(mode: String, onSelect: (String) -> Unit) {
    SettingsCard {
        SettingTitle("Appearance")
        Text(
            text = "Auto follows your phone's light or dark setting.",
            fontSize = 12.sp,
            color = SuperfitTheme.colors.textSecondary
        )
        SegmentedPills(
            options = listOf("system" to "Auto", "light" to "Light", "dark" to "Dark"),
            selected = mode,
            onSelect = onSelect
        )
    }
}

/** Sign out, and delete the account after a clear warning (hidden where deleting isn't built yet). */
@Composable
fun AccountActionsCard(onSignOut: () -> Unit, onDeleteAccount: (() -> Unit)?) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently deletes your Superfit account and everything in it: meals, workouts, " +
                        "weight, sleep and settings, on this phone and in the cloud backup. It can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) {
                    Text("Delete account", color = DeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f),
                    contentColor = SuperfitTheme.colors.textPrimary
                ),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Sign out", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (onDeleteAccount != null) {
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed.copy(alpha = 0.15f), contentColor = DeleteRed),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Delete account", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
