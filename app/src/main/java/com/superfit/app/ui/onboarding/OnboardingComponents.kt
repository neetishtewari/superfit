package com.superfit.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.IconBadge

/** Title and one plain line under it, at the top of every step. */
@Composable
internal fun StepTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = SuperfitTheme.colors.textPrimary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textSecondary)
        }
    }
}

/** A small grey label above a field or a group of choices. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SuperfitTheme.colors.textSecondary, modifier = modifier)
}

/** One line with a round green icon, a bold title and a short explanation. */
@Composable
internal fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon = icon, background = SuperfitTheme.colors.brandSoft, tint = SuperfitTheme.colors.primaryAccent, size = 40)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = SuperfitTheme.colors.textPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = SuperfitTheme.colors.textSecondary)
        }
    }
}

/** A label and its value on one line, e.g. "Protein   120 g". */
@Composable
internal fun ValueRow(label: String, value: String, valueColor: Color = SuperfitTheme.colors.textPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

/**
 * Choices laid out [columns] per row as rounded pills, the chosen one solid green with white text
 * (the same look as the app's SegmentedPills, but it can wrap onto two rows).
 */
@Composable
internal fun <T> ChoiceGrid(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, columns: Int = 2) {
    val colors = SuperfitTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.fieldBg)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (key, label) ->
                    val isOn = key == selected
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                        color = if (isOn) Color.White else colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (isOn) NeonMint else Color.Transparent)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = isOn
                            }
                            .clickable { onSelect(key) }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
