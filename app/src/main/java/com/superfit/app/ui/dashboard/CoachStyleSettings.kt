package com.superfit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** "AI coach style": how the daily tip and the chat talk to the user. One roomy row per style. */
@Composable
fun CoachStyleSettings(viewModel: DashboardViewModel) {
    val style by viewModel.coachStyle.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "AI coach style",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        CoachStyle.entries.forEach { option ->
            val isSelected = option == style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) SuperfitTheme.colors.brandSoft else SuperfitTheme.colors.fieldBg)
                    .then(if (isSelected) Modifier.border(1.5.dp, NeonMint, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { viewModel.setCoachStyle(option) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.setCoachStyle(option) },
                    colors = RadioButtonDefaults.colors(selectedColor = NeonMint)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = option.label,
                        color = SuperfitTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = option.blurb + if (option == CoachStyle.ROAST) " Roasts your habits, never you." else "",
                        color = SuperfitTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
