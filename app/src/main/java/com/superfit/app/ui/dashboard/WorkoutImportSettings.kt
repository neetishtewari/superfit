package com.superfit.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.WorkoutImporter
import com.superfit.app.ui.settings.WorkoutImportCard

/** Settings for workouts from other apps on Android: the shared card with Health Connect's access sheet. */
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

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.onWorkoutPermissionsResult(granted)
    }

    WorkoutImportCard(
        enabled = enabled,
        sources = "Samsung Health, Strava, watches and more. Each workout is counted once.",
        mainApp = mainApp,
        apps = sourceApps,
        addBack = addBack,
        hiddenCount = hiddenCount,
        onEnabled = { on ->
            if (on) com.superfit.app.ui.launchHealthConnectPermissions(context, launcher, WorkoutImporter.permissions) else viewModel.setWorkoutImportEnabled(false)
        },
        onMainApp = {
            mainApp = it
            viewModel.setMainWorkoutApp(it)
        },
        onAddBack = {
            addBack = it
            viewModel.setWorkoutAddBack(it)
        },
        onShowHidden = {
            viewModel.unhideAllWorkouts()
            hiddenCount = 0
        }
    )
}
