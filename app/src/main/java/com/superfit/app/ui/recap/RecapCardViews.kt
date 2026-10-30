package com.superfit.app.ui.recap

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
import com.superfit.app.domain.FoodVerdict
import com.superfit.app.domain.MonthlyRecap
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.RecapWriter
import com.superfit.app.theme.Manrope
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** A recap card's own colours: background, main text, soft text and the accent for bars and numbers. */
private data class StoryPalette(val bg: Color, val text: Color, val soft: Color, val accent: Color, val track: Color)

private val Mint = StoryPalette(Color(0xFFCFEDE2), Color(0xFF0B3B2C), Color(0xFF2F5E4E), Color(0xFF075C44), Color(0x330B3B2C))
private val Night = StoryPalette(Color(0xFF14201B), Color(0xFFF1F5F3), Color(0xFFA9BCB4), Color(0xFF34D399), Color(0x33FFFFFF))
private val Cream = StoryPalette(Color(0xFFFFF1D6), Color(0xFF2A1D05), Color(0xFF6B5326), Color(0xFFB86E00), Color(0x332A1D05))
private val Sky = StoryPalette(Color(0xFFD7EEF5), Color(0xFF06303D), Color(0xFF2E5966), Color(0xFF0A8FB0), Color(0x3306303D))
private val Orange = StoryPalette(Color(0xFFF2A33A), Color(0xFF14201B), Color(0xFF3B2A12), Color(0xFF14201B), Color(0x3314201B))
private val Paper = StoryPalette(Color(0xFFF4F7F6), Color(0xFF14201B), Color(0xFF55635C), Color(0xFF0E9F75), Color(0x2214201B))

private fun paletteFor(card: RecapCard): StoryPalette = when (card) {
    is RecapCard.Goal, is RecapCard.Choices -> Mint
    is RecapCard.Needle, is RecapCard.Moving -> Night
    is RecapCard.OnRepeat -> Cream
    is RecapCard.Faster -> Sky
    is RecapCard.Strength -> Orange
    else -> Paper
}

fun monthName(month: YearMonth): String = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())

private fun kicker(card: RecapCard): String = when (card) {
    is RecapCard.Goal -> "Goal"
    is RecapCard.Needle -> "What moved the needle"
    is RecapCard.OnRepeat -> "On repeat"
    is RecapCard.Macros -> "Your macros"
    is RecapCard.Choices -> "Choices that stood out"
    is RecapCard.Faster -> "Pace"
    is RecapCard.Moving -> "Movement"
    is RecapCard.Strength -> "Strength"
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
        is RecapCard.OnRepeat -> {
            BasicText(
                card.food,
                maxLines = 2,
                style = TextStyle(color = p.text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, lineHeight = 1.05.em),
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 60.sp)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("×${card.times}", color = p.accent, fontFamily = Manrope, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                card.verdict?.let {
                    Spacer(Modifier.width(12.dp))
                    Pill(verdictWord(it), p)
                }
            }
        }
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
        is RecapCard.Setup -> Unit
    }
}

private fun verdictWord(v: FoodVerdict): String = when (v) {
    FoodVerdict.GREAT -> "Great pick"
    FoodVerdict.GOOD -> "Solid pick"
    FoodVerdict.PORTION -> "Watch the portion"
    FoodVerdict.TREAT -> "A treat"
    FoodVerdict.SMALL_TREAT -> "A small treat"
    FoodVerdict.AVOID -> "Ease off"
}

@Composable
private fun Big(value: String, unit: String, p: StoryPalette) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = p.text, fontFamily = Manrope, fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em)
        Spacer(Modifier.width(6.dp))
        Text(unit, color = p.text, fontFamily = Manrope, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun Small(text: String, p: StoryPalette, bold: Boolean = false) {
    Text(text, color = p.soft, fontFamily = Manrope, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
}

@Composable
private fun Pill(text: String, p: StoryPalette) {
    Text(
        text,
        color = p.bg,
        fontFamily = Manrope,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.accent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun Bar(fraction: Float, p: StoryPalette, modifier: Modifier = Modifier) {
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
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
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
