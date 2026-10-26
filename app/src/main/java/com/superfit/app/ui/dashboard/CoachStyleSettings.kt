package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** "AI coach style": how the daily tip and the chat talk to the user. */
@Composable
fun CoachStyleSettings(viewModel: DashboardViewModel) {
    val style by viewModel.coachStyle.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "AI coach style",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SuperfitTheme.colors.textPrimary.copy(alpha = 0.03f))
                .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoachStyle.entries.forEach { option ->
                val isSelected = option == style
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonMint else Color.Transparent)
                        .clickable { viewModel.setCoachStyle(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        color = if (isSelected) Color.White else SuperfitTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text(
            text = style.blurb + if (style == CoachStyle.ROAST) " It roasts your habits, never you, and gets serious about pain or health worries." else "",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp
        )
    }
}
