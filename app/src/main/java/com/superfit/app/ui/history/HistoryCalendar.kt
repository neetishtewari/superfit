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
import com.superfit.app.ui.dashboard.GoalProgressCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
                    text = currentMonthName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = ThemeTextPrimary,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTextTertiary
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

            // Legend: wraps onto a second line on narrow phones instead of squashing.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val untrackedColor = if (SuperfitTheme.isDark) Color(0xFF2E2E3A) else Color(0xFFCBD5E1)
                LegendItem(color = NeonMint, label = "Under what you burned")
                LegendItem(color = SolarAmber, label = "Over")
                LegendItem(color = untrackedColor, label = "Not fully logged")
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
    val isToday = dayState.date == LocalDate.now()
    val dotColor = when (dayState.status) {
        DayStatus.Deficit -> NeonMint
        DayStatus.Surplus -> SolarAmber
        DayStatus.Insufficient -> Color.Transparent
    }

    // A plain number with a coloured dot under it; the chosen day is a filled green circle.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NeonMint else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, NeonMint, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = dayState.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    isSelected -> Color.White
                    dayState.status == DayStatus.Insufficient -> ThemeTextTertiary
                    else -> ThemeTextPrimary
                }
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected && dayState.status != DayStatus.Insufficient) Color.White else dotColor)
            )
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
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 12.sp, color = ThemeTextSecondary, maxLines = 1)
    }
}
