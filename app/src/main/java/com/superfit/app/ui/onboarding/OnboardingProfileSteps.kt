package com.superfit.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.theme.*
import com.superfit.app.ui.components.GoalPicker
import com.superfit.app.ui.components.SuperfitField

// 1. Welcome Step View (Step 0)
@Composable
internal fun WelcomeStep(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Easiest way to track your fitness & get personalized insights",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary,
        lineHeight = 30.sp
    )

    // Upfront Appearance Customizer segmented toggle slider
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Choose Appearance",
            fontSize = 12.sp,
            color = ThemeTextTertiary,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
        ) {
            val themeSliderOffset by animateFloatAsState(
                targetValue = if (isDarkTheme) 0f else 1f,
                label = "ThemeSliderPill"
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val halfWidth = maxWidth / 2
                Box(
                    modifier = Modifier
                        .offset(x = halfWidth * themeSliderOffset)
                        .width(halfWidth)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(HyperViolet)
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onThemeChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dark",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onThemeChange(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Light",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Simplified High-Impact Bullet highlights
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        FeatureItemBullet(icon = "🎙️", title = "Log by voice or photo", desc = "Say what you ate in your own words, or snap your plate.")
        FeatureItemBullet(icon = "🔒", title = "Private by design", desc = "Your data is never sold or used for ads. Delete it any time.")
        FeatureItemBullet(icon = "💸", title = "Free, no ads", desc = "Every feature, no paywall.")
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// 2. Physiology Metrics Step View (Step 1)
@Composable
internal fun PhysiologyStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    validationError: String?,
    onOpenCheatSheet: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Physiological Profile",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Enter your core physical metrics. We use these to calculate your baseline resting metabolism.",
        fontSize = 13.sp,
        color = ThemeTextTertiary,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sex Toggle Segment
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Biological Sex", fontSize = 12.sp, color = ThemeTextTertiary, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp))
            ) {
                val sexSliderOffset by animateFloatAsState(
                    targetValue = if (uiState.isMale) 0f else 1f,
                    label = "SexSliderPill"
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val halfWidth = maxWidth / 2
                    Box(
                        modifier = Modifier
                            .offset(x = halfWidth * sexSliderOffset)
                            .width(halfWidth)
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonGreen)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { viewModel.onSexChanged(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Male", color = if (uiState.isMale) Color.White else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { viewModel.onSexChanged(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Female", color = if (!uiState.isMale) Color.White else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Age field
        SuperfitField(
            value = uiState.age,
            onValueChange = { viewModel.onAgeChanged(it) },
            label = { Text("Age (Years)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Height field strictly in Centimeters with Live Foot/Inches conversion text below it
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Height", fontSize = 12.sp, color = ThemeTextTertiary, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Foot/Inches Helper",
                    fontSize = 12.sp,
                    color = HyperViolet,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenCheatSheet() }
                )
            }
            SuperfitField(
                value = uiState.height,
                onValueChange = { viewModel.onHeightChanged(it) },
                placeholder = { Text("e.g. 175") },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            // Live Feet/Inches equivalent readout calculation
            val heightDouble = uiState.height.toDoubleOrNull()
            val ftInReadout = if (heightDouble != null && heightDouble > 0) {
                val totalInches = heightDouble / 2.54
                val feet = (totalInches / 12).toInt()
                val inches = Math.round(totalInches % 12).toInt()
                val displayInches = if (inches == 12) 0 else inches
                val displayFeet = if (inches == 12) feet + 1 else feet
                "Equivalent: $displayFeet ft $displayInches in"
            } else {
                "Equivalent: - ft - in"
            }
            Text(text = ftInReadout, color = com.superfit.app.theme.ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Weight field
        SuperfitField(
            value = uiState.weight,
            onValueChange = { viewModel.onWeightChanged(it) },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (validationError != null) {
        Text(text = validationError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// 3. Baseline Activity & Goal selection (Step 2)
@Composable
internal fun ActivityGoalsStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onOpenTdeePopup: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Baseline Activity",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Configure your baseline activity level and goals. We use these to calculate your target calorie budget.",
        fontSize = 13.sp,
        color = ThemeTextTertiary,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activity Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val multiplierDesc = when (uiState.activityMultiplier) {
                1.2 -> "Sedentary (No formal exercise)"
                1.375 -> "Lightly Active (1-3 days/week)"
                1.55 -> "Moderately Active (3-5 days/week)"
                1.725 -> "Very Active (6-7 days/week)"
                else -> "Active Athlete"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Activity Level", fontSize = 12.sp, color = ThemeTextTertiary, fontWeight = FontWeight.SemiBold)
                Text(text = "${uiState.activityMultiplier}", fontSize = 12.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
            }
            Text(text = multiplierDesc, fontSize = 14.sp, color = ThemeTextPrimary, fontWeight = FontWeight.Bold)

            val values = listOf(1.2, 1.375, 1.55, 1.725)
            Slider(
                value = values.indexOf(uiState.activityMultiplier).toFloat().coerceAtLeast(0f),
                onValueChange = { index ->
                    val idx = index.toInt().coerceIn(0, values.size - 1)
                    viewModel.onActivityMultiplierChanged(values[idx])
                },
                valueRange = 0f..3f,
                steps = 2,
                colors = SliderDefaults.colors(
                    activeTrackColor = NeonGreen,
                    thumbColor = NeonGreen,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        // Goals Cards selection list
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Your goal", fontSize = 12.sp, color = ThemeTextTertiary, fontWeight = FontWeight.SemiBold)

            GoalPicker(
                selected = uiState.goal,
                onSelect = { goal -> viewModel.onGoalChanged(goal.key, goal.calorieOffset) }
            )
        }

        // Live Mifflin-St Jeor TDEE calculation Ticker
        val ageVal = uiState.age.toIntOrNull() ?: 28
        val heightVal = uiState.height.toDoubleOrNull() ?: 175.0
        val weightVal = uiState.weight.toDoubleOrNull() ?: 75.0
        val bmr = if (uiState.isMale) {
            10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal + 5.0
        } else {
            10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal - 161.0
        }
        val tdeeVal = bmr * uiState.activityMultiplier

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(com.superfit.app.theme.ElectricCyan.copy(alpha = 0.06f), HyperViolet.copy(alpha = 0.06f))))
                .border(1.dp, com.superfit.app.theme.ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Daily TDEE", color = com.superfit.app.theme.ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "What is this?",
                        fontSize = 12.sp,
                        color = HyperViolet,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onOpenTdeePopup() }
                    )
                }
                Text(text = "${Math.round(tdeeVal)} kcal", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ThemeTextPrimary)
            }
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
