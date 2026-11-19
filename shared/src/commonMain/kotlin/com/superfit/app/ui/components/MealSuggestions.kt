package com.superfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.superfit.app.data.UsageEvents
import com.superfit.app.domain.MealSuggestion
import com.superfit.app.theme.SuperfitTheme

private val ListShape = RoundedCornerShape(14.dp)

/**
 * The short list under a meal box while typing: past meals (with their calories) and single
 * foods from past meals, the typed letters in green. Tapping one hands back the whole new box text.
 */
@Composable
fun MealSuggestionList(
    suggestions: List<MealSuggestion>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    val colors = SuperfitTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ListShape)
            .background(colors.fieldBg)
            .border(1.dp, colors.glassBorder, ListShape)
    ) {
        suggestions.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = colors.glassBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable {
                        UsageEvents.mealSuggestionUsed(if (s.kind == MealSuggestion.Kind.MEAL) "meal" else "food")
                        onPick(s.result)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (s.kind == MealSuggestion.Kind.MEAL) Icons.Default.History else Icons.Default.Restaurant,
                    contentDescription = if (s.kind == MealSuggestion.Kind.MEAL) "Past meal" else "Food",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = buildAnnotatedString {
                        val start = s.matchStart.coerceIn(0, s.text.length)
                        val end = (start + s.matchLength).coerceIn(start, s.text.length)
                        append(s.text.substring(0, start))
                        withStyle(SpanStyle(color = colors.primaryAccent, fontWeight = FontWeight.Bold)) {
                            append(s.text.substring(start, end))
                        }
                        append(s.text.substring(end))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                s.kcal?.let {
                    Text(
                        text = "$it kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}
