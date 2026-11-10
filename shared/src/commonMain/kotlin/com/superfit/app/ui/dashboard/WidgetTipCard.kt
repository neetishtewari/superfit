package com.superfit.app.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** One-time Home tip that offers to put the Superfit widget on the home screen. */
@Composable
fun WidgetTipCard(onAdd: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(NeonMint.copy(alpha = 0.5f), SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Log faster: add Superfit to your home screen.",
                fontWeight = FontWeight.Bold,
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 14.sp
            )
            Text(
                text = "See calories left at a glance, and tap the mic or camera to log in seconds.",
                fontSize = 12.sp,
                color = SuperfitTheme.colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNotNow) {
                    Text("Not now", color = SuperfitTheme.colors.textSecondary)
                }
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
