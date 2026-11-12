package com.superfit.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.LocalFormat
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.domain.WorkoutView
import com.superfit.app.theme.*
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.ui.dashboard.MealEditSheet
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/** Where a meal typed on History for a past day is: working it out, done, or why it failed. */
sealed interface HistoryParsingState {
    object Idle : HistoryParsingState
    object Loading : HistoryParsingState
    data class Success(val foodText: String) : HistoryParsingState
    data class Error(val message: String) : HistoryParsingState
}

/**
 * The picked day on History, the same on Android and iPhone: eaten against what was burned, its
 * meals and workouts (tap a meal to edit it), and a box to log a meal that was missed. [onShare]
 * is null where share cards aren't ready yet, which hides the share buttons.
 */
@Composable
fun DayBalanceCard(
    summary: DaySummaryState,
    parsingState: HistoryParsingState,
    predictedFoods: List<PredictedFood>,
    onAddMeal: (String) -> Unit,
    onResetParsing: () -> Unit,
    onUpdateMeal: (NutritionEntryEntity) -> Unit,
    onDeleteMeal: (NutritionEntryEntity) -> Unit,
    onDeleteWorkout: (WorkoutEntryEntity) -> Unit,
    onHideImported: (WorkoutView) -> Unit,
    modifier: Modifier = Modifier,
    onShare: ((ShareCard) -> Unit)? = null,
    mealSuggester: com.superfit.app.domain.MealSuggester = com.superfit.app.domain.MealSuggester.EMPTY
) {
    val netColor = if (summary.netBalance < 0) NeonMint else SolarAmber
    val balanceLabel = if (summary.netBalance < 0) "Under what you burned" else "Over what you burned"

    val today = Now.today()
    val dateLabel = when (summary.date) {
        today -> "Today"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> LocalFormat.longDay(summary.date)
    }
    fun count(x: Double) = LocalFormat.integer(x.toLong())

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(ThemeGlassBorder, ThemeGlassBorderGlow)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ThemeTextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Text(
                    text = if (summary.netBalance < 0) "On target" else "Over target",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.netBalance < 0) NeonMint else SolarAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background((if (summary.netBalance < 0) NeonMint else SolarAmber).copy(alpha = 0.15f))
                        .border(1.dp, (if (summary.netBalance < 0) NeonMint else SolarAmber).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Stats: two rows of two, so numbers never wrap on narrow phones.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    BalanceStat("Eaten", "${count(summary.caloriesEaten)} kcal", ThemeTextPrimary, Modifier.weight(1f))
                    BalanceStat("Burned in total", "${count(summary.tdee)} kcal", ThemeTextPrimary, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    BalanceStat("Steps", LocalFormat.integer(summary.steps.toLong()), ThemeTextPrimary, Modifier.weight(1f))
                    BalanceStat("From exercise", "${count(summary.activeBurn)} kcal", ElectricCyan, Modifier.weight(1f))
                }
            }

            // Net balance bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = balanceLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextTertiary
                )
                Text(
                    text = "${kotlin.math.abs(summary.netBalance.toInt())} kcal",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = netColor
                )
            }

            // List of meals ledger
            Text(
                text = "Logged meals",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeTextTertiary,
                letterSpacing = 0.sp
            )

            if (summary.meals.isEmpty()) {
                Text(
                    text = "No meals logged on this day.",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else {
                var editingMeal by remember { mutableStateOf<NutritionEntryEntity?>(null) }
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.meals.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                                .clickable { editingMeal = entry }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.foodText.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextPrimary
                                )
                                Text(
                                    text = "P: ${entry.proteinG.toInt()}g | C: ${entry.carbsG.toInt()}g | F: ${entry.fatG.toInt()}g",
                                    fontSize = 12.sp,
                                    color = ThemeTextTertiary
                                )
                                Text(
                                    text = "Tap to edit",
                                    fontSize = 12.sp,
                                    color = ThemeTextTertiary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${entry.calories.toInt()} kcal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonMint
                                )
                                IconButton(
                                    onClick = { onDeleteMeal(entry) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Meal",
                                        tint = DeleteRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of workouts ledger
            Text(
                text = "Logged workouts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeTextTertiary,
                letterSpacing = 0.sp
            )

            if (summary.importedWorkouts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.importedWorkouts.forEach { workout ->
                        com.superfit.app.ui.dashboard.ImportedWorkoutRow(
                            workout = workout,
                            onHide = { onHideImported(workout) },
                            onShare = onShare?.let { share -> { share(ShareCards.activity(workout)) } }
                        )
                    }
                }
            }

            if (summary.workouts.isEmpty() && summary.importedWorkouts.isEmpty()) {
                Text(
                    text = "No workouts logged on this day.",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            } else if (summary.workouts.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.workouts.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextPrimary
                                )
                                val subText = if (entry.workoutType == "Strength") {
                                    "Strength | ${entry.setsCount} sets x ${entry.repsCount} reps"
                                } else {
                                    "Cardio"
                                }
                                Text(
                                    text = subText,
                                    fontSize = 12.sp,
                                    color = ThemeTextTertiary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "~${entry.caloriesBurned.toInt()} kcal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SolarAmber
                                )
                                if (onShare != null) {
                                    IconButton(
                                        onClick = { onShare(ShareCards.activity(entry)) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share workout",
                                            tint = ThemeTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteWorkout(entry) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Workout",
                                        tint = DeleteRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = ThemeTextPrimary.copy(alpha = 0.05f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Add missed meal text input row
            var foodText by remember { mutableStateOf("") }

            var statusMessage by remember { mutableStateOf<String?>(null) }
            var isErrorState by remember { mutableStateOf(false) }

            LaunchedEffect(parsingState) {
                val state = parsingState
                when (state) {
                    is HistoryParsingState.Success -> {
                        statusMessage = "Logged: ${state.foodText}"
                        isErrorState = false
                        foodText = ""
                        onResetParsing()
                    }
                    is HistoryParsingState.Error -> {
                        statusMessage = state.message
                        isErrorState = true
                        onResetParsing()
                    }
                    else -> {}
                }
            }

            LaunchedEffect(statusMessage) {
                if (statusMessage != null) {
                    kotlinx.coroutines.delay(4000)
                    statusMessage = null
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Log a missed meal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextTertiary,
                    letterSpacing = 0.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuperfitField(
                        value = foodText,
                        onValueChange = { foodText = it },
                        placeholder = { Text("e.g. 1 plate of pasta and chicken breast", fontSize = 12.sp, color = ThemeTextTertiary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = parsingState != HistoryParsingState.Loading
                    )

                    Button(
                        onClick = {
                            if (foodText.isNotBlank()) {
                                onAddMeal(foodText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.15f),
                            contentColor = ElectricCyan
                        ),
                        enabled = foodText.isNotBlank() && parsingState != HistoryParsingState.Loading
                    ) {
                        if (parsingState == HistoryParsingState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Meal",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Past meals and foods that finish what's being typed
                val typingSuggestions = remember(foodText, mealSuggester, parsingState) {
                    if (parsingState == HistoryParsingState.Loading) emptyList() else mealSuggester.suggest(foodText)
                }
                com.superfit.app.ui.components.MealSuggestionList(
                    suggestions = typingSuggestions,
                    onPick = { foodText = it }
                )

                // Suggestions chips
                if (predictedFoods.isNotEmpty() && typingSuggestions.isEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Your usual foods",
                            fontSize = 12.sp,
                            color = ThemeTextTertiary,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(predictedFoods.take(6)) { predicted ->
                                SuggestionChip(
                                    onClick = { foodText = predicted.foodText },
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
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (isErrorState) SolarAmber else NeonMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 12.sp, color = ThemeTextSecondary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}
