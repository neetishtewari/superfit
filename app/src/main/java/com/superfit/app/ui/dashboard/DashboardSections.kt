package com.superfit.app.ui.dashboard


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.zIndex
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.*
import com.superfit.app.theme.*
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import android.text.format.DateUtils
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.net.Uri
import com.superfit.app.data.PredictedFood

@Composable
internal fun DashboardHeader(
    grantedPermissions: Set<String>,
    hasHealthConnectPermissions: Boolean,
    onSyncClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
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
                color = ThemeTextPrimary,
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
                onClick = onSyncClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ThemeCardBg,
                    contentColor = ThemeTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Telemetry"
                )
            }

            IconButton(
                onClick = onNavigateToHistory,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ThemeCardBg,
                    contentColor = ThemeTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "History Ledger"
                )
            }

            Box {
                IconButton(
                    onClick = onNavigateToSettings,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ThemeCardBg,
                        contentColor = ThemeTextPrimary
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
                        .background(ThemeCardBg)
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
}

@Composable
internal fun CalorieRingsCard(state: DashboardUiState.Success) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
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
            val stepsTarget = 10000.0
            val caloriesTarget = state.macroTargets.calories

            val caloriesProgress = (state.caloriesEaten / caloriesTarget).toFloat().coerceIn(0f, 1f)
            val stepsProgress = (state.activity.steps.toDouble() / stepsTarget).toFloat().coerceIn(0f, 1f)
            val caloriesRemaining = (caloriesTarget - state.caloriesEaten).toInt()

            ConcentricActivityRings(
                caloriesEatenProgress = caloriesProgress,
                stepsProgress = stepsProgress,
                caloriesRemaining = caloriesRemaining,
                modifier = Modifier.size(160.dp)
            )

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
            }
        }
    }
}

@Composable
internal fun NutrientLedgerCard(state: DashboardUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
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
            color = ThemeTextPrimary
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
}

@Composable
internal fun CoachInsightsCard(
    coachingState: CoachingInsightState,
    onRefresh: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
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
                    color = ThemeTextPrimary
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
                onClick = onRefresh,
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
                    onClick = onRefresh,
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = cState.insight,
                        color = ThemeTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Button(
                        onClick = onOpenChat,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chat with Coach 💬", fontWeight = FontWeight.Bold)
                    }
                }
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
                            onClick = onOpenSettings,
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
        }
    }
}

@Composable
internal fun TrackMealsCard(
    state: DashboardUiState.Success,
    viewModel: DashboardViewModel,
    parsingState: ParsingState,
    foodInputText: String,
    onFoodInputChange: (String) -> Unit,
    onViewAllFoods: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
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
            color = ThemeTextPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = foodInputText,
                onValueChange = onFoodInputChange,
                label = { Text("Log food in natural language...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = ThemeGlassBorder,
                    focusedTextColor = ThemeTextPrimary,
                    unfocusedTextColor = ThemeTextPrimary,
                    focusedLabelColor = NeonGreen,
                    unfocusedLabelColor = ThemeTextSecondary
                ),
                placeholder = { Text("e.g. 2 eggs and a banana") },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    if (foodInputText.isNotBlank()) {
                        viewModel.parseAndAddMeal(foodInputText)
                        onFoodInputChange("")
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

        // Suggestions chips
        val predictedFoods by viewModel.predictedFoods.collectAsState()
        if (predictedFoods.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Frequently Tracked (Tap to fill):",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(predictedFoods.take(6)) { predicted ->
                        SuggestionChip(
                            onClick = { onFoodInputChange(predicted.foodText) },
                            label = {
                                Text(
                                    text = predicted.foodText,
                                    color = ThemeTextPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = ThemeTextPrimary.copy(alpha = 0.05f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = ThemeGlassBorder
                            )
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = onViewAllFoods,
                            label = {
                                Text(
                                    text = "View All...",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = NeonGreen.copy(alpha = 0.1f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = NeonGreen.copy(alpha = 0.3f)
                            )
                        )
                    }
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
                        onClick = onOpenSettings,
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
            color = ThemeTextPrimary
        )

        if (state.nutritionList.isEmpty()) {
            Text(
                text = "No meals logged today yet.",
                fontSize = 13.sp,
                color = ThemeTextSecondary
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
}

@Composable
internal fun ActivityPerformanceCard(
    state: DashboardUiState.Success,
    onDeleteWorkout: (WorkoutEntryEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "Activity & Performance",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ThemeTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            val distanceKm = state.activity.steps * 0.00075
            Text(
                text = String.format("Estimated Distance: %.2f km", distanceKm),
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Steps Progress
        ActivityProgressBar(
            label = "Steps",
            current = state.activity.steps.toDouble(),
            target = 10000.0,
            color = ElectricCyan,
            unit = "steps"
        )

        if (state.workoutList.isNotEmpty()) {
            HorizontalDivider(
                color = ThemeTextPrimary.copy(alpha = 0.05f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = "TODAY'S WORKOUTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.workoutList.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThemeTextPrimary.copy(alpha = 0.03f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTextPrimary
                            )
                            val subText = if (entry.workoutType == "Strength") {
                                "Strength | ${entry.setsCount} sets x ${entry.repsCount} reps"
                            } else {
                                "Cardio"
                            }
                            Text(
                                text = subText,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "~${entry.caloriesBurned.toInt()} kcal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EnergeticCoral
                            )
                            IconButton(
                                onClick = { onDeleteWorkout(entry) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Workout",
                                    tint = EnergeticCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SleepRecoveryCard(state: DashboardUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
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
                color = ThemeTextPrimary
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
            color = ThemeTextSecondary
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
}
