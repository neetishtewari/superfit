package com.superfit.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superfit.app.R
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** The main action on a screen: solid brand green with white text, the same everywhere. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonMint,
            contentColor = Color.White,
            disabledContainerColor = NeonMint.copy(alpha = if (loading) 1f else 0.4f),
            disabledContentColor = Color.White
        )
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** The green "S" app icon, used wherever the app introduces itself. */
@Composable
fun SuperfitLogo(modifier: Modifier = Modifier, size: Int = 64) {
    Image(
        painter = painterResource(R.drawable.ic_superfit_launcher),
        contentDescription = "Superfit",
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp))
    )
}

/** Logo, name and tagline, centred: the top of the sign-in screen. */
@Composable
fun SuperfitBrandHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SuperfitLogo(size = 72)
        Text("Superfit", style = MaterialTheme.typography.headlineMedium, color = SuperfitTheme.colors.textPrimary)
        Text("AI Fitness Coach", style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textSecondary)
    }
}
