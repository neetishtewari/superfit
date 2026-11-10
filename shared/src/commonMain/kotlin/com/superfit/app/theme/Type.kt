package com.superfit.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.superfit.shared.resources.Res
import com.superfit.shared.resources.manrope_bold
import com.superfit.shared.resources.manrope_extrabold
import com.superfit.shared.resources.manrope_medium
import com.superfit.shared.resources.manrope_regular
import com.superfit.shared.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

// Manrope everywhere: rounded and friendly, with clear numbers. The font files live in shared
// composeResources so Android and iPhone use the same ones.
val Manrope: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
        Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
        Font(Res.font.manrope_extrabold, FontWeight.Black)
    )

// Line height is relative (em), so a Text that only changes fontSize keeps
// comfortable spacing. A fixed 24.sp here made every small wrapped line double spaced.
private fun style(family: FontFamily, size: Int, weight: FontWeight, lineHeight: Double = 1.35, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.em,
    letterSpacing = letterSpacing.sp
)

val Typography: Typography
    @Composable get() {
        val manrope = Manrope
        return remember(manrope) { superfitTypography(manrope) }
    }

private fun superfitTypography(f: FontFamily) = Typography(
    // Big numbers: calories left, goal percent
    displayLarge = style(f, 44, FontWeight.ExtraBold, 1.1, -0.5),
    displayMedium = style(f, 36, FontWeight.ExtraBold, 1.1, -0.5),
    displaySmall = style(f, 30, FontWeight.ExtraBold, 1.15, -0.25),
    // Screen and sheet titles
    headlineLarge = style(f, 28, FontWeight.ExtraBold, 1.2, -0.25),
    headlineMedium = style(f, 24, FontWeight.ExtraBold, 1.2),
    headlineSmall = style(f, 22, FontWeight.ExtraBold, 1.25),
    // Card titles
    titleLarge = style(f, 18, FontWeight.ExtraBold, 1.3),
    titleMedium = style(f, 16, FontWeight.Bold, 1.3),
    titleSmall = style(f, 14, FontWeight.Bold, 1.3),
    // Running text
    bodyLarge = style(f, 16, FontWeight.Normal, 1.45),
    bodyMedium = style(f, 14, FontWeight.Normal, 1.45),
    bodySmall = style(f, 13, FontWeight.Normal, 1.4),
    // Buttons, chips, small labels
    labelLarge = style(f, 14, FontWeight.Bold, 1.3),
    labelMedium = style(f, 12, FontWeight.Bold, 1.3),
    labelSmall = style(f, 12, FontWeight.SemiBold, 1.3)
)
