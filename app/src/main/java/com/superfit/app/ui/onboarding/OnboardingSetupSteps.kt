package com.superfit.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.platform.LocalView
import android.view.View

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.superfit.app.theme.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalClipboardManager

// 4. Google Health Connect Sync Step (Step 3)
@Composable
internal fun HealthConnectStep(
    uiState: OnboardingUiState,
    healthConnectManager: HealthConnectManager,
    requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Set<String>>,
    onNext: () -> Unit
) {
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
        color = Color.Gray,
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
        Text(text = "Health Connect Status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeTextPrimary)
        
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
        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Text(text = "Privacy first. Health records stay strictly locally stored on-device.", fontSize = 11.sp, color = Color.Gray)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (isHcSynced) {
                    onNext()
                } else {
                    requestPermissionsLauncher.launch(healthConnectManager.permissions)
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
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

// 5. Gemini API Key Configuration Step (Step 4)
@Composable
internal fun GeminiApiKeyStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    validationError: String?,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    Text(
        text = "AI Assistant Setup",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Superfit uses Gemini for natural voice logging. Get a free API key in 2 clicks. Stored 100% securely on-device.",
        fontSize = 13.sp,
        color = Color.LightGray,
        lineHeight = 18.sp
    )

    // Simplified Setup Checklist guide
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.02f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepGuideItem(number = "1", text = "Tap 'Get Free API Key' to open Google AI Studio.")
        StepGuideItem(number = "2", text = "Tap 'Create API Key' in project and copy it.")
        StepGuideItem(number = "3", text = "Return here and tap 'Paste' to save key.")
    }

    var keyVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { viewModel.onApiKeyChanged(it) },
            label = { Text("Gemini API Key") },
            placeholder = { Text("Starts with AIzaSy or AQ...") },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Visibility", tint = Color.Gray)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = ThemeGlassBorder,
                focusedTextColor = ThemeTextPrimary,
                unfocusedTextColor = ThemeTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Clipboard Paste Helper
        val clipboardManager = LocalClipboardManager.current
        Button(
            onClick = {
                val pasted = clipboardManager.getText()?.text
                if (!pasted.isNullOrBlank()) {
                    viewModel.onApiKeyChanged(pasted.trim())
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = ThemeTextPrimary.copy(alpha = 0.05f),
                contentColor = ThemeTextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = "Paste Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (validationError != null) {
        Text(text = validationError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open Browser. Please visit: https://aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = com.superfit.app.theme.ElectricCyan.copy(alpha = 0.08f),
                contentColor = com.superfit.app.theme.ElectricCyan
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp).border(1.dp, com.superfit.app.theme.ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Text(text = "Get Free API Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                Text(text = "Save & Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        // Skip button completely omitted from setup step 4
    }
}

// 6. Microphone Permission Step View (Step 5)
@Composable
internal fun MicrophonePermissionStep(
    uiState: OnboardingUiState,
    micPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onNext: () -> Unit
) {
    val granted = uiState.isMicPermissionGranted
    Text(
        text = "Voice Permission",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Required to transcribe your speech when dictating meals. Say what you had, and AI does the calculations.",
        fontSize = 13.sp,
        color = Color.Gray,
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
        Text(text = "🎙️", fontSize = 42.sp)
        Text(text = "Microphone Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeTextPrimary)

        val badgeColor = if (granted) NeonGreen else Color.Gray
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (granted) "Granted" else "Not Granted",
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Superfit records audio only when you actively press the voice log button on your ledger. No background listening.",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (granted) {
                    onNext()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    text = if (granted) "Continue" else "Grant Microphone Access",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Skip / Grant Later", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

// 7. Onboarding Summary & Targets (Step 6)
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
        color = Color.Gray,
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
                    Text(text = "CALCULATED BMR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
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
                    Text(text = "DAILY BUDGET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
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
            ChecklistRow(label = "💚 Health Connect Integration", value = if (uiState.hasHealthConnectPermissions) "Active Sync" else "Not Configured", isOk = uiState.hasHealthConnectPermissions)
            ChecklistRow(label = "🔑 Gemini AI Parser key", value = if (uiState.apiKey.isNotEmpty()) "Configured" else "Not Configured", isOk = uiState.apiKey.isNotEmpty())
            ChecklistRow(label = "🎙️ Microphone Voice ledger", value = if (uiState.isMicPermissionGranted) "Access Granted" else "Not Granted", isOk = uiState.isMicPermissionGranted)
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
