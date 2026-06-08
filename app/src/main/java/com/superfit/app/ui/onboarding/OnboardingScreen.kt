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
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    var showKeyInstructionsDialog by remember { mutableStateOf(false) }

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
        if (granted.isNotEmpty()) {
            viewModel.autofillFromHealthConnect()
        }
    }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMicPermissionStateChanged(isGranted)
        if (!isGranted) {
            Toast.makeText(context, "Opening Settings to enable microphone permission...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Please open Settings and grant microphone permissions manually.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto check microphone permission on entry
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionStateChanged(hasPermission)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ThemeBgStart, ThemeBgEnd)
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

        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThemeCardBgTranslucent)
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = ThemeTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit Onboarding Preview",
                    modifier = Modifier.size(20.dp)
                )
            }
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
                color = ThemeTextPrimary,
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
                                color = ThemeTextPrimary,
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

            // Voice Logging & AI Configuration Card
            Box(
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
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header row
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
                                text = "Voice & AI Setup",
                                fontWeight = FontWeight.Bold,
                                color = ThemeTextPrimary,
                                fontSize = 16.sp
                            )
                        }

                        // Status Badge
                        val isKeyConfigured = uiState.apiKey.trim().startsWith("AIzaSy") || uiState.apiKey.trim().startsWith("AQ.")
                        val micGranted = uiState.isMicPermissionGranted
                        val overallStatus = when {
                            isKeyConfigured && micGranted -> "Ready"
                            isKeyConfigured -> "Mic Missing"
                            micGranted -> "Key Missing"
                            else -> "Not Setup"
                        }
                        val statusColor = when {
                            isKeyConfigured && micGranted -> NeonGreen
                            isKeyConfigured || micGranted -> ElectricCyan
                            else -> Color.Gray
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = overallStatus,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Quick Voice Logging uses Gemini AI to parse spoken meals. Set up access below to get started.",
                        fontSize = 13.sp,
                        color = ThemeTextSecondary
                    )

                    // Step 1: Microphone permission request button
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.onMicPermissionStateChanged(true)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isMicPermissionGranted) NeonGreen.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f),
                            contentColor = if (uiState.isMicPermissionGranted) NeonGreen else ElectricCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (uiState.isMicPermissionGranted) "Microphone Access: Granted" else "Grant Microphone Access",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(
                    color = ThemeTextPrimary.copy(alpha = 0.05f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Step 2: Gemini API Key Setup
                    Text(
                        text = "Configure Gemini API Key",
                        fontWeight = FontWeight.Bold,
                        color = ThemeTextPrimary,
                        fontSize = 14.sp
                    )

                    var keyVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = { viewModel.onApiKeyChanged(it) },
                        label = { Text("Gemini API Key") },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = if (keyVisible) "Hide API Key" else "Show API Key",
                                    tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = ThemeGlassBorder,
                            focusedLabelColor = ElectricCyan,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = ThemeTextPrimary,
                            unfocusedTextColor = ThemeTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Clipboard Paste Helper
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                viewModel.onApiKeyChanged(clipText.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeTextPrimary.copy(alpha = 0.05f),
                            contentColor = ThemeTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Paste from Clipboard", fontSize = 12.sp)
                    }

                    // Only show "Get Free Key" button if a valid key is NOT configured
                    val isKeyWorking = uiState.apiKey.trim().startsWith("AIzaSy") || uiState.apiKey.trim().startsWith("AQ.")
                    if (!isKeyWorking) {
                        Button(
                            onClick = {
                                showKeyInstructionsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.1f),
                                contentColor = ElectricCyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Get Free Gemini Key (30 Seconds)", fontSize = 12.sp)
                        }
                    } else {
                        // Success Feedback
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Working key detected! Helper instructions hidden.",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Input Form Card
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
                    text = "Physiology Metrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ThemeTextPrimary
                )

                // Age input
                OutlinedTextField(
                    value = uiState.age,
                    onValueChange = { viewModel.onAgeChanged(it) },
                    label = { Text("Age (years)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = ThemeGlassBorder,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary
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
                        unfocusedBorderColor = ThemeGlassBorder,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary
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
                        unfocusedBorderColor = ThemeGlassBorder,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary
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
                        targetValue = if (uiState.isMale) Color.Black else ThemeTextPrimary,
                        label = "MaleTextColor"
                    )
                    val femaleBgColor by animateColorAsState(
                        targetValue = if (!uiState.isMale) NeonGreen else Color.Transparent,
                        label = "FemaleBgColor"
                    )
                    val femaleTextColor by animateColorAsState(
                        targetValue = if (!uiState.isMale) Color.Black else ThemeTextPrimary,
                        label = "FemaleTextColor"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ThemeTextPrimary.copy(alpha = 0.03f))
                            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp)),
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
                        color = ThemeTextPrimary,
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

                // Goal Selection Card Group
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Fitness Goal",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )

                    val goals = listOf(
                        Triple("LOSE_WEIGHT", "Weight Loss", -500),
                        Triple("MAINTAIN", "Maintenance", 0),
                        Triple("GAIN_MUSCLE", "Muscle Gain", 300)
                    )

                    goals.forEach { (goalKey, goalLabel, offset) ->
                        val isSelected = uiState.goal == goalKey
                        val borderAlpha by animateFloatAsState(targetValue = if (isSelected) 0.8f else 0.08f, label = "GoalBorderAlpha")
                        val bgAlpha by animateFloatAsState(targetValue = if (isSelected) 0.15f else 0.03f, label = "GoalBgAlpha")
                        val textColor by animateColorAsState(targetValue = if (isSelected) NeonGreen else ThemeTextPrimary, label = "GoalTextColor")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeTextPrimary.copy(alpha = bgAlpha))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonGreen.copy(alpha = borderAlpha) else ThemeGlassBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.onGoalChanged(goalKey, offset)
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = goalLabel,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    val desc = when (goalKey) {
                                        "LOSE_WEIGHT" -> "Calorie Deficit: -500 kcal"
                                        "MAINTAIN" -> "Energy Balance: 0 kcal"
                                        else -> "Calorie Surplus: +300 kcal"
                                    }
                                    Text(
                                        text = desc,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.onGoalChanged(goalKey, offset) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NeonGreen,
                                        unselectedColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
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

        if (showKeyInstructionsDialog) {
            AlertDialog(
                onDismissRequest = { showKeyInstructionsDialog = false },
                title = {
                    Text(
                        text = "🔑 Get Your Gemini API Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ThemeTextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "When you land on Google AI Studio in the browser, follow these simple steps to generate your free key:",
                            fontSize = 13.sp,
                            color = ThemeTextSecondary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InstructionStep(
                                number = "1",
                                text = "Click 'Create API Key' at the top-left or top-right of the screen."
                            )
                            InstructionStep(
                                number = "2",
                                text = "Select 'Create API key in new project' or select 'superfit'."
                            )
                            InstructionStep(
                                number = "3",
                                text = "Copy the generated key (usually starts with 'AIzaSy' or 'AQ.')."
                            )
                            InstructionStep(
                                number = "4",
                                text = "Return to Superfit and tap 'Paste from Clipboard'."
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showKeyInstructionsDialog = false
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser. Please visit: https://aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open AI Studio", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showKeyInstructionsDialog = false }
                    ) {
                        Text("Cancel", color = ThemeTextPrimary)
                    }
                },
                containerColor = ThemeCardBgTranslucent,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                        shape = RoundedCornerShape(28.dp)
                    )
            )
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

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(com.superfit.app.theme.ElectricCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = com.superfit.app.theme.ElectricCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = ThemeTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}
