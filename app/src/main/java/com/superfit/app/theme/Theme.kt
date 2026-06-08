package com.superfit.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

private val DarkColorScheme = darkColorScheme(
    primary = HyperViolet,
    secondary = ElectricCyan,
    tertiary = NeonMint,
    background = DarkBgStart,
    surface = CardBg,
    error = CoralRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED), // vibrant violet-600
    secondary = Color(0xFF0891B2), // vibrant cyan-600
    tertiary = Color(0xFF059669),  // vibrant emerald-600
    background = LightBgStart,
    surface = LightCardBg,
    error = Color(0xFFDC2626),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onError = Color.White
)

val LocalThemeIsDark = staticCompositionLocalOf { true }

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color disabled to enforce our custom premium visual system
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
            content = content
        )
    }
}

