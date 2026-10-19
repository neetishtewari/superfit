package com.superfit.app.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.R
import com.superfit.app.domain.CardStat
import com.superfit.app.domain.ShareCard
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.HyperViolet
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber

// The share cards always use their own dark look, whatever theme the app is in.
private val CardBackground = Color(0xFF0D0D14)
private val TileBackground = Color(0xFF181824)
private val Divider = Color(0xFF2A2A3A)
private val TextMain = Color(0xFFF4F4F8)
private val TextSoft = Color(0xFFA8A8BC)
private val TextFaint = Color(0xFF8A8AA0)

/** The colours the user can pick in the preview. */
val CardAccents = listOf(NeonMint, ElectricCyan, HyperViolet, SolarAmber)

/** Personal bests default to violet, everything else to mint, as in the approved mock-ups. */
fun defaultAccent(card: ShareCard): Color = if (card is ShareCard.Best) HyperViolet else NeonMint

/** A share card, laid out at 360 x 640 dp (Story size, 9:16). */
@Composable
fun ShareCardView(card: ShareCard, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(360.dp, 640.dp)
            .background(CardBackground)
    ) {
        when (card) {
            is ShareCard.Daily -> DailyGlow(accent)
            is ShareCard.Activity -> ActivityWaves(accent)
            is ShareCard.Best -> BestGlow(accent)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 22.dp)
        ) {
            CardHeader(card.dateLine)
            when (card) {
                is ShareCard.Daily -> DailyBody(card, accent)
                is ShareCard.Activity -> ActivityBody(card, accent)
                is ShareCard.Best -> BestBody(card, accent)
            }
            CardFooter()
        }
    }
}

@Composable
private fun CardHeader(dateLine: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_superfit_launcher),
            contentDescription = "Superfit logo",
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text("SUPERFIT", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        Text(dateLine, color = TextSoft, fontSize = 12.sp)
    }
}

@Composable
private fun CardFooter() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider)
    )
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Tracked with Superfit", color = TextFaint, fontSize = 11.sp)
        Text("On Google Play", color = TextFaint, fontSize = 11.sp)
    }
}

@Composable
private fun ColumnScope.DailyBody(card: ShareCard.Daily, accent: Color) {
    Spacer(Modifier.height(48.dp))
    Text("MY DAY", color = TextSoft, fontSize = 12.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
    if (card.streakDays > 0) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${card.streakDays}", color = accent, fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 76.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "day streak",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    } else {
        Text("Day 1", color = accent, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 70.sp)
    }
    Spacer(Modifier.height(28.dp))
    // Tiles left out (no meals, rest day) close up, so there are never empty boxes.
    card.tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { stat ->
                DailyTile(stat, barColor = if (stat.label == "PROTEIN") accent else secondBar(accent), modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
    card.coachLine?.let { line ->
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )
        Spacer(Modifier.height(16.dp))
        Text("COACH", color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(line, color = TextMain, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.weight(1f))
}

private fun secondBar(accent: Color): Color = if (accent == ElectricCyan) NeonMint else ElectricCyan

@Composable
private fun DailyTile(stat: CardStat, barColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TileBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stat.label, color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
        ValueWithUnit(stat.value, stat.unit, valueSize = 24.sp, unitSize = 14.sp)
        stat.progress?.let { p ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Divider)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
        stat.note?.let { Text(it, color = TextSoft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ValueWithUnit(value: String, unit: String?, valueSize: TextUnit, unitSize: TextUnit) {
    Text(
        text = buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(SpanStyle(color = TextSoft, fontSize = unitSize, fontWeight = FontWeight.Normal)) {
                    append(if (unit.startsWith("/")) unit else " $unit")
                }
            }
        },
        color = TextMain,
        fontSize = valueSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
private fun ColumnScope.ActivityBody(card: ShareCard.Activity, accent: Color) {
    Spacer(Modifier.height(56.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(card.heading, color = TextSoft, fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(card.bigValue, color = TextMain, fontSize = 92.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 92.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(card.bigUnit, color = accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
    }
    card.subtitle?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = TextMain, fontSize = 17.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.weight(1f))
    card.stats.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { stat ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stat.label, color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
                    ValueWithUnit(stat.value, stat.unit, valueSize = 26.sp, unitSize = 13.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ColumnScope.BestBody(card: ShareCard.Best, accent: Color) {
    Spacer(Modifier.height(64.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (card.isNew) "NEW PERSONAL BEST" else "PERSONAL BEST",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(card.label, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(card.bigValue, color = TextMain, fontSize = 88.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 88.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(card.bigUnit, color = accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        }
        card.detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.weight(1f))
    if (card.previous != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TileBackground)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PREVIOUS BEST", color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(card.previous, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            card.gain?.let { Text(it, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DailyGlow(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.08f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.95f, size.height * 0.08f)
        )
    }
}

@Composable
private fun ActivityWaves(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val top = size.height * 0.34f
        fun wave(lift: Float) = Path().apply {
            moveTo(-0.08f * w, top + lift * 0.77f)
            cubicTo(0.1f * w, top + lift * 0.77f, 0.16f * w, top + lift * 0.46f, 0.31f * w, top + lift * 0.5f)
            cubicTo(0.46f * w, top + lift * 0.54f, 0.55f * w, top + lift * 0.85f, 0.7f * w, top + lift * 0.58f)
            cubicTo(0.85f * w, top + lift * 0.3f, 0.95f * w, top + lift * 0.15f, 1.08f * w, top + lift * 0.23f)
        }
        drawPath(wave(260.dp.toPx()), accent.copy(alpha = 0.22f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(wave(300.dp.toPx()), ElectricCyan.copy(alpha = 0.18f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BestGlow(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * 0.39f)
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.33f), Color.Transparent), center = center, radius = size.width * 0.6f),
            radius = size.width * 0.6f,
            center = center
        )
    }
}
