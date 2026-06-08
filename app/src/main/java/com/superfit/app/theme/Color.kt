package com.superfit.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// Premium Modern Theme Palette
val DarkBgStart = Color(0xFF08080C)
val DarkBgEnd = Color(0xFF111116)
val CardBg = Color(0xFF16161F)
val CardBgTranslucent = Color(0xD816161F)

// Accent Colors
val NeonMint = Color(0xFF10B981)
val ElectricCyan = Color(0xFF06B6D4)
val HyperViolet = Color(0xFF8B5CF6)
val SolarAmber = Color(0xFFF59E0B)
val CoralRed = Color(0xFFFF476A)

// Glassmorphic Border System
val GlassBorder = Color(0x24FFFFFF)
val GlassBorderGlow = Color(0x3BFFFFFF)

// Core Brand Colors (Required for Material3 default mappings)
val Purple80 = HyperViolet
val PurpleGrey80 = Color(0xFFB4B3C4)
val Pink80 = CoralRed

val Purple40 = Color(0xFF6D28D9)
val PurpleGrey40 = Color(0xFF4C4B5E)
val Pink40 = Color(0xFFDB2777)

// Premium Light Mode Palette
val LightBgStart = Color(0xFFF8FAFC) // Alabaster / slate-50
val LightBgEnd = Color(0xFFE2E8F0)   // slate-200
val LightCardBg = Color(0xFFFFFFFF)
val LightCardBgTranslucent = Color(0xD8FFFFFF) // 85% translucent white for glassy overlay
val LightGlassBorder = Color(0x1A0F172A) // 10% opacity slate-900 border
val LightGlassBorderGlow = Color(0x050F172A)

// Custom App Color Mappings
data class SuperfitColors(
    val bgStart: Color,
    val bgEnd: Color,
    val cardBg: Color,
    val cardBgTranslucent: Color,
    val glassBorder: Color,
    val glassBorderGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val errorColor: Color,
    val isDark: Boolean
)

val DarkColors = SuperfitColors(
    bgStart = DarkBgStart,
    bgEnd = DarkBgEnd,
    cardBg = CardBg,
    cardBgTranslucent = CardBgTranslucent,
    glassBorder = GlassBorder,
    glassBorderGlow = GlassBorderGlow,
    textPrimary = Color.White,
    textSecondary = Color(0xFFE5E7EB), // gray-200
    textTertiary = Color(0xFF9CA3AF),  // gray-400
    primaryAccent = HyperViolet,
    secondaryAccent = ElectricCyan,
    tertiaryAccent = NeonMint,
    errorColor = CoralRed,
    isDark = true
)

val LightColors = SuperfitColors(
    bgStart = LightBgStart,
    bgEnd = LightBgEnd,
    cardBg = LightCardBg,
    cardBgTranslucent = LightCardBgTranslucent,
    glassBorder = LightGlassBorder,
    glassBorderGlow = LightGlassBorderGlow,
    textPrimary = Color(0xFF0F172A),   // slate-900
    textSecondary = Color(0xFF475569), // slate-600
    textTertiary = Color(0xFF64748B),  // slate-500
    primaryAccent = Color(0xFF7C3AED), // vibrant violet-600
    secondaryAccent = Color(0xFF0891B2), // vibrant cyan-600
    tertiaryAccent = Color(0xFF059669),  // vibrant emerald-600
    errorColor = Color(0xFFDC2626),    // clean red-600
    isDark = false
)

// Dynamic Composable Theme Shortcuts
val ThemeBgStart @Composable get() = SuperfitTheme.colors.bgStart
val ThemeBgEnd @Composable get() = SuperfitTheme.colors.bgEnd
val ThemeCardBg @Composable get() = SuperfitTheme.colors.cardBg
val ThemeCardBgTranslucent @Composable get() = SuperfitTheme.colors.cardBgTranslucent
val ThemeGlassBorder @Composable get() = SuperfitTheme.colors.glassBorder
val ThemeGlassBorderGlow @Composable get() = SuperfitTheme.colors.glassBorderGlow
val ThemeTextPrimary @Composable get() = SuperfitTheme.colors.textPrimary
val ThemeTextSecondary @Composable get() = SuperfitTheme.colors.textSecondary
val ThemeTextTertiary @Composable get() = SuperfitTheme.colors.textTertiary
val ThemePrimaryAccent @Composable get() = SuperfitTheme.colors.primaryAccent
val ThemeSecondaryAccent @Composable get() = SuperfitTheme.colors.secondaryAccent
val ThemeTertiaryAccent @Composable get() = SuperfitTheme.colors.tertiaryAccent
val ThemeErrorColor @Composable get() = SuperfitTheme.colors.errorColor


