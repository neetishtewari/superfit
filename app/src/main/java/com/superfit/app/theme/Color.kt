package com.superfit.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Fresh Green": the logo's green is the one brand colour; teal is for activity,
// purple belongs to the AI coach only, amber means "watch out", red is only for delete.
val DarkBgStart = Color(0xFF0E1513)
val DarkBgEnd = Color(0xFF0E1513)
val CardBg = Color(0xFF151E1B)
val CardBgTranslucent = Color(0xFF151E1B)

// Accent Colors (names kept from the old palette so every screen picks up the new values)
val NeonMint = Color(0xFF0E9F75) // brand green: buttons, progress, success
val ElectricCyan = Color(0xFF0A8FB0) // teal: steps and activity
val HyperViolet = Color(0xFF6D4FD8) // AI coach
val SolarAmber = Color(0xFFC77A00) // warnings, over target
val CoralRed = Color(0xFFE5484D)

// Softer fills of the brand colours, for selected chips and tinted cards
val BrandGreenSoft = Color(0xFFDDF4EB)
val BrandGreenDeep = Color(0xFF087A59)
val CoachPurpleSoft = Color(0xFFEEEAFD)
val AmberSoft = Color(0xFFFCEFD9)

// Red is kept for delete and other actions that can't be undone; warnings use SolarAmber.
val DeleteRed = CoralRed

// Card outlines: barely there, so cards read as calm surfaces rather than glass panels
val GlassBorder = Color(0x14FFFFFF)
val GlassBorderGlow = Color(0x14FFFFFF)

// Light Mode Palette: slightly green-tinted whites
val LightBgStart = Color(0xFFF5F8F7)
val LightBgEnd = Color(0xFFF5F8F7)
val LightCardBg = Color(0xFFFFFFFF)
val LightCardBgTranslucent = Color(0xFFFFFFFF)
val LightGlassBorder = Color(0x0F10201A)
val LightGlassBorderGlow = Color(0x0F10201A)

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
    val brandSoft: Color,
    val coachSoft: Color,
    val amberSoft: Color,
    // Fill of text boxes and small tiles inside a card
    val fieldBg: Color,
    val isDark: Boolean
)

val DarkColors = SuperfitColors(
    bgStart = DarkBgStart,
    bgEnd = DarkBgEnd,
    cardBg = CardBg,
    cardBgTranslucent = CardBgTranslucent,
    glassBorder = GlassBorder,
    glassBorderGlow = GlassBorderGlow,
    textPrimary = Color(0xFFE6EEEA),
    textSecondary = Color(0xFFC3D0CA),
    textTertiary = Color(0xFFA2B3AC),
    primaryAccent = Color(0xFF3FD3A2),
    secondaryAccent = Color(0xFF4CC9E8),
    tertiaryAccent = Color(0xFFA993FF),
    errorColor = Color(0xFFF2B04C),
    brandSoft = Color(0xFF143A2E),
    coachSoft = Color(0xFF1C2230),
    amberSoft = Color(0xFF3A2C12),
    fieldBg = Color(0xFF1C2724),
    isDark = true
)

val LightColors = SuperfitColors(
    bgStart = LightBgStart,
    bgEnd = LightBgEnd,
    cardBg = LightCardBg,
    cardBgTranslucent = LightCardBgTranslucent,
    glassBorder = LightGlassBorder,
    glassBorderGlow = LightGlassBorderGlow,
    textPrimary = Color(0xFF10201A),
    textSecondary = Color(0xFF4A5B54),
    textTertiary = Color(0xFF5F6F68),
    primaryAccent = NeonMint,
    secondaryAccent = ElectricCyan,
    tertiaryAccent = HyperViolet,
    errorColor = Color(0xFFB26A00), // amber; red is kept for delete
    brandSoft = BrandGreenSoft,
    coachSoft = CoachPurpleSoft,
    amberSoft = AmberSoft,
    fieldBg = Color(0xFFEDF2F0),
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
val ThemeBrandSoft @Composable get() = SuperfitTheme.colors.brandSoft
val ThemeCoachSoft @Composable get() = SuperfitTheme.colors.coachSoft
val ThemeAmberSoft @Composable get() = SuperfitTheme.colors.amberSoft
val ThemeFieldBg @Composable get() = SuperfitTheme.colors.fieldBg
