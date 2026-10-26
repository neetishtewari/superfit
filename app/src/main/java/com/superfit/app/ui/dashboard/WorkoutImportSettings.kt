package com.superfit.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.WorkoutImporter
import com.superfit.app.domain.WorkoutAddBack
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** Settings for workouts from other apps: the import switch, main app, calorie add-back and hidden workouts. */
@Composable
fun WorkoutImportSettings(viewModel: DashboardViewModel) {
    val enabled by viewModel.workoutImportEnabled.collectAsState()
    val prefs = viewModel.workoutImportPrefs
    var mainApp by remember { mutableStateOf(prefs.mainApp) }
    var addBack by remember { mutableStateOf(prefs.addBack) }
    var hiddenCount by remember { mutableStateOf(prefs.hiddenIds.size) }
    var sourceApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(enabled) {
        if (enabled) sourceApps = viewModel.workoutSourceApps()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.onWorkoutPermissionsResult(granted)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Import workouts from other apps",
                    fontWeight = FontWeight.Bold,
                    color = SuperfitTheme.colors.textPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Samsung Health, Strava, watches and more. Each workout is counted once.",
                    color = SuperfitTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) com.superfit.app.ui.launchHealthConnectPermissions(context, launcher, WorkoutImporter.permissions) else viewModel.setWorkoutImportEnabled(false)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonMint,
                    checkedTrackColor = NeonMint.copy(alpha = 0.3f),
                    uncheckedThumbColor = SuperfitTheme.colors.textTertiary,
                    uncheckedTrackColor = SuperfitTheme.colors.glassBorder
                )
            )
        }

        if (enabled) {
            MainAppPicker(
                mainApp = mainApp,
                apps = sourceApps,
                onPick = {
                    mainApp = it
                    viewModel.setMainWorkoutApp(it)
                }
            )

            Text(
                text = "Add workout calories to my target",
                fontWeight = FontWeight.Bold,
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutAddBack.entries.forEach { option ->
                    FilterChip(
                        selected = addBack == option,
                        onClick = {
                            addBack = option
                            viewModel.setWorkoutAddBack(option)
                        },
                        label = { Text(option.label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonMint,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Text(
                text = when (addBack) {
                    WorkoutAddBack.OFF -> "Your target stays the same every day, from the activity level you chose."
                    WorkoutAddBack.HALF -> "Your target covers everyday life, plus half of each workout's calories that day."
                    WorkoutAddBack.ALL -> "Your target covers everyday life, plus all of each workout's calories that day."
                },
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 12.sp
            )

            if (hiddenCount > 0) {
                TextButton(onClick = {
                    viewModel.unhideAllWorkouts()
                    hiddenCount = 0
                }) {
                    Text("Show hidden workouts ($hiddenCount)", color = NeonMint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MainAppPicker(mainApp: String?, apps: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = apps.firstOrNull { it.first == mainApp }?.second ?: "Automatic"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "Main workout app",
                fontWeight = FontWeight.Bold,
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 13.sp
            )
            Text(
                text = "When two apps record the same workout, this one's copy is kept. Automatic keeps the most detailed one.",
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        Box {
            TextButton(onClick = { open = true }) {
                Text(current, color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Automatic") }, onClick = {
                    open = false
                    onPick(null)
                })
                apps.forEach { (pkg, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        open = false
                        onPick(pkg)
                    })
                }
            }
        }
    }
}

private val WorkoutAddBack.label: String
    get() = when (this) {
        WorkoutAddBack.OFF -> "Off"
        WorkoutAddBack.HALF -> "Half"
        WorkoutAddBack.ALL -> "All"
    }
