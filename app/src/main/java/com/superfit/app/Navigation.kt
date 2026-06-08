package com.superfit.app

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
import com.superfit.app.data.DataRepository
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.ui.auth.LoginScreen
import com.superfit.app.ui.auth.LoginViewModel
import com.superfit.app.ui.dashboard.DashboardScreen
import com.superfit.app.ui.dashboard.DashboardViewModel
import com.superfit.app.ui.dashboard.SettingsScreen
import com.superfit.app.ui.history.HistoryScreen
import com.superfit.app.ui.history.HistoryViewModel
import android.content.Context
import com.superfit.app.data.getUserSharedPrefs
import com.superfit.app.ui.onboarding.OnboardingScreen
import com.superfit.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    healthConnectManager: HealthConnectManager,
    repository: DataRepository,
    triggerVoiceLog: Boolean = false,
    triggerFavoritesLog: Boolean = false,
    onVoiceLogTriggeredHandled: () -> Unit = {},
    onFavoritesLogTriggeredHandled: () -> Unit = {}
) {
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
    val context = LocalContext.current

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
                    onBack = if (backStack.size > 1) { { backStack.removeLastOrNull() } } else null
                )
            }
            entry<Dashboard> {
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(repository, context.applicationContext)
                }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    triggerVoiceLog = triggerVoiceLog,
                    triggerFavoritesLog = triggerFavoritesLog,
                    onVoiceLogTriggeredHandled = onVoiceLogTriggeredHandled,
                    onFavoritesLogTriggeredHandled = onFavoritesLogTriggeredHandled,
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
                HistoryScreen(
                    viewModel = historyViewModel,
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
