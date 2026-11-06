package com.superfit.app.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.superfit.app.R
import com.superfit.app.domain.CardStat
import com.superfit.app.domain.Effort
import com.superfit.app.domain.HeartLine
import com.superfit.app.domain.PhotoFrame
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.domain.StrengthCardDetail
import com.superfit.app.domain.WeekMark
import com.superfit.app.theme.ElectricCyan
import com.superfit.app.theme.HyperViolet
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SolarAmber
import kotlin.math.roundToInt

/** A share card's own colours, whatever theme the app is in: dark by default, or light if the user picks it. */
private data class CardPalette(
    val background: Color,
    val tile: Color,
    val divider: Color,
    val textMain: Color,
    val textSoft: Color,
    val textDate: Color,
    val badgeBackground: Color,
    val badgeBorder: Color,
    /** "SUPERFIT" on the badge; the main text colour unless the badge is inverted. */
    val badgeText: Color = textMain,
    /** The face for the big number; null keeps the app's own (Classic). */
    val display: FontFamily? = null
)

private val DarkCard = CardPalette(
    background = Color(0xFF0D0D14),
    tile = Color(0xFF181824),
    divider = Color(0xFF2A2A3A),
    textMain = Color(0xFFF4F4F8),
    textSoft = Color(0xFFA8A8BC),
    textDate = Color(0xFFC8C8D8),
    badgeBackground = Color(0xFF1E1E2C),
    badgeBorder = Color(0xFF34344A)
)

private val LightCard = CardPalette(
    background = Color(0xFFF6F6F9),
    tile = Color(0xFFFFFFFF),
    divider = Color(0xFFDCDCE5),
    textMain = Color(0xFF15151C),
    textSoft = Color(0xFF5E5E70),
    textDate = Color(0xFF45455A),
    badgeBackground = Color(0xFFFFFFFF),
    badgeBorder = Color(0xFFD5D5DF)
)

private val LocalCardPalette = staticCompositionLocalOf { DarkCard }

private val CardBackground: Color @Composable get() = LocalCardPalette.current.background
private val TileBackground: Color @Composable get() = LocalCardPalette.current.tile
private val Divider: Color @Composable get() = LocalCardPalette.current.divider
private val TextMain: Color @Composable get() = LocalCardPalette.current.textMain
private val TextSoft: Color @Composable get() = LocalCardPalette.current.textSoft
private val TextDate: Color @Composable get() = LocalCardPalette.current.textDate
private val BadgeBackground: Color @Composable get() = LocalCardPalette.current.badgeBackground
private val BadgeBorder: Color @Composable get() = LocalCardPalette.current.badgeBorder
private val BigFont: FontFamily? @Composable get() = LocalCardPalette.current.display

/** A condensed face is narrower, so the big number can be this much taller in the same width. */
private val BigScale: Float @Composable get() = if (LocalCardPalette.current.display != null) 1.25f else 1f

private val ShouldersBlack = FontFamily(Font(R.font.big_shoulders_black, FontWeight.Black))

/**
 * The look of a share card. The colour the user picks drives each one: Poster is filled with it,
 * Glow is a dark card lit by it, Race bib prints the number on a bib with a band in it, and Classic
 * is the plain dark or light card. Every style shows the same numbers and the same add-ons.
 */
enum class CardStyle(val key: String, val label: String) {
    POSTER("poster", "Poster"),
    GLOW("glow", "Glow"),
    BIB("bib", "Race bib"),
    CLASSIC("classic", "Classic");

    companion object {
        val DEFAULT = POSTER
        fun of(key: String?): CardStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Black on a bright colour, white on a deep one (violet, pink). */
fun inkOn(color: Color): Color = if (color.luminance() > 0.33f) Color(0xFF111111) else Color.White

private fun posterPalette(accent: Color): CardPalette {
    val ink = inkOn(accent)
    return CardPalette(
        background = accent,
        tile = ink.copy(alpha = 0.1f),
        divider = ink.copy(alpha = 0.22f),
        textMain = ink,
        textSoft = ink.copy(alpha = 0.72f),
        textDate = ink.copy(alpha = 0.85f),
        badgeBackground = ink,
        badgeBorder = ink,
        badgeText = accent,
        display = ShouldersBlack
    )
}

private val GlowCard = CardPalette(
    background = Color(0xFF0A0B10),
    tile = Color.White.copy(alpha = 0.07f),
    divider = Color.White.copy(alpha = 0.14f),
    textMain = Color(0xFFF7F7FA),
    textSoft = Color(0xFFB4B4C4),
    textDate = Color(0xFFD0D0DC),
    badgeBackground = Color.White.copy(alpha = 0.1f),
    badgeBorder = Color.White.copy(alpha = 0.18f),
    display = ShouldersBlack
)

private val BibCard = CardPalette(
    background = Color(0xFFFFFFFF),
    tile = Color(0xFFF3F2EE),
    divider = Color(0xFFE2E1DB),
    textMain = Color(0xFF121212),
    textSoft = Color(0xFF5F5F66),
    textDate = Color(0xFF3F3F46),
    badgeBackground = Color(0xFF121212),
    badgeBorder = Color(0xFF121212),
    badgeText = Color.White,
    display = ShouldersBlack
)

/** The beige the race bib sits on. */
private val BibGround = Color(0xFFECEBE6)

/** The colours the user can pick in the preview. */
val CardOrange = Color(0xFFFF7A1A)
val CardPink = Color(0xFFFF4F8B)
val CardAccents = listOf(CardOrange, NeonMint, ElectricCyan, HyperViolet, CardPink)

/** Orange by default (Neetish, 2026-11-03); the user can pick another. */
fun defaultAccent(card: ShareCard): Color = CardOrange

/** A share card, laid out at 360 x 640 dp (Story size, 9:16). */
@Composable
fun ShareCardView(
    card: ShareCard,
    accent: Color,
    modifier: Modifier = Modifier,
    photo: ImageBitmap? = null,
    light: Boolean = false,
    frame: PhotoFrame = PhotoFrame(),
    style: CardStyle = CardStyle.CLASSIC
) {
    // Every card can sit on the user's photo.
    if (photo != null) {
        CompositionLocalProvider(LocalCardPalette provides DarkCard) {
            PhotoCardView(card, accent, photo, frame, modifier)
        }
        return
    }
    when (style) {
        CardStyle.CLASSIC -> CompositionLocalProvider(LocalCardPalette provides if (light) LightCard else DarkCard) {
            // The bright accents are hard to read on white, so the light card uses a deeper shade.
            CardOnBackground(card, if (light) lerp(accent, Color.Black, 0.3f) else accent, modifier)
        }
        // On a Poster the colour is the background, so everything drawn "in the colour" is drawn in ink.
        CardStyle.POSTER -> CompositionLocalProvider(LocalCardPalette provides posterPalette(accent)) {
            CardOnBackground(card, inkOn(accent), modifier, style)
        }
        CardStyle.GLOW -> CompositionLocalProvider(LocalCardPalette provides GlowCard) {
            CardOnBackground(card, accent, modifier, style, glow = accent)
        }
        CardStyle.BIB -> CompositionLocalProvider(LocalCardPalette provides BibCard) {
            BibCardView(card, accent, modifier)
        }
    }
}

/** The day, workout or best's own words for the bib's band. */
private fun bandLabel(card: ShareCard): String = when (card) {
    is ShareCard.Daily -> if (card.shownRoast != null) "ROAST MY DAY" else "MY DAY"
    is ShareCard.Activity -> card.heading
    is ShareCard.Best -> if (card.podium) "TOP 3" else "PERSONAL BEST"
}

/** The card's body: the same for every style, so every style carries the same numbers and add-ons. */
@Composable
private fun ColumnScope.CardBody(card: ShareCard, accent: Color) {
    when (card) {
        is ShareCard.Daily -> card.asDay.let { day ->
            // The roast shrinks its own text to fit; the day's numbers scale down like the others.
            if (day.roast) RoastBody(day, accent) else FitBody { DailyBody(day, accent) }
        }
        is ShareCard.Activity -> FitBody { ActivityBody(card, accent) }
        is ShareCard.Best -> FitBody { BestBody(card, accent) }
    }
}

/**
 * Race bib: the header on a beige ground, then a white bib with a band in the user's colour, two
 * pin holes and the card's usual body, then the Superfit footer under the bib.
 */
@Composable
private fun BibCardView(card: ShareCard, accent: Color, modifier: Modifier) {
    val deep = lerp(accent, Color.Black, 0.25f)
    val ink = inkOn(accent)
    Column(
        modifier = modifier
            .size(360.dp, 640.dp)
            .background(BibGround)
            .padding(start = 18.dp, end = 18.dp, top = SAFE_TOP, bottom = 96.dp)
    ) {
        CardHeader(card.dateLine)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SUPERFIT", color = ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(bandLabel(card), color = ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Two pin holes, like a real bib.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BibGround)
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            ) {
                CardBody(card, deep)
            }
        }
        CardFooter(showDivider = false, roasted = roasted(card))
    }
}

@Composable
private fun CardOnBackground(card: ShareCard, accent: Color, modifier: Modifier, style: CardStyle = CardStyle.CLASSIC, glow: Color? = null) {
    Box(
        modifier = modifier
            .size(360.dp, 640.dp)
            .background(CardBackground)
    ) {
        when {
            glow != null -> GlowLights(glow)
            style == CardStyle.POSTER -> PosterGhost(card)
            card is ShareCard.Daily -> DailyGlow(accent)
            card is ShareCard.Activity -> ActivityWaves(accent)
            card is ShareCard.Best -> BestGlow(accent)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top and bottom strips stay clear: Instagram and WhatsApp draw their own bars there.
                .padding(start = 24.dp, end = 24.dp, top = SAFE_TOP, bottom = 96.dp)
        ) {
            CardHeader(card.dateLine)
            CardBody(card, accent)
            CardFooter(showDivider = card !is ShareCard.Daily, roasted = roasted(card))
        }
    }
}

/**
 * Clear of Instagram's top bar (progress lines, profile picture, name), which covers about the top
 * 13% of a story, with room to spare so the logo is never hidden behind the profile picture.
 */
private val SAFE_TOP = 100.dp

/** The Superfit "S" and the date at the top; the full Superfit badge lives at the bottom. */
@Composable
private fun CardHeader(dateLine: String, dateColor: Color = TextDate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_superfit_launcher),
            contentDescription = "Superfit logo",
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(dateLine, color = dateColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

/**
 * The card's middle, between the header and the footer. The footer is laid out first, so the
 * Superfit badge and "Get it on Google Play" always stay on the picture; a body with more on it than
 * fits is scaled down a little (smaller text and spacing) instead of pushing them off.
 */
@Composable
private fun ColumnScope.FitBody(content: @Composable ColumnScope.() -> Unit) {
    FitToSpace(
        Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        Column(Modifier.fillMaxSize(), content = content)
    }
}

/**
 * Shows [content] at full size when it fits the height it's given, else laid out wider and scaled
 * down to fit, so it still fills the width. Scales to at most [MIN_FIT_SCALE] and clips past that.
 */
@Composable
private fun FitToSpace(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val child = measurables.first()
        val width = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val natural = child.maxIntrinsicHeight(width)
        if (natural <= maxHeight) {
            val placeable = child.measure(constraints.copy(minWidth = width))
            layout(width, placeable.height.coerceIn(constraints.minHeight, maxHeight)) { placeable.place(0, 0) }
        } else {
            val scale = (maxHeight.toFloat() / natural).coerceAtLeast(MIN_FIT_SCALE)
            val placeable = child.measure(Constraints.fixed((width / scale).roundToInt(), (maxHeight / scale).roundToInt()))
            layout(width, maxHeight) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

private const val MIN_FIT_SCALE = 0.7f

/** Room for the numbers on a photo card, between the header and the Superfit row. */
private val PHOTO_STATS_MAX = 310.dp

/** A card with a roast on it says who wrote it. */
private fun roasted(card: ShareCard): Boolean = (card as? ShareCard.Daily)?.shownRoast != null

@Composable
private fun CardFooter(showDivider: Boolean, roasted: Boolean = false) {
    if (showDivider) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BrandBadge(background = BadgeBackground, border = BadgeBorder, textColor = LocalCardPalette.current.badgeText)
        FooterNote(roasted, TextMain, TextDate)
    }
}

/** "Get it on Google Play", with "Roasted by Superfit" above it on a roast. */
@Composable
private fun FooterNote(roasted: Boolean, main: Color, soft: Color) {
    Column(horizontalAlignment = Alignment.End) {
        if (roasted) RoastLabelRow("Roasted by Superfit", main, 12.sp, spacing = 0.sp)
        Text("Get it on Google Play", color = soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The logo and name in a pill, big enough to read in a story at a glance. */
@Composable
private fun BrandBadge(background: Color, border: Color, textColor: Color = TextMain) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(start = 7.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_superfit_launcher),
            contentDescription = "Superfit logo",
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text("SUPERFIT", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
    }
}

@Composable
private fun ColumnScope.DailyBody(card: ShareCard.Daily, accent: Color) {
    Spacer(Modifier.height(10.dp))
    Text("MY DAY", color = TextSoft, fontSize = 12.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
    if (card.streakDays > 0) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${card.streakDays}", color = accent, fontSize = 58.sp * BigScale, fontWeight = FontWeight.ExtraBold, fontFamily = BigFont, lineHeight = 58.sp * BigScale)
            Spacer(Modifier.width(10.dp))
            Text(
                "day streak",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    } else {
        Text("Day 1", color = accent, fontSize = 52.sp * BigScale, fontWeight = FontWeight.ExtraBold, fontFamily = BigFont, lineHeight = 58.sp * BigScale)
    }
    Spacer(Modifier.height(16.dp))
    // Tiles left out (no meals, rest day) close up, so there are never empty boxes.
    card.tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { stat ->
                DailyTile(stat, barColor = if (stat.label == "PROTEIN") accent else secondBar(accent), modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
    card.coachLine?.let { line ->
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )
        Spacer(Modifier.height(12.dp))
        if (card.roast) {
            RoastLabelRow("ROAST MY DAY", SolarAmber, 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(line, color = TextMain, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 6, overflow = TextOverflow.Ellipsis)
        } else {
            Text("COACH", color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(line, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.weight(1f))
}

/**
 * "Roast my day": the whole roast, never cut to one line. A short opening line is the headline and the
 * rest sits under it, shrinking to fit; the day's numbers show only when the roast leaves room for them.
 */
@Composable
private fun ColumnScope.RoastBody(card: ShareCard.Daily, accent: Color) {
    val roast = card.shownRoast.orEmpty()
    val (headline, rest) = ShareCards.splitRoast(roast)
    // Only the numbers the roast talks about, and only when Numbers is on.
    val tiles = card.roastTiles
    val bodySize = if (rest != null) 17.sp else 22.sp
    Spacer(Modifier.height(20.dp))
    RoastLabelRow("ROAST MY DAY", accent, 12.sp)
    Spacer(Modifier.height(12.dp))
    if (rest != null) {
        Text(headline, color = TextMain, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.height(12.dp))
    }
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        BasicText(
            text = rest ?: headline,
            style = TextStyle(
                color = TextMain,
                fontSize = bodySize,
                lineHeight = 1.4.em,
                fontWeight = if (rest != null) FontWeight.Medium else FontWeight.Bold
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = bodySize, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxSize()
        )
    }
    if (tiles.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            tiles.forEach { stat ->
                DailyTile(stat, barColor = if (stat.label == "PROTEIN") accent else secondBar(accent), modifier = Modifier.weight(1f))
            }
            if (tiles.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    card.shownJourney?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = TextSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(16.dp))
}

private fun secondBar(accent: Color): Color = if (accent == ElectricCyan) NeonMint else ElectricCyan

@Composable
private fun DailyTile(stat: CardStat, barColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TileBackground)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(stat.label, color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
        ValueWithUnit(stat.value, stat.unit, valueSize = 24.sp, unitSize = 14.sp)
        stat.progress?.let { p ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Divider)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
        stat.note?.let { Text(it, color = TextSoft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ValueWithUnit(value: String, unit: String?, valueSize: TextUnit, unitSize: TextUnit) {
    Text(
        text = buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(SpanStyle(color = TextSoft, fontSize = unitSize, fontWeight = FontWeight.Normal)) {
                    append(if (unit.startsWith("/")) unit else " $unit")
                }
            }
        },
        color = TextMain,
        fontSize = valueSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
private fun ColumnScope.ActivityBody(card: ShareCard.Activity, accent: Color) {
    // With a coach line, roast or strength detail the card tightens up so everything fits the picture.
    val busy = card.shownCoach != null || card.shownRoast != null || card.strength != null ||
        (momentOf(card) != null && card.shownJourney != null)
    val big = if (busy) 72.sp else 92.sp
    Spacer(Modifier.height(if (busy) 16.dp else 36.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(card.heading, color = TextSoft, fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        val shown = big * BigScale
        Text(card.bigValue, color = TextMain, fontSize = shown, fontWeight = if (BigFont != null) FontWeight.Black else FontWeight.ExtraBold, fontFamily = BigFont, lineHeight = shown, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(card.bigUnit, color = accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = if (busy) 10.dp else 14.dp))
    }
    card.subtitle?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = TextMain, fontSize = 17.sp, lineHeight = 24.sp, maxLines = if (busy) 2 else 3, overflow = TextOverflow.Ellipsis)
    }
    card.shownHeart?.let { heart ->
        Spacer(Modifier.height(if (busy) 14.dp else 22.dp))
        HeartLineGraph(heart, accent, TextMain, Modifier.fillMaxWidth().height(if (busy) 52.dp else 70.dp))
        if (heart.effortMinutes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            EffortLegend(heart, TextSoft)
        }
    }
    card.strength?.let {
        Spacer(Modifier.height(18.dp))
        StrengthBlock(it, accent, TextMain, TextSoft, TileBackground, compact = false)
    }
    CardExtras(card, TextMain, TextSoft, TileBackground, compact = false)
    Spacer(Modifier.weight(1f))
    val perRow = if (busy) 3 else 2
    card.stats.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { stat ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (busy) shortLabel(stat.label) else stat.label, color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
                    ValueWithUnit(stat.value, stat.unit, valueSize = if (busy) 22.sp else 26.sp, unitSize = if (busy) 12.sp else 13.sp)
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(if (busy) 14.dp else 20.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ColumnScope.BestBody(card: ShareCard.Best, accent: Color) {
    Spacer(Modifier.height(34.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                card.podium -> "TOP 3"
                card.isNew -> "NEW PERSONAL BEST"
                else -> "PERSONAL BEST"
            },
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(card.label, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val size = bigValueSize(card.bigValue, 88) * BigScale
            Text(card.bigValue, color = TextMain, fontSize = size.sp, fontWeight = if (BigFont != null) FontWeight.Black else FontWeight.ExtraBold, fontFamily = BigFont, lineHeight = size.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                card.bigUnit,
                color = accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = (size / 6).dp)
            )
        }
        card.detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.weight(1f))
    val previous = card.previous
    if (previous != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TileBackground)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PREVIOUS BEST", color = TextSoft, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(previous, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            card.gain?.let { Text(it, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The flame icon and a label, for roasts (an icon, never an emoji). */
@Composable
private fun RoastLabelRow(text: String, color: Color, size: TextUnit, spacing: TextUnit = 2.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = color, modifier = Modifier.size(with(LocalDensity.current) { (size * 1.3f).toDp() }))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = spacing)
    }
}

/** Glow: two soft lights in the user's colour on a near-black card. */
@Composable
private fun GlowLights(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.16f),
                radius = size.width * 1.0f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 0.95f
            )
        )
    }
}

/** Poster: the first figure of the big number, huge and faint behind everything, for texture. */
@Composable
private fun PosterGhost(card: ShareCard) {
    val figure = when (card) {
        is ShareCard.Activity -> card.bigValue
        is ShareCard.Best -> card.bigValue
        is ShareCard.Daily -> card.streakDays.takeIf { it > 0 }?.toString()
    }?.firstOrNull { it.isDigit() } ?: return
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Text(
            figure.toString(),
            color = TextMain.copy(alpha = 0.08f),
            fontSize = 340.sp,
            lineHeight = 340.sp,
            fontFamily = ShouldersBlack,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 24.dp, y = (-40).dp)
        )
    }
}

@Composable
private fun DailyGlow(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.08f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.95f, size.height * 0.08f)
        )
    }
}

@Composable
private fun ActivityWaves(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val top = size.height * 0.34f
        fun wave(lift: Float) = Path().apply {
            moveTo(-0.08f * w, top + lift * 0.77f)
            cubicTo(0.1f * w, top + lift * 0.77f, 0.16f * w, top + lift * 0.46f, 0.31f * w, top + lift * 0.5f)
            cubicTo(0.46f * w, top + lift * 0.54f, 0.55f * w, top + lift * 0.85f, 0.7f * w, top + lift * 0.58f)
            cubicTo(0.85f * w, top + lift * 0.3f, 0.95f * w, top + lift * 0.15f, 1.08f * w, top + lift * 0.23f)
        }
        drawPath(wave(260.dp.toPx()), accent.copy(alpha = 0.22f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(wave(300.dp.toPx()), ElectricCyan.copy(alpha = 0.18f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BestGlow(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * 0.39f)
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.33f), Color.Transparent), center = center, radius = size.width * 0.6f),
            radius = size.width * 0.6f,
            center = center
        )
    }
}

/** A workout or personal-best card on the user's own photo, with the numbers on a dark fade. */
@Composable
private fun PhotoCardView(card: ShareCard, accent: Color, photo: ImageBitmap, frame: PhotoFrame, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(360.dp, 640.dp)
            .clipToBounds()
            .background(Color.Black)
    ) {
        FramedPhoto(photo, frame)
        // A soft fade behind the header, and a deep one behind the stats wherever they sit.
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
        )
        if (frame.statsTop) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(440.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xEB08080C),
                            0.6f to Color(0xBF08080C),
                            1f to Color(0x0008080C)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(440.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x0008080C),
                            0.4f to Color(0xBF08080C),
                            1f to Color(0xEB08080C)
                        )
                    )
            )
        }
        Box(Modifier.padding(start = 22.dp, top = SAFE_TOP)) {
            CardHeader(card.dateLine, dateColor = Color.White)
        }
        if (frame.statsTop) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = SAFE_TOP + 56.dp)
            ) {
                FitToSpace(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = PHOTO_STATS_MAX)
                ) {
                    Column { PhotoStats(card, accent) }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 96.dp)
            ) {
                PhotoBrandRow(roasted(card))
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 96.dp)
            ) {
                FitToSpace(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = PHOTO_STATS_MAX)
                ) {
                    Column { PhotoStats(card, accent) }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Spacer(Modifier.height(14.dp))
                PhotoBrandRow(roasted(card))
            }
        }
    }
}

/** The user's photo, zoomed and moved as they set it; at scale 1 it just covers the card. */
@Composable
private fun FramedPhoto(photo: ImageBitmap, frame: PhotoFrame) {
    Canvas(Modifier.fillMaxSize()) {
        val cardW = size.width
        val cardH = size.height
        val f = frame.clamped(photo.width, photo.height, cardW.roundToInt(), cardH.roundToInt())
        val (w, h) = PhotoFrame.drawnSize(photo.width, photo.height, cardW.roundToInt(), cardH.roundToInt(), f.scale)
        val left = (cardW - w) / 2f + f.offsetX * cardW
        val top = (cardH - h) / 2f + f.offsetY * cardH
        drawImage(
            image = photo,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(photo.width, photo.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High
        )
    }
}

@Composable
private fun PhotoBrandRow(roasted: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BrandBadge(background = Color(0x9908080C), border = Color.White.copy(alpha = 0.22f))
        FooterNote(roasted, Color.White, Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun PhotoStats(card: ShareCard, accent: Color) {
    when (card) {
        is ShareCard.Activity -> {
            Text(card.heading, color = PhotoSoft, fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BigNumber(card.bigValue, card.bigUnit, accent)
            card.subtitle?.let {
                Text(it, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            card.shownHeart?.let { heart ->
                Spacer(Modifier.height(10.dp))
                // Distance cards have no room for PEAK among three stats, so the dot carries it.
                val labelPeak = card.stats.take(3).none { it.label == "PEAK" }
                HeartLineGraph(heart, accent, Color.White, Modifier.fillMaxWidth().height(48.dp), labelPeak = labelPeak)
                if (heart.effortMinutes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    EffortLegend(heart, PhotoSoft)
                }
            }
            if (card.stats.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.stats.take(3).forEach { stat ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(shortLabel(stat.label), color = PhotoSoft, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                            ValueWithUnit(stat.value, stat.unit, valueSize = 20.sp, unitSize = 11.sp)
                        }
                    }
                }
            }
            card.strength?.let {
                Spacer(Modifier.height(10.dp))
                StrengthBlock(it, accent, Color.White, PhotoSoft, Color.White.copy(alpha = 0.14f), compact = true)
            }
            CardExtras(card, Color.White, PhotoSoft, Color.White.copy(alpha = 0.14f), compact = true)
        }
        is ShareCard.Best -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        card.podium -> "TOP 3"
                        card.isNew -> "NEW PERSONAL BEST"
                        else -> "PERSONAL BEST"
                    },
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                card.label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            BigNumber(card.bigValue, card.bigUnit, accent)
            card.detail?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White, fontSize = 14.sp) }
            card.previous?.let { prev ->
                Spacer(Modifier.height(6.dp))
                Text("Previous best $prev" + (card.gain?.let { "  ($it)" } ?: ""), color = PhotoSoft, fontSize = 13.sp)
            }
        }
        is ShareCard.Daily -> PhotoDay(card.asDay, accent)
    }
}

/**
 * A day or roast card on the user's photo. A roast shows its short version (written when the photo
 * goes on) so it reads over the picture, with the numbers it talks about and the journey line.
 */
@Composable
private fun PhotoDay(card: ShareCard.Daily, accent: Color) {
    val tileBg = Color.White.copy(alpha = 0.14f)
    val roast = card.shownRoast
    val tiles = if (roast != null) card.roastTiles else card.tiles.take(4)
    if (roast != null) {
        RoastLabelRow("ROAST MY DAY", accent, 12.sp)
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = card.shortRoast ?: ShareCards.photoRoast(roast),
            style = TextStyle(color = Color.White, fontSize = 24.sp, lineHeight = 1.3.em, fontWeight = FontWeight.ExtraBold),
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 0.5.sp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
        )
    } else {
        Text("MY DAY", color = PhotoSoft, fontSize = 12.sp, letterSpacing = 2.sp)
        if (card.streakDays > 0) BigNumber("${card.streakDays}", "day streak", accent) else BigNumber("Day 1", "", accent)
        card.coachLine?.let {
            Text(it, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (tiles.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { stat ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tileBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(shortLabel(stat.label), color = PhotoSoft, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                    ValueWithUnit(stat.value, stat.unit, valueSize = 18.sp, unitSize = 11.sp)
                }
            }
        }
    }
    card.shownJourney?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = PhotoSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The stand-out line, only when there is one and the user added it. */
private fun momentOf(card: ShareCard.Activity): String? = card.moment?.takeIf { card.showMoment && it.isNotBlank() }

private val CoachLabel = Color(0xFF9D86FF)
private val RoastLabel = Color(0xFFF2A33A)

/** What the user added to a workout card: the stand-out line, the coach line or roast, the journey line. */
@Composable
private fun CardExtras(card: ShareCard.Activity, main: Color, soft: Color, tile: Color, compact: Boolean) {
    val gap = if (compact) 10.dp else 12.dp
    val size = if (compact) 14.sp else 15.sp
    val tileShape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    val tilePad = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 8.dp else 10.dp)
    momentOf(card)?.let {
        Spacer(Modifier.height(gap))
        Text(
            it,
            color = main,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(tileShape).background(tile).then(tilePad)
        )
    }
    val said = card.shownCoach?.let { "AI COACH" to it } ?: card.shownRoast?.let { "ROAST" to it }
    said?.let { (label, line) ->
        Spacer(Modifier.height(gap))
        Column(
            Modifier.fillMaxWidth().clip(tileShape).background(tile).then(tilePad),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = if (label == "ROAST") RoastLabel else CoachLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(line, color = main, fontSize = size, lineHeight = size * 1.35f, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (label == "ROAST") {
                card.fairLine?.let { Text(it, color = soft, fontSize = if (compact) 12.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    card.shownJourney?.let {
        Spacer(Modifier.height(gap))
        Text(it, color = soft, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A voice-logged strength workout: exercises, body areas this week, and the week's training days. */
@Composable
private fun StrengthBlock(detail: StrengthCardDetail, accent: Color, main: Color, soft: Color, tile: Color, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        if (detail.exercises.isNotEmpty()) {
            val list = detail.exercises.joinToString(" · ") { (name, reps) -> "$reps $name" } +
                if (detail.moreExercises > 0) " · +${detail.moreExercises} more" else ""
            Text(list, color = if (compact) main else soft, fontSize = if (compact) 13.sp else 15.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StrengthCardDetail.SHOWN.forEach { area ->
                val today = area in detail.today
                val week = area in detail.thisWeek
                val name = area.label.replaceFirstChar { it.uppercase() }
                Text(
                    if (today || week) name else "$name · not this week",
                    color = if (today) {
                        Color.Black
                    } else if (week) {
                        main
                    } else {
                        soft.copy(alpha = 0.7f)
                    },
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (today) accent else tile)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        WeekStrip(detail, accent, main, soft, compact)
    }
}

/** Monday to Sunday: green dot for cardio days, violet for strength, the workout's day ringed. */
@Composable
private fun WeekStrip(detail: StrengthCardDetail, accent: Color, main: Color, soft: Color, compact: Boolean) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val dot = if (compact) 18.dp else 22.dp
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        detail.week.take(7).forEachIndexed { i, mark ->
            val fill = when (mark) {
                WeekMark.STRENGTH -> CoachLabel
                WeekMark.CARDIO -> accent
                WeekMark.NONE -> Color.Transparent
            }
            Box(
                Modifier
                    .size(dot)
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .border(if (i == detail.dayIndex) 2.dp else 1.dp, if (i == detail.dayIndex) main else soft.copy(alpha = 0.5f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(letters[i], color = if (mark == WeekMark.NONE) soft else Color.Black, fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Effort colours. Max is a hot orange: red is kept for delete buttons only. */
fun effortColor(effort: Effort): Color = when (effort) {
    Effort.EASY -> ElectricCyan
    Effort.STEADY -> NeonMint
    Effort.HARD -> Color(0xFFF2A33A)
    Effort.MAX -> Color(0xFFFF6A3D)
}

/** Heart rate through the workout, coloured by effort, with a dot on the peak. */
@Composable
private fun HeartLineGraph(heart: HeartLine, accent: Color, dotColor: Color, modifier: Modifier, labelPeak: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = dotColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier) {
        if (heart.bpm.isEmpty()) return@Canvas
        val top = if (labelPeak) 16.dp.toPx() else 6.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val low = heart.bpm.min()
        val high = heart.bpm.max()
        val range = (high - low).coerceAtLeast(10)
        fun point(i: Int) = Offset(
            heart.x[i] * size.width,
            bottom - (heart.bpm[i] - low).toFloat() / range * (bottom - top)
        )
        val stroke = 2.5.dp.toPx()
        if (heart.bpm.size == 1) {
            drawCircle(accent, radius = stroke, center = point(0))
        }
        for (i in 0 until heart.bpm.size - 1) {
            val color = if (heart.efforts.isEmpty()) {
                accent
            } else {
                effortColor(maxOf(heart.efforts[i], heart.efforts[i + 1]))
            }
            drawLine(color, point(i), point(i + 1), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        val peak = point(heart.peakIndex.coerceIn(0, heart.bpm.size - 1))
        drawCircle(dotColor, radius = 4.dp.toPx(), center = peak)
        if (labelPeak) {
            val text = measurer.measure("${heart.peakBpm}", labelStyle)
            val x = (peak.x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, (peak.y - 6.dp.toPx() - text.size.height).coerceAtLeast(0f)))
        }
    }
}

/** "EASY 5 · STEADY 9 · HARD 10 · MAX 4 MIN", each with its colour. */
@Composable
private fun EffortLegend(heart: HeartLine, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val bands = heart.effortMinutes.entries.toList()
        bands.forEachIndexed { i, (effort, minutes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(effortColor(effort))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${effort.label.uppercase()} $minutes" + if (i == bands.lastIndex) " MIN" else "",
                    color = textColor,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private val PhotoSoft = Color.White.copy(alpha = 0.8f)

@Composable
private fun BigNumber(value: String, unit: String, accent: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        val size = bigValueSize(value, 76)
        Text(value, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, lineHeight = size.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = (size / 6).dp)
        )
    }
}

/** Long numbers like "80,733" get a smaller size so the number and its unit fit on one line. */
private fun bigValueSize(value: String, full: Int): Int = when {
    value.length <= 4 -> full
    value.length == 5 -> full * 7 / 8
    value.length == 6 -> full * 3 / 4
    else -> full * 5 / 8
}

private fun shortLabel(label: String): String = if (label == "AVG HEART RATE") "AVG HR" else label
