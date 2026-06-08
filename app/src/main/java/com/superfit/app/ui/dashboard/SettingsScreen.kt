package com.superfit.app.ui.dashboard

import android.content.Context
import android.content.Intent
import com.superfit.app.data.getUserSharedPrefs
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { getUserSharedPrefs(context) }
    
    val dashboardState by viewModel.dashboardState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val scrollState = rememberScrollState()

    // Custom Targets Overrides State
    var customMacroEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("custom_macro_enabled", false)) }
    var customProtein by remember { mutableStateOf(sharedPrefs.getInt("custom_protein_g", 130).toString()) }
    var customCarbs by remember { mutableStateOf(sharedPrefs.getInt("custom_carbs_g", 200).toString()) }
    var customFat by remember { mutableStateOf(sharedPrefs.getInt("custom_fat_g", 70).toString()) }
    var customCalories by remember { mutableStateOf(sharedPrefs.getInt("custom_calories", 2000).toString()) }

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.checkPermissions()
        viewModel.syncTelemetry()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SETTINGS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SuperfitTheme.colors.bgStart, SuperfitTheme.colors.bgEnd)
                )
            )
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECTION 1: Gemini API
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Gemini API Configuration",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )
                    var keyVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        label = { Text("Gemini API Key", color = SuperfitTheme.colors.textSecondary) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = if (keyVisible) "Hide API Key" else "Show API Key",
                                    tint = SuperfitTheme.colors.textTertiary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SuperfitTheme.colors.secondaryAccent,
                            unfocusedBorderColor = SuperfitTheme.colors.textTertiary,
                            focusedLabelColor = SuperfitTheme.colors.secondaryAccent,
                            unfocusedLabelColor = SuperfitTheme.colors.textSecondary,
                            focusedTextColor = SuperfitTheme.colors.textPrimary,
                            unfocusedTextColor = SuperfitTheme.colors.textPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Providing your Gemini API Key allows natural language meal logging to run locally. If the default key has exceeded its quota, please enter your own API Key from Google AI Studio.",
                        fontSize = 11.sp,
                        color = SuperfitTheme.colors.textTertiary
                    )
                }
            }

            // SECTION 2: Google Health Connect
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
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
                            text = "Google Health Connect",
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textPrimary,
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hasHealthConnectPermissions) NeonMint.copy(alpha = 0.15f)
                                    else CoralRed.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasHealthConnectPermissions) "Connected 🟢" else "Disconnected 🔴",
                                color = if (hasHealthConnectPermissions) NeonMint else CoralRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = if (hasHealthConnectPermissions) {
                            "Telemetry sync active. Superfit is automatically reading step counts and sleep durations from Google Health Connect."
                        } else {
                            "Telemetry sync paused. Grant permissions to enable automatic step count and sleep duration tracking."
                        },
                        fontSize = 11.sp,
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
                                    requestPermissionsLauncher.launch(viewModel.healthConnectManager.permissions)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f) else NeonMint,
                                contentColor = if (hasHealthConnectPermissions) SuperfitTheme.colors.textPrimary else Color.Black
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (hasHealthConnectPermissions) "Manage Permissions" else "Grant Permissions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
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
                                text = "Sync Telemetry Now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
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
                        fontSize = 11.sp,
                        color = SuperfitTheme.colors.textTertiary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // SECTION 3: Fitness Goals and Targets Overrides
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Fitness Goal Target",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )

                    if (dashboardState is DashboardUiState.Success) {
                        val state = dashboardState as DashboardUiState.Success
                        val goals = listOf(
                            Triple("LOSE_WEIGHT", "Deficit", -500),
                            Triple("MAINTAIN", "Maintain", 0),
                            Triple("GAIN_MUSCLE", "Surplus", 300)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SuperfitTheme.colors.textPrimary.copy(alpha = 0.03f))
                                .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            goals.forEach { (goalKey, goalLabel, offset) ->
                                val isSelected = state.profile.goal == goalKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeonMint else Color.Transparent)
                                        .clickable {
                                            viewModel.updateGoal(goalKey, offset)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = goalLabel,
                                        color = if (isSelected) Color.Black else SuperfitTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Custom Macro Targets",
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Override computed recommended values",
                                color = SuperfitTheme.colors.textSecondary,
                                fontSize = 11.sp
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
                                OutlinedTextField(
                                    value = customCalories,
                                    onValueChange = { customCalories = it },
                                    label = { Text("Calories (kcal)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SuperfitTheme.colors.secondaryAccent,
                                        unfocusedBorderColor = SuperfitTheme.colors.textTertiary,
                                        focusedTextColor = SuperfitTheme.colors.textPrimary,
                                        unfocusedTextColor = SuperfitTheme.colors.textPrimary,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = customProtein,
                                    onValueChange = { customProtein = it },
                                    label = { Text("Protein (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SuperfitTheme.colors.secondaryAccent,
                                        unfocusedBorderColor = SuperfitTheme.colors.textTertiary,
                                        focusedTextColor = SuperfitTheme.colors.textPrimary,
                                        unfocusedTextColor = SuperfitTheme.colors.textPrimary,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = customCarbs,
                                    onValueChange = { customCarbs = it },
                                    label = { Text("Carbohydrates (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SuperfitTheme.colors.secondaryAccent,
                                        unfocusedBorderColor = SuperfitTheme.colors.textTertiary,
                                        focusedTextColor = SuperfitTheme.colors.textPrimary,
                                        unfocusedTextColor = SuperfitTheme.colors.textPrimary,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = customFat,
                                    onValueChange = { customFat = it },
                                    label = { Text("Fat (g)", color = SuperfitTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SuperfitTheme.colors.secondaryAccent,
                                        unfocusedBorderColor = SuperfitTheme.colors.textTertiary,
                                        focusedTextColor = SuperfitTheme.colors.textPrimary,
                                        unfocusedTextColor = SuperfitTheme.colors.textPrimary,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
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

                                    Toast.makeText(context, "Custom targets saved successfully!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Customized Targets", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // SECTION 4: App Appearance Theme Selector
            val isDark by ThemeConfig.isDarkTheme.collectAsState()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "App Appearance",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Toggle between Dark Mode and dynamic Glassmorphic Light Mode.",
                        fontSize = 11.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuperfitTheme.colors.textPrimary.copy(alpha = 0.04f))
                            .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dark Theme option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) SuperfitTheme.colors.primaryAccent else Color.Transparent)
                                .clickable {
                                    ThemeConfig.setThemeMode(context, true)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Dark Mode 🌙",
                                color = if (isDark) Color.White else SuperfitTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Light Theme option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!isDark) SuperfitTheme.colors.primaryAccent else Color.Transparent)
                                .clickable {
                                    ThemeConfig.setThemeMode(context, false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Light Mode ☀️",
                                color = if (!isDark) Color.White else SuperfitTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // SECTION 5: Onboarding Preview & Diagnostics
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Diagnostics & User Testing",
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Preview and test the onboarding flow (Health Connect settings, microphone permissions, BMR configuration, and Gemini key setups) for new users. Your existing meal and workout history will not be lost.",
                        fontSize = 11.sp,
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
                            text = "Preview Onboarding Flow",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SECTION 6: Account Actions
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                        RoundedCornerShape(20.dp)
                    )
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
                            text = "Sign Out",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralRed.copy(alpha = 0.15f),
                            contentColor = CoralRed
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Clear & Sign Out",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
