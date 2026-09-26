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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.*
import com.superfit.app.ui.dashboard.DashboardViewModel
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.GoalProgressCard
import com.superfit.app.ui.dashboard.FoodQualityCard
import com.superfit.app.ui.dashboard.FoodSwapCard
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
    var selectedTab by remember { mutableStateOf(0) } // 0 = LOG HISTORY, 1 = PROGRESS & ANALYTICS
    val dashboardState = dashboardViewModel?.dashboardState?.collectAsState()?.value

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
                            text = if (selectedTab == 0) "HISTORY LEDGER" else "PROGRESS METRICS",
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
                // Top Segmented Control Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ThemeCardBgTranslucent)
                        .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 0) NeonMint else Color.Transparent)
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOG HISTORY",
                            color = if (selectedTab == 0) Color.Black else ThemeTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 1) NeonMint else Color.Transparent)
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PROGRESS",
                            color = if (selectedTab == 1) Color.Black else ThemeTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (selectedTab == 1 && dashboardState is DashboardUiState.Success) {
                    val state = dashboardState as DashboardUiState.Success

                    // Goal & Weight Tracker Card
                    GoalProgressCard(
                        metrics = state.weightMetrics,
                        onLogWeight = { weight, note ->
                            dashboardViewModel?.logWeight(weight, note)
                        },
                        onUpdateGoal = { start, target ->
                            dashboardViewModel?.updateWeightGoal(start, target)
                        }
                    )

                    // Food Quality Index Card
                    FoodQualityCard(
                        metrics = state.dietQualityMetrics
                    )

                    // Smart Food Swaps
                    state.dietQualityMetrics.swapSuggestions.forEach { swap ->
                        FoodSwapCard(
                            swap = swap,
                            onLogSwap = { foodText ->
                                dashboardViewModel?.parseAndAddMeal(foodText)
                            }
                        )
                    }
                } else {
                    // 1. Consistency Score Gauge
                    ConsistencyScoreGauge(
                        deficitScore = viewModel.consistencyScore,
                        deficitDays = viewModel.deficitDaysCount,
                        loggedDays = viewModel.loggedDaysCount,
                        trainingScore = viewModel.workoutConsistencyScore
                    )
                }

                // Calorie Trend Chart
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

                // 1.5 Weekly Trends Card
                viewModel.weeklyTrends?.let { trends ->
                    WeeklyTrendsCard(trends = trends)
                }

                // 2. Calendar Card
                CalendarCard(
                    currentMonthName = viewModel.currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + viewModel.currentMonth.year,
                    daysInMonth = viewModel.daysInMonth,
                    selectedDate = viewModel.selectedDate,
                    onDateSelected = { viewModel.selectDate(it) },
                    onPrevMonth = { viewModel.prevMonth() },
                    onNextMonth = { viewModel.nextMonth() }
                )

                // 3. Selected Day's Balance Card
                viewModel.selectedSummary?.let { summary ->
                    DayBalanceCard(
                        summary = summary,
                        selectedDate = viewModel.selectedDate,
                        viewModel = viewModel
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
