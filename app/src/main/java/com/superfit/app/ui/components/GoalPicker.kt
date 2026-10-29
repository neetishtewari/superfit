package com.superfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.FitnessGoal
import com.superfit.app.domain.FitnessGoals
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/**
 * The goal choice used in onboarding and Settings: one card per goal with its name and a line on
 * what it means. [selectedDetail] is shown under the chosen card, e.g. the person's own targets.
 */
@Composable
fun GoalPicker(
    selected: String,
    onSelect: (FitnessGoal) -> Unit,
    modifier: Modifier = Modifier,
    selectedDetail: String? = null
) {
    val colors = SuperfitTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FitnessGoals.all.forEach { goal ->
            val isSelected = goal.key == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isSelected) colors.brandSoft else colors.cardBg)
                    .border(1.dp, if (isSelected) NeonMint else colors.glassBorder, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(goal) })
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = goal.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = goal.explanation,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary
                    )
                    if (isSelected && !selectedDetail.isNullOrBlank()) {
                        Text(
                            text = selectedDetail,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonMint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = NeonMint, unselectedColor = colors.textTertiary)
                )
            }
        }
    }
}
