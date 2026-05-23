package com.superfit.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.*
import com.superfit.app.theme.*
import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import android.text.format.DateUtils

// Design tokens matching premium aesthetic
private val DarkBg = DarkBgStart
private val NeonGreen = NeonMint
private val ElectricCyan = com.superfit.app.theme.ElectricCyan
private val EnergeticCoral = CoralRed
private val CarbYellow = SolarAmber
private val HyperVioletAccent = HyperViolet

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onLogout: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val parsingState by viewModel.parsingState.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val grantedPermissions by viewModel.grantedPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val coachingState by viewModel.coachingState.collectAsState()
    val scrollState = rememberScrollState()

    var showApiKeySettings by remember { mutableStateOf(false) }
    var foodInputText by remember { mutableStateOf("") }

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
        viewModel.syncTelemetry()
    }

    // Auto sync on entry
    LaunchedEffect(Unit) {
        viewModel.syncTelemetry()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DarkBgStart, DarkBgEnd)
                )
            )
    ) {
        if (dashboardState is DashboardUiState.Success) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(HyperVioletAccent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.minDimension * 0.8f
                    ),
                    radius = size.minDimension * 0.8f,
                    center = Offset(0f, 0f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.5f),
                        radius = size.minDimension * 0.7f
                    ),
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width, size.height * 0.5f)
                )
            }
        }

        when (val state = dashboardState) {
            DashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            }
            DashboardUiState.NotInitialized -> {
                LaunchedEffect(Unit) {
                    onNavigateToOnboarding()
                }
            }
            is DashboardUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SUPERFIT",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = LocalDate.now().toString(),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateToHistory,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = CardBg,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "History Ledger"
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showApiKeySettings = !showApiKeySettings },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = CardBg,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "API Settings"
                                    )
                                }

                                // Subtle Health Connect Status Badge Dot
                                val badgeColor = remember(grantedPermissions, hasHealthConnectPermissions) {
                                    when {
                                        grantedPermissions.isEmpty() -> Color.Gray
                                        hasHealthConnectPermissions -> NeonGreen
                                        else -> ElectricCyan
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 1.dp, y = (-1).dp)
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(DarkBg)
                                        .padding(1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeColor)
                                    )
                                }
                            }
                        }
                    }

                    // API Settings panel (collapsible)
                    AnimatedVisibility(visible = showApiKeySettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardBg)
                                .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Gemini API Configuration",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { viewModel.updateApiKey(it) },
                                label = { Text("Gemini API Key") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedLabelColor = ElectricCyan,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Providing your Gemini API Key allows natural language meal logging to run locally. If the default key has exceeded its quota, please enter your own API Key from Google AI Studio.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            HorizontalDivider(
                                color = Color.DarkGray.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Text(
                                text = "Fitness Goal Target",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )

                            val goals = listOf(
                                Triple("LOSE_WEIGHT", "Weight Loss (-500 kcal)", -500),
                                Triple("MAINTAIN", "Maintenance (0 kcal)", 0),
                                Triple("GAIN_MUSCLE", "Muscle Gain (+300 kcal)", 300)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                goals.forEach { (goalKey, goalLabel, offset) ->
                                    val isSelected = state.profile.goal == goalKey
                                    val bgActiveColor by animateColorAsState(
                                        targetValue = if (isSelected) NeonGreen else Color.Transparent,
                                        label = "SettingsGoalBg"
                                    )
                                    val textActiveColor by animateColorAsState(
                                        targetValue = if (isSelected) Color.Black else Color.White,
                                        label = "SettingsGoalText"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgActiveColor)
                                            .clickable {
                                                viewModel.updateGoal(goalKey, offset)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (goalKey) {
                                                "LOSE_WEIGHT" -> "Deficit"
                                                "MAINTAIN" -> "Maintain"
                                                else -> "Surplus"
                                            },
                                            color = textActiveColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = Color.DarkGray.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Text(
                                text = "Google Health Connect Status",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )

                            val connectionStatus = remember(grantedPermissions, hasHealthConnectPermissions) {
                                when {
                                    grantedPermissions.isEmpty() -> "Disconnected"
                                    hasHealthConnectPermissions -> "Fully Connected"
                                    else -> "Partially Connected (${grantedPermissions.size}/${viewModel.healthConnectManager.permissions.size})"
                                }
                            }
                            val statusColor = remember(grantedPermissions, hasHealthConnectPermissions) {
                                when {
                                    grantedPermissions.isEmpty() -> Color.Gray
                                    hasHealthConnectPermissions -> NeonGreen
                                    else -> ElectricCyan
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor)
                                    )
                                    Text(
                                        text = connectionStatus,
                                        color = statusColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!hasHealthConnectPermissions) {
                                    Button(
                                        onClick = {
                                            requestPermissionsLauncher.launch(viewModel.healthConnectManager.permissions)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan.copy(alpha = 0.15f),
                                            contentColor = ElectricCyan
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (grantedPermissions.isEmpty()) "Connect" else "Grant Rest",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { viewModel.syncTelemetry() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen.copy(alpha = 0.15f),
                                    contentColor = NeonGreen
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Text(
                                    text = "Sync Telemetry Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            if (lastSyncTime > 0L) {
                                val relativeTime = DateUtils.getRelativeTimeSpanString(
                                    lastSyncTime,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString()
                                Text(
                                    text = "Last synced: $relativeTime",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSignOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
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
                                        containerColor = EnergeticCoral.copy(alpha = 0.15f),
                                        contentColor = EnergeticCoral
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
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

                    // Circular Activity & Target Rings
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Concentric rings canvas
                            val stepsTarget = 10000.0
                            val caloriesTarget = state.macroTargets.calories
                            val activeCaloriesTarget = 500.0

                            val caloriesProgress = (state.caloriesEaten / caloriesTarget).toFloat().coerceIn(0f, 1f)
                            val stepsProgress = (state.activity.steps.toDouble() / stepsTarget).toFloat().coerceIn(0f, 1f)
                            val activeBurnProgress = (state.activity.activeCalories / activeCaloriesTarget).toFloat().coerceIn(0f, 1f)
                            val caloriesRemaining = (caloriesTarget - state.caloriesEaten).toInt()

                            ConcentricActivityRings(
                                caloriesEatenProgress = caloriesProgress,
                                stepsProgress = stepsProgress,
                                activeBurnProgress = activeBurnProgress,
                                caloriesRemaining = caloriesRemaining,
                                modifier = Modifier.size(160.dp)
                            )

                            // Ring descriptions/legends
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RingLegendItem(
                                    label = "Eaten",
                                    value = "${state.caloriesEaten.toInt()} / ${caloriesTarget.toInt()} kcal",
                                    color = NeonGreen
                                )
                                RingLegendItem(
                                    label = "Steps",
                                    value = "${state.activity.steps} / 10K",
                                    color = ElectricCyan
                                )
                                RingLegendItem(
                                    label = "Active Burn",
                                    value = "${state.activity.activeCalories.toInt()} / 500 kcal",
                                    color = EnergeticCoral
                                )
                            }
                        }
                    }

                    // Activity & Performance Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Activity & Performance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )

                            // Glassmorphic estimated distance pill
                            val distanceKm = state.activity.steps * 0.00075
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElectricCyan.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format("%.2f km (Est)", distanceKm),
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Steps Progress
                        ActivityProgressBar(
                            label = "Steps",
                            current = state.activity.steps.toDouble(),
                            target = 10000.0,
                            color = ElectricCyan,
                            unit = "steps"
                        )

                        // Active Energy Burn Progress
                        ActivityProgressBar(
                            label = "Active Energy Burned",
                            current = state.activity.activeCalories,
                            target = 500.0,
                            color = EnergeticCoral,
                            unit = "kcal"
                        )
                    }

                    // AI Daily Coach Insights Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "✨ AI Daily Coach",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }

                            val isCoachingLoading = coachingState is CoachingInsightState.Loading
                            val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotation")
                            val rotationAngle by if (isCoachingLoading) {
                                infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "Rotation"
                                )
                            } else {
                                remember { mutableStateOf(0f) }
                            }

                            IconButton(
                                onClick = { viewModel.refreshCoachingInsight() },
                                modifier = Modifier.rotate(rotationAngle),
                                enabled = !isCoachingLoading,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = ElectricCyan
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Insights"
                                )
                            }
                        }

                        when (val cState = coachingState) {
                            CoachingInsightState.Idle -> {
                                Text(
                                    text = "Generate your personalized AI coaching advice based on today's physical and nutritional telemetry.",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = { viewModel.refreshCoachingInsight() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan.copy(alpha = 0.15f),
                                        contentColor = ElectricCyan
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Analyze & Generate Insights", fontWeight = FontWeight.Bold)
                                }
                            }
                            CoachingInsightState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = ElectricCyan,
                                            strokeWidth = 3.dp
                                        )
                                        Text(
                                            text = "Analyzing physiological telemetry with Gemini...",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            is CoachingInsightState.Success -> {
                                Text(
                                    text = cState.insight,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            is CoachingInsightState.Error -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = EnergeticCoral
                                        )
                                        Text(
                                            text = cState.message,
                                            color = EnergeticCoral,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (cState.message.contains("Settings", ignoreCase = true) || cState.message.contains("API Key", ignoreCase = true)) {
                                        Button(
                                            onClick = { showApiKeySettings = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EnergeticCoral.copy(alpha = 0.15f),
                                                contentColor = EnergeticCoral
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = "Configure API Key",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Nutrition & Dynamic Macros Budget Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Daily Nutrient Ledger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        // Protein
                        MacroProgressBar(
                            label = "Protein",
                            eaten = state.proteinEaten,
                            target = state.macroTargets.proteinG,
                            color = NeonGreen,
                            unit = "g",
                            entries = state.nutritionList,
                            macroSelector = { it.proteinG }
                        )

                        // Carbs
                        MacroProgressBar(
                            label = "Carbohydrates",
                            eaten = state.carbsEaten,
                            target = state.macroTargets.carbsG,
                            color = CarbYellow,
                            unit = "g",
                            entries = state.nutritionList,
                            macroSelector = { it.carbsG }
                        )

                        // Fat
                        MacroProgressBar(
                            label = "Fats",
                            eaten = state.fatEaten,
                            target = state.macroTargets.fatG,
                            color = ElectricCyan,
                            unit = "g",
                            entries = state.nutritionList,
                            macroSelector = { it.fatG }
                        )
                    }

                    // Sleep & Readiness Recovery Dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sleep & Recovery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )

                            // Status Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (state.readinessScore) {
                                            in 85..100 -> NeonGreen.copy(alpha = 0.2f)
                                            in 70..84 -> ElectricCyan.copy(alpha = 0.2f)
                                            else -> EnergeticCoral.copy(alpha = 0.2f)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Readiness: ${state.readinessScore}%",
                                    color = when (state.readinessScore) {
                                        in 85..100 -> NeonGreen
                                        in 70..84 -> ElectricCyan
                                        else -> EnergeticCoral
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        val readinessDesc = when (state.readinessScore) {
                            in 85..100 -> "Optimum Recovery - Max training capacity authorized."
                            in 70..84 -> "Stable Homeostasis - Baseline performance capacity."
                            in 50..69 -> "Suboptimal Sleep - Focus on active recovery."
                            else -> "Critical Exhaustion - Rest day strongly recommended."
                        }

                        Text(
                            text = readinessDesc,
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )

                        if (state.sleep != null) {
                            val hours = state.sleep.sleepDurationSeconds / 3600
                            val minutes = (state.sleep.sleepDurationSeconds % 3600) / 60
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SleepMetricPill(
                                    label = "Duration",
                                    value = "${hours}h ${minutes}m",
                                    modifier = Modifier.weight(1.0f)
                                )
                                val deepHours = state.sleep.deepSleepDurationSeconds / 3600
                                val deepMins = (state.sleep.deepSleepDurationSeconds % 3600) / 60
                                SleepMetricPill(
                                    label = "Deep Sleep (Est)",
                                    value = "${deepHours}h ${deepMins}m",
                                    modifier = Modifier.weight(1.0f)
                                )
                            }
                        } else {
                            Text(
                                text = "No sleep record synced for today. Connect Google Sleep or record sleep details to enable active recovery shifting.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // Food Ledger & Real-time AI Entry Logger
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBgTranslucent)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Track Your Meals",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = foodInputText,
                                onValueChange = { foodInputText = it },
                                label = { Text("Log food in natural language...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                placeholder = { Text("e.g. 2 eggs and a banana") },
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    if (foodInputText.isNotBlank()) {
                                        viewModel.parseAndAddMeal(foodInputText)
                                        foodInputText = ""
                                    }
                                },
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                enabled = parsingState != ParsingState.Loading
                            ) {
                                if (parsingState == ParsingState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Track", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Display parsing state messages
                        AnimatedVisibility(visible = parsingState is ParsingState.Error) {
                            val errMsg = (parsingState as? ParsingState.Error)?.message ?: "Error"
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = EnergeticCoral
                                    )
                                    Text(
                                        text = errMsg,
                                        color = EnergeticCoral,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (errMsg.contains("Settings", ignoreCase = true)) {
                                    Button(
                                        onClick = { showApiKeySettings = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EnergeticCoral.copy(alpha = 0.15f),
                                            contentColor = EnergeticCoral
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "Open API Settings",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Meals logged today list
                        Text(
                            text = "Today's Ledger Entries",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )

                        if (state.nutritionList.isEmpty()) {
                            Text(
                                text = "No meals logged today yet.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.nutritionList.forEach { entry ->
                                    MealItemRow(
                                        entry = entry,
                                        onDelete = { viewModel.deleteMeal(entry) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

// Concentric Rings graphic
@Composable
fun ConcentricActivityRings(
    caloriesEatenProgress: Float,
    stepsProgress: Float,
    activeBurnProgress: Float,
    caloriesRemaining: Int,
    modifier: Modifier = Modifier
) {
    val caloriesAnimated by animateFloatAsState(
        targetValue = caloriesEatenProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "CaloriesProgress"
    )
    val stepsAnimated by animateFloatAsState(
        targetValue = stepsProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "StepsProgress"
    )
    val activeBurnAnimated by animateFloatAsState(
        targetValue = activeBurnProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ActiveBurnProgress"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val strokeWidth = 12.dp.toPx()
            val spacing = 12.dp.toPx()

            val radius1 = (size.minDimension / 2) - strokeWidth
            val radius2 = radius1 - strokeWidth - spacing
            val radius3 = radius2 - strokeWidth - spacing

            // Background tracks
            drawCircle(
                color = Color.DarkGray.copy(alpha = 0.15f),
                radius = radius1,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = Color.DarkGray.copy(alpha = 0.15f),
                radius = radius2,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = Color.DarkGray.copy(alpha = 0.15f),
                radius = radius3,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Progress Arcs with shadow glows
            val glowWidth = strokeWidth + 4.dp.toPx()

            // 1. Calories Eaten Arc
            drawArc(
                color = NeonGreen.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = (caloriesAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius1, center.y - radius1),
                size = Size(radius1 * 2, radius1 * 2),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = NeonGreen,
                startAngle = -90f,
                sweepAngle = (caloriesAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius1, center.y - radius1),
                size = Size(radius1 * 2, radius1 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Steps Arc
            drawArc(
                color = ElectricCyan.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = (stepsAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius2, center.y - radius2),
                size = Size(radius2 * 2, radius2 * 2),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ElectricCyan,
                startAngle = -90f,
                sweepAngle = (stepsAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius2, center.y - radius2),
                size = Size(radius2 * 2, radius2 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 3. Active Burn Arc
            drawArc(
                color = EnergeticCoral.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = (activeBurnAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius3, center.y - radius3),
                size = Size(radius3 * 2, radius3 * 2),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = EnergeticCoral,
                startAngle = -90f,
                sweepAngle = (activeBurnAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius3, center.y - radius3),
                size = Size(radius3 * 2, radius3 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$caloriesRemaining",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "kcal left",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Legend helper
@Composable
fun RingLegendItem(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Progress bar helper for macros
@Composable
fun MacroProgressBar(
    label: String,
    eaten: Double,
    target: Double,
    color: Color,
    unit: String,
    entries: List<NutritionEntryEntity> = emptyList(),
    macroSelector: (NutritionEntryEntity) -> Double = { 0.0 }
) {
    val progress = if (target > 0.0) (eaten / target).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "MacroProgress"
    )

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 8.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "${eaten.toInt()}${unit} / ${target.toInt()}${unit}",
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val contributingEntries = entries
                    .map { it to macroSelector(it) }
                    .filter { it.second > 0.0 }
                    .sortedByDescending { it.second }

                if (contributingEntries.isEmpty()) {
                    Text(
                        text = "No entries logged today containing $label.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    contributingEntries.forEach { (entry, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.foodText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${amount.toInt()}$unit",
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MealItemRow(
    entry: NutritionEntryEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodText,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${entry.calories.toInt()} kcal",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "P: ${entry.proteinG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = "C: ${entry.carbsG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = "F: ${entry.fatG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.DarkGray)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete entry",
                tint = EnergeticCoral.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ActivityProgressBar(
    label: String,
    current: Double,
    target: Double,
    color: Color,
    unit: String
) {
    val progress = if (target > 0.0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "ActivityProgress"
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${current.toInt()} / ${target.toInt()} $unit",
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }
    }
}
