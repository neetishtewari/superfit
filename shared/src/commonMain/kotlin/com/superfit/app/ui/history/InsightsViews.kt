@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.domain.CalorieFood
import com.superfit.app.domain.CalorieSources
import com.superfit.app.domain.CalorieSourcesEngine
import com.superfit.app.domain.ForecastStatus
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.Insight
import com.superfit.app.domain.InsightsEngine
import com.superfit.app.domain.JourneyForecast
import com.superfit.app.domain.LocalFormat
import com.superfit.app.domain.WeekReport
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.ThemeAmberSoft
import com.superfit.app.theme.ThemeCardBg
import com.superfit.app.theme.ThemeCardBgTranslucent
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** "4 Nov", in the phone's language. */
private fun LocalDate.short(): String = LocalFormat.dayMonth(this)
private val Orange = SolarAmber

@Composable
private fun SectionCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBg)
            .border(1.dp, borderColor ?: ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String, color: Color = ThemeTextSecondary) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
}

@Composable
private fun InsightRow(item: Insight, dot: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
            Text(item.detail, fontSize = 13.sp, color = ThemeTextSecondary)
        }
    }
}

/** Insights: the week's verdict, what's working, what to watch, eating vs burning, then one-tap extras. */
@Composable
fun InsightsTab(
    report: WeekReport,
    onOpenJourney: () -> Unit,
    /** Null hides the report row (the iPhone gets it with its own PDF). */
    onShareWithCoach: ((weeks: Int) -> Unit)?,
    foodQuality: @Composable () -> Unit,
    /** Null hides personal bests (the iPhone gets them with share cards). */
    bests: (@Composable () -> Unit)?,
    trends: @Composable () -> Unit,
    /** "Your body now": the latest weight and scale readings, first thing on Progress. */
    bodyNow: @Composable () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        bodyNow()
        SectionCard {
            SectionLabel("Your week · ${report.from.short()} to ${report.to.short()}", NeonMint)
            Text(report.verdict, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 24.sp)
            val s = report.thisWeek
            val l = report.lastWeek
            // 2 x 2 so values like "-1,062" and "vs -1,337" have room.
            val moveDiff = s.moveMinutes - l.moveMinutes
            val kmDiff = s.distanceKm - l.distanceKm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WeekTile("${hours(s.moveMinutes)}", "moving", delta(moveDiff.toDouble(), hours(abs(moveDiff)), higherIsGood = true), Modifier.weight(1f))
                WeekTile(km(s.distanceKm), "km", delta(kmDiff, km(abs(kmDiff)), higherIsGood = true), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (s.loggedDays > 0) {
                    val net = signedKcal(s.netKcal)
                    val vs = if (l.loggedDays > 0) "vs ${signedKcal(l.netKcal)}" else ""
                    WeekTile(net, "kcal net", Delta(vs, ThemeTextSecondary), Modifier.weight(1f))
                    val pct = s.proteinPct ?: 0
                    val pctDelta = l.proteinPct?.let { delta((pct - it).toDouble(), "${abs(pct - it)} pts", higherIsGood = true) } ?: Delta("", ThemeTextSecondary)
                    WeekTile("$pct%", "protein goal", pctDelta, Modifier.weight(1f))
                } else {
                    WeekTile("${s.workouts}", "workouts", delta((s.workouts - l.workouts).toDouble(), "${abs(s.workouts - l.workouts)}", higherIsGood = true), Modifier.weight(1f))
                    WeekTile("${s.activeDays}/7", "active days", delta((s.activeDays - l.activeDays).toDouble(), "${abs(s.activeDays - l.activeDays)}", higherIsGood = true), Modifier.weight(1f))
                }
            }
            if (s.loggedDays > 0) {
                Text(
                    "Protein goal reached on ${s.proteinDaysHit} of ${s.loggedDays} logged ${if (s.loggedDays == 1) "day" else "days"}",
                    fontSize = 12.sp,
                    color = ThemeTextSecondary
                )
            }
        }

        if (report.working.isNotEmpty()) {
            SectionCard {
                SectionLabel("Going well", NeonMint)
                report.working.forEach { InsightRow(it, NeonMint) }
            }
        }
        if (report.watch.isNotEmpty()) {
            SectionCard {
                SectionLabel("Watch out", SolarAmber)
                report.watch.forEach { InsightRow(it, SolarAmber) }
                TextButton(onClick = onOpenJourney, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("See my goal date", color = NeonMint, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (report.dailyNet.any { it.second != null }) {
            SectionCard {
                SectionLabel("Eating vs target, last 7 days")
                NetBars(report.dailyNet)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendDot(NeonMint, "Under target")
                    LegendDot(Orange, "Over target")
                    LegendDot(ThemeGlassBorder, "Not fully logged")
                }
            }
        }

        report.calories?.let { CalorieSourcesCard(it, days = 7) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ThemeCardBg)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
        ) {
            ExpandRow("Food Quality and swaps", content = foodQuality)
            bests?.let { ExpandRow("Personal bests", content = it) }
            ExpandRow("All trends", "steps, sleep, protein, streaks", content = trends)
            onShareWithCoach?.let { ShareWithCoachRow(it) }
        }
    }
}

/** The week's top 5 foods by calories added, each with how often and its share; treats tagged amber. */
@Composable
private fun CalorieSourcesCard(c: CalorieSources, days: Int) {
    SectionCard {
        SectionLabel("Where your calories came from")
        val biggest = c.top.maxOf { it.kcal }.coerceAtLeast(1)
        c.top.forEachIndexed { i, food -> CalorieFoodRow(i + 1, food, biggest, days) }
        val treatLine = when {
            c.biggestTreat != null -> {
                val b = c.biggestTreat!!
                val share = if (c.totalKcal > 0) (c.treatKcal * 100.0 / c.totalKcal).roundToInt() else 0
                "Treat foods: ${InsightsEngine.kcal(c.treatKcal.toDouble())} kcal this week ($share%). Biggest: ${b.name.lowercase()}, ${b.times} ${if (b.times == 1) "time" else "times"}."
            }
            c.surelyNoTreats -> "No treat foods this week."
            else -> null
        }
        Text(
            listOfNotNull("From ${InsightsEngine.kcal(c.totalKcal.toDouble())} kcal logged.", treatLine).joinToString(" "),
            fontSize = 12.sp,
            color = ThemeTextSecondary
        )
    }
}

@Composable
private fun CalorieFoodRow(rank: Int, food: CalorieFood, max: Int, days: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ThemeTextSecondary, modifier = Modifier.width(20.dp))
            Text(
                food.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (food.isTreat) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "Treat",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SolarAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ThemeAmberSoft)
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(InsightsEngine.kcal(food.kcal.toDouble()), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary)
        }
        val often = CalorieSourcesEngine.howOften(food.times, days)
        Text(
            "${food.times} ${if (food.times == 1) "time" else "times"}${often?.let { ", $it" } ?: ""} · ${food.pct}%",
            fontSize = 12.sp,
            color = ThemeTextSecondary,
            modifier = Modifier.padding(start = 20.dp)
        )
        Box(
            Modifier
                .padding(start = 20.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(ThemeTextPrimary.copy(alpha = 0.06f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((food.kcal.toFloat() / max).coerceIn(0.03f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (food.isTreat) SolarAmber else NeonMint)
            )
        }
    }
}

/** "Share my report": pick 2 or 4 weeks, then the phone makes a PDF and opens the share sheet. */
@Composable
private fun ShareWithCoachRow(onShare: (weeks: Int) -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { asking = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text("Share my report", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ThemeTextPrimary)
            Text("A PDF for your coach, a friend or an accountability partner", fontSize = 12.sp, color = ThemeTextSecondary)
        }
        Text("PDF", fontSize = 12.sp, color = ThemeTextSecondary, modifier = Modifier.padding(start = 8.dp))
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Share my report") },
            text = {
                Text("Makes a PDF of your meals, calories, protein, workouts and weight. Send it to a coach, a friend or an accountability partner by WhatsApp or email, or save it to your files. It's made on your phone and only goes where you send it.")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        asking = false
                        onShare(2)
                    }) { Text("Last 2 weeks") }
                    TextButton(onClick = {
                        asking = false
                        onShare(4)
                    }) { Text("Last 4 weeks") }
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text("Cancel") }
            }
        )
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
        Text(label, fontSize = 12.sp, color = ThemeTextSecondary, maxLines = 1)
        if (delta.text.isNotEmpty()) Text(delta.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = delta.color, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = ThemeTextSecondary)
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
            Text(d.dayOfWeek.name.take(1), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = ThemeTextSecondary)
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
fun JourneyTab(
    j: JourneyForecast,
    goalSet: Boolean,
    onWeighIn: () -> Unit,
    onEditGoal: () -> Unit,
    /** "How your body has changed": trend lines for each measure the person has. */
    bodyTrends: @Composable () -> Unit = {}
) {
    val lose = j.targetKg < j.startKg
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!goalSet) {
            SectionCard {
                SectionLabel("Your goal", NeonMint)
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
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Set my goal", fontWeight = FontWeight.Bold) }
            }
            WeighInCard(j, onWeighIn)
            bodyTrends()
            return@Column
        }
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Goal covered")
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
                Text(j.targetDate?.let { "Edit goal · target ${it.short()}" } ?: "Edit goal or add a target date", color = NeonMint, fontWeight = FontWeight.Bold)
            }
        }

        val (statusLabel, statusColor) = when (j.status) {
            ForecastStatus.ON_TRACK -> "On track" to NeonMint
            ForecastStatus.AT_RISK -> "At risk" to SolarAmber
            ForecastStatus.OFF_TRACK -> "Off track" to SolarAmber
            ForecastStatus.REACHED -> "Goal reached" to NeonMint
            ForecastStatus.NOT_ENOUGH_DATA -> "Needs more data" to ThemeTextSecondary
        }
        SectionCard(borderColor = statusColor.copy(alpha = 0.45f)) {
            SectionLabel("Forecast · $statusLabel", statusColor)
            Text(j.headline, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, lineHeight = 26.sp)
            if (j.explanation.isNotEmpty()) Text(j.explanation, fontSize = 14.sp, color = ThemeTextSecondary, lineHeight = 20.sp)
            if (j.weighIns.isNotEmpty()) {
                JourneyChart(j, statusColor)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendDot(NeonMint, "Weigh-ins")
                    if (j.forecastDate != null) LegendDot(statusColor, "At current habits")
                    LegendDot(SolarAmber, "Goal")
                }
            }
        }

        if (j.slowing.isNotEmpty()) {
            SectionCard {
                SectionLabel("What's slowing you down", SolarAmber)
                j.slowing.forEach { InsightRow(it, SolarAmber) }
            }
        }

        if (j.fixes.isNotEmpty()) {
            SectionCard {
                SectionLabel("What gets you there sooner", NeonMint)
                j.fixes.forEach { fix ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fix.label, fontSize = 15.sp, color = ThemeTextPrimary, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            fix.weeksSooner?.let { if (it == 1) "1 week sooner" else "$it weeks sooner" } ?: "goal by ${fix.newDate?.short()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint
                        )
                    }
                }
                j.allFixesDate?.let { Text("Both together: goal by ${it.short()}.", fontSize = 13.sp, color = ThemeTextSecondary) }
            }
        }

        if (j.milestones.isNotEmpty()) {
            SectionCard {
                SectionLabel("Milestones")
                j.milestones.forEach { m ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(m.label, fontSize = 15.sp, color = ThemeTextPrimary, modifier = Modifier.weight(1f))
                        Text(
                            m.reachedOn?.let { "${it.short()} ✓" } ?: "${GoalForecast.fmt(m.kgToGo)} kg to go",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (m.reachedOn != null) NeonMint else ThemeTextSecondary
                        )
                    }
                }
            }
        }

        WeighInCard(j, onWeighIn)
        bodyTrends()
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
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 44.dp)
            ) { Text("Log weight", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Weigh-ins so far, the goal line, and a dashed line from today to the forecast date. */
@Composable
private fun JourneyChart(j: JourneyForecast, forecastColor: Color) {
    val points = j.weighIns.takeLast(30)
    val forecastDate = j.forecastDate
    val targetDate = j.targetDate
    val first = points.first().first
    val today = Now.today()
    val end = listOfNotNull(forecastDate, targetDate, today).maxOrNull()!!.coerceAtMost(today.plus(DatePeriod(days = 365)))
    val span = max(1, first.daysUntil(end)).toFloat()
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
        fun x(d: LocalDate) = first.daysUntil(d).toFloat() / span * size.width
        fun y(kg: Double) = ((hi - kg) / (hi - lo) * size.height).toFloat()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        drawLine(SolarAmber, Offset(0f, y(j.targetKg)), Offset(size.width, y(j.targetKg)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        targetDate?.let { td ->
            if (td <= end) drawLine(grid, Offset(x(td), 0f), Offset(x(td), size.height), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        val path = Path()
        points.forEachIndexed { i, (d, kg) -> if (i == 0) path.moveTo(x(d), y(kg)) else path.lineTo(x(d), y(kg)) }
        drawPath(path, NeonMint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { (d, kg) -> drawCircle(NeonMint, 3.dp.toPx(), Offset(x(d), y(kg))) }
        forecastDate?.let { fd ->
            if (fd <= end) {
                drawLine(forecastColor, Offset(x(today), y(j.currentKg)), Offset(x(fd), y(j.targetKg)), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                drawCircle(forecastColor, 5.dp.toPx(), Offset(x(fd), y(j.targetKg)))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(first.short(), fontSize = 12.sp, color = ThemeTextSecondary)
        Spacer(Modifier.weight(1f))
        Text("Today", fontSize = 12.sp, color = ThemeTextSecondary)
        Spacer(Modifier.weight(1f))
        Text(end.short(), fontSize = 12.sp, color = ThemeTextSecondary)
    }
}

private fun hours(minutes: Long): String = if (minutes >= 60) "${oneDecimal(minutes / 60.0)} h" else "$minutes min"
private fun km(v: Double): String = if (v >= 10) kotlin.math.round(v).toLong().toString() else oneDecimal(v)

/** Like "%.1f" with a dot, whatever the phone's language ("1.5"). */
private fun oneDecimal(v: Double): String {
    val tenths = kotlin.math.round(v * 10).toLong()
    return "${tenths / 10}.${abs(tenths % 10)}"
}
private fun signedKcal(v: Double): String = (
    if (v > 0) {
        "+"
    } else if (v < 0) {
        "-"
    } else {
        ""
    }
    ) + InsightsEngine.kcal(v)
