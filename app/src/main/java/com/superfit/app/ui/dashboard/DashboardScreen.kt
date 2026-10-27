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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.ReviewPrompt
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.theme.*
import com.superfit.app.ui.camera.FoodCameraController
import com.superfit.app.ui.camera.FoodCameraScreen
import com.superfit.app.widget.WidgetPin
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecap: (java.time.YearMonth) -> Unit = {},
    triggerVoiceLog: Boolean = false,
    triggerFavoritesLog: Boolean = false,
    onVoiceLogTriggeredHandled: () -> Unit = {},
    onFavoritesLogTriggeredHandled: () -> Unit = {},
    triggerPhotoLog: Boolean = false,
    onPhotoLogTriggeredHandled: () -> Unit = {}
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val parsingState by viewModel.parsingState.collectAsState()
    val workoutParsingState by viewModel.workoutParsingState.collectAsState()
    val hasHealthConnectPermissions by viewModel.hasHealthConnectPermissions.collectAsState()
    val grantedPermissions by viewModel.grantedPermissions.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val coachingState by viewModel.coachingState.collectAsState()
    val recapOffer by viewModel.recapOffer.collectAsState()
    // Back from the recap, or the app left open overnight into the 1st: check the offer again.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshRecapOffer()
        onPauseOrDispose { }
    }
    val scrollState = rememberScrollState()

    var logType by remember { mutableStateOf("MEAL") }
    // Hidden for the rest of the visit once Add or Not now is tapped, even across tab switches.
    var widgetTipHidden by rememberSaveable { mutableStateOf(false) }

    var showChatBottomSheet by remember { mutableStateOf(false) }
    val coachStyle by viewModel.coachStyle.collectAsState()
    var foodInputText by remember { mutableStateOf("") }

    var showFavoritesDialog by remember { mutableStateOf(false) }
    // Saveable so turning the phone while taking a photo keeps the camera open.
    var showFoodCamera by rememberSaveable { mutableStateOf(false) }
    val foodCameraController: FoodCameraController = viewModel()

    LaunchedEffect(triggerFavoritesLog) {
        if (triggerFavoritesLog) {
            showFavoritesDialog = true
        }
    }

    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    // Voice Quick-Log State variables
    var isListening by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var voiceBannerColor by remember { mutableStateOf(Color.Transparent) }
    var showVoiceBanner by remember { mutableStateOf(false) }

    // The phone's own voice-typing dialog, used when the in-app recognizer can't run on this phone.
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.smartLog(text)
    }
    var showMicOffDialog by remember { mutableStateOf(false) }
    var voiceLiveText by remember { mutableStateOf("") }

    // Speech recognizer helper instantiation
    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = { text ->
                // One mic for everything: food, exercise or both, whichever tab is showing.
                viewModel.smartLog(text)
            },
            onError = { error ->
                voiceMessage = error
                voiceBannerColor = EnergeticCoral
                showVoiceBanner = true
            },
            onListeningStateChange = { listening ->
                isListening = listening
            },
            onLiveText = { voiceLiveText = it },
            onNeedsSystemDialog = {
                try {
                    systemSpeechLauncher.launch(SpeechRecognizerHelper.systemDialogIntent())
                } catch (e: Exception) {
                    voiceMessage = "Voice input isn't available on this phone. Type your meal or use the camera instead."
                    voiceBannerColor = EnergeticCoral
                    showVoiceBanner = true
                }
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
            // Explain first. Jumping straight to system Settings looked like the app had closed.
            showMicOffDialog = true
        }
    }

    if (showMicOffDialog) {
        AlertDialog(
            onDismissRequest = { showMicOffDialog = false },
            title = { Text("Microphone is off for Superfit") },
            text = {
                Text(
                    "Superfit needs the microphone to hear what you ate or did. Nothing is recorded or kept. " +
                        "You can turn it on in Settings, or type your meal or use the camera instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicOffDialog = false
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please open Settings and allow the microphone for Superfit.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showMicOffDialog = false }) { Text("Not now") }
            }
        )
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
                voiceMessage = state.message ?: "Logged: ${state.foodText}"
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
                voiceMessage = state.message ?: "Logged: ${state.foodText}"
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

    // Health Connect's screen for workouts from other apps (Connect on the Activity card)
    val workoutPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onWorkoutPermissionsResult(granted)
    }
    val workoutImportEnabled by viewModel.workoutImportEnabled.collectAsState()
    val workoutCardDismissed by viewModel.workoutCardDismissed.collectAsState()
    val usualDay by viewModel.usualDay.collectAsState()
    val workoutPermissionMissing by viewModel.workoutPermissionMissing.collectAsState()
    val workoutImportRunning by viewModel.workoutImportRunning.collectAsState()
    val duplicateWorkoutPrompt by viewModel.duplicateWorkoutPrompt.collectAsState()
    val pendingScaleReading by viewModel.pendingScaleReading.collectAsState()
    val personalBests by viewModel.personalBests.collectAsState()
    // The card in the share preview, and the new best it shows (remembered once it's shared).
    var shareCard by remember { mutableStateOf<com.superfit.app.domain.ShareCard?>(null) }
    var shareBest by remember { mutableStateOf<com.superfit.app.domain.PersonalBest?>(null) }

    // Auto sync on entry
    LaunchedEffect(Unit) {
        viewModel.syncTelemetry()
    }

    // Play Store rating, asked once after meals on 5 different days (not while a new best is offered)
    val mealDaysLogged = (dashboardState as? DashboardUiState.Success)?.mealDaysLogged ?: 0
    val bestOffered = personalBests.toOffer != null
    LaunchedEffect(mealDaysLogged, bestOffered) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstNotNullOfOrNull { it as? android.app.Activity } ?: return@LaunchedEffect
        if (ReviewPrompt.shouldAsk(mealDaysLogged, ReviewPrompt.wasAsked(context), bestOffered)) {
            ReviewPrompt.ask(activity)
        }
    }

    // Camera button on the home-screen widget
    LaunchedEffect(triggerPhotoLog) {
        if (triggerPhotoLog) {
            foodCameraController.reset()
            showFoodCamera = true
            onPhotoLogTriggeredHandled()
        }
    }

    // Trigger voice log from notifications and the widget mic
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
            .background(ThemeBgStart)
    ) {
        if (showFoodCamera) {
            Dialog(
                onDismissRequest = { showFoodCamera = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                FoodCameraScreen(
                    controller = foodCameraController,
                    onLogged = { entry, isPackaged ->
                        com.superfit.app.data.AppAnalytics.mealLogged(
                            when (foodCameraController.mode) {
                                com.superfit.app.ui.camera.CameraMode.BARCODE -> "barcode"
                                com.superfit.app.ui.camera.CameraMode.LABEL -> "label"
                                else -> "photo"
                            }
                        )
                        viewModel.addPhotoMeal(entry, isPackaged)
                        showFoodCamera = false
                    },
                    onClose = { showFoodCamera = false }
                )
            }
        }

        if (showFavoritesDialog) {
            val predictedFoods by viewModel.predictedFoods.collectAsState()
            FoodHistoryDialog(
                foods = predictedFoods,
                onDismiss = {
                    showFavoritesDialog = false
                    onFavoritesLogTriggeredHandled()
                },
                onFillInput = { foodText ->
                    foodInputText = foodText
                    showFavoritesDialog = false
                    onFavoritesLogTriggeredHandled()
                },
                onLogNow = { foodText ->
                    viewModel.parseAndAddMeal(foodText)
                    showFavoritesDialog = false
                    onFavoritesLogTriggeredHandled()
                }
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
                // Pull down to sync with Health Connect (it also syncs on its own when Home opens).
                var refreshing by remember { mutableStateOf(false) }
                val refreshScope = rememberCoroutineScope()
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        viewModel.syncTelemetry()
                        refreshScope.launch {
                            kotlinx.coroutines.delay(1200)
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Bar
                        DashboardHeader(
                            grantedPermissions = grantedPermissions,
                            hasHealthConnectPermissions = hasHealthConnectPermissions,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToSettings = onNavigateToSettings
                        )

                        // Voice Quick Log Card (MEAL / WORKOUT tab switcher)
                        val isProcessing = parsingState is ParsingState.Loading || workoutParsingState is ParsingState.Loading
                        VoiceQuickLogCard(
                            logType = logType,
                            onLogTypeChange = { logType = it },
                            isListening = isListening,
                            isProcessing = isProcessing,
                            liveText = voiceLiveText,
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
                            },
                            onCameraClick = if (hasCamera) {
                                {
                                    foodCameraController.reset()
                                    showFoodCamera = true
                                }
                            } else {
                                null
                            }
                        )

                        recapOffer?.let { month ->
                            com.superfit.app.ui.recap.RecapReadyCard(month, onOpen = { onOpenRecap(month) })
                        }

                        personalBests.toOffer?.let { best ->
                            NewBestCard(
                                best = best,
                                onShare = {
                                    shareBest = best
                                    shareCard = com.superfit.app.domain.ShareCards.best(best, isNew = true, today = LocalDate.now())
                                },
                                onDismiss = { viewModel.acknowledgeBest(best) }
                            )
                        }

                        if (logType == "MEAL") {
                            // MEAL VIEW: Concentric Rings, Streak, Macros, Coach, and Track Your Meals
                            // 1. Concentric Activity & Target Rings (ABOVE THE FOLD)
                            CalorieRingsCard(state = state)

                            // One-time tip offering the home-screen widget
                            val widgetTipEligible = remember(state.totalMealsLogged) {
                                WidgetPin.shouldShowTip(context, state.totalMealsLogged)
                            }
                            if (widgetTipEligible && !widgetTipHidden) {
                                WidgetTipCard(
                                    onAdd = {
                                        WidgetPin.requestPin(context)
                                        widgetTipHidden = true
                                    },
                                    onNotNow = {
                                        WidgetPin.dismissTip(context)
                                        widgetTipHidden = true
                                    }
                                )
                            }

                            // 2. Daily Streak Header Card (Narrow Band)
                            StreakHeaderCard(
                                streakState = state.streakState,
                                onShare = { shareCard = dailyShareCard(state, coachingState) }
                            )

                            // 3. Daily Nutrient Ledger
                            NutrientLedgerCard(state = state)

                            // Quick weigh-in, so weight logging doesn't need the History tab
                            WeightQuickRow(
                                currentWeightKg = state.weightMetrics.currentWeightKg,
                                weightEntries = state.weightEntries,
                                onLogWeight = { weight, note -> viewModel.logWeight(weight, note) }
                            )

                            // 4. AI Daily Coach Insights Card
                            CoachInsightsCard(
                                coachingState = coachingState,
                                onRefresh = { viewModel.refreshCoachingInsight() },
                                onOpenChat = { showChatBottomSheet = true },
                                onRoast = {
                                    viewModel.sendChatMessage(DashboardViewModel.ROAST_MY_DAY, how = "chip")
                                    showChatBottomSheet = true
                                }.takeIf { coachStyle == com.superfit.app.domain.CoachStyle.ROAST }
                            )

                            // Diet Quality (a 7-day score) lives in Insights; Home stays about today.

                            // 5. Track Your Meals Card (With Text Box, Chips, and Logged List)
                            TrackMealsCard(
                                state = state,
                                viewModel = viewModel,
                                parsingState = parsingState,
                                foodInputText = foodInputText,
                                onFoodInputChange = { foodInputText = it },
                                onViewAllFoods = { showFavoritesDialog = true }
                            )
                        } else {
                            // WORKOUT VIEW: Activity & Performance, Sleep & Recovery
                            // One-time offer to bring in workouts from other apps
                            val offerWorkoutConnect = workoutPermissionMissing || (!workoutImportEnabled && !workoutCardDismissed)
                            if (offerWorkoutConnect && viewModel.healthConnectManager.isAvailable()) {
                                WorkoutConnectCard(
                                    onConnect = {
                                        workoutPermissionsLauncher.launch(com.superfit.app.data.WorkoutImporter.permissions)
                                    },
                                    onNotNow = { viewModel.dismissWorkoutCard() }
                                )
                            }

                            // 1. Activity & Performance Card
                            ActivityPerformanceCard(
                                state = state,
                                vsUsual = viewModel.todayVsUsual(state, usualDay),
                                onDeleteWorkout = { viewModel.deleteWorkout(it) },
                                onHideImportedWorkout = { viewModel.hideWorkout(it) },
                                workoutImportEnabled = workoutImportEnabled,
                                workoutImportRunning = workoutImportRunning,
                                onShareWorkout = { shareCard = it }
                            )

                            // 2. Sleep & Readiness Recovery Dashboard
                            SleepRecoveryCard(state = state)
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // Once, for people who signed up before "Pick your coach" existed.
        var askCoachStyle by remember { mutableStateOf(!com.superfit.app.data.CoachStyleStore.hasChosen(context)) }
        if (askCoachStyle) {
            CoachStyleChooserDialog(
                onChoose = { style ->
                    viewModel.setCoachStyle(style)
                    askCoachStyle = false
                }
            )
        }

        shareCard?.let { card ->
            com.superfit.app.ui.share.ShareCardSheet(
                card = card,
                onDismiss = {
                    shareCard = null
                    shareBest = null
                },
                onShared = { shareBest?.let { viewModel.acknowledgeBest(it) } }
            )
        }

        pendingScaleReading?.let { reading ->
            ScaleReadingDialog(
                reading = reading,
                onMe = { viewModel.answerScaleReading(isMe = true) },
                onNotMe = { viewModel.answerScaleReading(isMe = false) },
                onLater = { viewModel.postponeScaleReading() }
            )
        }

        duplicateWorkoutPrompt?.let { prompt ->
            DuplicateWorkoutDialog(
                prompt = prompt,
                onKeepImported = { viewModel.keepImportedWorkout() },
                onKeepBoth = { viewModel.keepBothWorkouts() }
            )
        }

        if (showChatBottomSheet && dashboardState is DashboardUiState.Success) {
            val dayState = dashboardState as DashboardUiState.Success
            CoachChatSheet(
                viewModel = viewModel,
                onDismiss = { showChatBottomSheet = false },
                // Any roast can go on a card, even one about an empty log.
                onShareRoast = { line: String ->
                    showChatBottomSheet = false
                    shareCard = com.superfit.app.domain.ShareCards.roast(dailyShareCard(dayState, coachingState), line)
                }
            )
        }
    }
}
