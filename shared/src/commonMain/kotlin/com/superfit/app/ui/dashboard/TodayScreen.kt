package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.data.Now
import com.superfit.app.domain.LogKind
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.camera.PhotoCamera
import com.superfit.app.ui.camera.PhotoKind
import com.superfit.app.ui.camera.PhotoKindSheet
import com.superfit.app.ui.camera.PhotoLog
import com.superfit.app.ui.camera.PhotoLogOverlay
import com.superfit.app.ui.camera.PhotoLogState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Today on the iPhone, with the same Food / Activity switch and shared cards Android shows.
 * Food: calorie rings, streak, nutrients, weigh-in and meals. Activity: steps and workouts next to
 * the usual, then sleep, from Apple Health once it's connected. [footer] holds what the iPhone still
 * adds for now.
 */
@Composable
fun TodayScreen(
    model: TodayModel,
    onOpenSettings: () -> Unit = {},
    /** Opens Superfit's page in the phone's own Settings, for turning the microphone back on. */
    onOpenPhoneSettings: () -> Unit = {},
    /** The camera for "Snap your plate", with what happens to its pictures; null hides the button. */
    camera: PhotoCamera? = null,
    photoLog: PhotoLog? = null,
    /** The widget's mic or camera, done once Today is showing; [onLaunchHandled] clears it. */
    launch: TodayLaunch? = null,
    onLaunchHandled: () -> Unit = {},
    footer: @Composable () -> Unit = {}
) {
    val state by model.state.collectAsState()
    val usualDay by model.usualDay.collectAsState()
    val stepGoal by model.stepGoal.collectAsState()
    val stepGoalNoteDismissedOn by model.stepGoalNoteDismissedOn.collectAsState()
    val coaching by model.coaching.collectAsState()
    val healthAsked by model.healthAsked.collectAsState()
    val distanceKm by model.distanceKm.collectAsState()
    val workoutImportEnabled by model.workoutImportEnabled.collectAsState()
    val workoutImportRunning by model.workoutImportRunning.collectAsState()
    val pendingScaleReading by model.pendingScaleReading.collectAsState()
    var showStepGoalSheet by remember { mutableStateOf(false) }
    var side by rememberSaveable { mutableStateOf("MEAL") }
    val parsing by model.parsing.collectAsState()
    val predictedFoods by model.predictedFoods.collectAsState()
    var foodInput by remember { mutableStateOf("") }
    var showAllFoods by remember { mutableStateOf(false) }
    val voice = model.voiceLog?.takeIf { it.available }
    val noVoice = remember { VoiceIdle() }
    val voiceUi by (voice?.ui ?: noVoice.ui).collectAsState()
    val voiceNotice by (voice?.notice ?: noVoice.notice).collectAsState()
    val micOff by (voice?.micOff ?: noVoice.micOff).collectAsState()
    var banner by remember { mutableStateOf<LogBanner?>(null) }
    val photoCamera = camera?.takeIf { photoLog != null && it.isAvailable() }
    var showPhotoKinds by remember { mutableStateOf(false) }
    // The camera opens once the sheet has gone, so Apple's camera isn't shown on the closing sheet.
    var pendingCamera by remember { mutableStateOf<PhotoKind?>(null) }
    val idlePhoto = remember { MutableStateFlow<PhotoLogState>(PhotoLogState.Idle) }
    val photoState by (photoLog?.state ?: idlePhoto).collectAsState()
    val openCamera: (PhotoKind) -> Unit = { kind ->
        photoLog?.close()
        photoCamera?.take(
            kind,
            onPhoto = { photoLog?.onPhoto(kind, it) },
            onBarcode = { photoLog?.onBarcode(it) },
            onError = { banner = LogBanner(it, isError = true) }
        )
    }

    // What was logged, or why it couldn't be, slides in at the top for a few seconds, as on Android.
    LaunchedEffect(parsing) {
        when (val p = parsing) {
            is ParsingState.Success -> banner = LogBanner(p.message ?: "Logged: ${p.foodText}", isError = false)
            is ParsingState.Error -> banner = LogBanner(p.message, isError = true)
            else -> return@LaunchedEffect
        }
        model.resetParsing()
    }
    LaunchedEffect(voiceNotice) {
        voiceNotice?.let {
            banner = LogBanner(it, isError = true)
            voice?.dismissNotice()
        }
    }
    LaunchedEffect(pendingCamera) {
        pendingCamera?.let { kind ->
            delay(400)
            openCamera(kind)
            pendingCamera = null
        }
    }
    // The widget's mic or camera: on the Food side, once today's numbers are showing.
    val ready = state is DashboardUiState.Success
    LaunchedEffect(launch, ready) {
        if (launch == null || !ready) return@LaunchedEffect
        side = "MEAL"
        when (launch) {
            TodayLaunch.VOICE -> voice?.start(LogKind.MEAL)
            TodayLaunch.CAMERA -> if (photoCamera != null) pendingCamera = PhotoKind.MEAL
        }
        onLaunchHandled()
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(4000)
            banner = null
        }
    }
    // Leaving Today stops the mic without logging half a sentence.
    DisposableEffect(voice) { onDispose { voice?.cancel() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuperfitTheme.colors.bgStart)
    ) {
        when (val s = state) {
            DashboardUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NeonGreen)
            DashboardUiState.NotInitialized -> Column(
                modifier = Modifier.systemBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "No profile on this account yet. Set it up in the Android app for now; setup on iPhone comes with onboarding.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SuperfitTheme.colors.textPrimary
                )
                footer()
            }
            is DashboardUiState.Success -> Column(
                modifier = Modifier
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardHeader(grantedPermissions = emptySet(), hasHealthConnectPermissions = true, onNavigateToSettings = onOpenSettings)
                FoodActivitySwitch(side = side, onSelect = {
                    voice?.cancel()
                    side = it
                })
                if (voice != null) {
                    val kind = if (side == "MEAL") LogKind.MEAL else LogKind.WORKOUT
                    // Only the mic that's listening shows it; the other side's waits.
                    val mine = voiceUi.listening && voiceUi.kind == kind
                    VoiceQuickLogCard(
                        logType = if (side == "MEAL") "MEAL" else "WORKOUT",
                        isListening = mine,
                        isChecking = mine && voiceUi.checking,
                        liveText = if (mine) voiceUi.liveText else "",
                        isProcessing = parsing is ParsingState.Loading,
                        onMicClick = { voice.start(kind) },
                        onStopClick = voice::stop,
                        onCameraClick = photoCamera?.let { { showPhotoKinds = true } }
                    )
                }
                if (side == "MEAL") {
                    CalorieRingsCard(state = s, stepGoal = stepGoal, onStepsClick = { showStepGoalSheet = true })
                    val day = Now.today()
                    stepGoal?.takeIf { it.raisedOn(day) && stepGoalNoteDismissedOn != day.toString() }?.let { goal ->
                        StepGoalRaisedNote(goal = goal, onDismiss = model::dismissStepGoalNote)
                    }
                    StreakHeaderCard(streakState = s.streakState)
                    NutrientLedgerCard(state = s)
                    WeightQuickRow(
                        currentWeightKg = s.weightMetrics.currentWeightKg,
                        weightEntries = s.weightEntries,
                        onLogWeight = model::logWeight
                    )
                    // Coach chat joins in a later step, so the card offers today's tip only for now.
                    CoachInsightsCard(coachingState = coaching, onRefresh = model::refreshCoachingInsight, onOpenChat = null)
                    TrackMealsCard(
                        state = s,
                        predictedFoods = predictedFoods,
                        canRetryLastMeal = model.canRetryLastMeal,
                        onLogMeal = model::logMeal,
                        onRetryLastMeal = model::retryLastMeal,
                        onDeleteMeal = model::deleteMeal,
                        onUpdateMeal = model::updateMeal,
                        parsingState = parsing,
                        foodInputText = foodInput,
                        onFoodInputChange = { foodInput = it },
                        onViewAllFoods = { showAllFoods = true }
                    )
                } else {
                    // Sharing a workout card joins with share cards.
                    if (model.healthAvailable && !healthAsked) ConnectHealthCard(onConnect = model::connectHealth)
                    ActivityPerformanceCard(
                        state = s,
                        vsUsual = model.vsUsual(s, usualDay, distanceKm),
                        onDeleteWorkout = model::deleteWorkout,
                        onHideImportedWorkout = model::hideImportedWorkout,
                        workoutImportEnabled = workoutImportEnabled,
                        workoutImportRunning = workoutImportRunning
                    )
                    SleepRecoveryCard(state = s)
                }
                footer()
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
        photoLog?.let { log ->
            PhotoLogOverlay(
                state = photoState,
                kind = log.kind,
                onLog = { entry, isPackaged ->
                    log.close()
                    model.addPhotoMeal(entry, isPackaged)
                },
                onRetake = { kind ->
                    // Closes the check sheet first; the camera opens once it has gone.
                    log.close()
                    pendingCamera = kind
                },
                onClose = log::close
            )
        }
        LogResultBanner(banner, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showPhotoKinds) {
        PhotoKindSheet(
            onPick = { kind ->
                showPhotoKinds = false
                pendingCamera = kind
            },
            onDismiss = { showPhotoKinds = false }
        )
    }

    if (micOff) {
        MicOffDialog(
            onOpenSettings = {
                voice?.dismissMicOff()
                onOpenPhoneSettings()
            },
            onDismiss = { voice?.dismissMicOff() }
        )
    }

    if (showStepGoalSheet) {
        stepGoal?.let { goal ->
            StepGoalSheet(goal = goal, onSetOwn = model::setOwnStepGoal, onDismiss = { showStepGoalSheet = false })
        }
    }

    pendingScaleReading?.let { reading ->
        ScaleReadingDialog(
            reading = reading,
            onMe = { model.answerScaleReading(isMe = true) },
            onNotMe = { model.answerScaleReading(isMe = false) },
            onLater = model::postponeScaleReading
        )
    }

    if (showAllFoods) {
        FoodHistoryDialog(
            foods = predictedFoods,
            onDismiss = { showAllFoods = false },
            onFillInput = {
                foodInput = it
                showAllFoods = false
            },
            onLogNow = {
                model.logMeal(it)
                showAllFoods = false
            }
        )
    }
}

/** Food / Activity on Today, with icons; [unseenActivity] puts a dot on Activity until it's opened. */
@Composable
fun FoodActivitySwitch(side: String, onSelect: (String) -> Unit, unseenActivity: Boolean = false) {
    SegmentedPills(
        options = listOf("MEAL" to "Food", "WORKOUT" to "Activity"),
        selected = side,
        onSelect = onSelect,
        icons = mapOf(
            "MEAL" to (Icons.Rounded.Restaurant to Icons.Outlined.Restaurant),
            "WORKOUT" to (Icons.AutoMirrored.Rounded.DirectionsRun to Icons.AutoMirrored.Outlined.DirectionsRun)
        ),
        unseen = if (unseenActivity) setOf("WORKOUT") else emptySet()
    )
}
