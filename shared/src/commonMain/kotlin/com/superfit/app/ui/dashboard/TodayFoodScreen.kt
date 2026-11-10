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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.theme.SuperfitTheme

/**
 * Today's Food side on the iPhone: header, calorie rings, streak, nutrients, weigh-in and meals,
 * the same shared cards Android shows. [footer] holds what the iPhone still adds for now (sign out).
 */
@Composable
fun TodayFoodScreen(model: TodayModel, footer: @Composable () -> Unit = {}) {
    val state by model.state.collectAsState()
    val parsing by model.parsing.collectAsState()
    val predictedFoods by model.predictedFoods.collectAsState()
    var foodInput by remember { mutableStateOf("") }
    var showAllFoods by remember { mutableStateOf(false) }

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
                DashboardHeader(grantedPermissions = emptySet(), hasHealthConnectPermissions = true, onNavigateToSettings = {})
                CalorieRingsCard(state = s)
                StreakHeaderCard(streakState = s.streakState)
                NutrientLedgerCard(state = s)
                WeightQuickRow(
                    currentWeightKg = s.weightMetrics.currentWeightKg,
                    weightEntries = s.weightEntries,
                    onLogWeight = model::logWeight
                )
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
                footer()
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
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
