package com.superfit.app.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.*
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.DashboardViewModel
import com.superfit.app.ui.dashboard.FoodQualityCard
import com.superfit.app.ui.dashboard.FoodSwapCard
import com.superfit.app.ui.dashboard.PersonalBestsList
import com.superfit.app.ui.dashboard.WeighInDialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    dashboardViewModel: DashboardViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val bodyPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onBodyAccessResult() }
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Insights, 1 = Journey, 2 = Log
    LaunchedEffect(selectedTab) { com.superfit.app.data.AppAnalytics.screenOpened(listOf("insights", "journey", "log")[selectedTab]) }
    val dashboardState = dashboardViewModel?.dashboardState?.collectAsState()?.value
    val bests = dashboardViewModel?.personalBests?.collectAsState()?.value
    val dietNote = dashboardViewModel?.dietNote?.collectAsState()?.value
    var showWeighIn by remember { mutableStateOf(false) }
    var showGoalEdit by remember { mutableStateOf(false) }
    var shareCard by remember { mutableStateOf<com.superfit.app.domain.ShareCard?>(null) }

    // Refresh data when navigating to this screen
    LaunchedEffect(Unit) {
        viewModel.loadData()
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
        // Ambient background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HyperViolet.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.minDimension * 0.8f
                ),
                radius = size.minDimension * 0.8f,
                center = Offset(size.width * 0.8f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonMint.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.7f),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width * 0.2f, size.height * 0.7f)
            )
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "INSIGHTS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = ThemeTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = ThemeCardBg,
                                contentColor = ThemeTextPrimary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Insights | Journey | Log
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ThemeCardBgTranslucent)
                        .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("INSIGHTS", "JOURNEY", "LOG").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selectedTab == index) NeonMint else Color.Transparent)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selectedTab == index) Color.Black else ThemeTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> viewModel.weekReport?.let { report ->
                        InsightsTab(
                            report = report,
                            onOpenJourney = { selectedTab = 1 },
                            onShareWithCoach = { weeks -> viewModel.shareWithCoach(context, weeks) },
                            foodQuality = {
                                if (dashboardState is DashboardUiState.Success) {
                                    FoodQualityCard(
                                        metrics = dashboardState.dietQualityMetrics,
                                        onLogSwap = { foodText -> dashboardViewModel?.parseAndAddMeal(foodText) },
                                        note = dietNote,
                                        onFineForMe = { item, fine -> dashboardViewModel?.setFineForMe(item.approvalKey, fine) }
                                    )
                                    dashboardState.dietQualityMetrics.swapSuggestions.forEach { swap ->
                                        FoodSwapCard(swap = swap, onLogSwap = { foodText -> dashboardViewModel?.parseAndAddMeal(foodText) })
                                    }
                                }
                            },
                            bests = {
                                PersonalBestsList(bests = bests?.all.orEmpty(), onShare = { best ->
                                    shareCard = com.superfit.app.domain.ShareCards.best(best, isNew = false, today = LocalDate.now())
                                })
                            },
                            trends = {
                                if (dashboardState is DashboardUiState.Success) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TrendItem(
                                            label = "CURRENT STREAK",
                                            value = "${dashboardState.streakState.currentStreak} ${if (dashboardState.streakState.currentStreak == 1) "day" else "days"}",
                                            valueColor = SolarAmber,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TrendItem(
                                            label = "LONGEST STREAK",
                                            value = "${dashboardState.streakState.longestStreak} ${if (dashboardState.streakState.longestStreak == 1) "day" else "days"}",
                                            valueColor = ElectricCyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                ConsistencyScoreGauge(
                                    deficitScore = viewModel.consistencyScore,
                                    deficitDays = viewModel.deficitDaysCount,
                                    loggedDays = viewModel.loggedDaysCount,
                                    trainingScore = viewModel.workoutConsistencyScore
                                )
                                viewModel.weeklyTrends?.let { trends -> WeeklyTrendsCard(trends = trends) }
                            }
                        )
                    }
                    1 -> viewModel.journey?.let { journey ->
                        JourneyTab(
                            j = journey,
                            goalSet = viewModel.goalSet,
                            onWeighIn = { showWeighIn = true },
                            onEditGoal = { showGoalEdit = true },
                            body = viewModel.body,
                            canAskBodyAccess = viewModel.canAskBodyAccess,
                            onAllowBody = {
                                com.superfit.app.ui.launchHealthConnectPermissions(context, bodyPermissionLauncher, viewModel.bodyPermissions)
                            }
                        )
                    }
                    else -> {
                        // Calorie Trend Chart for the week up to the chosen day
                        val daysInMonth = viewModel.daysInMonth
                        if (daysInMonth.isNotEmpty()) {
                            val selectedDate = viewModel.selectedDate
                            val chartDays = remember(daysInMonth, selectedDate) {
                                val selectedIndex = daysInMonth.indexOfFirst { it.date == selectedDate }
                                if (selectedIndex != -1) {
                                    val start = (selectedIndex - 6).coerceAtLeast(0)
                                    daysInMonth.subList(start, selectedIndex + 1)
                                } else {
                                    daysInMonth.takeLast(7)
                                }
                            }
                            CalorieTrendChart(days = chartDays)
                        }

                        CalendarCard(
                            currentMonthName = viewModel.currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + viewModel.currentMonth.year,
                            daysInMonth = viewModel.daysInMonth,
                            selectedDate = viewModel.selectedDate,
                            onDateSelected = { viewModel.selectDate(it) },
                            onPrevMonth = { viewModel.prevMonth() },
                            onNextMonth = { viewModel.nextMonth() }
                        )

                        viewModel.selectedSummary?.let { summary ->
                            DayBalanceCard(
                                summary = summary,
                                selectedDate = viewModel.selectedDate,
                                viewModel = viewModel
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        if (showWeighIn) {
            WeighInDialog(
                currentWeight = viewModel.journey?.currentKg ?: 70.0,
                onDismiss = { showWeighIn = false },
                onConfirm = { kg ->
                    viewModel.logWeight(kg)
                    showWeighIn = false
                }
            )
        }
        if (showGoalEdit) {
            viewModel.journey?.let { j ->
                GoalEditDialog(
                    startingWeight = j.startKg,
                    targetWeight = j.targetKg,
                    targetDate = viewModel.goalTargetDate,
                    onDismiss = { showGoalEdit = false },
                    onConfirm = { start, target, date ->
                        viewModel.updateGoal(start, target, date)
                        showGoalEdit = false
                    }
                )
            }
        }
        shareCard?.let { card ->
            com.superfit.app.ui.share.ShareCardSheet(card = card, onDismiss = { shareCard = null })
        }
    }
}
