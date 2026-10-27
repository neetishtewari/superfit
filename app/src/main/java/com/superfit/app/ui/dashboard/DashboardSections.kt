package com.superfit.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.CoachPatternEngine
import com.superfit.app.domain.MealTags
import com.superfit.app.theme.*
import com.superfit.app.ui.components.SuperfitField
import java.time.LocalDate

@Composable
internal fun DashboardHeader(
    grantedPermissions: Set<String>,
    hasHealthConnectPermissions: Boolean,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val today = LocalDate.now()
    val dateText = remember(today) {
        today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
    }
    val greeting = when (java.time.LocalTime.now().hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    // Only point at Settings when Health Connect needs attention.
    val needsHealthConnect = grantedPermissions.isNotEmpty() && !hasHealthConnectPermissions

    // Date and the two buttons share the top line, so the greeting gets the full width under them.
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelLarge,
                color = ThemeTextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            HeaderNavButton(icon = Icons.Default.Insights, label = "Insights", onClick = onNavigateToHistory)
            Box {
                HeaderNavButton(icon = Icons.Default.Settings, label = "Settings", onClick = onNavigateToSettings)
                if (needsHealthConnect) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ThemeErrorColor)
                    )
                }
            }
        }
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = ThemeTextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun HeaderNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    // Compact: icon over a small label, so the date beside it keeps its room.
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .widthIn(min = 52.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ThemeCardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ThemeTextPrimary, modifier = Modifier.size(20.dp))
        }
        Text(text = label, fontSize = 11.sp, color = ThemeTextSecondary, maxLines = 1)
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                modifier = Modifier.size(150.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val fmt = java.text.NumberFormat.getIntegerInstance()
                RingLegendItem(
                    label = "Eaten, kcal",
                    value = "${fmt.format(state.caloriesEaten.toInt())} of ${fmt.format(caloriesTarget.toInt())}",
                    color = NeonGreen
                )
                RingLegendItem(
                    label = "Steps",
                    value = "${fmt.format(state.activity.steps)} of 10,000",
                    color = ElectricCyan
                )
                workoutTargetLine(caloriesTarget, state.workoutTargetKcal, state.workoutTargetSource)?.let {
                    Text(text = it, fontSize = 12.sp, color = ThemeTextSecondary)
                }
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
            text = "Today's nutrients",
            style = MaterialTheme.typography.titleLarge,
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
            color = NeonGreen,
            unit = "g",
            entries = state.nutritionList,
            macroSelector = { it.carbsG }
        )

        // Fat
        MacroProgressBar(
            label = "Fats",
            eaten = state.fatEaten,
            target = state.macroTargets.fatG,
            color = NeonGreen,
            unit = "g",
            entries = state.nutritionList,
            macroSelector = { it.fatG }
        )

        // Fibre: estimated per meal from the AI's tags (or a guess from the name for older meals).
        val fibreEaten = remember(state.nutritionList) { state.nutritionList.sumOf { MealTags.of(it).fiberG } }
        MacroProgressBar(
            label = "Fibre",
            eaten = fibreEaten,
            target = CoachPatternEngine.fibreTargetG(state.macroTargets.calories),
            color = NeonGreen,
            unit = "g",
            entries = state.nutritionList,
            macroSelector = { MealTags.of(it).fiberG }
        )
    }
}

@Composable
internal fun CoachInsightsCard(
    coachingState: CoachingInsightState,
    onRefresh: () -> Unit,
    onOpenChat: () -> Unit,
    onRoast: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCoachSoft)
            .border(1.dp, HyperViolet.copy(alpha = if (SuperfitTheme.isDark) 0.35f else 0.12f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.superfit.app.ui.components.IconBadge(
                    icon = Icons.Default.AutoAwesome,
                    background = HyperViolet,
                    tint = Color.White
                )
                Text(
                    text = "AI coach",
                    style = MaterialTheme.typography.titleLarge,
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
                    contentColor = ThemeTertiaryAccent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "New tip"
                )
            }
        }

        when (val cState = coachingState) {
            CoachingInsightState.Idle -> {
                Text(
                    text = "Your coach looks at your last few weeks of meals, steps, sleep and workouts and tells you the one thing that matters most right now.",
                    color = ThemeTextTertiary,
                    fontSize = 13.sp
                )
                TextButton(onClick = onRefresh) {
                    Text("Get today's tip", color = ThemeTertiaryAccent, fontWeight = FontWeight.Bold)
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
                            color = ThemeTertiaryAccent,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Looking over your last few weeks...",
                            color = ThemeTextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            is CoachingInsightState.Success -> {
                val insight = cState.insight
                var showWhy by remember(insight) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = boldMarkdown(insight.headline),
                        color = ThemeTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                    if (insight.action.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeCardBg)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Today",
                                color = ThemeTertiaryAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = boldMarkdown(insight.action),
                                color = ThemeTextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                    if (insight.why.isNotEmpty()) {
                        Text(
                            text = if (showWhy) "Hide why" else "Why?",
                            color = ThemeTertiaryAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showWhy = !showWhy }
                                .padding(vertical = 4.dp)
                        )
                        if (showWhy) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                insight.why.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        color = ThemeTextTertiary,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                    if (insight.note != null) {
                        Text(
                            text = "Couldn't reach the AI just now, so this is a plain summary. ${insight.note}",
                            color = ThemeTextTertiary,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
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
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnergeticCoral.copy(alpha = 0.15f),
                            contentColor = EnergeticCoral
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = "Try again",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // Chat is always one tap away, whatever state the tip is in. In Roast me style, so is the roast.
        // Wraps the roast button onto its own line when both don't fit, instead of cutting it off.
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenChat,
                colors = ButtonDefaults.buttonColors(containerColor = HyperViolet, contentColor = Color.White),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chat with coach", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            if (onRoast != null) {
                Button(
                    onClick = onRoast,
                    colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = Color.White),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Roast", style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
    onViewAllFoods: () -> Unit
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
            text = "Today's meals",
            style = MaterialTheme.typography.titleLarge,
            color = ThemeTextPrimary
        )

        val submitMeal = {
            if (foodInputText.isNotBlank()) {
                viewModel.parseAndAddMeal(foodInputText)
                onFoodInputChange("")
            }
        }
        SuperfitField(
            value = foodInputText,
            onValueChange = onFoodInputChange,
            placeholder = { Text("Type what you ate, e.g. 2 eggs and a banana") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submitMeal() }),
            trailingIcon = {
                if (parsingState == ParsingState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NeonGreen, strokeWidth = 2.dp)
                } else {
                    com.superfit.app.ui.components.FieldActionButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Log this meal",
                        enabled = foodInputText.isNotBlank(),
                        onClick = submitMeal,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        )

        // Suggestions chips
        val predictedFoods by viewModel.predictedFoods.collectAsState()
        if (predictedFoods.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Your usual foods",
                    fontSize = 12.sp,
                    color = ThemeTextTertiary,
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
                                    fontSize = 12.sp,
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
                                    text = "See all",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
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
                // Re-sends what the user last said or typed, so they don't have to say it again.
                if (viewModel.canRetryLastMeal) {
                    Button(
                        onClick = { viewModel.retryLastMeal() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnergeticCoral.copy(alpha = 0.15f),
                            contentColor = EnergeticCoral
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = "Try again",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Meals logged today list
        Text(
            text = "Logged so far",
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
            var editingMeal by remember { mutableStateOf<NutritionEntryEntity?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.nutritionList.forEach { entry ->
                    MealItemRow(
                        entry = entry,
                        onDelete = { viewModel.deleteMeal(entry) },
                        onClick = { editingMeal = entry }
                    )
                }
            }
            editingMeal?.let { meal ->
                MealEditSheet(
                    entry = meal,
                    onSave = {
                        viewModel.updateMeal(it)
                        editingMeal = null
                    },
                    onDismiss = { editingMeal = null }
                )
            }
        }
    }
}

@Composable
internal fun ActivityPerformanceCard(
    state: DashboardUiState.Success,
    vsUsual: com.superfit.app.domain.TodayVsUsual? = null,
    onDeleteWorkout: (WorkoutEntryEntity) -> Unit,
    onHideImportedWorkout: (com.superfit.app.domain.WorkoutView) -> Unit = {},
    workoutImportEnabled: Boolean = false,
    workoutImportRunning: Boolean = false,
    onShareWorkout: (com.superfit.app.domain.ShareCard) -> Unit = {}
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
        Text(
            text = "Today's activity",
            style = MaterialTheme.typography.titleLarge,
            color = ThemeTextPrimary
        )

        val today = vsUsual ?: com.superfit.app.domain.TodayVsUsual.of(LocalDate.now(), state.activity.steps, emptyList(), null)
        val usual = today.usual
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TodayTile(
                    label = "Steps",
                    value = String.format(java.util.Locale.US, "%,d", today.steps),
                    usual = usual?.let { String.format(java.util.Locale.US, "%,d", it.steps) },
                    ahead = usual != null && usual.steps > 0 && today.steps >= usual.steps,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                TodayTile(
                    label = "Distance (est.)",
                    value = String.format(java.util.Locale.US, "%.1f km", today.distanceKm),
                    usual = usual?.let { String.format(java.util.Locale.US, "%.1f km", it.distanceKm) },
                    ahead = usual != null && usual.distanceKm > 0 && today.distanceKm >= usual.distanceKm,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TodayTile(
                    label = "Move time",
                    value = "${today.moveMinutes} min",
                    usual = usual?.let { "${it.moveMinutes} min" },
                    ahead = usual != null && usual.moveMinutes > 0 && today.moveMinutes >= usual.moveMinutes,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                TodayTile(
                    label = "Workout burn",
                    value = "${today.workoutKcal.toInt()} kcal",
                    usual = usual?.let { "${it.workoutKcal.toInt()} kcal" },
                    ahead = usual != null && usual.workoutKcal > 0 && today.workoutKcal >= usual.workoutKcal,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            today.coachLine(java.time.LocalTime.now().hour)?.let { line ->
                Text(text = line, fontSize = 13.sp, color = ThemeTextPrimary, lineHeight = 18.sp)
            }
            if (usual == null) {
                Text(
                    text = "A couple more weeks of data and Superfit will compare each day with your usual for that weekday.",
                    fontSize = 12.sp,
                    color = ThemeTextSecondary
                )
            }
        }

        if (workoutImportEnabled) {
            Text(
                text = when {
                    workoutImportRunning -> "Bringing in your workouts from other apps…"
                    state.importedWorkoutCount == 0 -> "No workouts from other apps yet. They appear here once Samsung Health, Strava or your watch shares them with Health Connect."
                    else -> "Workouts from other apps appear here on their own. Older ones are in History."
                },
                fontSize = 12.sp,
                color = ThemeTextSecondary
            )
        }

        if (state.workoutList.isNotEmpty() || state.importedWorkouts.isNotEmpty()) {
            HorizontalDivider(
                color = ThemeTextPrimary.copy(alpha = 0.05f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = "Today's workouts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeTextTertiary,
                letterSpacing = 0.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.importedWorkouts.forEach { workout ->
                    ImportedWorkoutRow(
                        workout = workout,
                        onHide = { onHideImportedWorkout(workout) },
                        onShare = { onShareWorkout(com.superfit.app.domain.ShareCards.activity(workout)) }
                    )
                }
                state.workoutList.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThemeTextPrimary.copy(alpha = 0.03f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTextPrimary
                            )
                            val subText = if (entry.workoutType == "Strength") {
                                "${entry.setsCount} sets × ${entry.repsCount} reps · you logged this"
                            } else {
                                "Cardio · you logged this"
                            }
                            Text(
                                text = subText,
                                fontSize = 12.sp,
                                color = ThemeTextTertiary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Text(
                                text = "~${entry.caloriesBurned.toInt()} kcal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            IconButton(
                                onClick = { onShareWorkout(com.superfit.app.domain.ShareCards.activity(entry)) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share workout",
                                    tint = ThemeTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteWorkout(entry) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete workout",
                                    tint = DeleteRed,
                                    modifier = Modifier.size(20.dp)
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
                text = "Sleep",
                style = MaterialTheme.typography.titleLarge,
                color = ThemeTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            // Status Pill: only when there's sleep to base it on, otherwise the score is a guess.
            if (state.sleep != null) {
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
                        text = when (state.readinessScore) {
                            in 85..100 -> "Ready to train"
                            in 70..84 -> "Normal day"
                            in 50..69 -> "Go easy"
                            else -> "Rest day"
                        },
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
        }

        val readinessDesc = when (state.readinessScore) {
            in 85..100 -> "Well rested. A good day for a harder workout."
            in 70..84 -> "Rested enough for your usual training."
            in 50..69 -> "Short on sleep. A walk or light session is a better fit today."
            else -> "Very little sleep. Rest today if you can."
        }

        if (state.sleep != null) {
            Text(
                text = readinessDesc,
                fontSize = 13.sp,
                color = ThemeTextSecondary
            )

            val hours = state.sleep.sleepDurationSeconds / 3600
            val minutes = (state.sleep.sleepDurationSeconds % 3600) / 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SleepMetricPill(
                    label = "Slept",
                    value = "${hours}h ${minutes}m",
                    modifier = Modifier.weight(1.0f)
                )
                val deepHours = state.sleep.deepSleepDurationSeconds / 3600
                val deepMins = (state.sleep.deepSleepDurationSeconds % 3600) / 60
                SleepMetricPill(
                    label = "Est. deep sleep",
                    value = "${deepHours}h ${deepMins}m",
                    modifier = Modifier.weight(1.0f)
                )
            }
        } else {
            Text(
                text = "Your sleep shows here if you track it with a watch, ring or sleep app that saves to Health Connect. " +
                    "Once it does, Superfit uses it to tell you when to push and when to take it easy.",
                fontSize = 13.sp,
                color = ThemeTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

/** Gemini marks key phrases as **bold**; show them bold instead of with raw asterisks. */
internal fun boldMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    appendBold(text)
}

/**
 * Coach chat replies can also use markdown bullets ("* ", "- ") and headings ("## ");
 * show those as plain bullets and bold lines rather than raw symbols.
 */
internal fun chatMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { i, rawLine ->
        if (i > 0) append("\n")
        val indent = rawLine.takeWhile { it == ' ' }
        val line = rawLine.trimStart()
        when {
            line.startsWith("#") -> {
                val heading = line.trimStart('#').trim().removeSurrounding("**")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(heading) }
            }
            line.startsWith("* ") || line.startsWith("- ") -> {
                append(indent)
                append("• ")
                appendBold(line.substring(2))
            }
            else -> appendBold(rawLine)
        }
    }
}

private fun AnnotatedString.Builder.appendBold(text: String) {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        // Odd-numbered parts sit between a pair of ** markers. A lone unmatched ** is dropped.
        if (index % 2 == 1 && index < parts.lastIndex) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
    }
}

/** One of Home's activity numbers, with this weekday's usual underneath. */
@Composable
private fun TodayTile(label: String, value: String, usual: String?, ahead: Boolean, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ThemeFieldBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = ThemeTextSecondary)
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = color)
        if (usual != null) {
            Text(
                text = if (ahead) "↑ usual $usual" else "Usual $usual",
                fontSize = 12.sp,
                color = if (ahead) NeonMint else ThemeTextSecondary
            )
        }
    }
}
