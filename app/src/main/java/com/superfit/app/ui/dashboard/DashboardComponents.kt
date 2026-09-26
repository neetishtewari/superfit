package com.superfit.app.ui.dashboard


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.*
import com.superfit.app.theme.*
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import android.text.format.DateUtils
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.net.Uri
import com.superfit.app.data.PredictedFood

// Concentric Rings graphic
@Composable
fun ConcentricActivityRings(
    caloriesEatenProgress: Float,
    stepsProgress: Float,
    caloriesRemaining: Int,
    modifier: Modifier = Modifier
) {
    val caloriesAnimated by animateFloatAsState(
        targetValue = caloriesEatenProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "CaloriesProgress"
    )
    val stepsAnimated by animateFloatAsState(
        targetValue = stepsProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "StepsProgress"
    )

    val trackBgColor = ThemeTextPrimary.copy(alpha = 0.15f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val strokeWidth = 12.dp.toPx()
            val spacing = 12.dp.toPx()

            val radius1 = (size.minDimension / 2) - strokeWidth
            val radius2 = radius1 - strokeWidth - spacing

            // Background tracks
            drawCircle(
                color = trackBgColor,
                radius = radius1,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = trackBgColor,
                radius = radius2,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Progress Arcs with shadow glows
            val glowWidth = strokeWidth + 4.dp.toPx()

            // 1. Calories Eaten Arc
            drawArc(
                color = NeonGreen.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = (caloriesAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius1, center.y - radius1),
                size = Size(radius1 * 2, radius1 * 2),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = NeonGreen,
                startAngle = -90f,
                sweepAngle = (caloriesAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius1, center.y - radius1),
                size = Size(radius1 * 2, radius1 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Steps Arc
            drawArc(
                color = ElectricCyan.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = (stepsAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius2, center.y - radius2),
                size = Size(radius2 * 2, radius2 * 2),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ElectricCyan,
                startAngle = -90f,
                sweepAngle = (stepsAnimated * 360f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius2, center.y - radius2),
                size = Size(radius2 * 2, radius2 * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$caloriesRemaining",
                color = ThemeTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "kcal left",
                color = ThemeTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Legend helper
@Composable
fun RingLegendItem(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = ThemeTextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = ThemeTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Progress bar helper for macros
@Composable
fun MacroProgressBar(
    label: String,
    eaten: Double,
    target: Double,
    color: Color,
    unit: String,
    entries: List<NutritionEntryEntity> = emptyList(),
    macroSelector: (NutritionEntryEntity) -> Double = { 0.0 }
) {
    val progress = if (target > 0.0) (eaten / target).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "MacroProgress"
    )

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = ThemeTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 8.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "${eaten.toInt()}${unit} / ${target.toInt()}${unit}",
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThemeTextPrimary.copy(alpha = 0.05f))
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val contributingEntries = entries
                    .map { it to macroSelector(it) }
                    .filter { it.second > 0.0 }
                    .sortedByDescending { it.second }

                if (contributingEntries.isEmpty()) {
                    Text(
                        text = "No entries logged today containing $label.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    contributingEntries.forEach { (entry, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.foodText,
                                color = ThemeTextPrimary.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${amount.toInt()}$unit",
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = ThemeTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MealItemRow(
    entry: NutritionEntryEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodText,
                color = ThemeTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${entry.calories.toInt()} kcal",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "P: ${entry.proteinG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = "C: ${entry.carbsG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = "F: ${entry.fatG.toInt()}g",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.DarkGray)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete entry",
                tint = EnergeticCoral.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ActivityProgressBar(
    label: String,
    current: Double,
    target: Double,
    color: Color,
    unit: String
) {
    val progress = if (target > 0.0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "ActivityProgress"
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = ThemeTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${current.toInt()} / ${target.toInt()} $unit",
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThemeTextPrimary.copy(alpha = 0.05f))
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }
    }
}
