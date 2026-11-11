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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.domain.LocalFormat
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeCardBgTranslucent
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import com.superfit.app.theme.ThemeTextTertiary
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/**
 * Progress on the iPhone, starting with its History page: the week's chart, the month calendar
 * and the picked day. Insights and Journey join next, then editing past days.
 */
@Composable
fun ProgressHistoryPage(model: HistoryModel) {
    val page by model.page.collectAsState()
    Box(Modifier.fillMaxSize().background(SuperfitTheme.colors.bgStart)) {
        val p = page
        if (p == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NeonMint)
            return@Box
        }
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Progress",
                style = MaterialTheme.typography.titleLarge,
                color = ThemeTextPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
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
            DayAtAGlance(p.summary)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The picked day, read only for now: eaten against what you burn, and what was logged. */
@Composable
private fun DayAtAGlance(summary: DaySummaryState) {
    val today = Now.today()
    val label = when (summary.date) {
        today -> "Today"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> LocalFormat.longDay(summary.date)
    }
    fun kcal(x: Double) = LocalFormat.integer(x.roundToLong())
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary)
            if (summary.meals.isEmpty()) {
                Text("Nothing logged this day.", fontSize = 14.sp, color = ThemeTextSecondary)
            } else {
                val under = summary.netBalance < 0
                Text(
                    "Ate ${kcal(summary.caloriesEaten)} of ${kcal(summary.tdee)} kcal · ${kcal(abs(summary.netBalance))} ${if (under) "under" else "over"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (under) NeonMint else SolarAmber
                )
                summary.meals.sortedBy { it.timestamp }.forEach { meal ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            meal.foodText,
                            fontSize = 14.sp,
                            color = ThemeTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${kcal(meal.calories)} kcal", fontSize = 14.sp, color = ThemeTextTertiary)
                    }
                }
            }
            summary.workouts.forEach { workout ->
                Text("${workout.description} · ${kcal(workout.caloriesBurned)} kcal", fontSize = 14.sp, color = ThemeTextSecondary)
            }
        }
    }
}
