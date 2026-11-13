package com.superfit.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CardChip
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/**
 * Settings > Sharing, on both phones: which runs and walks Today offers to share, what workout cards start with,
 * and whether weight progress may appear.
 */
@Composable
fun SharingSettingsCard(onOffersChanged: () -> Unit = {}) {
    val prefs = LocalShareCardHost.current.prefs
    var plain by remember { mutableStateOf(prefs.defaults(photo = false)) }
    var photo by remember { mutableStateOf(prefs.defaults(photo = true)) }
    var showWeight by remember { mutableStateOf(prefs.showWeight) }
    var offerRuns by remember { mutableStateOf(prefs.offerRuns) }
    var offerWalkMinutes by remember { mutableStateOf(prefs.offerWalkMinutes) }
    val order = remember { CardChip.order(CoachStyle.current) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Suggest runs to share", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(
                        "After every run, Today shows a card you can share in one tap.",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = offerRuns, onCheckedChange = {
                    offerRuns = it
                    prefs.offerRuns = it
                    onOffersChanged()
                })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Suggest walks to share", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
                Text(
                    "When a walk is at least this many minutes long.",
                    fontSize = 12.sp,
                    color = SuperfitTheme.colors.textSecondary
                )
                com.superfit.app.ui.dashboard.SegmentedPills(
                    options = listOf("0" to "Off") + com.superfit.app.domain.ShareOffer.WALK_CHOICES.map { "$it" to "$it" },
                    selected = "${offerWalkMinutes ?: 0}",
                    onSelect = { key ->
                        offerWalkMinutes = key.toInt().takeIf { it > 0 }
                        prefs.offerWalkMinutes = offerWalkMinutes
                        onOffersChanged()
                    }
                )
            }
            Text("Workout cards start with", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
            Text(
                "You can still change these on each card before you share.",
                fontSize = 12.sp,
                color = SuperfitTheme.colors.textSecondary
            )
            DefaultRow("Plain cards", order, plain) {
                plain = CardChip.toggle(plain, it)
                prefs.setDefaults(photo = false, plain)
            }
            DefaultRow("Photo cards", order, photo) {
                photo = CardChip.toggle(photo, it)
                prefs.setDefaults(photo = true, photo)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show weight progress on cards", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(
                        "Adds \"4 of 10 kg down\" to the Journey line. Off keeps your weight private.",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = showWeight, onCheckedChange = {
                    showWeight = it
                    prefs.showWeight = it
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultRow(title: String, order: List<CardChip>, picked: Set<CardChip>, onToggle: (CardChip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            order.forEach { chip ->
                val on = chip in picked
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) NeonMint else SuperfitTheme.colors.fieldBg)
                        .clickable { onToggle(chip) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics { contentDescription = "$title, ${chip.label}${if (on) ", on" else ", off"}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chip.label,
                        color = if (on) Color.White else SuperfitTheme.colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
