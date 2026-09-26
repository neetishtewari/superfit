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
