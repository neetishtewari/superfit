package com.superfit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.superfit.app.data.DataRepository
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.ui.dashboard.DashboardScreen
import com.superfit.app.ui.dashboard.DashboardViewModel
import com.superfit.app.ui.onboarding.OnboardingScreen
import com.superfit.app.ui.onboarding.OnboardingViewModel

@Composable
fun MainNavigation(
    healthConnectManager: HealthConnectManager,
    repository: DataRepository
) {
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        hasProfile = repository.getProfile() != null
    }

    val startDestination = when (hasProfile) {
        true -> Dashboard
        false -> Onboarding
        null -> null
    }

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val backStack = rememberNavBackStack(startDestination)
    val context = LocalContext.current

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
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
                    }
                )
            }
        }
    )
}
