package com.superfit.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.ForecastStatus
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.Insight
import com.superfit.app.domain.InsightsEngine
import com.superfit.app.domain.JourneyForecast
import com.superfit.app.domain.WeekReport
import com.superfit.app.theme.CoralRed
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.HyperViolet
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.ThemeCardBgTranslucent
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val Orange = Color(0xFFF97316)

@Composable
private fun SectionCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, borderColor ?: ThemeGlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String, color: Color = ThemeTextSecondary) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = color)
}

@Composable
private fun InsightRow(item: Insight) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
        Text(item.detail, fontSize = 13.sp, color = ThemeTextSecondary, lineHeight = 18.sp)
    }
}

/** Insights: the week's verdict, what's working, what to watch, eating vs burning, then one-tap extras. */
@Composable
fun InsightsTab(
    report: WeekReport,
    onOpenJourney: () -> Unit,
    foodQuality: @Composable () -> Unit,
    bests: @Composable () -> Unit,
    trends: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            SectionLabel("YOUR WEEK · ${report.from.format(shortDate)} to ${report.to.format(shortDate)}", HyperViolet)
            Text(report.verdict, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 24.sp)
            val s = report.thisWeek
            val l = report.lastWeek
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                val moveDiff = s.moveMinutes - l.moveMinutes
                WeekTile("${hours(s.moveMinutes)}", "moving", delta(moveDiff.toDouble(), hours(abs(moveDiff)), higherIsGood = true), Modifier.weight(1f))
                val kmDiff = s.distanceKm - l.distanceKm
                WeekTile(km(s.distanceKm), "km", delta(kmDiff, km(abs(kmDiff)), higherIsGood = true), Modifier.weight(1f))
                if (s.loggedDays > 0) {
                    val net = signedKcal(s.netKcal)
                    val vs = if (l.loggedDays > 0) "vs ${signedKcal(l.netKcal)}" else ""
                    WeekTile(net, "kcal net", Delta(vs, ThemeTextSecondary), Modifier.weight(1f))
                    WeekTile("${s.proteinDaysHit}/${s.loggedDays}", "protein days", delta((s.proteinDaysHit - l.proteinDaysHit).toDouble(), "${abs(s.proteinDaysHit - l.proteinDaysHit)}", higherIsGood = true), Modifier.weight(1f))
                } else {
                    WeekTile("${s.workouts}", "workouts", delta((s.workouts - l.workouts).toDouble(), "${abs(s.workouts - l.workouts)}", higherIsGood = true), Modifier.weight(1f))
                    WeekTile("${s.activeDays}/7", "active days", delta((s.activeDays - l.activeDays).toDouble(), "${abs(s.activeDays - l.activeDays)}", higherIsGood = true), Modifier.weight(1f))
                }
            }
        }

        if (report.working.isNotEmpty()) {
            SectionCard {
                SectionLabel("WORKING", NeonMint)
                report.working.forEach { InsightRow(it) }
            }
        }
        if (report.watch.isNotEmpty()) {
            SectionCard {
                SectionLabel("WATCH", SolarAmber)
                report.watch.forEach { InsightRow(it) }
                TextButton(onClick = onOpenJourney, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("See how this affects your goal date", color = NeonMint, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (report.dailyNet.any { it.second != null }) {
            SectionCard {
                SectionLabel("EATING VS TARGET · 7 DAYS")
                NetBars(report.dailyNet)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(NeonMint, "Under target")
                    LegendDot(Orange, "Over target")
                    LegendDot(ThemeGlassBorder, "Not fully logged")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ThemeCardBgTranslucent)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(18.dp))
        ) {
            ExpandRow("Food Quality and swaps", content = foodQuality)
            ExpandRow("Personal bests", content = bests)
            ExpandRow("All trends", "steps, sleep, protein, streaks", content = trends)
        }
    }
}

private data class Delta(val text: String, val color: Color)

@Composable
private fun delta(diff: Double, amount: String, higherIsGood: Boolean): Delta = when {
    abs(diff) < 0.05 -> Delta("same", ThemeTextSecondary)
    (diff > 0) == higherIsGood -> Delta("${if (diff > 0) "▲" else "▼"} $amount", NeonMint)
    else -> Delta("${if (diff > 0) "▲" else "▼"} $amount", SolarAmber)
}

@Composable
private fun WeekTile(value: String, label: String, delta: Delta, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.05f))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, maxLines = 1)
        Text(label, fontSize = 11.sp, color = ThemeTextSecondary, maxLines = 1)
        if (delta.text.isNotEmpty()) Text(delta.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = delta.color, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = ThemeTextSecondary)
    }
}

@Composable
private fun NetBars(days: List<Pair<LocalDate, Double?>>) {
    val maxAbs = max(300.0, days.mapNotNull { it.second?.let(::abs) }.maxOrNull() ?: 0.0)
    val missing = ThemeGlassBorder
    val axis = ThemeTextSecondary.copy(alpha = 0.4f)
    val summary = days.joinToString { (d, n) -> "${d.dayOfWeek.name.take(3)} ${n?.let { signedKcal(it) } ?: "not logged"}" }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Calories against target: $summary" }
    ) {
        val slot = size.width / days.size
        val barW = slot * 0.55f
        val mid = size.height / 2
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        days.forEachIndexed { i, (_, net) ->
            val x = slot * i + (slot - barW) / 2
            if (net == null) {
                drawRoundRect(missing, Offset(x, mid - 3.dp.toPx()), Size(barW, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            } else {
                val h = (abs(net) / maxAbs * (mid - 4.dp.toPx())).toFloat().coerceAtLeast(3.dp.toPx())
                val top = if (net > 0) mid - h else mid
                drawRoundRect(if (net > 0) Orange else NeonMint, Offset(x, top), Size(barW, h), CornerRadius(4.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEach { (d, _) ->
            Text(d.dayOfWeek.name.take(1), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = ThemeTextSecondary)
        }
    }
}

@Composable
private fun ExpandRow(title: String, hint: String = "", content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { open = !open }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ThemeTextPrimary, modifier = Modifier.weight(1f))
            if (hint.isNotEmpty() && !open) Text(hint, fontSize = 12.sp, color = ThemeTextSecondary)
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Hide" else "Show",
                tint = ThemeTextSecondary
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

/** Journey: how much of the goal is done, when the user will get there at current habits, and what would help. */
@Composable
fun JourneyTab(j: JourneyForecast, goalSet: Boolean, onWeighIn: () -> Unit, onEditGoal: () -> Unit) {
    val lose = j.targetKg < j.startKg
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!goalSet) {
            SectionCard {
                SectionLabel("YOUR GOAL", NeonMint)
                Text("Set a goal weight to see your journey", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 26.sp)
                Text(
                    "Superfit will show how much of it you've covered, when you'll get there at your current habits, and what would get you there sooner.",
                    fontSize = 14.sp,
                    color = ThemeTextSecondary,
                    lineHeight = 20.sp
                )
                Button(
                    onClick = onEditGoal,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Set my goal", fontWeight = FontWeight.Bold) }
            }
            WeighInCard(j, onWeighIn)
            return@Column
        }
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("GOAL COVERED")
                Spacer(Modifier.weight(1f))
                Text("Day ${j.dayNumber}", fontSize = 13.sp, color = ThemeTextSecondary)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${j.percentCovered}%", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 48.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${GoalForecast.fmt(j.kgDone)} of ${GoalForecast.fmt(abs(j.targetKg - j.startKg))} kg ${if (lose) "lost" else "gained"}",
                    fontSize = 15.sp,
                    color = ThemeTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            LinearProgressIndicator(
                progress = { j.percentCovered / 100f },
                color = NeonMint,
                trackColor = ThemeTextPrimary.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Start ${GoalForecast.fmt(j.startKg)}", fontSize = 12.sp, color = ThemeTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("Now ${GoalForecast.fmt(j.currentKg)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
                Spacer(Modifier.weight(1f))
                Text("Goal ${GoalForecast.fmt(j.targetKg)}", fontSize = 12.sp, color = ThemeTextSecondary)
            }
            TextButton(onClick = onEditGoal, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(if (j.targetDate == null) "Edit goal or add a target date" else "Edit goal · target ${j.targetDate.format(shortDate)}", color = NeonMint, fontWeight = FontWeight.Bold)
            }
        }

        val (statusLabel, statusColor) = when (j.status) {
            ForecastStatus.ON_TRACK -> "ON TRACK" to NeonMint
            ForecastStatus.AT_RISK -> "AT RISK" to SolarAmber
            ForecastStatus.OFF_TRACK -> "OFF TRACK" to CoralRed
            ForecastStatus.REACHED -> "GOAL REACHED" to NeonMint
            ForecastStatus.NOT_ENOUGH_DATA -> "NEEDS MORE DATA" to ThemeTextSecondary
        }
        SectionCard(borderColor = statusColor.copy(alpha = 0.45f)) {
            SectionLabel("FORECAST · $statusLabel", statusColor)
            Text(j.headline, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 26.sp)
            if (j.explanation.isNotEmpty()) Text(j.explanation, fontSize = 14.sp, color = ThemeTextSecondary, lineHeight = 20.sp)
            if (j.weighIns.isNotEmpty()) {
                JourneyChart(j, statusColor)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(NeonMint, "Weigh-ins")
                    if (j.forecastDate != null) LegendDot(statusColor, "At current habits")
                    LegendDot(SolarAmber, "Goal")
                }
            }
        }

        if (j.slowing.isNotEmpty()) {
            SectionCard {
                SectionLabel("WHAT'S SLOWING YOU DOWN", SolarAmber)
                j.slowing.forEach { InsightRow(it) }
            }
        }

        if (j.fixes.isNotEmpty()) {
            SectionCard {
                SectionLabel("WHAT GETS YOU THERE SOONER", NeonMint)
                j.fixes.forEach { fix ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fix.label, fontSize = 15.sp, color = ThemeTextPrimary, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            fix.weeksSooner?.let { if (it == 1) "1 week sooner" else "$it weeks sooner" } ?: "goal by ${fix.newDate?.format(shortDate)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint
                        )
                    }
                }
                j.allFixesDate?.let { Text("Both together: goal by ${it.format(shortDate)}.", fontSize = 13.sp, color = ThemeTextSecondary) }
            }
        }

        if (j.milestones.isNotEmpty()) {
            SectionCard {
                SectionLabel("MILESTONES")
                j.milestones.forEach { m ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(m.label, fontSize = 15.sp, color = ThemeTextPrimary, modifier = Modifier.weight(1f))
                        Text(
                            m.reachedOn?.let { "${it.format(shortDate)} ✓" } ?: "${GoalForecast.fmt(m.kgToGo)} kg to go",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (m.reachedOn != null) NeonMint else ThemeTextSecondary
                        )
                    }
                }
            }
        }

        WeighInCard(j, onWeighIn)
    }
}

@Composable
private fun WeighInCard(j: JourneyForecast, onWeighIn: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly weigh-in", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
                Text(
                    when (val d = j.daysSinceWeighIn) {
                        null -> "No weigh-ins yet. One a week keeps the forecast honest."
                        0L -> "Weighed in today. Thanks!"
                        1L -> "Last one yesterday."
                        else -> "Last one $d days ago.${if (d >= 7) " A fresh weight keeps the forecast honest." else ""}"
                    },
                    fontSize = 13.sp,
                    color = ThemeTextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onWeighIn,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if ((j.daysSinceWeighIn ?: 99) >= 7) NeonMint else ThemeTextPrimary.copy(alpha = 0.1f), contentColor = if ((j.daysSinceWeighIn ?: 99) >= 7) Color.Black else ThemeTextPrimary),
                modifier = Modifier.heightIn(min = 44.dp)
            ) { Text("Weigh in", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Weigh-ins so far, the goal line, and a dashed line from today to the forecast date. */
@Composable
private fun JourneyChart(j: JourneyForecast, forecastColor: Color) {
    val points = j.weighIns.takeLast(30)
    val first = points.first().first
    val today = LocalDate.now()
    val end = listOfNotNull(j.forecastDate, j.targetDate, today).maxOrNull()!!.coerceAtMost(today.plusDays(365))
    val span = max(1L, ChronoUnit.DAYS.between(first, end)).toFloat()
    val weights = points.map { it.second } + j.targetKg + j.currentKg
    val lo = weights.min() - 0.5
    val hi = weights.max() + 0.5
    val grid = ThemeTextSecondary.copy(alpha = 0.25f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = "Weight from ${GoalForecast.fmt(points.first().second)} to ${GoalForecast.fmt(j.currentKg)} kg, goal ${GoalForecast.fmt(j.targetKg)} kg" }
    ) {
        fun x(d: LocalDate) = ChronoUnit.DAYS.between(first, d).toFloat() / span * size.width
        fun y(kg: Double) = ((hi - kg) / (hi - lo) * size.height).toFloat()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        drawLine(SolarAmber, Offset(0f, y(j.targetKg)), Offset(size.width, y(j.targetKg)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        j.targetDate?.let { td ->
            if (!td.isAfter(end)) drawLine(grid, Offset(x(td), 0f), Offset(x(td), size.height), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        val path = Path()
        points.forEachIndexed { i, (d, kg) -> if (i == 0) path.moveTo(x(d), y(kg)) else path.lineTo(x(d), y(kg)) }
        drawPath(path, NeonMint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { (d, kg) -> drawCircle(NeonMint, 3.dp.toPx(), Offset(x(d), y(kg))) }
        j.forecastDate?.let { fd ->
            if (!fd.isAfter(end)) {
                drawLine(forecastColor, Offset(x(today), y(j.currentKg)), Offset(x(fd), y(j.targetKg)), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                drawCircle(forecastColor, 5.dp.toPx(), Offset(x(fd), y(j.targetKg)))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(first.format(shortDate), fontSize = 11.sp, color = ThemeTextSecondary)
        Spacer(Modifier.weight(1f))
        Text("Today", fontSize = 11.sp, color = ThemeTextSecondary)
        Spacer(Modifier.weight(1f))
        Text(end.format(shortDate), fontSize = 11.sp, color = ThemeTextSecondary)
    }
}

private fun hours(minutes: Long): String = if (minutes >= 60) String.format(Locale.US, "%.1f h", minutes / 60.0) else "$minutes min"
private fun km(v: Double): String = String.format(Locale.US, if (v >= 10) "%.0f" else "%.1f", v)
private fun signedKcal(v: Double): String = (if (v > 0) "+" else if (v < 0) "-" else "") + InsightsEngine.kcal(v)
