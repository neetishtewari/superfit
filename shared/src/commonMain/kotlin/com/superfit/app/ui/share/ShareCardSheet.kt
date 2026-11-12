package com.superfit.app.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.RoastCardData
import com.superfit.app.domain.CardChip
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.DayChip
import com.superfit.app.domain.PhotoFrame
import com.superfit.app.domain.RoastCard
import com.superfit.app.domain.ShareCard
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.report.ThisAppStore
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Share pictures are 1080 x 1920 px: the 360 x 640 dp card drawn at 3x. */
const val CARD_WIDTH_PX = 1080
const val CARD_HEIGHT_PX = 1920
private val CardDensity = Density(density = 3f, fontScale = 1f)

/** The words sent with a shared picture: this phone's store link. */
val SHARE_TEXT: String get() = ThisAppStore.shareText
private val accentNames = listOf("Orange", "Mint", "Cyan", "Violet")

/** Longest side of a loaded photo: room to zoom in on a 1080 x 1920 card, about 20 MB at most. */
const val MAX_PHOTO_EDGE = 2560

/**
 * Preview of a share card with colour choices, the same on Android and iPhone. Share draws the
 * picture on the phone and opens the phone's share sheet ([LocalShareCardHost]); nothing is uploaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(card: ShareCard, onDismiss: () -> Unit, onShared: () -> Unit = {}) {
    val host = LocalShareCardHost.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val cardPrefs = host.prefs
    var accent by remember(card) { mutableStateOf(defaultAccent(card)) }
    var busy by remember { mutableStateOf(false) }
    // Light background by default (Neetish, 2026-11-03).
    var light by remember { mutableStateOf(true) }
    // The user's photo lives only in memory while the sheet is open; it is never saved or uploaded.
    var photo by remember(card) { mutableStateOf<ImageBitmap?>(null) }
    var frame by remember(card) { mutableStateOf(PhotoFrame()) }
    var touching by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    // A workout card gets its heart-rate line and stand-out line once they've been read.
    var detailed by remember(card) { mutableStateOf<ShareCard.Activity?>(null) }
    LaunchedEffect(card) {
        if (card is ShareCard.Activity && card.workout != null) {
            detailed = host.workoutCards?.enrich(card) ?: card
        }
    }
    // What's added to a workout card, kept apart for photo and plain cards, each starting from its default.
    var savedPlain by remember { mutableStateOf(cardPrefs.defaults(photo = false)) }
    var savedPhoto by remember { mutableStateOf(cardPrefs.defaults(photo = true)) }
    var chipsPlain by remember(card) { mutableStateOf(savedPlain) }
    var chipsPhoto by remember(card) { mutableStateOf(savedPhoto) }
    val onPhoto = photo != null
    val chips = if (onPhoto) chipsPhoto else chipsPlain
    val style = remember { CoachStyle.current }
    // The AI lines are written only when their chip is picked, then kept for this card.
    var coachLine by remember(card) { mutableStateOf<String?>(null) }
    var roastLine by remember(card) { mutableStateOf<String?>(null) }
    var writing by remember(card) { mutableStateOf<CardChip?>(null) }
    var failed by remember(card) { mutableStateOf<CardChip?>(null) }
    var retries by remember(card) { mutableStateOf(0) }
    val base = detailed ?: card
    val activityBase = base as? ShareCard.Activity
    val wantsAi = when {
        CardChip.COACH in chips && coachLine == null -> CardChip.COACH
        CardChip.ROAST in chips && roastLine == null -> CardChip.ROAST
        else -> null
    }
    LaunchedEffect(wantsAi, retries, activityBase) {
        val ask = wantsAi ?: return@LaunchedEffect
        val target = activityBase ?: return@LaunchedEffect
        // Wait for the heart-rate and week details before writing, so the line can use them.
        if (card is ShareCard.Activity && card.workout != null && detailed == null) return@LaunchedEffect
        if (failed == ask && retries == 0) return@LaunchedEffect
        writing = ask
        val line = try {
            host.workoutCards?.aiLine(target, ask)
        } finally {
            writing = null
        }
        if (line == null) {
            failed = ask
        } else {
            failed = null
            if (ask == CardChip.COACH) coachLine = line else roastLine = line
        }
        retries = 0
    }
    val available = activityBase?.let { a ->
        CardChip.order(style).filter {
            when (it) {
                CardChip.HEART -> a.heart != null
                CardChip.STANDOUT -> a.moment != null
                CardChip.JOURNEY -> a.journey != null
                else -> true
            }
        }
    }.orEmpty()
    // A "Roast my day" card: the roast (a fresh one from Try another), its chips, journey line and photo version.
    val roastCard = (card as? ShareCard.Daily)?.takeIf { it.roast }
    var dayChips by remember(card) { mutableStateOf(DayChip.DEFAULTS) }
    var roastText by remember(card) { mutableStateOf((card as? ShareCard.Daily)?.coachLine) }
    var shortRoast by remember(roastText) { mutableStateOf<String?>(null) }
    var dayJourney by remember(card) { mutableStateOf<String?>(null) }
    var picked by remember(roastText) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(roastText) {
        val text = roastText ?: return@LaunchedEffect
        if (roastCard == null) return@LaunchedEffect
        picked = RoastCardData.pick(cardPrefs, text, roastCard.pool)
    }
    var againLeft by remember(card) { mutableStateOf(0) }
    var againBusy by remember(card) { mutableStateOf(false) }
    LaunchedEffect(card) {
        if (roastCard != null) {
            againLeft = RoastCardData.againLeft(cardPrefs)
            dayJourney = host.workoutCards?.dayJourney()
        }
    }
    val onPhotoNow = photo != null
    LaunchedEffect(onPhotoNow, roastText) {
        val text = roastText ?: return@LaunchedEffect
        if (roastCard == null || !onPhotoNow || shortRoast != null || text.length <= RoastCard.PHOTO_MAX_CHARS) return@LaunchedEffect
        shortRoast = RoastCardData.short(cardPrefs, text, roastCard.pool)
    }
    val shown = activityBase?.copy(chips = chips, coachLine = coachLine, roastLine = roastLine)
        ?: roastCard?.copy(coachLine = roastText, chips = dayChips, journey = dayJourney, shortRoast = shortRoast, picked = picked)
        ?: base

    val pickPhoto = host.rememberPhotoPicker { loaded ->
        if (loaded != null) {
            photo = loaded
            frame = PhotoFrame()
            if (!cardPrefs.photoHintShown) {
                showHint = true
                cardPrefs.photoHintShown = true
            }
        } else {
            host.notice("Couldn't open that photo.")
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuperfitTheme.colors.cardBg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SuperfitTheme.colors.textSecondary) }
    ) {
        // The choices scroll; the Share button stays pinned below them so it's never missed.
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val current = photo
                val preview: @Composable (Modifier) -> Unit = { m ->
                    if (current == null) {
                        CapturableCard(shown, accent, null, light, frame, layer, m)
                    } else {
                        // Pinch to zoom, drag to move, double-tap to reset. Grid lines show while a finger
                        // is down; they sit on top of the preview, outside the picture that gets shared.
                        Box(
                            m
                                .pointerInput(current) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        touching = true
                                        do {
                                            val event = awaitPointerEvent()
                                        } while (event.changes.any { it.pressed })
                                        touching = false
                                    }
                                }
                                .pointerInput(current) {
                                    detectTapGestures(onDoubleTap = { frame = frame.reset() })
                                }
                                .pointerInput(current) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        showHint = false
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        if (w > 0f && h > 0f) {
                                            frame = frame.moved(zoom, pan.x / w, pan.y / h, current.width, current.height, CARD_WIDTH_PX, CARD_HEIGHT_PX)
                                        }
                                    }
                                }
                        ) {
                            CapturableCard(shown, accent, current, light, frame, layer, Modifier.fillMaxWidth())
                            if (touching) {
                                Canvas(Modifier.matchParentSize()) {
                                    val line = Color.White.copy(alpha = 0.45f)
                                    for (i in 1..2) {
                                        drawLine(line, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), strokeWidth = 1.dp.toPx())
                                        drawLine(line, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), strokeWidth = 1.dp.toPx())
                                    }
                                }
                            }
                        }
                    }
                }
                // A big preview with small colour and background choices beside it.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    preview(Modifier.fillMaxWidth(0.74f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CardAccents.forEachIndexed { i, color ->
                            val picked = color == accent
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (picked) Modifier.border(2.dp, SuperfitTheme.colors.textPrimary, CircleShape) else Modifier)
                                    .clickable { accent = color }
                                    .semantics { contentDescription = "${accentNames[i]}${if (picked) ", chosen" else ""}" }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (current == null) {
                            // Dark or light background; a photo card always keeps its dark fade.
                            MiniChoice("Dark", !light, accent) { light = false }
                            MiniChoice("Light", light, accent) { light = true }
                        } else {
                            Text("Stats", color = SuperfitTheme.colors.textSecondary, fontSize = 10.sp)
                            MiniChoice("Top", frame.statsTop, accent) { frame = frame.copy(statsTop = true) }
                            MiniChoice("Bottom", !frame.statsTop, accent) { frame = frame.copy(statsTop = false) }
                        }
                    }
                }
                if (showHint && current != null) {
                    Text("Pinch to zoom · drag to move · double-tap to reset", color = SuperfitTheme.colors.textSecondary, fontSize = 12.sp)
                }
                if (current == null) {
                    OptionPill("Add your photo", Icons.Filled.AddPhotoAlternate, on = false, accent = accent, highlight = true) {
                        pickPhoto()
                    }
                } else {
                    OptionPill("Remove photo", Icons.Filled.Close, on = false, accent = accent, highlight = false, centered = true) {
                        photo = null
                        frame = PhotoFrame()
                        showHint = false
                    }
                }
                if (available.isNotEmpty()) {
                    Text("Add to card", color = SuperfitTheme.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    // One row, so every option is seen at once.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        available.forEach { chip ->
                            val on = chip in chips
                            val label = if (chip == CardChip.HEART) "Heart" else chip.label
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) accent else SuperfitTheme.colors.fieldBg)
                                    .clickable {
                                        val next = CardChip.toggle(chips, chip)
                                        if (chip == failed) failed = null
                                        if (onPhoto) chipsPhoto = next else chipsPlain = next
                                    }
                                    .padding(horizontal = 2.dp, vertical = 10.dp)
                                    .semantics { contentDescription = "${chip.label}${if (on) ", on" else ", off"}" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (writing == chip) "$label…" else label,
                                    color = if (on) Color.Black else SuperfitTheme.colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                if (roastCard != null) {
                    val dayAvailable = DayChip.entries.filter { it != DayChip.JOURNEY || dayJourney != null }
                    Text("On the card", color = SuperfitTheme.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayAvailable.forEach { chip ->
                            SheetChip(chip.label, chip in dayChips, accent, Modifier.weight(1f)) { dayChips = DayChip.toggle(dayChips, chip) }
                        }
                    }
                    val roastShown = shown as? ShareCard.Daily
                    if (picked != null && roastShown?.shownRoast != null && DayChip.NUMBERS in dayChips && roastShown.roastTiles.isEmpty()) {
                        Text(
                            "This roast doesn't talk about any of today's numbers, so none are shown.",
                            color = SuperfitTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (DayChip.ROAST in dayChips && roastText != null) {
                        Text(
                            when {
                                againBusy -> "Writing another roast…"
                                againLeft > 0 -> "Try another roast · $againLeft left today"
                                else -> "No more fresh roasts today"
                            },
                            color = if (againLeft > 0 && !againBusy) accent else SuperfitTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(enabled = againLeft > 0 && !againBusy) {
                                    val old = roastText ?: return@clickable
                                    againBusy = true
                                    scope.launch {
                                        val fresh = try {
                                            RoastCardData.again(cardPrefs, old, roastCard.pool)
                                        } finally {
                                            againBusy = false
                                        }
                                        againLeft = RoastCardData.againLeft(cardPrefs)
                                        if (fresh != null) {
                                            roastText = fresh
                                        } else {
                                            host.notice("Couldn't write another roast just now.")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                failed?.takeIf { it in chips }?.let { chip ->
                    Text(
                        "Couldn't write the ${if (chip == CardChip.COACH) "coach line" else "roast"} just now. Tap to try again.",
                        color = SuperfitTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { retries++ }
                    )
                }
                val saved = if (onPhoto) savedPhoto else savedPlain
                if (available.isNotEmpty() && chips != saved) {
                    Text(
                        "Make this my default for ${if (onPhoto) "photo" else "plain"} cards",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                cardPrefs.setDefaults(onPhoto, chips)
                                if (onPhoto) savedPhoto = chips else savedPlain = chips
                                host.notice("Saved. Change it any time in Settings > Sharing.")
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            host.share(layer.toImageBitmap())
                            val activity = shown as? ShareCard.Activity
                            host.shared(
                                CardShared(
                                    type = when (card) {
                                        is ShareCard.Daily -> if (card.roast) "roast" else "day"
                                        is ShareCard.Activity -> "activity"
                                        is ShareCard.Best -> "best"
                                    },
                                    photo = photo != null,
                                    adjusted = photo != null && frame.adjusted,
                                    chips = activity?.let { a -> CardChip.encode(a.chips.filter { it in available }.toSet()).ifEmpty { "numbers" } }
                                        ?: roastCard?.let { dayChips.joinToString(",") { it.key } },
                                    statsPlace = if (frame.statsTop) "top" else "bottom",
                                    heartLine = activity?.shownHeart != null
                                )
                            )
                            onShared()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            host.notice("Couldn't make the picture. Please try again.")
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 24.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** One on/off choice in a row of equal chips, filled with the accent when on. */
@Composable
private fun SheetChip(label: String, on: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) accent else SuperfitTheme.colors.fieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 10.dp)
            .semantics { contentDescription = "$label${if (on) ", on" else ", off"}" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (on) Color.Black else SuperfitTheme.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** One full-width choice beside the preview: filled with the accent when on; [highlight] draws the eye to "Add photo". */
@Composable
private fun OptionPill(
    label: String,
    icon: ImageVector?,
    on: Boolean,
    accent: Color,
    highlight: Boolean = false,
    centered: Boolean = highlight,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) accent else SuperfitTheme.colors.fieldBg)
            .then(if (highlight) Modifier.border(1.5.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (highlight) 14.dp else 10.dp)
            .semantics { contentDescription = if (highlight) label else "$label${if (on) ", on" else ", off"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (on) Color.Black else accent, modifier = Modifier.size(if (highlight) 20.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (on) Color.Black else SuperfitTheme.colors.textPrimary,
            fontSize = if (highlight) 15.sp else 13.sp,
            fontWeight = if (on || highlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** A small stacked choice beside the preview (Dark / Light, stats Top / Bottom). */
@Composable
private fun MiniChoice(label: String, picked: Boolean, accent: Color, onPick: () -> Unit) {
    Text(
        label,
        color = if (picked) Color.Black else SuperfitTheme.colors.textPrimary,
        fontSize = 11.sp,
        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (picked) accent else SuperfitTheme.colors.fieldBg)
            .clickable(onClick = onPick)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "$label${if (picked) ", chosen" else ""}" },
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CapturableCard(
    card: ShareCard,
    accent: Color,
    photo: ImageBitmap?,
    light: Boolean,
    frame: PhotoFrame,
    layer: GraphicsLayer,
    modifier: Modifier
) {
    CapturableStory(layer, modifier) { ShareCardView(card, accent, photo = photo, light = light, frame = frame) }
}

/**
 * Shows a 360 x 640 dp story scaled to fit while recording it at full size into [layer], so the
 * shared picture is always 1080 x 1920 whatever the phone's screen.
 */
@Composable
fun CapturableStory(layer: GraphicsLayer, modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val scale = widthPx / CARD_WIDTH_PX.toFloat()
        val heightPx = (CARD_HEIGHT_PX * scale).roundToInt()
        val density = LocalDensity.current
        Box(
            Modifier
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .clip(RoundedCornerShape(16.dp))
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(CARD_WIDTH_PX, CARD_HEIGHT_PX))
                    layout(widthPx, heightPx) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            CompositionLocalProvider(LocalDensity provides CardDensity) { content() }
        }
    }
}
