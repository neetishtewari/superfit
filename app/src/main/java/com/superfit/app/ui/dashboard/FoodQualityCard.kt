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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.DietNote
import com.superfit.app.domain.DietQualityMetrics
import com.superfit.app.domain.DietScorePart
import com.superfit.app.domain.FoodItemRating
import com.superfit.app.domain.FoodSwapSuggestion
import com.superfit.app.domain.FoodVerdict
import com.superfit.app.theme.*

@Composable
fun FoodQualityCard(
    metrics: DietQualityMetrics,
    modifier: Modifier = Modifier,
    note: DietNote? = null,
    onFineForMe: ((FoodItemRating, Boolean) -> Unit)? = null
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
                    val headerTitle = "Diet quality · last 7 days"
                    Text(
                        text = headerTitle,
                        fontSize = 12.sp,
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
                            text = if (metrics.hasEnoughData) "${metrics.overallScore} / 100" else "– / 100",
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

            if (!metrics.hasEnoughData) {
                Text(
                    text = if (metrics.distinctDaysLogged >= com.superfit.app.domain.DietScoreEngine.MIN_DAYS) {
                        "Your meals are still being analysed. Your score appears once the AI has rated them."
                    } else {
                        "Log your meals on at least 3 days this week to see your score. It looks at the pattern of your week, so one meal or one day can't make or break it."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = SuperfitTheme.colors.textSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = metrics.scoreCategory,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SuperfitTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    metrics.changeFromLastWeek?.let { change ->
                        Text(
                            text = when {
                                change > 0 -> "▲ $change vs last week"
                                change < 0 -> "▼ ${-change} vs last week"
                                else -> "Same as last week"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (change >= 0) NeonMint else SolarAmber,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
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

                if (note != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DietNoteBlock(note)
                }

                metrics.focusTip?.takeIf { note == null }?.let { tip ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Biggest win available: $tip",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = SuperfitTheme.colors.textPrimary
                    )
                }

                if (metrics.breakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        metrics.breakdown.forEach { part -> DietScorePartRow(part) }
                    }
                }

                if (metrics.estimatedShare >= 0.3) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Some older meals are still being analysed, so this score may shift a little.",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textTertiary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tap to see each food",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            }

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
                        text = "Your best choices: " + metrics.topCleanFoods.joinToString(", "),
                        fontSize = 12.sp,
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
            onFineForMe = onFineForMe
        )
    }
}

/** The AI nutrition note (not the user's own nutritionist): what's going well and up to two things to change. */
@Composable
private fun DietNoteBlock(note: DietNote) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NeonMint.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "AI nutrition note",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = SuperfitTheme.colors.textTertiary
            )
            if (note.good.isNotBlank()) {
                Text(text = "✓ ${note.good}", fontSize = 12.sp, lineHeight = 17.sp, color = SuperfitTheme.colors.textPrimary)
            }
            note.fixes.forEach { fix ->
                Text(text = "→ $fix", fontSize = 12.sp, lineHeight = 17.sp, color = SuperfitTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun DietScorePartRow(part: DietScorePart) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = part.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SuperfitTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${part.points} / ${part.maxPoints}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (part.points * 10 >= part.maxPoints * 8) NeonMint else SolarAmber,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LinearProgressIndicator(
            progress = { part.points.toFloat() / part.maxPoints },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = if (part.points * 10 >= part.maxPoints * 8) NeonMint else SolarAmber,
            trackColor = SuperfitTheme.colors.textPrimary.copy(alpha = 0.1f)
        )
        Text(
            text = part.detail,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = SuperfitTheme.colors.textSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietQualityDetailBottomSheet(
    metrics: DietQualityMetrics,
    onDismiss: () -> Unit,
    onFineForMe: ((FoodItemRating, Boolean) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.White.copy(alpha = 0.6f),
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    // Same window and score as the card, so the two never disagree.
                    val sheetTitle = "Diet quality, last 7 days"
                    val sheetSubtitle = when {
                        metrics.distinctDaysLogged <= 1 -> "Each food rated as a nutritionist would"
                        else -> "Each food rated as a nutritionist would, for its amount and your goal"
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
                        text = if (metrics.hasEnoughData) "${metrics.overallScore} / 100" else "– / 100",
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textTertiary,
                            letterSpacing = 0.8.sp
                        )
                    }

                    val avoid = metrics.allRatedItems.filter { it.rated && it.verdict == FoodVerdict.AVOID }
                    if (avoid.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SolarAmber.copy(alpha = 0.10f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Avoid for now",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = SolarAmber
                                    )
                                    Text(
                                        text = avoid.joinToString(", ") { it.foodText } +
                                            ". These work against your goal, so skip them until you get there. Treats are different: a couple a week is fine.",
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = SuperfitTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    items(metrics.allRatedItems) { item ->
                        FoodRatingItemRow(item = item, onFineForMe = onFineForMe)
                    }

                    if (metrics.swapSuggestions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Easy swaps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuperfitTheme.colors.textTertiary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        items(metrics.swapSuggestions) { swap ->
                            FoodSwapCard(swap = swap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoodRatingItemRow(item: FoodItemRating, onFineForMe: ((FoodItemRating, Boolean) -> Unit)? = null) {
    val actionColor = if (!item.rated) {
        ElectricCyan
    } else {
        when (item.verdict) {
            FoodVerdict.GREAT -> NeonMint
            FoodVerdict.GOOD -> ElectricCyan
            FoodVerdict.PORTION -> SolarAmber
            FoodVerdict.TREAT -> HyperViolet
            FoodVerdict.SMALL_TREAT -> ElectricCyan
            FoodVerdict.AVOID -> SolarAmber
        }
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
                        item.daysLoggedCount <= 1 -> "Logged ${item.weeklyFrequency}x on one day • ~${item.avgCalories} kcal avg"
                        else -> "Logged ${item.weeklyFrequency}x across ${item.daysLoggedCount} days • ~${item.avgCalories} kcal avg"
                    }
                    Text(
                        text = freqDesc,
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = actionColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (item.rated) item.verdict.label else "Not rated yet",
                        fontSize = 12.sp,
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
                        text = "Why: ${item.scoreRationale.ifBlank { item.actionRecommendation }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = actionColor,
                        lineHeight = 15.sp
                    )
                }
            }

            if (onFineForMe != null && item.rated && (item.approved || item.verdict !in setOf(FoodVerdict.GREAT, FoodVerdict.GOOD))) {
                TextButton(
                    onClick = { onFineForMe(item, !item.approved) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (item.approved) "✓ Fine for me · undo" else "Mark as fine for me (e.g. my nutritionist approves)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.approved) NeonMint else ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun FoodSwapCard(
    swap: FoodSwapSuggestion,
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
                        text = "Smart food swap",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }

                if (swap.calorieSavings > 0) {
                    Surface(
                        shape = CircleShape,
                        color = NeonMint.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Save ~${swap.calorieSavings} kcal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
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
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textTertiary
                    )
                    Text(
                        text = swap.originalFood,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SolarAmber
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
                        fontSize = 12.sp,
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
                text = "Benefit: ${swap.benefitHighlight}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = SuperfitTheme.colors.textSecondary
            )
        }
    }
}
