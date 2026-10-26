package com.superfit.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3FD3A2),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF143A2E),
    onPrimaryContainer = Color(0xFFB9F2DC),
    secondary = Color(0xFF4CC9E8),
    onSecondary = Color(0xFF003543),
    tertiary = Color(0xFFA993FF),
    onTertiary = Color(0xFF241466),
    background = DarkBgStart,
    onBackground = Color(0xFFE6EEEA),
    surface = CardBg,
    onSurface = Color(0xFFE6EEEA),
    surfaceVariant = Color(0xFF1C2724),
    onSurfaceVariant = Color(0xFFA2B3AC),
    surfaceContainerHighest = Color(0xFF1C2724),
    surfaceContainerHigh = Color(0xFF18221F),
    surfaceContainer = CardBg,
    surfaceContainerLow = CardBg,
    outline = Color(0xFF3A4843),
    outlineVariant = Color(0xFF26332E),
    error = Color(0xFFF2B04C),
    onError = Color(0xFF3A2C12)
)

private val LightColorScheme = lightColorScheme(
    primary = NeonMint,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenDeep,
    secondary = ElectricCyan,
    onSecondary = Color.White,
    tertiary = HyperViolet,
    onTertiary = Color.White,
    background = LightBgStart,
    onBackground = Color(0xFF10201A),
    surface = LightCardBg,
    onSurface = Color(0xFF10201A),
    surfaceVariant = Color(0xFFEDF2F0),
    onSurfaceVariant = Color(0xFF4A5B54),
    surfaceContainerHighest = Color(0xFFEDF2F0),
    surfaceContainerHigh = Color(0xFFF1F5F3),
    surfaceContainer = LightCardBg,
    surfaceContainerLow = LightCardBg,
    outline = Color(0xFFB7C4BE),
    outlineVariant = Color(0xFFE1E8E5),
    error = Color(0xFFB26A00),
    onError = Color.White
)

// One set of corners: 12 for chips and small tiles, 20 for cards, 28 for sheets.
val SuperfitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalThemeIsDark = staticCompositionLocalOf { false }

object SuperfitTheme {
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeIsDark.current

    val colors: SuperfitColors
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) DarkColors else LightColors
}

@Composable
fun SuperfitTheme(
    darkTheme: Boolean = false,
    // Keep dynamic color disabled to enforce our custom premium visual system
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = SuperfitShapes,
            content = content
        )
    }
}
