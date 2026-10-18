package com.superfit.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.domain.MealItems
import com.superfit.app.domain.PackAmount
import com.superfit.app.domain.PackagedFood
import com.superfit.app.domain.ParsedNutritionResult
import com.superfit.app.domain.defaultAmount
import com.superfit.app.domain.stepDown
import com.superfit.app.domain.stepUp
import com.superfit.app.domain.toMealItems
import com.superfit.app.domain.withItems
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import com.superfit.app.ui.dashboard.MealItemsEditor
import com.superfit.app.ui.dashboard.NeonGreen
import com.superfit.app.ui.dashboard.StepButton
import java.util.Locale

/** What the AI saw in a meal photo, for the user to adjust before anything is saved. */
@Composable
fun CheckMealSheet(
    result: ParsedNutritionResult,
    onLog: (NutritionEntryEntity) -> Unit,
    onRetake: () -> Unit
) {
    val base = remember(result) {
        NutritionEntryEntity(
            foodText = result.foodText,
            calories = result.calories,
            proteinG = result.proteinG,
            carbsG = result.carbsG,
            fatG = result.fatG,
            timestamp = System.currentTimeMillis(),
            qualityTags = result.tags?.encode(),
            items = result.items?.encode()
        )
    }
    var meal by remember(result) { mutableStateOf(result.items ?: MealItems.legacy(base)) }

    CheckSheetFrame(onRetake = onRetake, onLog = { onLog(base.withItems(meal).copy(timestamp = System.currentTimeMillis())) }) {
        MealItemsEditor(meal = meal, onChange = { meal = it })
    }
}

/** A packet from a barcode or label: choose how much was eaten, then log it. */
@Composable
fun CheckPackSheet(
    food: PackagedFood,
    onLog: (NutritionEntryEntity) -> Unit,
    onRetake: () -> Unit
) {
    var amount by remember(food) { mutableStateOf(food.defaultAmount()) }
    val meal = food.toMealItems(amount)
    val totals = meal.totals()

    CheckSheetFrame(
        onRetake = onRetake,
        onLog = {
            onLog(
                NutritionEntryEntity(
                    foodText = meal.foodText(),
                    calories = totals.kcal,
                    proteinG = totals.protein,
                    carbsG = totals.carbs,
                    fatG = totals.fat,
                    timestamp = System.currentTimeMillis(),
                    items = meal.encode()
                )
            )
        }
    ) {
        Text(
            text = food.name ?: "Packaged food",
            color = ThemeTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        if (food.perServing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp))
                    .padding(2.dp)
            ) {
                AmountTab(
                    label = food.servingGrams?.let { "Servings (${formatAmount(it)} g)" } ?: "Servings",
                    selected = amount is PackAmount.Servings,
                    onClick = { amount = PackAmount.Servings(1.0) }
                )
                AmountTab(
                    label = "Grams",
                    selected = amount is PackAmount.Grams,
                    onClick = { amount = PackAmount.Grams(food.servingGrams?.let { roundTo25(it) } ?: 100.0) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val atMinimum = amount.stepDown() == amount
            StepButton(
                icon = { Icon(Icons.Default.Remove, contentDescription = "Less") },
                enabled = !atMinimum,
                onClick = { amount = amount.stepDown() }
            )
            Text(
                text = when (val a = amount) {
                    is PackAmount.Servings -> when (a.count) {
                        0.5 -> "½ serving"
                        1.0 -> "1 serving"
                        else -> "${formatAmount(a.count)} servings"
                    }
                    is PackAmount.Grams -> "${formatAmount(a.grams)} g"
                },
                color = ThemeTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 110.dp).padding(horizontal = 8.dp)
            )
            StepButton(
                icon = { Icon(Icons.Default.Add, contentDescription = "More") },
                enabled = true,
                onClick = { amount = amount.stepUp() }
            )
        }

        Text(
            text = "${totals.kcal.toInt()} kcal  ·  P ${totals.protein.toInt()}g  ·  C ${totals.carbs.toInt()}g  ·  F ${totals.fat.toInt()}g",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckSheetFrame(
    onRetake: () -> Unit,
    onLog: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onRetake,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = "Check your meal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ThemeTextPrimary)
            Text(
                text = "Fix anything that looks off. Nothing is saved until you tap Log meal.",
                fontSize = 13.sp,
                color = ThemeTextSecondary
            )
            content()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                    Text("Retake")
                }
                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
                ) {
                    Text("Log meal", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RowScope.AmountTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NeonGreen.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) NeonGreen else ThemeTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun roundTo25(grams: Double): Double = maxOf(25.0, Math.round(grams / 25.0) * 25.0)

private fun formatAmount(qty: Double): String =
    if (qty == Math.floor(qty)) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
