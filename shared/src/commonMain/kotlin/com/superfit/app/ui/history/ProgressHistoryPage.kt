package com.superfit.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.LocalFormat
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeTextPrimary

/**
 * Progress on the iPhone, starting with its History page: the week's chart, the month calendar
 * and the picked day, with the same day card as Android. Insights and Journey join next.
 */
@Composable
fun ProgressHistoryPage(model: HistoryModel) {
    val page by model.page.collectAsState()
    val parsing by model.parsing.collectAsState()
    val predictedFoods by model.predictedFoods.collectAsState()
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
                onHideImported = {}
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
