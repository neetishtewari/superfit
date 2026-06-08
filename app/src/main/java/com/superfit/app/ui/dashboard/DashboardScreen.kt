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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
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

// Design tokens matching premium aesthetic
private val NeonGreen = NeonMint
private val ElectricCyan = com.superfit.app.theme.ElectricCyan
private val EnergeticCoral = CoralRed
private val CarbYellow = SolarAmber
private val HyperVioletAccent = HyperViolet

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    triggerVoiceLog: Boolean = false,
    triggerFavoritesLog: Boolean = false,
    onVoiceLogTriggeredHandled: () -> Unit = {},
    onFavoritesLogTriggeredHandled: () -> Unit = {}
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val parsingState by viewModel.parsingState.collectAsState()
    val workoutParsingState by viewModel.workoutParsingState.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val grantedPermissions by viewModel.grantedPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val coachingState by viewModel.coachingState.collectAsState()
    val scrollState = rememberScrollState()

    var logType by remember { mutableStateOf("MEAL") }

    var showChatBottomSheet by remember { mutableStateOf(false) }
    var foodInputText by remember { mutableStateOf("") }

    var showFavoritesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(triggerFavoritesLog) {
        if (triggerFavoritesLog) {
            showFavoritesDialog = true
        }
    }

    val context = LocalContext.current

    // Voice Quick-Log State variables
    var isListening by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var voiceBannerColor by remember { mutableStateOf(Color.Transparent) }
    var showVoiceBanner by remember { mutableStateOf(false) }

    // Speech recognizer helper instantiation
    val speechHelper = remember(logType) {
        SpeechRecognizerHelper(
            context = context,
            onResult = { text ->
                if (logType == "MEAL") {
                    viewModel.parseAndAddMeal(text)
                } else {
                    viewModel.parseAndAddWorkout(text)
                }
            },
            onError = { error ->
                voiceMessage = error
                voiceBannerColor = EnergeticCoral
                showVoiceBanner = true
            },
            onListeningStateChange = { listening ->
                isListening = listening
            }
        )
    }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechHelper.startListening()
        } else {
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

    // Auto-dismiss voice feedback banner
    LaunchedEffect(showVoiceBanner) {
        if (showVoiceBanner) {
            kotlinx.coroutines.delay(4000)
            showVoiceBanner = false
        }
    }

    // Listen to parsing updates for success/error popups
    LaunchedEffect(parsingState) {
        val state = parsingState
        when (state) {
            is ParsingState.Success -> {
                voiceMessage = "Logged: ${state.foodText}"
                voiceBannerColor = NeonGreen
                showVoiceBanner = true
                viewModel.resetParsingState()
            }
            is ParsingState.Error -> {
                voiceMessage = state.message
                voiceBannerColor = EnergeticCoral
                showVoiceBanner = true
                viewModel.resetParsingState()
            }
            else -> {}
        }
    }

    LaunchedEffect(workoutParsingState) {
        val state = workoutParsingState
        when (state) {
            is ParsingState.Success -> {
                voiceMessage = "Logged: ${state.foodText}"
                voiceBannerColor = NeonGreen
                showVoiceBanner = true
                viewModel.resetWorkoutParsingState()
            }
            is ParsingState.Error -> {
                voiceMessage = state.message
                voiceBannerColor = EnergeticCoral
                showVoiceBanner = true
                viewModel.resetWorkoutParsingState()
            }
            else -> {}
        }
    }

    // Clean up voice listener on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
        }
    }

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

    // Trigger voice log from notifications
    LaunchedEffect(triggerVoiceLog) {
        if (triggerVoiceLog) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                speechHelper.startListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            onVoiceLogTriggeredHandled()
        }
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
        if (showFavoritesDialog) {
            AlertDialog(
                onDismissRequest = {
                    showFavoritesDialog = false
                    onFavoritesLogTriggeredHandled()
                },
                title = {
                    Text(
                        text = "LOG A FAVORITE MEAL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = ThemeTextPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Choose one of your frequently logged meals to track it instantly:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        val meals = viewModel.frequentMeals.collectAsState().value
                        if (meals.isEmpty()) {
                            Text(
                                text = "No frequent meals logged yet. Keep logging to see suggestions here!",
                                fontSize = 13.sp,
                                color = ThemeTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        } else {
                            meals.forEach { meal ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.parseAndAddMeal(meal)
                                            showFavoritesDialog = false
                                            onFavoritesLogTriggeredHandled()
                                        }
                                        .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = meal,
                                            color = ThemeTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Log",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFavoritesDialog = false
                            onFavoritesLogTriggeredHandled()
                        }
                    ) {
                        Text("Close", color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = ThemeBgStart,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            )
        }
        // Floating Success/Error Notification Banner
        AnimatedVisibility(
            visible = showVoiceBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .zIndex(99f)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, voiceBannerColor, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(voiceBannerColor)
                    )
                    Text(
                        text = voiceMessage ?: "",
                        color = ThemeTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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
                                onClick = { viewModel.syncTelemetry() },
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

                    // Voice Quick Log Card
                    val isProcessing = parsingState is ParsingState.Loading || workoutParsingState is ParsingState.Loading
                    VoiceQuickLogCard(
                        logType = logType,
                        onLogTypeChange = { logType = it },
                        isListening = isListening,
                        isProcessing = isProcessing,
                        onMicClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                speechHelper.startListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopClick = {
                            speechHelper.stopListening()
                        }
                    )

                    // Health Connect Permission Alert Card (if missing permissions)
                    if (!hasHealthConnectPermissions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = EnergeticCoral,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Telemetry Sync Paused",
                                    color = ThemeTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Grant Health Connect permissions to sync steps and sleep data.",
                                    color = ThemeTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Button(
                                onClick = {
                                    requestPermissionsLauncher.launch(viewModel.healthConnectManager.permissions)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EnergeticCoral),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Grant",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Circular Activity & Target Rings
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
                            // Concentric rings canvas
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
                            }
                        }
                    }

                    // Nutrition & Dynamic Macros Budget Card
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

                    // Activity & Performance Card
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
                                                onClick = { viewModel.deleteWorkout(entry) },
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

                    // AI Daily Coach Insights Card
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
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = cState.insight,
                                        color = ThemeTextPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                    Button(
                                        onClick = { showChatBottomSheet = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonMint,
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
                                            onClick = {
                                                showChatBottomSheet = false
                                                onNavigateToSettings()
                                            },
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


                    // Sleep & Readiness Recovery Dashboard
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

                    // Food Ledger & Real-time AI Entry Logger
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
                                onValueChange = { foodInputText = it },
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

                        // Suggestions chips
                        val frequentMeals by viewModel.frequentMeals.collectAsState()
                        if (frequentMeals.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Frequently Tracked (Tap to fill):",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    frequentMeals.forEach { meal ->
                                        SuggestionChip(
                                            onClick = { foodInputText = meal },
                                            label = {
                                                Text(
                                                    text = meal,
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
                                        onClick = {
                                            showChatBottomSheet = false
                                            onNavigateToSettings()
                                        },
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

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        if (showChatBottomSheet && dashboardState is DashboardUiState.Success) {
            val chatMessages by viewModel.chatMessages.collectAsState()
            val chatLoading by viewModel.chatLoading.collectAsState()
            var chatInputText by remember { mutableStateOf("") }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            
            ModalBottomSheet(
                onDismissRequest = { showChatBottomSheet = false },
                sheetState = sheetState,
                containerColor = ThemeCardBg,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = { BottomSheetDefaults.DragHandle(color = ThemeTextSecondary) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight(0.75f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💬 Chat with Coach",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ThemeTextPrimary
                        )
                        IconButton(
                            onClick = { viewModel.clearChat() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Clear Chat",
                                tint = ThemeTextSecondary
                            )
                        }
                    }
                    
                    // Messages list
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ThemeTextPrimary.copy(alpha = 0.03f))
                            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        val lazyListState = rememberLazyListState()
                        LaunchedEffect(chatMessages.size) {
                            if (chatMessages.isNotEmpty()) {
                                lazyListState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(chatMessages) { message ->
                                val isUser = message.sender == MessageSender.User
                                val alignment = if (isUser) Alignment.End else Alignment.Start
                                val bubbleColor = if (isUser) HyperVioletAccent.copy(alpha = 0.25f) else ThemeTextPrimary.copy(alpha = 0.05f)
                                val borderColor = if (isUser) HyperVioletAccent else ThemeGlassBorder
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = alignment
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 12.dp,
                                                    topEnd = 12.dp,
                                                    bottomStart = if (isUser) 12.dp else 2.dp,
                                                    bottomEnd = if (isUser) 2.dp else 12.dp
                                                )
                                            )
                                            .background(bubbleColor)
                                            .border(
                                                width = 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(
                                                    topStart = 12.dp,
                                                    topEnd = 12.dp,
                                                    bottomStart = if (isUser) 12.dp else 2.dp,
                                                    bottomEnd = if (isUser) 2.dp else 12.dp
                                                )
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = message.text,
                                            color = ThemeTextPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    Text(
                                        text = if (isUser) "You" else "Coach",
                                        color = ThemeTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                                    )
                                }
                            }
                            
                            if (chatLoading) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = NeonGreen,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Coach is thinking...",
                                            color = ThemeTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Input panel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            label = { Text("Ask Coach about diet or recovery...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen,
                                unfocusedBorderColor = ThemeGlassBorder,
                                focusedTextColor = ThemeTextPrimary,
                                unfocusedTextColor = ThemeTextPrimary,
                                focusedLabelColor = NeonGreen,
                                unfocusedLabelColor = ThemeTextSecondary
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                if (chatInputText.isNotBlank()) {
                                    viewModel.sendChatMessage(chatInputText)
                                    chatInputText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            enabled = !chatLoading && chatInputText.isNotBlank(),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
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

    val trackBgColor = ThemeTextPrimary.copy(alpha = 0.15f)
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

            // Background tracks
            drawCircle(
                color = trackBgColor,
                radius = radius1,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = trackBgColor,
                radius = radius2,
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
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$caloriesRemaining",
                color = ThemeTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "kcal left",
                color = ThemeTextSecondary,
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
                color = ThemeTextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = ThemeTextPrimary,
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
                    color = ThemeTextSecondary,
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
                .background(ThemeTextPrimary.copy(alpha = 0.05f))
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
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(8.dp))
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
                                color = ThemeTextPrimary.copy(alpha = 0.85f),
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
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
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
                color = ThemeTextPrimary,
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
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodText,
                color = ThemeTextPrimary,
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
                color = ThemeTextSecondary,
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
                .background(ThemeTextPrimary.copy(alpha = 0.05f))
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

// Voice Speech Recognition Helper class
class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChange: (Boolean) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onListeningStateChange(true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onListeningStateChange(false)
                    }

                    override fun onError(error: Int) {
                        onListeningStateChange(false)
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                            SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand audio. Try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy."
                            SpeechRecognizer.ERROR_SERVER -> "Server connection error."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
                            11 -> "Binding error. Try again."
                            else -> "Speech error: $error"
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            onResult(text)
                        } else {
                            onError("Could not understand speech.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        return speechRecognizer!!
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        
        try {
            speechRecognizer?.cancel()
            val recognizer = getOrCreateRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            onError("Failed to start listening: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
        onListeningStateChange(false)
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
        onListeningStateChange(false)
    }
}

// Voice Quick Log UI Composable Card
@Composable
fun VoiceQuickLogCard(
    logType: String,
    onLogTypeChange: (String) -> Unit,
    isListening: Boolean,
    isProcessing: Boolean,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
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
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Segment Selector Switch (MEAL / WORKOUT)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (logType == "MEAL") ThemeTextPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onLogTypeChange("MEAL") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MEAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (logType == "MEAL") ThemeSecondaryAccent else ThemeTextTertiary,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (logType == "WORKOUT") ThemeTextPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onLogTypeChange("WORKOUT") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WORKOUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (logType == "WORKOUT") ThemeSecondaryAccent else ThemeTextTertiary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pulse animated scale for listening indicator
                val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
                val scale by if (isListening) {
                    infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Scale"
                    )
                } else {
                    remember { mutableStateOf(1f) }
                }

                val glowBg = if (isListening) {
                    HyperViolet.copy(alpha = 0.3f)
                } else {
                    ThemeTextPrimary.copy(alpha = 0.05f)
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(glowBg)
                        .clickable(enabled = !isProcessing) {
                            if (isListening) onStopClick() else onMicClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ThemeSecondaryAccent,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size((36.dp.value * scale).dp)
                                .clip(CircleShape)
                                .background(if (isListening) NeonMint else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Quick Voice Log Microphone",
                                tint = if (isListening) Color.Black else ThemeTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when {
                            isListening -> "Listening..."
                            isProcessing -> "Analyzing Speech..."
                            logType == "MEAL" -> "Quick-Log Meal"
                            else -> "Quick-Log Workout"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            isListening -> NeonMint
                            isProcessing -> ThemeSecondaryAccent
                            else -> ThemeTextPrimary
                        }
                    )
                    Text(
                        text = when {
                            isListening -> if (logType == "MEAL") "Speak what you ate now..." else "Speak what workout you did..."
                            isProcessing -> if (logType == "MEAL") "Estimating calories & macros with Gemini..." else "Estimating calories & sets/reps with Gemini..."
                            logType == "MEAL" -> "Tap the mic and speak what you ate."
                            else -> "Tap the mic and speak what exercise you did."
                        },
                        fontSize = 11.sp,
                        color = ThemeTextSecondary
                    )
                }
            }
        }
    }
}
