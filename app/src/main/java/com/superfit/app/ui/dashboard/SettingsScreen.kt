package com.superfit.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.superfit.app.data.ReminderNotifications
import com.superfit.app.data.getUserSharedPrefs
import com.superfit.app.theme.*
import com.superfit.app.ui.settings.SettingsGroupLabel
import com.superfit.app.widget.WidgetPin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { getUserSharedPrefs(context) }

    val dashboardState by viewModel.dashboardState.collectAsState()
    val stepGoal by viewModel.stepGoal.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val scrollState = rememberScrollState()
    var reminderEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("reminder_enabled", true)) }
    var reminderHour by remember { mutableStateOf(sharedPrefs.getInt("reminder_hour", 21)) }
    var reminderMinute by remember { mutableStateOf(sharedPrefs.getInt("reminder_minute", 0)) }
    // Whether the phone lets Superfit show notifications at all. Re-checked when coming back
    // from the phone's settings.
    var notificationsAllowed by remember { mutableStateOf(ReminderNotifications.canPost(context)) }
    val canPinWidget = remember { WidgetPin.canPin(context) }
    var widgetPlaced by remember { mutableStateOf(WidgetPin.isPlaced(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = ReminderNotifications.canPost(context)
        widgetPlaced = WidgetPin.isPlaced(context)
        onPauseOrDispose { }
    }

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.checkPermissions()
        viewModel.syncTelemetry()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationsAllowed = ReminderNotifications.canPost(context)
        if (isGranted) {
            reminderEnabled = true
            sharedPrefs.edit().putBoolean("reminder_enabled", true).apply()
            com.superfit.app.data.ReminderScheduler.scheduleReminder(context, reminderHour, reminderMinute)
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            reminderEnabled = false
            sharedPrefs.edit().putBoolean("reminder_enabled", false).apply()
            Toast.makeText(context, "Notifications are disabled. Grant permission in App Info settings to receive alerts.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = SuperfitTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = SuperfitTheme.colors.cardBg,
                            contentColor = SuperfitTheme.colors.textPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .background(SuperfitTheme.colors.bgStart)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsGroupLabel("Account")
            val account = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
            if (account != null) {
                com.superfit.app.ui.settings.AccountCard(
                    name = account.displayName,
                    email = account.email,
                    signInMethod = if (account.providerData.any { it.providerId == "google.com" }) "Google sign-in" else "Email and password"
                )
            }

            SettingsGroupLabel("Health data")
            // SECTION 2: Google Health Connect
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Health Connect",
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hasHealthConnectPermissions) {
                                        NeonMint.copy(alpha = 0.15f)
                                    } else {
                                        SolarAmber.copy(alpha = 0.15f)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasHealthConnectPermissions) "Connected" else "Not connected",
                                color = if (hasHealthConnectPermissions) NeonMint else SolarAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = if (hasHealthConnectPermissions) {
                            "Superfit reads your steps, sleep, workouts and body data from Health Connect."
                        } else {
                            "Allow access so Superfit can read your steps, sleep, workouts and body data."
                        },
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (hasHealthConnectPermissions) {
                                    com.superfit.app.ui.openHealthConnectSettings(context)
                                } else {
                                    com.superfit.app.ui.launchHealthConnectPermissions(context, requestPermissionsLauncher, viewModel.healthConnectManager.permissions + viewModel.healthConnectManager.bodyPermissions)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f) else NeonMint,
                                contentColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary else Color.White
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (hasHealthConnectPermissions) "Manage access" else "Allow access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.syncTelemetry() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.15f),
                                contentColor = ElectricCyan
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Sync now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    val syncText = remember(lastSyncTime) {
                        if (lastSyncTime > 0L) {
                            val minutes = ((System.currentTimeMillis() - lastSyncTime) / 60000).toInt()
                            if (minutes == 0) "Last synced just now." else "Last synced $minutes min ago."
                        } else {
                            "Never synced."
                        }
                    }
                    Text(
                        text = syncText,
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textTertiary,
                        modifier = Modifier.align(Alignment.End)
                    )

                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

                    WorkoutImportSettings(viewModel)
                }
            }

            SettingsGroupLabel("Your plan")
            com.superfit.app.ui.settings.PlanSettingsCard(
                state = dashboardState,
                stepGoal = stepGoal,
                prefs = remember { com.superfit.app.data.AndroidPrefs(sharedPrefs) },
                onGoal = { goal -> viewModel.updateGoal(goal.key, goal.calorieOffset) },
                onSetOwnStepGoal = { viewModel.setOwnStepGoal(it) },
                onTargetsChanged = { saved -> if (saved) Toast.makeText(context, "Targets saved", Toast.LENGTH_SHORT).show() },
                nutritionistAdvice = {
                    NutritionistAdviceSettings(
                        prefs = remember { com.superfit.app.data.userPrefs(context) },
                        onSave = viewModel::saveNutritionistAdvice,
                        onApplyTargets = viewModel::applyNutritionistTargets
                    )
                }
            )

            SettingsGroupLabel("AI coach")
            // AI coach: kept apart from "My nutritionist's advice", which comes from a real person.
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val coachStyle by viewModel.coachStyle.collectAsState()
                    CoachStyleSettings(coachStyle, viewModel::setCoachStyle)
                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)
                    CoachMemorySettings(viewModel)
                }
            }

            val themeMode by ThemeConfig.mode.collectAsState()
            com.superfit.app.ui.settings.AppearanceCard(mode = themeMode, onSelect = { ThemeConfig.setMode(context, it) })

            // SECTION 4.5: Daily Reminder Notifications Settings
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daily reminder",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "A nudge to log your meals. If you've already logged, you get a coach tip instead.",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Remind me",
                            fontWeight = FontWeight.Medium,
                            color = SuperfitTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reminderEnabled && notificationsAllowed,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            ReminderNotifications.PERMISSION
                                        ) != PackageManager.PERMISSION_GRANTED
                                    // After two "Don't allow" taps Android stops showing the prompt,
                                    // so the only way left is the phone's own settings page.
                                    val promptBlocked = needsPermission &&
                                        ReminderNotifications.wasAsked(context) &&
                                        (context as? android.app.Activity)
                                            ?.shouldShowRequestPermissionRationale(ReminderNotifications.PERMISSION) == false

                                    if (needsPermission && !promptBlocked) {
                                        ReminderNotifications.markAsked(context)
                                        notificationPermissionLauncher.launch(ReminderNotifications.PERMISSION)
                                    } else {
                                        reminderEnabled = true
                                        sharedPrefs.edit().putBoolean("reminder_enabled", true).apply()
                                        com.superfit.app.data.ReminderScheduler.scheduleReminder(context, reminderHour, reminderMinute)
                                        if (!ReminderNotifications.canPost(context)) {
                                            Toast.makeText(context, "Turn on notifications for Superfit to get your reminder", Toast.LENGTH_LONG).show()
                                            ReminderNotifications.openSystemSettings(context)
                                        }
                                    }
                                } else {
                                    reminderEnabled = false
                                    sharedPrefs.edit().putBoolean("reminder_enabled", false).apply()
                                    com.superfit.app.data.ReminderScheduler.cancelReminder(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonMint,
                                uncheckedThumbColor = SuperfitTheme.colors.textTertiary,
                                uncheckedTrackColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f)
                            )
                        )
                    }

                    if (reminderEnabled && !notificationsAllowed) {
                        Text(
                            text = "Notifications are turned off for Superfit on this phone, so the reminder can't appear. Tap here to turn them on.",
                            color = NeonMint,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ReminderNotifications.openSystemSettings(context) }
                                .padding(vertical = 4.dp)
                        )
                    }

                    if (reminderEnabled) {
                        val amPm = if (reminderHour >= 12) "PM" else "AM"
                        val displayHour = when {
                            reminderHour == 0 -> 12
                            reminderHour > 12 -> reminderHour - 12
                            else -> reminderHour
                        }
                        val formattedTime = String.format("%02d:%02d %s", displayHour, reminderMinute, amPm)

                        val timePickerDialog = remember(reminderHour, reminderMinute) {
                            android.app.TimePickerDialog(
                                context,
                                { _, selectedHour, selectedMinute ->
                                    reminderHour = selectedHour
                                    reminderMinute = selectedMinute
                                    sharedPrefs.edit()
                                        .putInt("reminder_hour", selectedHour)
                                        .putInt("reminder_minute", selectedMinute)
                                        .apply()
                                    com.superfit.app.data.ReminderScheduler.scheduleReminder(context, selectedHour, selectedMinute)
                                    Toast.makeText(context, "Reminder time saved", Toast.LENGTH_SHORT).show()
                                },
                                reminderHour,
                                reminderMinute,
                                false
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SuperfitTheme.colors.textPrimary.copy(alpha = 0.04f))
                                .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(12.dp))
                                .clickable { timePickerDialog.show() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Time",
                                color = SuperfitTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formattedTime,
                                color = NeonMint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Home-screen widget (hidden where the launcher can't add one from an app)
            if (canPinWidget) {
                // After a tap, wait for the launcher to place it so a second tap doesn't add a second widget.
                var widgetRequested by remember { mutableStateOf(false) }
                LaunchedEffect(widgetRequested) {
                    if (widgetRequested) {
                        repeat(60) {
                            kotlinx.coroutines.delay(1000)
                            if (WidgetPin.isPlaced(context)) {
                                widgetPlaced = true
                                widgetRequested = false
                                return@LaunchedEffect
                            }
                        }
                        widgetRequested = false
                    }
                }
                // The add-widget box covers the app; when the person comes back without adding
                // it (they cancelled), stop waiting so the row doesn't keep saying "Tap Add".
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()
                DisposableEffect(lifecycleOwner, widgetRequested) {
                    var leftApp = false
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (!widgetRequested) return@LifecycleEventObserver
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) leftApp = true
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && leftApp) {
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                if (WidgetPin.isPlaced(context)) widgetPlaced = true
                                widgetRequested = false
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Home-screen widget",
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = when {
                                    widgetPlaced -> "On your home screen."
                                    widgetRequested -> "Tap Add on the box that pops up."
                                    else -> "Calories left, plus mic and camera buttons to log in seconds."
                                },
                                fontSize = 12.sp,
                                color = SuperfitTheme.colors.textSecondary
                            )
                        }
                        if (widgetPlaced) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonMint, modifier = Modifier.size(18.dp))
                                Text(text = "Added", fontWeight = FontWeight.Bold, color = NeonMint, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    widgetRequested = true
                                    if (!WidgetPin.requestPin(context)) {
                                        widgetRequested = false
                                        Toast.makeText(context, "Your phone couldn't add it. Long-press your home screen, tap Widgets, and find Superfit.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !widgetRequested,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            val isDebug = remember {
                (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            }

            if (isDebug) {
                // SECTION 5: Onboarding Preview & Diagnostics
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Testing (dev app only)",
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Preview and test the onboarding flow (body stats, goals and Health Connect) for new users. Your existing meal and workout history will not be lost.",
                            fontSize = 12.sp,
                            color = SuperfitTheme.colors.textSecondary
                        )
                        Button(
                            onClick = onNavigateToOnboarding,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.15f),
                                contentColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Preview sign-up steps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            SettingsGroupLabel("Sharing")
            com.superfit.app.ui.share.SharingSettingsCard(onOffersChanged = { viewModel.refreshShareOffer() })

            SettingsGroupLabel("Privacy")
            // Anonymous usage and crash reports (see AppAnalytics)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            ) {
                var usageReports by remember { mutableStateOf(com.superfit.app.data.AppAnalytics.isEnabled(context)) }
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        checked = usageReports,
                        onCheckedChange = { enabled ->
                            usageReports = enabled
                            com.superfit.app.data.AppAnalytics.setEnabled(context, enabled)
                        }
                    )
                }
            }

            com.superfit.app.ui.settings.AccountActionsCard(onSignOut = onSignOut, onDeleteAccount = onDeleteAccount)
        }
    }
}
