package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import com.superfit.app.domain.Level
import com.superfit.app.domain.MealItem
import com.superfit.app.domain.MealItems
import com.superfit.app.domain.withItems
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import java.util.Locale

/**
 * Lets the user fix a logged meal: the amount of each food, and how much oil and sugar went in.
 * Numbers update as they tap; nothing is saved until Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditSheet(
    entry: NutritionEntryEntity,
    onSave: (NutritionEntryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var meal by remember(entry.id) { mutableStateOf(MealItems.decode(entry.items) ?: MealItems.legacy(entry)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Edit meal",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeTextPrimary
            )

            MealItemsEditor(meal = meal, onChange = { meal = it })

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(entry.withItems(meal)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** The item rows, oil and sugar pickers and live totals, shared by Edit meal and Check your meal. */
@Composable
internal fun MealItemsEditor(meal: MealItems, onChange: (MealItems) -> Unit) {
    val totals = meal.totals()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        meal.items.forEachIndexed { index, item ->
            MealItemEditRow(
                item = item,
                canRemove = meal.items.size > 1,
                onQtyChange = { qty ->
                    onChange(meal.copy(items = meal.items.toMutableList().also { it[index] = item.copy(qty = qty) }))
                },
                onRemove = {
                    onChange(meal.copy(items = meal.items.toMutableList().also { it.removeAt(index) }))
                }
            )
        }

        if (meal.hasOil) {
            LevelPicker(label = "Oil / ghee", selected = meal.oil, onSelect = { onChange(meal.copy(oil = it)) })
        }
        if (meal.hasSugar) {
            LevelPicker(label = "Sugar", selected = meal.sugar, onSelect = { onChange(meal.copy(sugar = it)) })
        }

        Text(
            text = "${totals.kcal.toInt()} kcal  ·  P ${totals.protein.toInt()}g  ·  C ${totals.carbs.toInt()}g  ·  F ${totals.fat.toInt()}g",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
    }
}

@Composable
private fun MealItemEditRow(
    item: MealItem,
    canRemove: Boolean,
    onQtyChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    // Name on its own line and the controls below it, so large text sizes never squeeze the name.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                color = ThemeTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(item.kcal * item.qty).toInt()} kcal",
                color = ThemeTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                icon = { Icon(Icons.Default.Remove, contentDescription = "Less ${item.name}") },
                enabled = item.qty - item.step >= item.step - 1e-9,
                onClick = { onQtyChange(item.qty - item.step) }
            )
            Text(
                text = "${formatAmount(item.qty)} ${unitLabel(item.unit)}",
                color = ThemeTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp)
            )
            StepButton(
                icon = { Icon(Icons.Default.Add, contentDescription = "More ${item.name}") },
                enabled = true,
                onClick = { onQtyChange(item.qty + item.step) }
            )
            Spacer(modifier = Modifier.weight(1f))
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${item.name}", tint = EnergeticCoral.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
internal fun StepButton(icon: @Composable () -> Unit, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = CircleShape
    ) {
        icon()
    }
}

@Composable
private fun LevelPicker(label: String, selected: Level, onSelect: (Level) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = ThemeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { level ->
                val isSelected = level == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) NeonGreen.copy(alpha = 0.18f) else ThemeTextPrimary.copy(alpha = 0.04f))
                        .border(1.dp, if (isSelected) NeonGreen else ThemeGlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelect(level) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (level) {
                            Level.LOW -> "Low"
                            Level.NORMAL -> "Normal"
                            Level.HIGH -> "High"
                        },
                        color = if (isSelected) NeonGreen else ThemeTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun formatAmount(qty: Double): String =
    if (qty == Math.floor(qty)) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)

private fun unitLabel(unit: String): String = when (unit) {
    "piece" -> "pc"
    else -> unit
}
