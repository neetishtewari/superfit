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

// 4. Google Health Connect Sync Step (Step 3)
@Composable
internal fun HealthConnectStep(
    uiState: OnboardingUiState,
    healthConnectManager: HealthConnectManager,
    requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Set<String>>,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val isHcSynced = uiState.hasHealthConnectPermissions
    Text(
        text = "Google Health Sync",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Sync your baseline activity telemetry. Superfit reads steps, sleep cycles, and active workouts locally from your device.",
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "💚", fontSize = 42.sp)
        Text(text = "Health Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeTextPrimary)

        val badgeColor = if (isHcSynced) NeonGreen else Color.Gray
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isHcSynced) "Connected" else "Disconnected",
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Connecting allows Superfit to auto-calculate metabolic offsets from sleep quality and daily workouts.",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = ThemeTextTertiary, modifier = Modifier.size(16.dp))
        Text(text = "Your health data stays on your phone and in your private Superfit backup.", fontSize = 12.sp, color = ThemeTextTertiary)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (isHcSynced) {
                    onNext()
                } else {
                    com.superfit.app.ui.launchHealthConnectPermissions(context, requestPermissionsLauncher, healthConnectManager.permissions + healthConnectManager.bodyPermissions)
                }
            },
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
                Text(
                    text = if (isHcSynced) "Continue" else "Connect Google Health Connect",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isHcSynced) "Skip / Connect Later" else "Skip / Connect Later",
                color = ThemeTextTertiary,
                fontSize = 13.sp
            )
        }
    }
}

// 5. Onboarding Summary & Targets (Step 4)
@Composable
internal fun OnboardingSummaryStep(
    uiState: OnboardingUiState,
    onStartClick: () -> Unit
) {
    Text(
        text = "Activate Your AI Advisor",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Tracking your meals and workouts is now easier than ever! By logging your entries regularly, you provide richer, more complete data for analysis—making Superfit vastly more useful and accurate in helping you reach your fitness goals.",
        fontSize = 13.sp,
        color = ThemeTextTertiary,
        lineHeight = 18.sp
    )

    // Calculate final targets
    val ageVal = uiState.age.toIntOrNull() ?: 28
    val heightVal = uiState.height.toDoubleOrNull() ?: 175.0
    val weightVal = uiState.weight.toDoubleOrNull() ?: 75.0
    val bmr = if (uiState.isMale) {
        10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal + 5.0
    } else {
        10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal - 161.0
    }
    val tdee = bmr * uiState.activityMultiplier
    val calories = Math.max(1200, Math.round(tdee) + uiState.calorieOffset)

    // Recommended macros Target split
    val protein = Math.max(40, (weightVal * 2.0).toInt())
    val fat = Math.max(30, ((calories * 0.25) / 9.0).toInt())
    val proteinCalories = protein * 4.0
    val fatCalories = fat * 9.0
    val carbs = Math.max(50, ((calories - proteinCalories - fatCalories) / 4.0).toInt())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Cards grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.02f))
                    .border(1.5.dp, ThemeGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Resting burn", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThemeTextTertiary)
                    Text(text = "${Math.round(bmr)} kcal", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ThemeTextPrimary)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.02f))
                    .border(1.5.dp, ThemeGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Daily target", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThemeTextTertiary)
                    Text(text = "$calories kcal", fontSize = 18.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                }
            }
        }

        // Recommended macro targets list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ThemeCardBgTranslucent)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Macronutrient Target Split", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTextSecondary)

            MacroSplitRow(color = HyperViolet, label = "Protein", value = "${protein}g")
            MacroSplitRow(color = com.superfit.app.theme.ElectricCyan, label = "Carbohydrates", value = "${carbs}g")
            MacroSplitRow(color = NeonGreen, label = "Fat", value = "${fat}g")
        }

        // Checklist configurations
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ThemeCardBgTranslucent)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChecklistRow(label = "Health Connect", value = if (uiState.hasHealthConnectPermissions) "Active Sync" else "Not Configured", isOk = uiState.hasHealthConnectPermissions)
            ChecklistRow(label = "AI meal logging and coach", value = "Ready", isOk = true)
        }
    }

    Button(
        onClick = onStartClick,
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
            Text(text = "Activate Personal AI Coach", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
