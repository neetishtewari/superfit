package com.superfit.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.BrandGreenDeep
import com.superfit.app.theme.SuperfitTheme
import java.time.YearMonth

/** "Your October is ready": on Home from the 1st, and at the top of Progress for the first week. */
@Composable
fun RecapReadyCard(month: YearMonth, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BrandGreenDeep)
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Your ${monthName(month)} is ready", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Your month in a few cards worth sharing.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandGreenDeep)
        ) { Text("Open", fontWeight = FontWeight.Bold) }
    }
}

/** Progress's "Past months": every earlier recap, newest first. */
@Composable
fun PastMonthsList(months: List<YearMonth>, onOpen: (YearMonth) -> Unit) {
    if (months.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Past months", color = SuperfitTheme.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        months.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(m) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your ${monthName(m)} ${m.year}", color = SuperfitTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SuperfitTheme.colors.textSecondary)
            }
        }
    }
}
