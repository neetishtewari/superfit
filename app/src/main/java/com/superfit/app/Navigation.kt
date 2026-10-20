package com.superfit.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.firebase.auth.FirebaseAuth
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.DataRepository
import com.superfit.app.data.GeminiKeyStore
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.data.ReminderNotifications
import com.superfit.app.data.getUserSharedPrefs
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.auth.LoginViewModel
import com.superfit.app.ui.dashboard.DashboardScreen
import com.superfit.app.ui.dashboard.DashboardViewModel
import com.superfit.app.ui.dashboard.SettingsScreen
import com.superfit.app.ui.history.HistoryScreen
import com.superfit.app.ui.history.HistoryViewModel
import com.superfit.app.ui.onboarding.OnboardingScreen
import com.superfit.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    healthConnectManager: HealthConnectManager,
    repository: DataRepository,
    triggerVoiceLog: Boolean = false,
    triggerFavoritesLog: Boolean = false,
    triggerPhotoLog: Boolean = false,
    onVoiceLogTriggeredHandled: () -> Unit = {},
    onFavoritesLogTriggeredHandled: () -> Unit = {},
    onPhotoLogTriggeredHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    var startDestination by remember { mutableStateOf<NavKey?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startDestination = Login
        } else {
            val localProfile = repository.getProfile()
            if (localProfile != null) {
                startDestination = Dashboard
            } else {
                // If local database was wiped but user is logged in, attempt to restore from Firestore
                try {
                    repository.firebaseSyncManager.syncAllDown()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val hasProfile = repository.getProfile() != null
                startDestination = if (hasProfile) Dashboard else Onboarding
            }
            // Re-upload meals, workouts and weights that older versions failed to back up.
            coroutineScope.launch {
                repository.firebaseSyncManager.repairEntryBackupsOnce()
            }
        }
    }

    val startDest = startDestination
    if (startDest == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val backStack = rememberNavBackStack(startDest)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                val loginViewModel: LoginViewModel = viewModel {
                    LoginViewModel(repository)
                }
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        coroutineScope.launch {
                            try {
                                repository.firebaseSyncManager.syncAllDown()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            val hasProfile = repository.getProfile() != null
                            backStack.removeLastOrNull()
                            if (hasProfile) {
                                backStack.add(Dashboard)
                            } else {
                                backStack.add(Onboarding)
                            }
                        }
                    }
                )
            }
            entry<Onboarding> {
                val onboardingViewModel: OnboardingViewModel = viewModel {
                    OnboardingViewModel(repository, context.applicationContext)
                }
                OnboardingScreen(
                    healthConnectManager = healthConnectManager,
                    viewModel = onboardingViewModel,
                    onOnboardingComplete = {
                        backStack.removeLastOrNull()
                        backStack.add(Dashboard)
                    },
                    onBack = if (backStack.size > 1) {
                        { backStack.removeLastOrNull() }
                    } else {
                        null
                    }
                )
            }
            entry<Dashboard> {
                AskForNotificationPermissionOnce()
                LaunchedEffect(Unit) { AppAnalytics.screenOpened("home") }
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(repository, context.applicationContext)
                }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    triggerVoiceLog = triggerVoiceLog,
                    triggerFavoritesLog = triggerFavoritesLog,
                    onVoiceLogTriggeredHandled = onVoiceLogTriggeredHandled,
                    onFavoritesLogTriggeredHandled = onFavoritesLogTriggeredHandled,
                    triggerPhotoLog = triggerPhotoLog,
                    onPhotoLogTriggeredHandled = onPhotoLogTriggeredHandled,
                    onNavigateToOnboarding = {
                        backStack.removeLastOrNull()
                        backStack.add(Onboarding)
                    },
                    onNavigateToHistory = {
                        backStack.add(History)
                    },
                    onNavigateToSettings = {
                        backStack.add(Settings)
                    }
                )
            }
            entry<Settings> {
                LaunchedEffect(Unit) { AppAnalytics.screenOpened("settings") }
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(repository, context.applicationContext)
                }
                SettingsScreen(
                    viewModel = dashboardViewModel,
                    onBack = {
                        backStack.removeLastOrNull()
                    },
                    onLogout = {
                        coroutineScope.launch {
                            repository.clearAllCloudData()
                            repository.clearAllLocalData()
                            val prefs = getUserSharedPrefs(context)
                            prefs.edit().clear().commit()
                            GeminiKeyStore(context).clear()
                            FirebaseAuth.getInstance().signOut()
                            backStack.removeLastOrNull() // pop Settings
                            backStack.removeLastOrNull() // pop Dashboard
                            backStack.add(Login)
                        }
                    },
                    onSignOut = {
                        coroutineScope.launch {
                            FirebaseAuth.getInstance().signOut()
                            backStack.removeLastOrNull() // pop Settings
                            backStack.removeLastOrNull() // pop Dashboard
                            backStack.add(Login)
                        }
                    },
                    onNavigateToOnboarding = {
                        backStack.add(Onboarding)
                    }
                )
            }
            entry<History> {
                val historyViewModel: HistoryViewModel = viewModel {
                    HistoryViewModel(repository, context.applicationContext)
                }
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(repository, context.applicationContext)
                }
                HistoryScreen(
                    viewModel = historyViewModel,
                    dashboardViewModel = dashboardViewModel,
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}

/**
 * Shows Android's "Allow notifications?" prompt the first time the home screen opens, so the
 * daily reminder (on by default) can actually appear.
 */
@Composable
private fun AskForNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ReminderNotifications.shouldAskOnLaunch(context)) {
            ReminderNotifications.markAsked(context)
            launcher.launch(ReminderNotifications.PERMISSION)
        }
    }
}
