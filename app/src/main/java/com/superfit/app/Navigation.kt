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
import com.superfit.app.ui.history.HistoryScreen
import com.superfit.app.ui.history.HistoryViewModel
import com.superfit.app.ui.onboarding.OnboardingScreen
import com.superfit.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    healthConnectManager: HealthConnectManager,
    repository: DataRepository
) {
    var startDestination by remember { mutableStateOf<NavKey?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startDestination = Login
        } else {
            val hasProfile = repository.getProfile() != null
            startDestination = if (hasProfile) Dashboard else Onboarding
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
                    OnboardingViewModel(repository)
                }
                OnboardingScreen(
                    healthConnectManager = healthConnectManager,
                    viewModel = onboardingViewModel,
                    onOnboardingComplete = {
                        backStack.removeLastOrNull()
                        backStack.add(Dashboard)
                    }
                )
            }
            entry<Dashboard> {
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(repository, context.applicationContext)
                }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToOnboarding = {
                        backStack.removeLastOrNull()
                        backStack.add(Onboarding)
                    },
                    onNavigateToHistory = {
                        backStack.add(History)
                    },
                    onLogout = {
                        coroutineScope.launch {
                            repository.clearAllLocalData()
                            FirebaseAuth.getInstance().signOut()
                            backStack.removeLastOrNull()
                            backStack.add(Login)
                        }
                    },
                    onSignOut = {
                        coroutineScope.launch {
                            FirebaseAuth.getInstance().signOut()
                            backStack.removeLastOrNull()
                            backStack.add(Login)
                        }
                    }
                )
            }
            entry<History> {
                val historyViewModel: HistoryViewModel = viewModel {
                    HistoryViewModel(repository)
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
