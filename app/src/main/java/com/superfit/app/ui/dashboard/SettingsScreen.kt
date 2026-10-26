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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.superfit.app.data.ReminderNotifications
import com.superfit.app.data.getUserSharedPrefs
import com.superfit.app.theme.*
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.widget.WidgetPin

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
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val scrollState = rememberScrollState()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently deletes your Superfit account and everything in it: meals, workouts, " +
                        "weight, sleep and settings, on this phone and in the cloud backup. It can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountDialog = false
                    onDeleteAccount()
                }) {
                    Text("Delete account", color = DeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Custom Targets Overrides State
    var customMacroEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("custom_macro_enabled", false)) }
    var customProtein by remember { mutableStateOf(sharedPrefs.getInt("custom_protein_g", 130).toString()) }
    var customCarbs by remember { mutableStateOf(sharedPrefs.getInt("custom_carbs_g", 200).toString()) }
    var customFat by remember { mutableStateOf(sharedPrefs.getInt("custom_fat_g", 70).toString()) }
    var customCalories by remember { mutableStateOf(sharedPrefs.getInt("custom_calories", 2000).toString()) }

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
            // SECTION 1: Which account is signed in, so people with two accounts can tell them apart.
            val account = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
            if (account != null) {
                val viaGoogle = account.providerData.any { it.providerId == "google.com" }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Signed in as",
                            fontSize = 12.sp,
                            color = SuperfitTheme.colors.textSecondary
                        )
                        account.displayName?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SuperfitTheme.colors.textPrimary
                            )
                        }
                        Text(
                            text = account.email ?: "Account without an email",
                            fontWeight = if (account.displayName.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            color = SuperfitTheme.colors.textPrimary
                        )
                        Text(
                            text = if (viaGoogle) "Google sign-in" else "Email and password",
                            fontSize = 12.sp,
                            color = SuperfitTheme.colors.textSecondary
                        )
                    }
                }
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
                                    try {
                                        val intent = Intent("androidx.health.connect.client.ACTION_HEALTH_CONNECT_SETTINGS")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open Health Connect settings.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    com.superfit.app.ui.launchHealthConnectPermissions(context, requestPermissionsLauncher, viewModel.healthConnectManager.permissions + viewModel.healthConnectManager.bodyPermissions)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f) else NeonMint,
                                contentColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary else Color.White
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
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
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
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
            // SECTION 3: Fitness Goals and Targets Overrides
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Goal",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )

                    if (dashboardState is DashboardUiState.Success) {
                        val state = dashboardState as DashboardUiState.Success
                        val goals = listOf(
                            Triple("LOSE_WEIGHT", "Lose", -500),
                            Triple("MAINTAIN", "Maintain", 0),
                            Triple("GAIN_MUSCLE", "Gain", 300)
                        )

                        SegmentedPills(
                            options = goals.map { it.first to it.second },
                            selected = state.profile.goal,
                            onSelect = { key -> goals.first { it.first == key }.let { viewModel.updateGoal(it.first, it.third) } }
                        )
                    }

                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

                    NutritionistAdviceSettings(viewModel)

                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily targets",
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Set your own calories and macros instead of ours",
                                color = SuperfitTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = customMacroEnabled,
                            onCheckedChange = {
                                customMacroEnabled = it
                                sharedPrefs.edit().putBoolean("custom_macro_enabled", it).apply()
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SuperfitField(
                                    value = customCalories,
                                    onValueChange = { customCalories = it },
                                    label = { Text("Calories (kcal)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                SuperfitField(
                                    value = customProtein,
                                    onValueChange = { customProtein = it },
                                    label = { Text("Protein (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SuperfitField(
                                    value = customCarbs,
                                    onValueChange = { customCarbs = it },
                                    label = { Text("Carbs (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                SuperfitField(
                                    value = customFat,
                                    onValueChange = { customFat = it },
                                    label = { Text("Fat (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    val calVal = customCalories.toIntOrNull() ?: 2000
                                    val protVal = customProtein.toIntOrNull() ?: 130
                                    val carbVal = customCarbs.toIntOrNull() ?: 200
                                    val fatVal = customFat.toIntOrNull() ?: 70

                                    sharedPrefs.edit()
                                        .putInt("custom_calories", calVal)
                                        .putInt("custom_protein_g", protVal)
                                        .putInt("custom_carbs_g", carbVal)
                                        .putInt("custom_fat_g", fatVal)
                                        .apply()

                                    Toast.makeText(context, "Targets saved", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save targets", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

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
                    CoachStyleSettings(viewModel)
                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)
                    CoachMemorySettings(viewModel)
                }
            }

            // SECTION 4: App Appearance Theme Selector
            val themeMode by ThemeConfig.mode.collectAsState()
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
                        text = "Appearance",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Auto follows your phone's light or dark setting.",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )

                    SegmentedPills(
                        options = listOf(
                            ThemeConfig.MODE_SYSTEM to "Auto",
                            ThemeConfig.MODE_LIGHT to "Light",
                            ThemeConfig.MODE_DARK to "Dark"
                        ),
                        selected = themeMode,
                        onSelect = { ThemeConfig.setMode(context, it) }
                    )
                }
            }

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
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                        .clickable { WidgetPin.requestPin(context) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add home-screen widget",
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (widgetPlaced) {
                                    "Already on your home screen. Tap to add another."
                                } else {
                                    "Calories left, plus mic and camera buttons to log in seconds."
                                },
                                fontSize = 12.sp,
                                color = SuperfitTheme.colors.textSecondary
                            )
                        }
                        Text(text = "Add", fontWeight = FontWeight.Bold, color = NeonMint, fontSize = 13.sp)
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
                                .height(40.dp),
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

            // SECTION 6: Account Actions
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f),
                            contentColor = SuperfitTheme.colors.textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Sign out",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { showDeleteAccountDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeleteRed.copy(alpha = 0.15f),
                            contentColor = DeleteRed
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Delete account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/** Small heading above each group of settings. */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = SuperfitTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}
