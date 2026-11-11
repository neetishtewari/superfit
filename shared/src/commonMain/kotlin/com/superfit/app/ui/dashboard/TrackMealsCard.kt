package com.superfit.app.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.CoachPatternEngine
import com.superfit.app.domain.MealTags
import com.superfit.app.theme.*
import com.superfit.app.ui.components.SuperfitField

@Composable
fun TrackMealsCard(
    state: DashboardUiState.Success,
    predictedFoods: List<PredictedFood>,
    canRetryLastMeal: Boolean,
    onLogMeal: (String) -> Unit,
    onRetryLastMeal: () -> Unit,
    onDeleteMeal: (NutritionEntryEntity) -> Unit,
    onUpdateMeal: (NutritionEntryEntity) -> Unit,
    parsingState: ParsingState,
    foodInputText: String,
    onFoodInputChange: (String) -> Unit,
    onViewAllFoods: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ThemeGlassBorder,
                        ThemeGlassBorderGlow
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Today's meals",
            style = MaterialTheme.typography.titleLarge,
            color = ThemeTextPrimary
        )

        val submitMeal = {
            if (foodInputText.isNotBlank()) {
                onLogMeal(foodInputText)
                onFoodInputChange("")
            }
        }
        SuperfitField(
            value = foodInputText,
            onValueChange = onFoodInputChange,
            placeholder = { Text("Type what you ate, e.g. 2 eggs and a banana") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submitMeal() }),
            trailingIcon = {
                if (parsingState == ParsingState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NeonGreen, strokeWidth = 2.dp)
                } else {
                    com.superfit.app.ui.components.FieldActionButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Log this meal",
                        enabled = foodInputText.isNotBlank(),
                        onClick = submitMeal,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        )

        // Suggestions chips
        if (predictedFoods.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Your usual foods",
                    fontSize = 12.sp,
                    color = ThemeTextTertiary,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(predictedFoods.take(6)) { predicted ->
                        SuggestionChip(
                            onClick = { onFoodInputChange(predicted.foodText) },
                            label = {
                                Text(
                                    text = predicted.foodText,
                                    color = ThemeTextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = ThemeTextPrimary.copy(alpha = 0.05f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = ThemeGlassBorder
                            )
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = onViewAllFoods,
                            label = {
                                Text(
                                    text = "See all",
                                    color = ThemeTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = ThemeTextPrimary.copy(alpha = 0.05f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = ThemeGlassBorder
                            )
                        )
                    }
                }
            }
        }

        // Display parsing state messages
        AnimatedVisibility(visible = parsingState is ParsingState.Error) {
            val errMsg = (parsingState as? ParsingState.Error)?.message ?: "Error"
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EnergeticCoral
                    )
                    Text(
                        text = errMsg,
                        color = EnergeticCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Re-sends what the user last said or typed, so they don't have to say it again.
                if (canRetryLastMeal) {
                    Button(
                        onClick = onRetryLastMeal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnergeticCoral.copy(alpha = 0.15f),
                            contentColor = EnergeticCoral
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = "Try again",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Meals logged today list
        Text(
            text = "Logged so far",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = ThemeTextPrimary
        )

        if (state.nutritionList.isEmpty()) {
            Text(
                text = "No meals logged today yet.",
                fontSize = 13.sp,
                color = ThemeTextSecondary
            )
        } else {
            var editingMeal by remember { mutableStateOf<NutritionEntryEntity?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.nutritionList.forEach { entry ->
                    MealItemRow(
                        entry = entry,
                        onDelete = { onDeleteMeal(entry) },
                        onClick = { editingMeal = entry }
                    )
                }
            }
            editingMeal?.let { meal ->
                MealEditSheet(
                    entry = meal,
                    onSave = {
                        onUpdateMeal(it)
                        editingMeal = null
                    },
                    onDismiss = { editingMeal = null }
                )
            }
        }
    }
}
