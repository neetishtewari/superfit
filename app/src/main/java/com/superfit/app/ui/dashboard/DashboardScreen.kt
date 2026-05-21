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
import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import android.text.format.DateUtils

// Design tokens matching premium aesthetic
private val DarkBg = Color(0xFF070709)
private val CardBg = Color(0xFF101014)
private val NeonGreen = Color(0xFF10B981)
private val ElectricCyan = Color(0xFF06B6D4)
private val EnergeticCoral = Color(0xFFFF5E7E)
private val CarbYellow = Color(0xFFFBBF24)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val parsingState by viewModel.parsingState.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val grantedPermissions by viewModel.grantedPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
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
            .background(DarkBg)
    ) {
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
                        }
                    }

                    // Circular Activity & Target Rings
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(24.dp)
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

                    // Nutrition & Dynamic Macros Budget Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBg)
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
                            unit = "g"
                        )

                        // Carbs
                        MacroProgressBar(
                            label = "Carbohydrates",
                            eaten = state.carbsEaten,
                            target = state.macroTargets.carbsG,
                            color = CarbYellow,
                            unit = "g"
                        )

                        // Fat
                        MacroProgressBar(
                            label = "Fats",
                            eaten = state.fatEaten,
                            target = state.macroTargets.fatG,
                            color = ElectricCyan,
                            unit = "g"
                        )
                    }

                    // Sleep & Readiness Recovery Dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBg)
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
                            .background(CardBg)
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

            // Progress Arcs
            drawArc(
                color = NeonGreen,
                startAngle = -90f,
                sweepAngle = (caloriesEatenProgress * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius1, center.y - radius1),
                size = Size(radius1 * 2, radius1 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = ElectricCyan,
                startAngle = -90f,
                sweepAngle = (stepsProgress * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius2, center.y - radius2),
                size = Size(radius2 * 2, radius2 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = EnergeticCoral,
                startAngle = -90f,
                sweepAngle = (activeBurnProgress * 360f).coerceAtLeast(1f),
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
    unit: String
) {
    val progress = if (target > 0.0) (eaten / target).toFloat().coerceIn(0f, 1f) else 0f
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.SemiBold
            )
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
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
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
            .background(Color.Black)
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
            .background(Color.Black)
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
