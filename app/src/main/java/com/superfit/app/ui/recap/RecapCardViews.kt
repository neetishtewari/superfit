package com.superfit.app.ui.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.superfit.app.R
import com.superfit.app.domain.ActivityPatterns
import com.superfit.app.domain.BodyArea
import com.superfit.app.domain.BodyChange
import com.superfit.app.domain.MonthlyRecap
import com.superfit.app.domain.RecapAutoplay
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.RecapWriter
import com.superfit.app.domain.StrengthSessions
import com.superfit.app.theme.Manrope
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far the card's numbers and bars have grown, 0-1. The playing recap counts them up as each card
 * comes on screen; everywhere else (and every shared picture) shows them in full.
 */
val LocalRecapReveal = compositionLocalOf { RecapReveal.Full }

enum class RecapReveal { Full, Waiting, Playing }

/** 0 to 1 over a second once the card is playing; 1 straight away when it isn't animated. */
@Composable
private fun revealFraction(): Float {
    val reveal = LocalRecapReveal.current
    val progress = remember { Animatable(if (reveal == RecapReveal.Full) 1f else 0f) }
    LaunchedEffect(reveal) {
        when (reveal) {
            RecapReveal.Full -> progress.snapTo(1f)
            RecapReveal.Playing -> progress.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
            RecapReveal.Waiting -> Unit
        }
    }
    return progress.value
}

/** A recap card's own colours: background, main text, soft text and the accent for bars and numbers. */
private data class StoryPalette(val bg: Color, val text: Color, val soft: Color, val accent: Color, val track: Color)

private val Mint = StoryPalette(Color(0xFFCFEDE2), Color(0xFF0B3B2C), Color(0xFF2F5E4E), Color(0xFF075C44), Color(0x330B3B2C))
private val Night = StoryPalette(Color(0xFF14201B), Color(0xFFF1F5F3), Color(0xFFA9BCB4), Color(0xFF34D399), Color(0x33FFFFFF))
private val Cream = StoryPalette(Color(0xFFFFF1D6), Color(0xFF2A1D05), Color(0xFF6B5326), Color(0xFFB86E00), Color(0x332A1D05))
private val Sky = StoryPalette(Color(0xFFD7EEF5), Color(0xFF06303D), Color(0xFF2E5966), Color(0xFF0A8FB0), Color(0x3306303D))
private val Orange = StoryPalette(Color(0xFFF2A33A), Color(0xFF14201B), Color(0xFF3B2A12), Color(0xFF14201B), Color(0x3314201B))
private val Paper = StoryPalette(Color(0xFFF4F7F6), Color(0xFF14201B), Color(0xFF55635C), Color(0xFF0E9F75), Color(0x2214201B))

private fun paletteFor(card: RecapCard): StoryPalette = when (card) {
    is RecapCard.Goal, is RecapCard.Choices, is RecapCard.Body -> Mint
    is RecapCard.Needle, is RecapCard.Moving, is RecapCard.Rhythm -> Night
    is RecapCard.Calories -> Cream
    is RecapCard.Faster, is RecapCard.Heart -> Sky
    is RecapCard.Strength, is RecapCard.Reps -> Orange
    else -> Paper
}

fun monthName(month: YearMonth): String = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())

private fun kicker(card: RecapCard): String = when (card) {
    is RecapCard.Goal -> "Goal"
    is RecapCard.Needle -> "What moved the needle"
    is RecapCard.Calories -> "Your plate"
    is RecapCard.Macros -> "Your macros"
    is RecapCard.Choices -> "Choices that stood out"
    is RecapCard.Faster -> "Pace"
    is RecapCard.Moving -> "Movement"
    is RecapCard.Strength -> "Strength"
    is RecapCard.Body -> "Body"
    is RecapCard.Heart -> "Heart"
    is RecapCard.Reps -> "Reps"
    is RecapCard.Rhythm -> "Rhythm"
    is RecapCard.Setup -> "Setup"
}

/** One recap story at 360 x 640 dp (9:16): header, the card's big moment, its words and the Superfit badge. */
@Composable
fun RecapStory(card: RecapCard, words: RecapWords, month: YearMonth, modifier: Modifier = Modifier) {
    val p = paletteFor(card)
    Column(
        modifier
            .size(360.dp, 640.dp)
            .background(p.bg)
            // Top and bottom strips stay clear: Instagram and WhatsApp draw their own bars there.
            .padding(start = 26.dp, end = 26.dp, top = 100.dp, bottom = 84.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_superfit_launcher),
                contentDescription = "Superfit logo",
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "YOUR ${monthName(month).uppercase(Locale.getDefault())} · ${kicker(card).uppercase(Locale.getDefault())}",
                color = p.accent,
                fontFamily = Manrope,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(words.title, color = p.text, fontFamily = Manrope, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f)) {
            Moment(card, p)
            Spacer(Modifier.height(16.dp))
            BasicText(
                words.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                style = TextStyle(color = p.text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, lineHeight = 1.4.em),
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.track)
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_superfit_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text("SUPERFIT", color = p.text, fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
            Text("Get it on Google Play ›", color = p.soft, fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The card's big moment: the number, the bar, the calendar or the lists the app worked out. */
@Composable
private fun Moment(card: RecapCard, p: StoryPalette) {
    when (card) {
        is RecapCard.Goal -> {
            val big = card.monthKg?.takeIf { it > 0 } ?: card.kgDone
            Big(RecapWriter.fmt(big), "kg", p)
            Spacer(Modifier.height(12.dp))
            Bar((card.kgDone / card.kgTotal.coerceAtLeast(0.1)).toFloat(), p)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Small("${RecapWriter.fmt(card.kgDone)} of ${RecapWriter.fmt(card.kgTotal)} kg ${if (card.lose) "down" else "up"}", p, bold = true)
                card.eta?.let { Small("Goal around ${it.dayOfMonth} ${it.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())}", p, bold = true) }
            }
        }
        is RecapCard.Needle -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            card.helped.forEach { FactRow(it.title, it.detail, p.accent, p) }
            card.held.forEach { FactRow(it.title, it.detail, Color(0xFFF2A33A), p) }
        }
        is RecapCard.Calories -> CalorieMoment(card, p)
        is RecapCard.Macros -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroRow("Protein", "${card.proteinPct}% of goal", card.proteinPct / 100f, p)
            if (card.weekdayProteinPct != null && card.weekendProteinPct != null) {
                MacroRow("Weekdays", "${card.weekdayProteinPct}%", card.weekdayProteinPct / 100f, p)
                MacroRow("Weekends", "${card.weekendProteinPct}%", card.weekendProteinPct / 100f, p)
            }
            card.carbsPct?.let { MacroRow("Carbs", "$it% of calories", it / 100f, p) }
            card.fibreG?.let { MacroRow("Fibre", "$it g a day", it / 30f, p) }
        }
        is RecapCard.Choices -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card.good.isNotEmpty()) {
                Small("GOOD CALLS", p, bold = true)
                card.good.forEach { FactRow(it, null, p.accent, p) }
            }
            if (card.rethink.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Small("WORTH A RETHINK", p, bold = true)
                card.rethink.forEach { FactRow(it, null, Color(0xFFB86E00), p) }
            }
        }
        is RecapCard.Faster -> {
            Big(MonthlyRecap.pace(card.paceNow), "/km", p)
            Small("Average ${card.activity} pace, was ${MonthlyRecap.pace(card.pacePrev)} /km", p, bold = true)
        }
        is RecapCard.Moving -> {
            if (card.km >= 1) Big(RecapWriter.fmt(card.km), "km", p) else Big("${card.activeDays.size}", "days", p)
            Spacer(Modifier.height(10.dp))
            MonthDots(card, p)
            Spacer(Modifier.height(8.dp))
            Small(
                listOfNotNull(
                    "${card.activeDays.size} active days",
                    if (card.avgSteps > 0) String.format(Locale.US, "%,d steps a day", card.avgSteps) else null
                ).joinToString(" · "),
                p,
                bold = true
            )
        }
        is RecapCard.Strength -> {
            Big("${card.sessions}", if (card.sessions == 1) "session" else "sessions", p)
            card.detail?.takeIf { card.sessions > 0 }?.let { d ->
                Small(
                    listOfNotNull(d.perWeekText(), if (d.timedSessions > 0) "${d.minutes} min${if (d.timedSessions < card.sessions) "+" else ""}" else null).joinToString(" · "),
                    p,
                    bold = true
                )
                if (d.areas.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(StrengthSessions.areasText(d), color = p.text, fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                d.missing?.let { m ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No ${m.label} logged. Add a ${if (m == BodyArea.LOWER) "leg" else "upper body"} day, or say it when you log it.",
                        color = Color.White,
                        fontFamily = Manrope,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14201B))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
            if (card.sessions == 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Already lifting? Tap the mic and say it, like \"30 minutes of weights\".",
                    color = Color.White,
                    fontFamily = Manrope,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF14201B))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
        is RecapCard.Body -> BodyMoment(card.change, p)
        is RecapCard.Heart -> {
            Big("−${card.change.bpmDrop}", "bpm", p)
            Small(
                "${card.change.activity.replaceFirstChar { it.titlecase(Locale.getDefault()) }}s at ${MonthlyRecap.pace(card.change.paceNow)} /km · ${card.change.bpmNow} bpm, was ${card.change.bpmPrev}",
                p,
                bold = true
            )
        }
        is RecapCard.Reps -> {
            Big(String.format(Locale.US, "%,d", card.totals.total), "reps", p)
            Small(
                listOfNotNull(
                    card.totals.top?.let { String.format(Locale.US, "%,d %s", it.second, it.first) },
                    card.totals.prev.takeIf { it > 0 }?.let { String.format(Locale.US, "last month %,d", it) }
                ).joinToString(" · "),
                p,
                bold = true
            )
        }
        is RecapCard.Rhythm -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val r = card.rhythm
            if (r.hour != null) {
                Big(ActivityPatterns.hourLabel(r.hour), "person", p, countUp = false)
                Small("${r.workoutsNearHour} of ${r.workoutsTimed} workouts started around then", p, bold = true)
            }
            r.bestDay?.let { d ->
                FactRow(
                    "${d.getDisplayName(DateTextStyle.FULL, Locale.getDefault())}s are your big step day",
                    String.format(Locale.US, "%,d steps on average, %,d on a usual day", r.bestDaySteps, r.avgSteps),
                    p.accent,
                    p
                )
            }
        }
        is RecapCard.Setup -> Unit
    }
}

/** The scale's own measures, start of the month to the end; good moves in the card's colour, the rest amber. */
@Composable
private fun BodyMoment(c: BodyChange, p: StoryPalette) {
    fun signed(v: Double) = (if (v > 0) "+" else "−") + RecapWriter.fmt(abs(v))
    when {
        c.fatMoved -> Big(signed(c.fat!!), "pts body fat", p)
        c.leanMoved -> Big(signed(c.lean!!), "kg muscle", p)
        else -> Big(signed(c.bmr!!), "kcal at rest", p)
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val amber = Color(0xFFB86E00)
        c.fat?.let { FactRow("Body fat", "${RecapWriter.fmt(c.fatFrom!!)}% → ${RecapWriter.fmt(c.fatTo!!)}%", if (it <= 0) p.accent else amber, p) }
        c.lean?.let { FactRow("Muscle", "${RecapWriter.fmt(c.leanFrom!!)} → ${RecapWriter.fmt(c.leanTo!!)} kg", if (it >= 0) p.accent else amber, p) }
        c.bmr?.let {
            FactRow(
                "Resting burn",
                String.format(Locale.US, "%,d → %,d kcal a day", c.bmrFrom!!.roundToInt(), c.bmrTo!!.roundToInt()),
                if (it >= 0) p.accent else amber,
                p
            )
        }
    }
}

/** Top 5 foods by calories: a strip of their shares, then each with how often; treats in amber. */
@Composable
private fun CalorieMoment(card: RecapCard.Calories, p: StoryPalette) {
    val c = card.sources
    val treatColor = Color(0xFFB86E00)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(p.track)
        ) {
            c.top.forEachIndexed { i, f ->
                Box(
                    Modifier
                        .weight(f.kcal.coerceAtLeast(1).toFloat())
                        .fillMaxHeight()
                        .background(if (f.isTreat) treatColor else p.accent.copy(alpha = 1f - i * 0.15f))
                )
            }
            val rest = (c.totalKcal - c.top.sumOf { it.kcal }).coerceAtLeast(0)
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
        Spacer(Modifier.height(2.dp))
        c.top.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${f.name} · ${f.times}×",
                    color = if (f.isTreat) treatColor else p.text,
                    fontFamily = Manrope,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format(Locale.US, "%,d", f.kcal), color = p.text, fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Small("kcal · ${c.top.sumOf { it.pct }}% of everything you logged", p, bold = true)
    }
}

@Composable
private fun Big(value: String, unit: String, p: StoryPalette, countUp: Boolean = true) {
    val f = revealFraction()
    val shown = RecapAutoplay.parse(value)?.takeIf { countUp && RecapAutoplay.worthCounting(it) }?.let { RecapAutoplay.format(it, f) } ?: value
    Row(verticalAlignment = Alignment.Bottom) {
        Text(shown, color = p.text, fontFamily = Manrope, fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em)
        Spacer(Modifier.width(6.dp))
        Text(unit, color = p.text, fontFamily = Manrope, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun Small(text: String, p: StoryPalette, bold: Boolean = false) {
    Text(text, color = p.soft, fontFamily = Manrope, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
}

@Composable
private fun Bar(fraction: Float, p: StoryPalette, modifier: Modifier = Modifier) {
    val grown = fraction * revealFraction()
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(p.track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(grown.coerceIn(0.03f, 1f))
                .clip(RoundedCornerShape(50))
                .background(p.accent)
        )
    }
}

@Composable
private fun MacroRow(label: String, value: String, fraction: Float, p: StoryPalette) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = p.text, fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(value, color = p.soft, fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Bar(fraction, p)
    }
}

@Composable
private fun FactRow(title: String, detail: String?, dot: Color, p: StoryPalette) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = p.text, fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, color = p.soft, fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2) }
        }
    }
}

/** The month as a grid of dots, filled on active days. */
@Composable
private fun MonthDots(card: RecapCard.Moving, p: StoryPalette) {
    val days = (1..card.daysInMonth).toList()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                week.forEach { d ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (d in card.activeDays) p.accent else p.track)
                    )
                }
            }
        }
    }
}
