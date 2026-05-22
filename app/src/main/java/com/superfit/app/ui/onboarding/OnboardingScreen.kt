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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.superfit.app.theme.*

// Premium Color System mapped to the unified brand palette
private val NeonGreen = NeonMint
private val GradientStart = NeonMint
private val GradientEnd = HyperViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    healthConnectManager: HealthConnectManager,
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
        if (granted.isNotEmpty()) {
            viewModel.autofillFromHealthConnect()
        }
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
        // Ambient Aurora background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HyperViolet.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.minDimension * 0.8f
                ),
                radius = size.minDimension * 0.8f,
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(com.superfit.app.theme.ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.5f),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width, size.height * 0.5f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App title / branding
            Text(
                text = "SUPERFIT",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Text(
                text = "Setup your physiological baseline to calculate TDEE & dynamically adjust recovery macros.",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            // Health Connect Integration Panel
            Box(
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
                    .padding(16.dp)
            ) {
                val hasAnyPermissions = remember(uiState.grantedPermissions) {
                    uiState.grantedPermissions.isNotEmpty()
                }
                val connectionStatus = remember(uiState.grantedPermissions) {
                    when {
                        uiState.grantedPermissions.isEmpty() -> "Disconnected"
                        uiState.hasHealthConnectPermissions -> "Fully Connected"
                        else -> "Partially Connected (${uiState.grantedPermissions.size}/${healthConnectManager.permissions.size})"
                    }
                }
                val statusColor = remember(uiState.grantedPermissions) {
                    when {
                        uiState.grantedPermissions.isEmpty() -> Color.Gray
                        uiState.hasHealthConnectPermissions -> NeonGreen
                        else -> ElectricCyan
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Text(
                                text = "Health Connect",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = connectionStatus,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Superfit auto-fills your profile and syncs step/sleep telemetry using Google Health Connect.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )

                    Button(
                        onClick = {
                            if (hasAnyPermissions) {
                                viewModel.autofillFromHealthConnect()
                            } else {
                                requestPermissionsLauncher.launch(healthConnectManager.permissions)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.15f),
                            contentColor = ElectricCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isAutofilling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ElectricCyan
                            )
                        } else {
                            val buttonText = when {
                                uiState.hasHealthConnectPermissions -> "Auto-fill Profile Data"
                                hasAnyPermissions -> "Auto-fill (Partial)"
                                else -> "Connect & Auto-fill"
                            }
                            Text(
                                text = buttonText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (hasAnyPermissions && !uiState.hasHealthConnectPermissions) {
                        Text(
                            text = "Missing some telemetry permissions. Tap here to grant remaining access for sleep/steps.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    requestPermissionsLauncher.launch(healthConnectManager.permissions)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Input Form Card
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
                    text = "Physiology Metrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                // Age input
                OutlinedTextField(
                    value = uiState.age,
                    onValueChange = { viewModel.onAgeChanged(it) },
                    label = { Text("Age (years)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Height input
                OutlinedTextField(
                    value = uiState.height,
                    onValueChange = { viewModel.onHeightChanged(it) },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Weight input
                OutlinedTextField(
                    value = uiState.weight,
                    onValueChange = { viewModel.onWeightChanged(it) },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Biological Sex Segmented Switch
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Biological Sex",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )

                    val maleBgColor by animateColorAsState(
                        targetValue = if (uiState.isMale) NeonGreen else Color.Transparent,
                        label = "MaleBgColor"
                    )
                    val maleTextColor by animateColorAsState(
                        targetValue = if (uiState.isMale) Color.Black else Color.White,
                        label = "MaleTextColor"
                    )
                    val femaleBgColor by animateColorAsState(
                        targetValue = if (!uiState.isMale) NeonGreen else Color.Transparent,
                        label = "FemaleBgColor"
                    )
                    val femaleTextColor by animateColorAsState(
                        targetValue = if (!uiState.isMale) Color.Black else Color.White,
                        label = "FemaleTextColor"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(maleBgColor)
                                .align(Alignment.CenterVertically)
                                .BoxClickable { viewModel.onSexChanged(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Male",
                                color = maleTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(femaleBgColor)
                                .align(Alignment.CenterVertically)
                                .BoxClickable { viewModel.onSexChanged(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Female",
                                color = femaleTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Activity Multiplier Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val multiplierDesc = when (uiState.activityMultiplier) {
                        1.2 -> "Sedentary (No formal exercise)"
                        1.375 -> "Lightly Active (1-3 days/week)"
                        1.55 -> "Moderately Active (3-5 days/week)"
                        1.725 -> "Very Active (6-7 days/week)"
                        else -> "Custom / High Athlete"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Baseline Activity Level",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Multiplier: ${uiState.activityMultiplier}",
                            fontSize = 12.sp,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = multiplierDesc,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    val multiplierValues = listOf(1.2, 1.375, 1.55, 1.725)
                    Slider(
                        value = multiplierValues.indexOf(uiState.activityMultiplier).toFloat().coerceAtLeast(0f),
                        onValueChange = { index ->
                            val cleanIndex = index.toInt().coerceIn(0, multiplierValues.size - 1)
                            viewModel.onActivityMultiplierChanged(multiplierValues[cleanIndex])
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
            }

            // Error display
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Save / Proceed Button
            Button(
                onClick = { viewModel.saveProfile(onOnboardingComplete) },
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
                        text = "Calculate BMR & Start Tracking",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// Utility extension for clickable Boxes without ripple (fits premium aesthetic)
@Composable
private fun Modifier.BoxClickable(onClick: () -> Unit): Modifier {
    return this.background(Color.Transparent).then(
        Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
}

// Re-expose standard clickable for ease of implementation
