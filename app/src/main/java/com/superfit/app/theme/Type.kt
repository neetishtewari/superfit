package com.superfit.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.superfit.app.R

// Manrope everywhere: rounded and friendly, with clear numbers.
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black)
)

// Line height is relative (em), so a Text that only changes fontSize keeps
// comfortable spacing. A fixed 24.sp here made every small wrapped line double spaced.
private fun style(size: Int, weight: FontWeight, lineHeight: Double = 1.35, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.em,
    letterSpacing = letterSpacing.sp
)

val Typography = Typography(
    // Big numbers: calories left, goal percent
    displayLarge = style(44, FontWeight.ExtraBold, 1.1, -0.5),
    displayMedium = style(36, FontWeight.ExtraBold, 1.1, -0.5),
    displaySmall = style(30, FontWeight.ExtraBold, 1.15, -0.25),
    // Screen and sheet titles
    headlineLarge = style(28, FontWeight.ExtraBold, 1.2, -0.25),
    headlineMedium = style(24, FontWeight.ExtraBold, 1.2),
    headlineSmall = style(22, FontWeight.ExtraBold, 1.25),
    // Card titles
    titleLarge = style(18, FontWeight.ExtraBold, 1.3),
    titleMedium = style(16, FontWeight.Bold, 1.3),
    titleSmall = style(14, FontWeight.Bold, 1.3),
    // Running text
    bodyLarge = style(16, FontWeight.Normal, 1.45),
    bodyMedium = style(14, FontWeight.Normal, 1.45),
    bodySmall = style(13, FontWeight.Normal, 1.4),
    // Buttons, chips, small labels
    labelLarge = style(14, FontWeight.Bold, 1.3),
    labelMedium = style(12, FontWeight.Bold, 1.3),
    labelSmall = style(12, FontWeight.SemiBold, 1.3)
)
