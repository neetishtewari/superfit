package com.superfit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.HealthSource
import com.superfit.app.domain.HealthTopic
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/**
 * The small "How it's worked out · Sources" link on a card with health numbers or tips. Tapping it
 * shows how the numbers are worked out, with links to the published sources, and the not-medical-advice note.
 */
@Composable
fun HealthSourcesLink(topics: List<HealthTopic>, modifier: Modifier = Modifier, label: String = "How it's worked out · Sources") {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Show sources") { open = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = SuperfitTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary, fontWeight = FontWeight.Medium)
    }
    if (open) HealthSourcesSheet(topics = topics, onDismiss = { open = false })
}

/** How the numbers are worked out, the sources behind them as links, and the not-medical-advice note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSourcesSheet(topics: List<HealthTopic>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("How it's worked out", style = MaterialTheme.typography.titleLarge, color = SuperfitTheme.colors.textPrimary)
            topics.forEach { topic -> HealthTopicSection(topic) }
            Text(
                HealthTopic.DISCLAIMER,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = SuperfitTheme.colors.textSecondary
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = SuperfitTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun HealthTopicSection(topic: HealthTopic) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(topic.title, style = MaterialTheme.typography.titleSmall, color = SuperfitTheme.colors.textPrimary)
        Text(topic.explanation, fontSize = 14.sp, lineHeight = 20.sp, color = SuperfitTheme.colors.textSecondary)
        if (topic.sources.isNotEmpty()) {
            Text("Sources", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textSecondary)
            topic.sources.forEach { SourceRow(it) }
        }
    }
}

@Composable
private fun SourceRow(source: HealthSource) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Open source") { runCatching { uriHandler.openUri(source.url) } }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = NeonMint,
            modifier = Modifier.padding(top = 2.dp).size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(source.title, fontSize = 14.sp, lineHeight = 19.sp, color = NeonMint, fontWeight = FontWeight.Medium)
            Text(source.publisher, fontSize = 12.sp, lineHeight = 16.sp, color = SuperfitTheme.colors.textSecondary)
        }
    }
}

/** Settings › Health info: the not-medical-advice note and every source the app's numbers and tips follow. */
@Composable
fun HealthInfoSettingsContent() {
    var open by remember { mutableStateOf(false) }
    Text("Health info and sources", style = MaterialTheme.typography.titleSmall, color = SuperfitTheme.colors.textPrimary)
    Text(HealthTopic.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = SuperfitTheme.colors.textSecondary)
    TextButton(onClick = { open = true }) {
        Text("See how targets are worked out, with sources", color = NeonMint, fontWeight = FontWeight.SemiBold)
    }
    if (open) HealthSourcesSheet(topics = HealthTopic.entries, onDismiss = { open = false })
}
