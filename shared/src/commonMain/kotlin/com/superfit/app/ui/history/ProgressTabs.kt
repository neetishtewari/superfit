package com.superfit.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.LocalFormat
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.FoodQualityCard
import com.superfit.app.ui.dashboard.FoodSwapCard
import com.superfit.app.ui.dashboard.SegmentedPills
import com.superfit.app.ui.dashboard.WeighInDialog

/**
 * Progress on the iPhone: Insights, Journey and History, with the same cards as Android.
 * [today] supplies the streak and food quality that Today already works out.
 */
@Composable
fun ProgressTabs(
    model: HistoryModel,
    today: DashboardUiState,
    /** "Share my report" for the last 2 or 4 weeks; null hides it. */
    onShareReport: ((weeks: Int) -> Unit)? = null
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var showWeighIn by remember { mutableStateOf(false) }
    var showGoalEdit by remember { mutableStateOf(false) }
    val progress by model.progress.collectAsState()
    // Each page keeps its own scroll place, as on Android.
    val scrolls = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    Box(Modifier.fillMaxSize().background(SuperfitTheme.colors.bgStart)) {
        Column(Modifier.statusBarsPadding()) {
            Text(
                "Progress",
                style = MaterialTheme.typography.titleLarge,
                color = ThemeTextPrimary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            SegmentedPills(
                options = listOf("0" to "Insights", "1" to "Journey", "2" to "History"),
                selected = tab.toString(),
                onSelect = { tab = it.toInt() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                icons = mapOf(
                    "0" to (Icons.Rounded.Lightbulb to Icons.Outlined.Lightbulb),
                    "1" to (Icons.Rounded.Flag to Icons.Outlined.Flag),
                    "2" to (Icons.Rounded.History to Icons.Outlined.History)
                )
            )
            val p = progress
            if (tab < 2 && p == null) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NeonMint)
                }
                return@Column
            }
            Column(
                modifier = Modifier
                    .verticalScroll(scrolls[tab])
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    tab == 0 && p != null -> InsightsTab(
                        report = p.progress.report,
                        onOpenJourney = { tab = 1 },
                        bodyNow = {
                            BodyNowCard(
                                body = p.progress.body,
                                weightKg = p.progress.latestWeightKg,
                                weightChange4w = p.progress.weightChange4w,
                                goalKgChange = p.progress.goalKgChange
                            )
                        },
                        // Personal bests come with share cards.
                        onShareWithCoach = onShareReport,
                        bests = null,
                        foodQuality = {
                            if (today is DashboardUiState.Success) {
                                FoodQualityCard(
                                    metrics = today.dietQualityMetrics,
                                    onFineForMe = { item, fine -> model.setFineForMe(item.approvalKey, fine) }
                                )
                                today.dietQualityMetrics.swapSuggestions.forEach { FoodSwapCard(swap = it) }
                            }
                        },
                        trends = { Trends(p, today) }
                    )
                    tab == 1 && p != null -> JourneyTab(
                        j = p.progress.journey,
                        goalSet = p.progress.goalSet,
                        onWeighIn = { showWeighIn = true },
                        onEditGoal = { showGoalEdit = true },
                        bodyTrends = {
                            BodyTrendsCard(
                                series = p.progress.bodySeries,
                                period = p.period,
                                onPeriod = model::choosePeriod,
                                insight = p.progress.body.insight,
                                // Body readings from a scale come with Apple Health.
                                canAskBodyAccess = false,
                                onAllowBody = {},
                                hasHistory = p.progress.bodyHasHistory
                            )
                        }
                    )
                    else -> HistoryPageContent(model)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        val p = progress
        if (showWeighIn && p != null) {
            WeighInDialog(
                currentWeight = p.progress.journey.currentKg,
                onDismiss = { showWeighIn = false },
                onConfirm = { kg ->
                    model.logWeight(kg)
                    showWeighIn = false
                }
            )
        }
        if (showGoalEdit && p != null) {
            GoalEditDialog(
                startingWeight = p.progress.journey.startKg,
                targetWeight = p.progress.journey.targetKg,
                targetDate = p.progress.goalDate,
                onDismiss = { showGoalEdit = false },
                onConfirm = { start, target, date ->
                    model.updateGoal(start, target, date)
                    showGoalEdit = false
                }
            )
        }
    }
}

@Composable
private fun Trends(p: ProgressPage, today: DashboardUiState) {
    if (today is DashboardUiState.Success) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val streak = today.streakState
            TrendItem(
                label = "Current streak",
                value = "${streak.currentStreak} ${if (streak.currentStreak == 1) "day" else "days"}",
                valueColor = SolarAmber,
                modifier = Modifier.weight(1f)
            )
            TrendItem(
                label = "Longest streak",
                value = "${streak.longestStreak} ${if (streak.longestStreak == 1) "day" else "days"}",
                valueColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
    ConsistencyScoreGauge(
        deficitScore = p.consistency.score,
        deficitDays = p.consistency.deficitDays,
        loggedDays = p.consistency.loggedDays,
        trainingScore = p.consistency.trainingScore
    )
    WeeklyTrendsCard(trends = p.trends)
}

/** History: the week's chart, the month calendar and the picked day, as on Android. */
@Composable
private fun HistoryPageContent(model: HistoryModel) {
    val page by model.page.collectAsState()
    val parsing by model.parsing.collectAsState()
    val predictedFoods by model.predictedFoods.collectAsState()
    val mealSuggester by model.mealSuggester.collectAsState()
    val p = page
    if (p == null) {
        CircularProgressIndicator(color = NeonMint)
        return
    }
    CalorieTrendChart(days = HistoryLog.chartWeek(p.days, p.selected))
    CalendarCard(
        currentMonthName = LocalFormat.monthYear(p.monthStart),
        daysInMonth = p.days,
        selectedDate = p.selected,
        onDateSelected = model::select,
        onPrevMonth = model::prevMonth,
        onNextMonth = model::nextMonth,
        canGoNext = p.canGoNext
    )
    DayBalanceCard(
        summary = p.summary,
        parsingState = parsing,
        predictedFoods = predictedFoods,
        onAddMeal = model::addMeal,
        onResetParsing = model::resetParsing,
        onUpdateMeal = model::updateMeal,
        onDeleteMeal = model::deleteMeal,
        onDeleteWorkout = model::deleteWorkout,
        // Hiding comes with Apple Health workouts; sharing with share cards.
        onHideImported = {},
        mealSuggester = mealSuggester
    )
}
