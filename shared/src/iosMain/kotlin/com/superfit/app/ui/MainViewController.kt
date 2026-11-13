package com.superfit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import com.superfit.app.data.CloudBackup
import com.superfit.app.data.HealthSync
import com.superfit.app.data.IosAccountDeletion
import com.superfit.app.data.IosAuth
import com.superfit.app.data.IosAuthClient
import com.superfit.app.data.IosBackupLocal
import com.superfit.app.data.IosCloudStore
import com.superfit.app.data.IosCloudStoreAdapter
import com.superfit.app.data.IosHealth
import com.superfit.app.data.IosHealthSource
import com.superfit.app.data.IosOnboardingHealth
import com.superfit.app.data.IosPrefs
import com.superfit.app.data.IosUsageReports
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.Prefs
import com.superfit.app.data.RecapData
import com.superfit.app.data.ScaleImport
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.UsageEvents
import com.superfit.app.data.WorkoutCardData
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.data.installUsageReports
import com.superfit.app.data.openIosDatabase
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.HealthApp
import com.superfit.app.domain.IosAi
import com.superfit.app.domain.LogKind
import com.superfit.app.domain.RecapSetup
import com.superfit.app.domain.ReminderPlan
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.domain.installIosAi
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.auth.LoginModel
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.camera.PhotoLog
import com.superfit.app.ui.coach.ChatMic
import com.superfit.app.ui.coach.CoachChatModel
import com.superfit.app.ui.coach.CoachChatView
import com.superfit.app.ui.components.dismissKeyboardOnTapOrScroll
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.MicOffDialog
import com.superfit.app.ui.dashboard.ProvideVoiceTyping
import com.superfit.app.ui.dashboard.TodayModel
import com.superfit.app.ui.dashboard.TodayScreen
import com.superfit.app.ui.dashboard.VoiceLog
import com.superfit.app.ui.dashboard.VoiceRecognizer
import com.superfit.app.ui.dashboard.dailyShareCard
import com.superfit.app.ui.history.HistoryModel
import com.superfit.app.ui.history.ProgressTabs
import com.superfit.app.ui.onboarding.OnboardingModel
import com.superfit.app.ui.onboarding.OnboardingScreen
import com.superfit.app.ui.recap.RecapModel
import com.superfit.app.ui.recap.RecapReadyCard
import com.superfit.app.ui.recap.RecapStoryScreen
import com.superfit.app.ui.recap.monthName
import com.superfit.app.ui.settings.CoachMemoryCard
import com.superfit.app.ui.settings.ReminderSettingsCard
import com.superfit.app.ui.settings.SettingsPage
import com.superfit.app.ui.share.LocalShareCardHost
import com.superfit.app.ui.share.ShareCardSheet
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.YearMonth
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController

/** The iPhone app's root screen, shown by the Swift app (ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController(auth: IosAuth, cloudStore: IosCloudStore, ai: IosAi, health: IosHealth, speech: VoiceRecognizer, camera: IosCamera, widget: IosWidget, reminders: IosReminders, report: IosReport, shareCards: IosShareCards, usage: IosUsageReports, recap: IosRecapMedia): UIViewController {
    installIosAi(ai)
    installUsageReports(usage)
    HealthApp.useAppleHealth()
    val healthSource = IosHealthSource(health)
    val photoCamera = IosPhotoCamera(camera)
    return ComposeUIViewController {
        // Light, dark or Auto, kept for the whole app like Android's "theme_mode".
        val appPrefs = remember { IosPrefs(APP_PREFS) }
        var themeMode by remember { mutableStateOf(appPrefs.getString(KEY_THEME_MODE, "system") ?: "system") }
        val dark = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
        SuperfitTheme(darkTheme = dark) {
            ProvideVoiceTyping(speech, onOpenPhoneSettings = ::openPhoneSettings) {
                IosRoot(auth, cloudStore, healthSource, speech, photoCamera, widget, reminders, report, shareCards, usage, recap, themeMode) { mode ->
                    appPrefs.edit { putString(KEY_THEME_MODE, mode) }
                    themeMode = mode
                }
            }
        }
    }
}

/** What the placeholder shows after the backup comes down: how many of each the iPhone now has. */
private data class Restored(val meals: Int, val workouts: Int, val weighIns: Int, val failed: Boolean)

/** Superfit's own page in the iPhone's Settings app (microphone, speech recognition, notifications). */
private fun openPhoneSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

private const val APP_PREFS = "superfit_prefs"
private const val KEY_THEME_MODE = "theme_mode"

@Composable
private fun IosRoot(
    auth: IosAuth,
    cloudStore: IosCloudStore,
    health: IosHealthSource,
    speech: VoiceRecognizer,
    camera: IosPhotoCamera,
    widget: IosWidget,
    reminders: IosReminders,
    report: IosReport,
    shareCards: IosShareCards,
    usage: IosUsageReports,
    recap: IosRecapMedia,
    themeMode: String,
    onThemeMode: (String) -> Unit
) {
    var email by remember { mutableStateOf(auth.currentEmail()) }
    var signedIn by remember { mutableStateOf(auth.currentUid() != null) }
    // What deleting the account did, shown once it's over (even after signing out).
    var accountNotice by remember { mutableStateOf<String?>(null) }
    accountNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { accountNotice = null },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { accountNotice = null }) { Text("OK") } }
        )
    }
    val scope = rememberCoroutineScope()
    val client = remember { IosAuthClient(auth) }
    val onSignedIn = {
        email = auth.currentEmail()
        signedIn = auth.currentUid() != null
    }

    if (!signedIn) {
        // Nobody's numbers on the home screen while signed out.
        LaunchedEffect(Unit) {
            widget.clear()
            reminders.cancelAll()
            reminders.scheduleRecap(null)
        }
        val model = remember { LoginModel(client, scope) }
        LoginScreen(
            model = model,
            onLoginSuccess = onSignedIn,
            onGoogle = { model.signInWith(onSignedIn) { client.signInWithGoogle() } },
            onApple = { model.signInWith(onSignedIn) { client.signInWithApple() } }
        )
    } else {
        val uid = auth.currentUid() ?: return
        var restored by remember(uid) { mutableStateOf<Restored?>(null) }
        val database = remember(uid) { openIosDatabase(uid) }
        val local = remember(uid) { IosBackupLocal(uid) }
        val backup = remember(uid) {
            CloudBackup(IosCloudStoreAdapter(cloudStore), { auth.currentUid() }, { database }, local)
        }
        // Same as Android after sign-in: bring the backup down into this account's database
        // (merge only), then count what arrived.
        LaunchedEffect(uid) {
            val failed = try {
                backup.syncAllDown()
                false
            } catch (e: Exception) {
                AppLog.w("Restore", "Restoring the backup failed", e)
                true
            }
            restored = Restored(
                meals = database.nutritionDao().getAllEntries().size,
                workouts = database.workoutDao().getAllEntries().size,
                weighIns = database.weightDao().getAllWeightEntries().size,
                failed = failed
            )
        }
        if (restored == null) {
            // Today waits for the backup, so it never says "no profile" before it arrives.
            Box(Modifier.fillMaxSize().background(SuperfitTheme.colors.bgStart), contentAlignment = Alignment.Center) {
                Text(
                    "Bringing back your meals and progress…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SuperfitTheme.colors.textPrimary
                )
            }
            return
        }
        // Lives while this account is signed in; signing out stops Today's updates.
        val accountScope = rememberCoroutineScope()
        val log = remember(uid) { LogRepository(database = { database }, backup = backup, isImportedWeight = ScaleImport::isImported) }
        // A new account sets up its profile first, as on Android; the backup has already come down.
        val hasProfile by remember(uid) { log.profileFlow.map { it != null } }.collectAsState(initial = null)
        if (hasProfile == null) return
        if (hasProfile == false) {
            val prefs = remember(uid) { IosPrefs.forUser(uid) }
            val onboarding = remember(uid) { OnboardingModel(log, IosOnboardingHealth(health, prefs).takeIf { health.isAvailable() }, accountScope) }
            OnboardingScreen(
                model = onboarding,
                prefs = prefs,
                onConnectHealth = if (health.isAvailable()) {
                    { done ->
                        accountScope.launch {
                            val answered = runCatching { health.requestAccess() }.getOrDefault(false)
                            if (answered) {
                                // Same as connecting on Today: steps, sleep and workouts come in from now on.
                                prefs.edit { putBoolean(HealthSync.KEY_ASKED, true) }
                                WorkoutImportPrefs(prefs).enabled = true
                            }
                            done(answered)
                        }
                    }
                } else {
                    null
                },
                // Today appears by itself once the profile is saved.
                onOnboardingComplete = {},
                onBack = {
                    auth.signOut()
                    signedIn = false
                    email = null
                }
            )
            return
        }
        val scale = remember(uid) { ScaleImport(IosPrefs.forUser(uid), log) }
        val today = remember(uid) {
            TodayModel(
                log = log,
                prefs = IosPrefs.forUser(uid),
                scope = accountScope,
                coachNotes = local::loadNotes,
                health = health,
                scale = scale,
                voice = speech
            )
        }
        // Share cards: Apple's photo picker and share sheet; workout cards read heart rate from Apple Health.
        val shareHost = remember(uid) {
            val sharePrefs = ShareCardPrefs(IosPrefs(ShareCardPrefs.FILE))
            IosShareCardHost(
                ios = shareCards,
                prefs = sharePrefs,
                workoutCards = WorkoutCardData(
                    log = log,
                    userPrefs = IosPrefs.forUser(uid),
                    share = sharePrefs,
                    importedViews = today::importedViews,
                    heartRate = { source, start, end -> health.heartRate(source, start, end) }
                )
            )
        }
        // The coach chat, kept on the phone and backed up like Android's.
        val coachChat = remember(uid) {
            CoachChatModel(local = local, clearLocalChat = local::clearChat, backup = backup, today = today, scope = accountScope, voice = speech)
        }
        // The evening reminder, written ahead from Today's numbers (the iPhone can't run code at 9 PM).
        val reminderSync = remember(uid) { ReminderSync(reminders, IosPrefs.forUser(uid), today) }
        LaunchedEffect(reminderSync) { reminderSync.run() }
        // Steps and sleep are read again each time the app comes back to the front.
        DisposableEffect(today) {
            val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ ->
                today.syncHealth()
                // Back from the phone's Settings: notifications may have been turned on or off.
                if (ReminderPlan.enabled(IosPrefs.forUser(uid))) reminderSync.checkAllowed()
            }
            onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }
        // The home-screen widget follows Today's calories left, as on Android.
        LaunchedEffect(today) {
            today.state.collect { state ->
                if (state is DashboardUiState.Success) widget.publish(today.day, state)
            }
        }
        val launch by IosLaunch.pending.collectAsState()
        val photoLog = remember(uid) { PhotoLog(accountScope, camera::lookup) }
        val history = remember(uid) { HistoryModel(log = log, prefs = IosPrefs.forUser(uid), scope = accountScope, scaleReadings = scale::mine) }
        var tab by remember(uid) { mutableStateOf(MainTab.TODAY) }
        // How the tab was reached, for usage reports: start, menu or card (the coach card on Today).
        var tabHow by remember(uid) { mutableStateOf("start") }
        LaunchedEffect(tab) {
            when (tab) {
                MainTab.TODAY -> UsageEvents.screenOpened("home")
                MainTab.PROGRESS -> UsageEvents.screenOpened("progress", tabHow)
                MainTab.COACH -> UsageEvents.screenOpened("coach", tabHow)
            }
        }
        var showSettings by remember(uid) { mutableStateOf(false) }
        LaunchedEffect(showSettings) { if (showSettings) UsageEvents.screenOpened("settings") }
        // The monthly recap: offered on Today from the 1st, and kept under Progress › Insights.
        var recapMonth by remember(uid) { mutableStateOf<YearMonth?>(null) }
        var recapOffer by remember(uid) { mutableStateOf<YearMonth?>(null) }
        val recapSetup: suspend () -> RecapSetup = remember(uid) { { iosRecapSetup(recap, health, IosPrefs.forUser(uid)) } }
        LaunchedEffect(uid, recapMonth) {
            if (recapMonth == null) recapOffer = runCatching { RecapModel.homeOffer(log, IosPrefs.forUser(uid), recapSetup(), scale.mine()) }.getOrNull()
        }
        // "Your October review is ready" at 10 am on the 1st, set ahead (the iPhone can't run code then).
        LaunchedEffect(history, recapMonth) {
            history.progress.collect { page ->
                if (page != null) {
                    val now = Now.local()
                    reminders.scheduleRecap(
                        RecapData.reminder(
                            prefs = IosPrefs.forUser(uid),
                            now = now,
                            thisMonthReady = page.recapThisMonth,
                            lastMonthReady = RecapData.offeredMonth(now.date) in page.recapMonths,
                            monthName = ::monthName
                        )
                    )
                }
            }
        }
        // Tapping that reminder opens the recap.
        val recapLaunch by IosLaunch.recap.collectAsState()
        LaunchedEffect(recapLaunch) {
            recapLaunch?.let { m ->
                runCatching { YearMonth.parse(m) }.getOrNull()?.let {
                    showSettings = false
                    recapMonth = it
                }
                IosLaunch.recapHandled()
            }
        }
        // The widget's mic or camera brings Today forward first.
        LaunchedEffect(launch) {
            if (launch != null) {
                tab = MainTab.TODAY
                showSettings = false
                recapMonth = null
            }
        }
        val signOut = {
            widget.clear()
            reminders.cancelAll()
            reminders.scheduleRecap(null)
            auth.signOut()
            signedIn = false
            email = null
        }
        var deleting by remember(uid) { mutableStateOf(false) }
        val deleteAccount = {
            if (!deleting) {
                deleting = true
                accountScope.launch {
                    val done = IosAccountDeletion(auth, backup, database, local, uid).delete()
                    deleting = false
                    if (done == null) {
                        accountNotice = IosAccountDeletion.NOT_DELETED
                    } else {
                        accountNotice = done
                        signOut()
                    }
                }
            }
        }
        recapMonth?.let { month ->
            val recapModel = remember(month) { RecapModel(log, IosPrefs.forUser(uid), accountScope, month, recapSetup, scale::mine) }
            val host = remember(month) {
                IosRecapHost(
                    media = recap,
                    prefs = IosPrefs.forUser(uid),
                    month = month,
                    onOpenSettings = {
                        recapMonth = null
                        showSettings = true
                    },
                    notice = shareCards::notice
                )
            }
            DisposableEffect(host) { onDispose { host.release() } }
            // The story pauses (and its music stops) while the app is in the background.
            var inBackground by remember { mutableStateOf(false) }
            DisposableEffect(Unit) {
                val center = NSNotificationCenter.defaultCenter
                val away = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ -> inBackground = true }
                val back = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> inBackground = false }
                onDispose {
                    center.removeObserver(away)
                    center.removeObserver(back)
                }
            }
            val state by recapModel.state.collectAsState()
            CompositionLocalProvider(LocalShareCardHost provides shareHost) {
                RecapStoryScreen(month, state, host, inBackground, onBack = { recapMonth = null }, onReload = recapModel::reload)
            }
            return
        }
        if (showSettings) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalShareCardHost provides shareHost) {
                    SettingsPage(
                        model = today,
                        prefs = IosPrefs.forUser(uid),
                        email = email,
                        name = auth.currentName(),
                        signInMethod = auth.signInMethod(),
                        themeMode = themeMode,
                        onThemeMode = onThemeMode,
                        onBack = { showSettings = false },
                        onSignOut = signOut,
                        reminders = {
                            val prefs = IosPrefs.forUser(uid)
                            var enabled by remember { mutableStateOf(ReminderPlan.enabled(prefs)) }
                            var time by remember { mutableStateOf(ReminderPlan.hour(prefs) to ReminderPlan.minute(prefs)) }
                            val allowed by reminderSync.allowed.collectAsState()
                            ReminderSettingsCard(
                                enabled = enabled,
                                hour = time.first,
                                minute = time.second,
                                allowed = allowed,
                                onEnabled = {
                                    enabled = it
                                    reminderSync.setEnabled(it)
                                },
                                onTime = { h, m ->
                                    time = h to m
                                    reminderSync.setTime(h, m)
                                },
                                onOpenPhoneSettings = ::openPhoneSettings
                            )
                        },
                        coachMemory = {
                            val notes by coachChat.notes.collectAsState()
                            CoachMemoryCard(
                                notes = notes,
                                onRemove = coachChat::deleteNote,
                                onAdd = coachChat::addNote,
                                onClearChat = coachChat::clearHistory
                            )
                        },
                        usageReports = usage.isEnabled(),
                        onUsageReports = usage::setEnabled,
                        widgetHelp = "Calories left, plus mic and camera buttons to log in seconds. To add it, touch and hold your home screen, tap Edit, then Add Widget, and find Superfit.",
                        onDeleteAccount = deleteAccount
                    )
                }
                if (deleting) {
                    Box(Modifier.fillMaxSize().background(SuperfitTheme.colors.bgStart.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                        Text("Deleting your account…", style = MaterialTheme.typography.bodyLarge, color = SuperfitTheme.colors.textPrimary)
                    }
                }
            }
            return
        }
        val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        CompositionLocalProvider(LocalShareCardHost provides shareHost) {
            Scaffold(
                containerColor = SuperfitTheme.colors.bgStart,
                // Each screen handles the top of the phone itself; the menu handles the bottom.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                // The keyboard covers the menu anyway; leaving it out stops its height showing as a gap
                // between a text box and the keyboard.
                bottomBar = {
                    if (!imeUp) {
                        MainMenuBar(tab, onSelect = {
                            tabHow = "menu"
                            tab = it
                        }, tabs = listOf(MainTab.TODAY, MainTab.PROGRESS, MainTab.COACH))
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).dismissKeyboardOnTapOrScroll()) {
                    when (tab) {
                        MainTab.COACH -> CoachTab(coachChat, today)
                        MainTab.PROGRESS -> ProgressTabs(
                            model = history,
                            today = today.state.collectAsState().value,
                            onShareReport = { weeks ->
                                accountScope.launch {
                                    history.buildReport(weeks, name = auth.currentName())?.let {
                                        UsageEvents.reportShared(weeks)
                                        report.share(it)
                                    }
                                }
                            },
                            bests = today.personalBests.collectAsState().value.all,
                            onOpenRecap = { recapMonth = it }
                        )
                        else -> TodayScreen(
                            today,
                            onOpenSettings = { showSettings = true },
                            onOpenPhoneSettings = ::openPhoneSettings,
                            camera = camera,
                            photoLog = photoLog,
                            launch = launch,
                            onLaunchHandled = IosLaunch::handled,
                            onOpenCoach = { roast ->
                                if (roast) coachChat.send(CoachChat.ROAST_MY_DAY, how = "chip")
                                tabHow = "card"
                                tab = MainTab.COACH
                            },
                            recapCard = recapOffer?.let { month -> { RecapReadyCard(month, onOpen = { recapMonth = month }) } }
                        ) {
                            if (restored?.failed == true) {
                                Text(
                                    text = "Couldn't reach your backup. Check your internet and open the app again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuperfitTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The Coach tab: the shared chat, with the iPhone's speech recognition on its mic. */
@Composable
private fun CoachTab(chat: CoachChatModel, today: TodayModel) {
    var shareCard by remember { mutableStateOf<ShareCard?>(null) }
    val day by today.state.collectAsState()
    val coaching by today.coaching.collectAsState()
    val messages by chat.messages.collectAsState()
    val loading by chat.loading.collectAsState()
    val notes by chat.notes.collectAsState()
    val style by chat.style.collectAsState()
    val voice = chat.voiceLog
    val voiceUi = voice?.ui?.collectAsState()?.value
    val notice = voice?.notice?.collectAsState()?.value
    val micOff = voice?.micOff?.collectAsState()?.value == true
    // Leaving the tab stops listening without sending anything.
    DisposableEffect(voice) { onDispose { voice?.cancel() } }
    if (micOff) {
        MicOffDialog(
            onOpenSettings = {
                voice?.dismissMicOff()
                openPhoneSettings()
            },
            onDismiss = { voice?.dismissMicOff() }
        )
    }
    CoachChatView(
        messages = messages,
        loading = loading,
        notes = notes,
        style = style,
        questions = chat::questions,
        mic = ChatMic(
            available = voice?.available == true,
            listening = voiceUi?.listening == true,
            liveText = voiceUi?.liveText.orEmpty(),
            problem = notice?.let { if (it == VoiceLog.NOTHING_HEARD_LINE) "I didn't catch that. Tap the mic and try again." else it },
            onStart = { voice?.start(LogKind.ANY) },
            onStop = { voice?.stop() },
            onProblemShown = { voice?.dismissNotice() }
        ),
        onSend = chat::send,
        onUndo = chat::undoNotes,
        onLogMeal = chat::logSuggestedMeal,
        // Any roast can go on a card, as on Android.
        onShareRoast = { line ->
            (day as? DashboardUiState.Success)?.let { shareCard = ShareCards.roast(dailyShareCard(it, coaching), line) }
        }
    )
    shareCard?.let { card -> ShareCardSheet(card = card, onDismiss = { shareCard = null }) }
}

/** What the recap's "Get more from Superfit" card checks on iPhone: Apple Health, the mic and the widget. */
private suspend fun iosRecapSetup(media: IosRecapMedia, health: IosHealthSource, prefs: Prefs): RecapSetup {
    val widgetPlaced = suspendCancellableCoroutine { cont -> media.widgetPlaced { cont.resume(it) } }
    return RecapSetup(
        healthConnect = !health.isAvailable() || prefs.getBoolean(HealthSync.KEY_ASKED, false),
        mic = media.micAllowed(),
        widget = widgetPlaced
    )
}
