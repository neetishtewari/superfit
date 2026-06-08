package com.superfit.app.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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
                            text = "HISTORY LEDGER",
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Consistency Score Gauge
                ConsistencyScoreGauge(
                    deficitScore = viewModel.consistencyScore,
                    deficitDays = viewModel.deficitDaysCount,
                    loggedDays = viewModel.loggedDaysCount,
                    trainingScore = viewModel.workoutConsistencyScore
                )

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

@Composable
fun ConsistencyScoreGauge(
    deficitScore: Int,
    deficitDays: Int,
    loggedDays: Int,
    trainingScore: Int,
    modifier: Modifier = Modifier
) {
    val gaugeTrackColor = ThemeTextPrimary.copy(alpha = 0.05f)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CONSISTENCY METRICS (ROLLING 30 DAYS)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deficit Gauge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "CALORIE DEFICIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8.dp.toPx()
                            drawArc(
                                color = gaugeTrackColor,
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            )
                            val progressSweep = (deficitScore / 100f) * 260f
                            drawArc(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(NeonMint, ElectricCyan)
                                ),
                                startAngle = -220f,
                                sweepAngle = progressSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$deficitScore%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ThemeTextPrimary
                            )
                        }
                    }
                    Text(
                        text = "$deficitDays / $loggedDays days",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Training Gauge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "TRAINING DAYS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8.dp.toPx()
                            drawArc(
                                color = gaugeTrackColor,
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            )
                            val progressSweep = (trainingScore / 100f) * 260f
                            drawArc(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(HyperViolet, NeonMint)
                                ),
                                startAngle = -220f,
                                sweepAngle = progressSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$trainingScore%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ThemeTextPrimary
                            )
                        }
                    }
                    val workoutDays = ((trainingScore / 100f) * 30).toInt()
                    Text(
                        text = "$workoutDays / 30 days",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarCard(
    currentMonthName: String,
    daysInMonth: List<HistoryDayState>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevMonth,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = ThemeTextPrimary.copy(alpha = 0.05f))
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month", tint = ThemeTextPrimary)
                }

                Text(
                    text = currentMonthName.uppercase(Locale.US),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = ThemeTextPrimary,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = onNextMonth,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = ThemeTextPrimary.copy(alpha = 0.05f))
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = ThemeTextPrimary)
                }
            }

            // Calendar weekday names
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val weekdays = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
                weekdays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            // Compute offsets for grid alignment
            if (daysInMonth.isNotEmpty()) {
                val firstDay = daysInMonth.first().date
                // Monday is 1, Sunday is 7. Convert to Sunday = 0 offset
                val offset = firstDay.dayOfWeek.value % 7
                val totalCells = offset + daysInMonth.size
                val rows = (totalCells + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (c in 0 until 7) {
                                val index = r * 7 + c
                                if (index < offset || index >= totalCells) {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val dayState = daysInMonth[index - offset]
                                    CalendarDayCell(
                                        dayState = dayState,
                                        isSelected = dayState.date == selectedDate,
                                        onClick = { onDateSelected(dayState.date) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Legend / Color indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = NeonMint, label = "Deficit")
                val untrackedColor = if (SuperfitTheme.isDark) Color(0xFF2E2E3A) else Color(0xFFCBD5E1)
                LegendItem(color = CoralRed, label = "Surplus")
                LegendItem(color = untrackedColor, label = "Untracked (<2 meals)")
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    dayState: HistoryDayState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val untrackedColor = if (SuperfitTheme.isDark) Color(0xFF2E2E3A) else Color(0xFFCBD5E1)
    val bgColor = when (dayState.status) {
        DayStatus.Deficit -> NeonMint.copy(alpha = 0.20f)
        DayStatus.Surplus -> CoralRed.copy(alpha = 0.20f)
        DayStatus.Insufficient -> untrackedColor.copy(alpha = 0.3f)
    }

    val borderColor = when {
        isSelected -> HyperViolet
        dayState.date == LocalDate.now() -> HyperViolet.copy(alpha = 0.6f)
        else -> when (dayState.status) {
            DayStatus.Deficit -> NeonMint
            DayStatus.Surplus -> CoralRed
            DayStatus.Insufficient -> Color.Transparent
        }
    }

    val borderWidth = when {
        isSelected -> 2.dp
        dayState.date == LocalDate.now() -> 2.dp
        else -> 1.dp
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayState.date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (dayState.date == LocalDate.now() || isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (dayState.status == DayStatus.Insufficient) ThemeTextTertiary else ThemeTextPrimary
            )
            if (dayState.status != DayStatus.Insufficient) {
                val net = (dayState.caloriesEaten - dayState.tdee).toInt()
                Text(
                    text = "${if (net > 0) "+" else ""}$net",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (net < 0) NeonMint else CoralRed
                )
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.3f))
                .border(1.dp, color, RoundedCornerShape(3.dp))
        )
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun DayBalanceCard(
    summary: DaySummaryState,
    selectedDate: LocalDate,
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val netColor = if (summary.netBalance < 0) NeonMint else CoralRed
    val balanceLabel = if (summary.netBalance < 0) "CALORIE DEFICIT" else "CALORIE SURPLUS"

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val dateLabel = when (selectedDate) {
        today -> "TODAY"
        yesterday -> "YESTERDAY"
        else -> selectedDate.toString()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BALANCE FOR $dateLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )

                Text(
                    text = if (summary.netBalance < 0) "ON TARGET" else "SURPLUS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.netBalance < 0) NeonMint else CoralRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background((if (summary.netBalance < 0) NeonMint else CoralRed).copy(alpha = 0.15f))
                        .border(1.dp, (if (summary.netBalance < 0) NeonMint else CoralRed).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Stats breakdown grid (Calories, TDEE, Steps, Active Burn)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "EATEN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = "${summary.caloriesEaten.toInt()} kcal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "TDEE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = "${summary.tdee.toInt()} kcal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "STEPS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = String.format("%,d", summary.steps), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "BURN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = "${summary.activeBurn.toInt()} kcal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                }
            }

            // Net balance bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = balanceLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = "${if (summary.netBalance > 0) "+" else ""}${summary.netBalance.toInt()} kcal",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = netColor
                )
            }

            // List of meals ledger
            Text(
                text = "LOGGED MEALS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )

            if (summary.meals.isEmpty()) {
                Text(
                    text = "No meals logged on this day.",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.meals.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.foodText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextPrimary
                                )
                                Text(
                                    text = "P: ${entry.proteinG.toInt()}g | C: ${entry.carbsG.toInt()}g | F: ${entry.fatG.toInt()}g",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${entry.calories.toInt()} kcal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonMint
                                )
                                IconButton(
                                    onClick = { viewModel.deleteMeal(entry) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Meal",
                                        tint = CoralRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of workouts ledger
            Text(
                text = "LOGGED WORKOUTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )

            if (summary.workouts.isEmpty()) {
                Text(
                    text = "No workouts logged on this day.",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.workouts.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextPrimary
                                )
                                val subText = if (entry.workoutType == "Strength") {
                                    "Strength | ${entry.setsCount} sets x ${entry.repsCount} reps"
                                } else {
                                    "Cardio"
                                }
                                Text(
                                    text = subText,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "~${entry.caloriesBurned.toInt()} kcal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoralRed
                                )
                                IconButton(
                                    onClick = { viewModel.deleteWorkout(entry) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Workout",
                                        tint = CoralRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = ThemeTextPrimary.copy(alpha = 0.05f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Add missed meal text input row
            var foodText by remember { mutableStateOf("") }
            val parsingState by viewModel.parsingState.collectAsState()

            var statusMessage by remember { mutableStateOf<String?>(null) }
            var isErrorState by remember { mutableStateOf(false) }

            LaunchedEffect(parsingState) {
                val state = parsingState
                when (state) {
                    is HistoryParsingState.Success -> {
                        statusMessage = "Logged: ${state.foodText}"
                        isErrorState = false
                        foodText = ""
                        viewModel.resetParsingState()
                    }
                    is HistoryParsingState.Error -> {
                        statusMessage = state.message
                        isErrorState = true
                        viewModel.resetParsingState()
                    }
                    else -> {}
                }
            }

            LaunchedEffect(statusMessage) {
                if (statusMessage != null) {
                    kotlinx.coroutines.delay(4000)
                    statusMessage = null
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "LOG A MISSED MEAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = foodText,
                        onValueChange = { foodText = it },
                        placeholder = { Text("e.g. 1 plate of pasta and chicken breast", fontSize = 12.sp, color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = ThemeGlassBorder,
                            focusedTextColor = ThemeTextPrimary,
                            unfocusedTextColor = ThemeTextPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = parsingState != HistoryParsingState.Loading
                    )

                    Button(
                        onClick = {
                            if (foodText.isNotBlank()) {
                                viewModel.parseAndAddMeal(foodText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.15f),
                            contentColor = ElectricCyan
                        ),
                        enabled = foodText.isNotBlank() && parsingState != HistoryParsingState.Loading
                    ) {
                        if (parsingState == HistoryParsingState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Meal",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Suggestions chips
                val frequentMeals = viewModel.frequentMeals
                if (frequentMeals.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Frequently Tracked (Tap to fill):",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequentMeals.forEach { meal ->
                                SuggestionChip(
                                    onClick = { foodText = meal },
                                    label = {
                                        Text(
                                            text = meal,
                                            color = ThemeTextPrimary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = ThemeTextPrimary.copy(alpha = 0.05f)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = ThemeGlassBorder
                                    )
                                )
                            }
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (isErrorState) CoralRed else NeonMint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyTrendsCard(
    trends: WeeklyTrendsState,
    modifier: Modifier = Modifier
) {
    val netColor = if (trends.avgNetCalories < 0) NeonMint else CoralRed
    val balanceText = if (trends.avgNetCalories < 0) {
        "${trends.avgNetCalories.toInt()} kcal"
    } else {
        "+${trends.avgNetCalories.toInt()} kcal"
    }
    val balanceLabel = if (trends.avgNetCalories < 0) "CALORIE DEFICIT" else "CALORIE SURPLUS"

    val sleepHours = trends.avgSleepDurationSeconds / 3600.0
    val sleepText = String.format("%.1fh (${trends.avgSleepReadiness}%%)", sleepHours)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "WEEKLY PROGRESS (7-DAY ROLLING AVG)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            // 2x2 grid for averages
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrendItem(
                        label = balanceLabel,
                        value = balanceText,
                        valueColor = netColor,
                        modifier = Modifier.weight(1f)
                    )
                    TrendItem(
                        label = "DAILY STEPS",
                        value = String.format("%,.0f", trends.avgSteps),
                        valueColor = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrendItem(
                        label = "SLEEP & READINESS",
                        value = sleepText,
                        valueColor = HyperViolet,
                        modifier = Modifier.weight(1f)
                    )
                    TrendItem(
                        label = "DAILY PROTEIN",
                        value = "${trends.avgProteinG.toInt()}g",
                        valueColor = SolarAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrendItem(
                        label = "TOTAL WORKOUTS (7D)",
                        value = "${trends.totalWorkouts} sessions",
                        valueColor = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TrendItem(
                        label = "STRENGTH VOLUME (7D)",
                        value = "${trends.totalStrengthSets} sets",
                        valueColor = SolarAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                text = if (trends.trackedDaysCount > 0) {
                    "Calculated from ${trends.trackedDaysCount} tracked days of the past 7. Keep logging to improve accuracy!"
                } else {
                    "No meals logged in the last 7 days. Start logging meals to view your average daily calorie trends!"
                },
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TrendItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.02f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
        }
    }
}

@Composable
fun CalorieTrendChart(
    days: List<HistoryDayState>,
    modifier: Modifier = Modifier
) {
    if (days.size < 2) return

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CALORIE TREND vs TDEE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonMint))
                        Text("Eaten", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ElectricCyan))
                        Text("TDEE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val chartBgColor = ThemeBgStart
            val gridColor = ThemeGlassBorder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val maxVal = (days.maxOf { maxOf(it.caloriesEaten, it.tdee) }).toFloat().coerceAtLeast(2000f) * 1.15f
                    val minVal = 0f

                    val stepX = width / (days.size - 1)

                    val eatenPoints = days.mapIndexed { index, day ->
                        val x = index * stepX
                        val y = height - ((day.caloriesEaten.toFloat() - minVal) / (maxVal - minVal)) * height
                        Offset(x, y)
                    }

                    val tdeePoints = days.mapIndexed { index, day ->
                        val x = index * stepX
                        val y = height - ((day.tdee.toFloat() - minVal) / (maxVal - minVal)) * height
                        Offset(x, y)
                    }

                    // Draw grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height * i / gridLines
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // TDEE Path (dashed curve or straight segments)
                    val tdeePath = Path().apply {
                        if (tdeePoints.isNotEmpty()) {
                            moveTo(tdeePoints[0].x, tdeePoints[0].y)
                            for (i in 1 until tdeePoints.size) {
                                lineTo(tdeePoints[i].x, tdeePoints[i].y)
                            }
                        }
                    }
                    drawPath(
                        path = tdeePath,
                        color = ElectricCyan,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )

                    // Eaten Bezier Path
                    if (eatenPoints.size >= 2) {
                        val eatenPath = Path().apply {
                            moveTo(eatenPoints[0].x, eatenPoints[0].y)
                            for (i in 0 until eatenPoints.size - 1) {
                                val from = eatenPoints[i]
                                val to = eatenPoints[i + 1]
                                val conX1 = from.x + (to.x - from.x) / 2
                                val conY1 = from.y
                                val conX2 = from.x + (to.x - from.x) / 2
                                val conY2 = to.y
                                cubicTo(conX1, conY1, conX2, conY2, to.x, to.y)
                            }
                        }

                        // Draw background fill under Bezier Path
                        val fillPath = Path().apply {
                            addPath(eatenPath)
                            lineTo(eatenPoints.last().x, height)
                            lineTo(eatenPoints.first().x, height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(NeonMint.copy(alpha = 0.2f), Color.Transparent)
                            )
                        )

                        // Draw primary stroke
                        drawPath(
                            path = eatenPath,
                            color = NeonMint,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw points/nodes
                        eatenPoints.forEach { pt ->
                            drawCircle(
                                color = chartBgColor,
                                radius = 6.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = NeonMint,
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            // X-Axis Labels (Date strings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = "${day.date.dayOfMonth}/${day.date.monthValue}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
