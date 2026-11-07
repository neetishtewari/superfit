package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superfit.app.theme.ThemeFieldBg
import com.superfit.app.theme.ThemeTextSecondary

/**
 * Pill-shaped switch used for Food / Activity, the Progress tabs and settings. The chosen option is
 * a raised neutral tile (white on light, lighter grey on dark) with dark text, so green stays for
 * the log buttons and the page doesn't read as all green. With [icons], each option shows its icon above the label and is taller,
 * so it reads as a place to go rather than a filter. Options in [unseen] get a small amber dot.
 */
@Composable
fun SegmentedPills(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icons: Map<String, Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.vector.ImageVector>> = emptyMap(),
    unseen: Set<String> = emptySet()
) {
    val colors = com.superfit.app.theme.SuperfitTheme.colors
    val shape = RoundedCornerShape(if (icons.isEmpty()) 50 else 18)
    val innerShape = RoundedCornerShape(if (icons.isEmpty()) 50 else 15)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThemeFieldBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (key, label) ->
            val isOn = key == selected
            val tint = if (isOn) colors.switchFg else ThemeTextSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (isOn) Modifier.shadow(2.dp, innerShape) else Modifier)
                    .clip(innerShape)
                    .background(if (isOn) colors.switchBg else Color.Transparent)
                    .then(if (isOn) Modifier.border(1.dp, colors.switchLine, innerShape) else Modifier)
                    .clickable { onSelect(key) }
                    .semantics { this.selected = isOn }
                    .heightIn(min = if (icons.isEmpty()) 40.dp else 56.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    icons[key]?.let { (filled, outlined) ->
                        Icon(if (isOn) filled else outlined, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                if (key in unseen && !isOn) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(com.superfit.app.theme.SolarAmber)
                    )
                }
            }
        }
    }
}
