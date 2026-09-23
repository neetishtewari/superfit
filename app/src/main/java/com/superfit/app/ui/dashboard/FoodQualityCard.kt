package com.superfit.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.DietQualityMetrics
import com.superfit.app.domain.FoodItemRating
import com.superfit.app.domain.FoodSwapSuggestion
import com.superfit.app.theme.*

@Composable
fun FoodQualityCard(
    metrics: DietQualityMetrics,
    modifier: Modifier = Modifier,
    onLogSwap: ((String) -> Unit)? = null
) {
    var showDetailBottomSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            )
            .clickable { showDetailBottomSheet = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SuperfitTheme.colors.cardBgTranslucent
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = NeonMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val headerTitle = when {
                        metrics.allRatedItems.isEmpty() -> "DIET QUALITY INDEX"
                        metrics.distinctDaysLogged <= 1 -> "DIET QUALITY INDEX (TODAY)"
                        metrics.distinctDaysLogged < 7 -> "DIET QUALITY INDEX (${metrics.distinctDaysLogged}-DAY ROLLING)"
                        else -> "DIET QUALITY INDEX (7-DAY ROLLING)"
                    }
                    Text(
                        text = headerTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = NeonMint.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${metrics.overallScore} / 100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonMint,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View Details",
                        tint = SuperfitTheme.colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Score Category Pill & Linear Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metrics.scoreCategory,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SuperfitTheme.colors.textPrimary
                )

                val tapHint = when {
                    metrics.allRatedItems.isEmpty() -> "Log meals to see items ➔"
                    metrics.distinctDaysLogged <= 1 -> "Tap for tracked items ➔"
                    metrics.distinctDaysLogged < 7 -> "Tap for ${metrics.distinctDaysLogged}-day items ➔"
                    else -> "Tap for 7-day items ➔"
                }
                Text(
                    text = tapHint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { metrics.overallScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (metrics.overallScore >= 75) NeonMint else SolarAmber,
                trackColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.1f)
            )

            // Top Clean Foods Row
            if (metrics.topCleanFoods.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonMint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Top Clean Staples: " + metrics.topCleanFoods.joinToString(", "),
                        fontSize = 11.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }
            }
        }
    }

    if (showDetailBottomSheet) {
        DietQualityDetailBottomSheet(
            metrics = metrics,
            onDismiss = { showDetailBottomSheet = false },
            onLogSwap = onLogSwap
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietQualityDetailBottomSheet(
    metrics: DietQualityMetrics,
    onDismiss: () -> Unit,
    onLogSwap: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val sheetTitle = when {
                        metrics.distinctDaysLogged <= 1 -> "🥗 Today's Diet Quality Breakdown"
                        metrics.distinctDaysLogged < 7 -> "🥗 ${metrics.distinctDaysLogged}-Day Diet Quality Breakdown"
                        else -> "🥗 7-Day Diet Quality Breakdown"
                    }
                    val sheetSubtitle = when {
                        metrics.distinctDaysLogged <= 1 -> "Itemized food cleanliness scores & day 1 feedback"
                        else -> "Itemized food cleanliness scores & frequency impact"
                    }
                    Text(
                        text = sheetTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SuperfitTheme.colors.textPrimary
                    )
                    Text(
                        text = sheetSubtitle,
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = NeonMint.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${metrics.overallScore} / 100",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonMint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = SuperfitTheme.colors.glassBorder)

            if (metrics.allRatedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No meals logged yet.\nLog your food using the mic or text input to see itemized quality scores!",
                        color = SuperfitTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        val trackedItemsHeader = when {
                            metrics.distinctDaysLogged <= 1 -> "TODAY'S TRACKED FOOD ITEMS (${metrics.allRatedItems.size})"
                            metrics.distinctDaysLogged < 7 -> "${metrics.distinctDaysLogged}-DAY TRACKED FOOD ITEMS (${metrics.allRatedItems.size})"
                            else -> "7-DAY TRACKED FOOD ITEMS (${metrics.allRatedItems.size})"
                        }
                        Text(
                            text = trackedItemsHeader,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textTertiary,
                            letterSpacing = 0.8.sp
                        )
                    }

                    items(metrics.allRatedItems) { item ->
                        FoodRatingItemRow(item = item)
                    }

                    if (metrics.swapSuggestions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "💡 SMART FOOD SWAP RECOMMENDATIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textTertiary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        items(metrics.swapSuggestions) { swap ->
                            FoodSwapCard(
                                swap = swap,
                                onLogSwap = { alternative ->
                                    onLogSwap?.invoke(alternative)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoodRatingItemRow(item: FoodItemRating) {
    val actionColor = when (item.actionColorType) {
        "GREEN" -> NeonMint
        "YELLOW" -> ElectricCyan
        "ORANGE" -> SolarAmber
        "RED" -> CoralRed
        else -> ElectricCyan
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SuperfitTheme.colors.textPrimary.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, SuperfitTheme.colors.glassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.foodText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuperfitTheme.colors.textPrimary
                    )
                    val freqDesc = when {
                        item.daysLoggedCount <= 1 && item.weeklyFrequency <= 1 -> "Logged 1x • ~${item.avgCalories} kcal"
                        item.daysLoggedCount <= 1 -> "Logged ${item.weeklyFrequency}x today • ~${item.avgCalories} kcal avg"
                        else -> "Logged ${item.weeklyFrequency}x across ${item.daysLoggedCount} days • ~${item.avgCalories} kcal avg"
                    }
                    Text(
                        text = freqDesc,
                        fontSize = 11.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = actionColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${item.cleanlinessScore}/10 Clean",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = actionColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = actionColor.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Why: ${item.scoreRationale.ifBlank { item.actionRecommendation }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = actionColor,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FoodSwapCard(
    swap: FoodSwapSuggestion,
    onLogSwap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SuperfitTheme.colors.cardBgTranslucent
        )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = NeonMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMART FOOD SWAP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = NeonMint.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Save ~${swap.calorieSavings} kcal",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Side-by-Side Comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Original Choice
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Choice",
                        fontSize = 10.sp,
                        color = SuperfitTheme.colors.textTertiary
                    )
                    Text(
                        text = swap.originalFood,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CoralRed
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NeonMint,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(20.dp)
                )

                // Recommended Swap
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "Recommended Swap",
                        fontSize = 10.sp,
                        color = SuperfitTheme.colors.textTertiary
                    )
                    Text(
                        text = swap.suggestedAlternative,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Benefit: ${swap.benefitHighlight}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SuperfitTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onLogSwap(swap.suggestedAlternative) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonMint,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Log Smart Swap (1-Tap)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
