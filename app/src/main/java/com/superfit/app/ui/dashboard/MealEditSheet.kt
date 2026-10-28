package com.superfit.app.ui.dashboard

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.Level
import com.superfit.app.domain.MealItem
import com.superfit.app.domain.MealItemReplacer
import com.superfit.app.domain.MealItems
import com.superfit.app.domain.withItems
import com.superfit.app.theme.DeleteRed
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeBrandSoft
import com.superfit.app.theme.ThemeErrorColor
import com.superfit.app.theme.ThemeFieldBg
import com.superfit.app.theme.ThemeGlassBorder
import com.superfit.app.theme.ThemeTextPrimary
import com.superfit.app.theme.ThemeTextSecondary
import com.superfit.app.ui.components.SuperfitField
import java.util.Locale
import kotlinx.coroutines.launch

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

/**
 * The item rows, oil and sugar pickers and live totals, shared by Edit meal and Check your meal.
 * Any item can be changed to a different food; only that item gets new numbers.
 */
@Composable
internal fun MealItemsEditor(
    meal: MealItems,
    onChange: (MealItems) -> Unit,
    replaceItem: suspend (item: MealItem, newName: String, rest: List<MealItem>) -> List<MealItem> = MealItemReplacer()::replace
) {
    val totals = meal.totals()
    val scope = rememberCoroutineScope()
    // The item being renamed, and whether the AI is working on it.
    var changing by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Why the last change failed, in words the user can act on; null when it didn't.
    var failed by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        meal.items.forEachIndexed { index, item ->
            if (changing == index) {
                ChangeItemBox(
                    item = item,
                    busy = busy,
                    failed = failed,
                    onCancel = {
                        changing = null
                        failed = null
                    },
                    onChange = { newName ->
                        busy = true
                        failed = null
                        scope.launch {
                            try {
                                val replaced = replaceItem(item, newName, meal.items.filterIndexed { i, _ -> i != index })
                                onChange(meal.replace(index, replaced))
                                AppAnalytics.mealItemChanged(typed = true)
                                changing = null
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("MealItemsEditor", "Changing item failed", e)
                                failed = if (e is MealItemReplacer.UnreadableAnswer) {
                                    "Couldn't work out that food. Try saying it a bit differently, like \"1 oats chilla\"."
                                } else {
                                    GeminiClient.sanitizeError(e)
                                }
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
            } else {
                MealItemEditRow(
                    item = item,
                    askToCheck = !meal.checked,
                    canRemove = meal.items.size > 1,
                    canChange = !busy,
                    onQtyChange = { qty ->
                        onChange(meal.copy(items = meal.items.toMutableList().also { it[index] = item.copy(qty = qty) }))
                    },
                    onChoose = { choice ->
                        onChange(meal.copy(items = meal.items.toMutableList().also { it[index] = item.choose(choice) }))
                        AppAnalytics.mealItemChanged(typed = false)
                    },
                    onChangeName = {
                        changing = index
                        failed = null
                    },
                    onRemove = {
                        onChange(meal.copy(items = meal.items.toMutableList().also { it.removeAt(index) }))
                    }
                )
            }
        }

        if (meal.hasOil) {
            LevelPicker(label = "Oil / ghee", selected = meal.oil, onSelect = { onChange(meal.copy(oil = it)) })
        }
        if (meal.hasSugar) {
            LevelPicker(label = "Sugar", selected = meal.sugar, onSelect = { onChange(meal.copy(sugar = it)) })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ThemeBrandSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Total ${totals.kcal.toInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                color = ThemeTextPrimary
            )
            Text(
                text = "Protein ${totals.protein.toInt()}g  ·  Carbs ${totals.carbs.toInt()}g  ·  Fat ${totals.fat.toInt()}g  ·  Fibre ${totals.fiber.toInt()}g",
                fontSize = 13.sp,
                color = ThemeTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MealItemEditRow(
    item: MealItem,
    askToCheck: Boolean,
    canRemove: Boolean,
    canChange: Boolean,
    onQtyChange: (Double) -> Unit,
    onChoose: (Int) -> Unit,
    onChangeName: () -> Unit,
    onRemove: () -> Unit
) {
    val highlight = item.isUnsure && askToCheck
    // Name on its own line and the controls below it, so large text sizes never squeeze the name.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, if (highlight) ThemeErrorColor else ThemeGlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canChange, onClickLabel = "Change this item", onClick = onChangeName)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    color = ThemeTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = ThemeTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
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
                    Icon(Icons.Default.Close, contentDescription = "Remove ${item.name}", tint = DeleteRed.copy(alpha = 0.8f))
                }
            }
        }
        if (item.isUnsure) {
            Text(
                text = if (highlight) "Is it…?" else "Or was it…?",
                color = if (highlight) ThemeErrorColor else ThemeTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item.choices.forEachIndexed { i, choice ->
                    FilterChip(
                        selected = choice.name.equals(item.name, ignoreCase = true),
                        onClick = { onChoose(i) },
                        label = { Text(choice.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.18f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                }
                FilterChip(
                    selected = false,
                    enabled = canChange,
                    onClick = onChangeName,
                    label = { Text("Something else…", fontSize = 13.sp, color = ThemeTextSecondary) }
                )
            }
        }
    }
}

/** Type or say what the item really was. */
@Composable
private fun ChangeItemBox(
    item: MealItem,
    busy: Boolean,
    failed: String?,
    onCancel: () -> Unit,
    onChange: (String) -> Unit
) {
    var text by remember(item) { mutableStateOf("") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            text = it
            if (it.isNotBlank()) onChange(it)
        }
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.03f))
            .border(1.dp, NeonGreen, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "What was it instead of ${item.name}?",
            color = ThemeTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonGreen)
                Text("Working out the numbers…", color = ThemeTextSecondary, fontSize = 13.sp)
            }
            return@Column
        }
        SuperfitField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("e.g. the right name of the food") },
            trailingIcon = {
                com.superfit.app.ui.components.MicButton(
                    onClick = {
                        try {
                            voice.launch(SpeechRecognizerHelper.systemDialogIntent(context).putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what it was"))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Voice typing isn't available. Please type it.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = "Say it"
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onChange(text) }),
            modifier = Modifier.fillMaxWidth()
        )
        if (failed != null) {
            Text(failed, color = ThemeErrorColor, fontSize = 12.sp)
        }
        Text(
            "Amount stays ${formatAmount(item.qty)} ${unitLabel(item.unit)} unless you say a different one.",
            color = ThemeTextSecondary,
            fontSize = 12.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onChange(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
            ) {
                Text("Change", fontWeight = FontWeight.Bold)
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
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = ThemeBrandSoft,
            contentColor = NeonGreen
        )
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
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) NeonGreen else ThemeFieldBg)
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
                        color = if (isSelected) Color.White else ThemeTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
