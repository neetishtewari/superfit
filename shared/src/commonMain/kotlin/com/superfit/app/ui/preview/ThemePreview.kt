package com.superfit.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.Decimals
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeBgStart
import com.superfit.app.theme.ThemeTextSecondary

/**
 * A stand-in screen drawn with Superfit's shared theme (colours, Manrope, shapes), so the iPhone
 * can show real Superfit styling before the actual screens move to shared code.
 */
@Composable
fun ThemePreview(darkTheme: Boolean) {
    SuperfitTheme(darkTheme = darkTheme) {
        var taps by remember { mutableIntStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeBgStart)
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Superfit", style = MaterialTheme.typography.headlineLarge)
            Text("Your AI coach for food and fitness", style = MaterialTheme.typography.bodyLarge, color = ThemeTextSecondary)
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleLarge)
                    Text("1,240 of 1,804 kcal", style = MaterialTheme.typography.displaySmall)
                    Text("Protein ${Decimals.one(72.46)} g", style = MaterialTheme.typography.bodyMedium, color = ThemeTextSecondary)
                }
            }
            Button(onClick = { taps++ }, modifier = Modifier.fillMaxWidth()) {
                Text(if (taps == 0) "Tell me what you ate" else "Tapped $taps")
            }
        }
    }
}
