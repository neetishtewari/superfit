package com.superfit.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.SuperfitTheme

/** "AI coach style": how the daily tip and the chat talk to the user. */
@Composable
fun CoachStyleSettings(viewModel: DashboardViewModel) {
    val style by viewModel.coachStyle.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "AI coach style",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        SegmentedPills(
            options = CoachStyle.entries.map { it.name to it.label },
            selected = style.name,
            onSelect = { name -> viewModel.setCoachStyle(CoachStyle.fromName(name)) },
            fitLabels = true
        )
        Text(
            text = style.blurb + if (style == CoachStyle.ROAST) " It roasts your habits, never you, and gets serious about pain or health worries." else "",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
