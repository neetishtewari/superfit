package com.superfit.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.BodyDirection
import com.superfit.app.domain.BodyMeasure
import com.superfit.app.domain.BodyPeriod
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.BodySeries
import com.superfit.app.domain.BodySummary
import com.superfit.app.domain.MeasuredBody
import com.superfit.app.theme.SolarAmber
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeCardBg
import com.superfit.app.theme.ThemeFieldBg
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.toJavaLocalDate

private val bodyDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Composable
private fun BodySection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBg)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun directionColor(direction: BodyDirection): Color = when (direction) {
    BodyDirection.GOOD -> SuperfitTheme.colors.primaryAccent
    BodyDirection.NOT_GOOD -> SolarAmber
    BodyDirection.NEUTRAL -> ThemeTextSecondary
}

private fun arrow(diff: Double) = if (diff < 0) "▼" else "▲"

/** Green when it moved the right way, amber when not, grey for small moves. */
private fun direction(diff: Double, goodIfDown: Boolean?, steadyBelow: Double): BodyDirection = when {
    abs(diff) < steadyBelow || goodIfDown == null -> BodyDirection.NEUTRAL
    (diff < 0) == goodIfDown -> BodyDirection.GOOD
    else -> BodyDirection.NOT_GOOD
}

/**
 * "Your body now" at the top of Insights: the latest weight, and body fat and muscle when a smart
 * scale sends them. Without a scale it shows weight and BMI, so it never looks half empty.
 */
@Composable
fun BodyNowCard(body: BodySummary?, weightKg: Double?, weightChange4w: Double?, goalKgChange: Double) {
    if (weightKg == null && body?.hasScaleData != true) return
    val weightGoodIfDown = when {
        goalKgChange <= -0.5 -> true
        goalKgChange >= 0.5 -> false
        else -> null
    }
    BodySection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your body now", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextSecondary, modifier = Modifier.weight(1f))
            val from = if (body?.hasScaleData == true) {
                listOfNotNull(body.source, body.measuredOn?.toJavaLocalDate()?.format(bodyDate)).joinToString(" · ")
            } else {
                ""
            }
            if (from.isNotEmpty()) Text(from, fontSize = 12.sp, color = ThemeTextSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weightKg?.let { kg ->
                NowTile(
                    "Weight",
                    "${BodyReadings.fmt(kg)} kg",
                    weightChange4w?.let { change4w(it, "kg", weightGoodIfDown, 0.2) },
                    Modifier.weight(1f)
                )
            }
            if (body?.hasScaleData == true) {
                body.bodyFatPct?.let { fat ->
                    NowTile("Body fat", "${BodyReadings.fmt(fat)}%", body.bodyFatChange?.let { change4w(it, "pts", true, 0.2) }, Modifier.weight(1f))
                }
                body.leanKg?.let { lean ->
                    NowTile("Muscle", "${BodyReadings.fmt(lean)} kg", body.leanChange?.let { change4w(it, "kg", false, 0.2) }, Modifier.weight(1f))
                }
            } else {
                body?.bmi?.let { bmi ->
                    NowTile("BMI", BodyReadings.fmt(bmi), Pair("Healthy 18.5 to 24.9", ThemeTextSecondary), Modifier.weight(1f))
                }
            }
        }
        if (body?.hasScaleData == true) {
            val extras = listOfNotNull(
                body.bmi?.let { "BMI ${BodyReadings.fmt(it)}" },
                body.boneKg?.let { "Bone ${BodyReadings.fmt(it)} kg" },
                body.scaleBmr?.let { "Resting burn ${"%,d".format(it.toInt())} kcal" }
            )
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), fontSize = 13.sp, color = ThemeTextSecondary)
            if (MeasuredBody.currentBodyFat() != null) {
                Text("Your calorie target uses this body fat.", fontSize = 12.sp, color = ThemeTextSecondary)
            }
        }
    }
}

@Composable
private fun change4w(diff: Double, unit: String, goodIfDown: Boolean?, steadyBelow: Double): Pair<String, Color> {
    if (abs(diff) < steadyBelow) return "Steady over 4 wks" to ThemeTextSecondary
    val color = directionColor(direction(diff, goodIfDown, steadyBelow))
    return "${arrow(diff)} ${BodyReadings.fmt(abs(diff))} $unit in 4 wks" to color
}

@Composable
private fun NowTile(label: String, value: String, note: Pair<String, Color>?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ThemeFieldBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 12.sp, color = ThemeTextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextPrimary, maxLines = 1)
        note?.let { (text, color) -> Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 14.sp) }
    }
}

/**
 * Journey's "How your body has changed": a small trend line per measure, over 1, 3, 6 or 12 months.
 * Only measures with readings show; people without a smart scale see weight and BMI.
 */
@Composable
fun BodyTrendsCard(
    series: List<BodySeries>,
    period: BodyPeriod,
    onPeriod: (BodyPeriod) -> Unit,
    insight: String?,
    canAskBodyAccess: Boolean,
    onAllowBody: () -> Unit,
    /** At least two weigh-ins ever; without them there is nothing to chart in any period. */
    hasHistory: Boolean
) {
    if (!hasHistory) return
    BodySection {
        Text("How your body has changed", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ThemeTextSecondary)
        com.superfit.app.ui.dashboard.SegmentedPills(
            options = BodyPeriod.entries.map { it.label to it.label },
            selected = period.label,
            onSelect = { label -> onPeriod(BodyPeriod.fromLabel(label)) }
        )
        if (series.isEmpty()) {
            Text("Not enough weigh-ins in this period yet. Try a longer one.", fontSize = 13.sp, color = ThemeTextSecondary)
        }
        insight?.takeIf { series.any { it.measure == BodyMeasure.BODY_FAT } }?.let {
            Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary, lineHeight = 21.sp)
        }
        series.forEach { TrendRow(it) }
        if (canAskBodyAccess && series.none { it.measure == BodyMeasure.BODY_FAT }) {
            Text(
                "Use a smart scale? Body fat and muscle can show here too.",
                fontSize = 13.sp,
                color = ThemeTextSecondary
            )
            TextButton(onClick = onAllowBody, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Allow body measurements", color = SuperfitTheme.colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun valueText(measure: BodyMeasure, v: Double): String = when (measure) {
    BodyMeasure.BODY_FAT -> "${BodyReadings.fmt(v)}%"
    BodyMeasure.BMI -> BodyReadings.fmt(v)
    else -> "${BodyReadings.fmt(v)} ${measure.unit}"
}

private fun changeText(s: BodySeries): String {
    if (s.steady) return "Steady"
    val unit = when (s.measure) {
        BodyMeasure.BODY_FAT -> " pts"
        BodyMeasure.BMI -> ""
        else -> " ${s.measure.unit}"
    }
    return "${arrow(s.change)} ${BodyReadings.fmt(abs(s.change))}$unit"
}

@Composable
private fun TrendRow(s: BodySeries) {
    val colors = SuperfitTheme.colors
    val line = when (s.measure) {
        BodyMeasure.WEIGHT -> colors.primaryAccent
        BodyMeasure.BODY_FAT -> colors.secondaryAccent
        BodyMeasure.MUSCLE -> colors.carbs
        BodyMeasure.BONE -> colors.fat
        BodyMeasure.BMI -> colors.fibre
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.measure.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
            Text("  ${valueText(s.measure, s.latest)}", fontSize = 14.sp, color = ThemeTextSecondary, modifier = Modifier.weight(1f))
            Text(changeText(s), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = directionColor(s.direction))
        }
        val first = s.points.first().value
        val last = s.points.last().value
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics {
                    contentDescription = "${s.measure.label} went from ${valueText(s.measure, first)} to ${valueText(s.measure, last)}"
                }
        ) {
            val pts = s.points
            val minV = pts.minOf { it.value }
            val maxV = pts.maxOf { it.value }
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
            val startDay = pts.first().date.toEpochDays()
            val days = (pts.last().date.toEpochDays() - startDay).toFloat().coerceAtLeast(1f)
            val pad = 4.dp.toPx()
            fun at(i: Int): Offset {
                val p = pts[i]
                val x = pad + (size.width - 2 * pad) * (p.date.toEpochDays() - startDay).toFloat() / days
                val y = if (maxV == minV) size.height / 2 else pad + (size.height - 2 * pad) * (1f - ((p.value - minV) / range).toFloat())
                return Offset(x, y)
            }
            val path = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until pts.size) lineTo(at(i).x, at(i).y)
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(line, radius = 3.5.dp.toPx(), center = at(pts.size - 1))
        }
    }
}
