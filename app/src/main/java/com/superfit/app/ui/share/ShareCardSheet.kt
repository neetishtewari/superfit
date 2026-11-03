package com.superfit.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.WorkoutCardData
import com.superfit.app.domain.CardChip
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.PhotoFrame
import com.superfit.app.domain.ShareCard
import com.superfit.app.theme.SuperfitTheme
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Share pictures are 1080 x 1920 px: the 360 x 640 dp card drawn at 3x. */
const val CARD_WIDTH_PX = 1080
const val CARD_HEIGHT_PX = 1920
private val CardDensity = Density(density = 3f, fontScale = 1f)

private const val SHARE_TEXT = "Tracked with Superfit https://play.google.com/store/apps/details?id=com.superfit.aifitness"
private val accentNames = listOf("Orange", "Mint", "Cyan", "Violet")

// Whether the photo hint was seen; the rest of the saved share choices live in ShareCardPrefs.
private const val KEY_HINT_SHOWN = "photo_hint_shown"

// Longest side of a loaded photo: room to zoom in on a 1080 x 1920 card, about 20 MB at most.
private const val MAX_PHOTO_EDGE = 2560

/**
 * Preview of a share card with colour choices. Share draws the picture on the phone and opens
 * the Android share sheet; nothing is uploaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(card: ShareCard, onDismiss: () -> Unit, onShared: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val prefs = remember { context.getSharedPreferences(ShareCardPrefs.FILE, Context.MODE_PRIVATE) }
    val cardPrefs = remember { ShareCardPrefs(prefs) }
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
            detailed = withContext(Dispatchers.IO) { WorkoutCardData.enrich(context, card) }
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
            withContext(Dispatchers.IO) { WorkoutCardData.aiLine(context, target, ask) }
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
    val shown = activityBase?.copy(chips = chips, coachLine = coachLine, roastLine = roastLine) ?: base

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                if (loaded != null) {
                    photo = loaded.asImageBitmap()
                    frame = PhotoFrame()
                    if (!prefs.getBoolean(KEY_HINT_SHOWN, false)) {
                        showHint = true
                        prefs.edit().putBoolean(KEY_HINT_SHOWN, true).apply()
                    }
                } else {
                    Toast.makeText(context, "Couldn't open that photo.", Toast.LENGTH_SHORT).show()
                }
            }
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
                if (card !is ShareCard.Daily) {
                    if (current == null) {
                        OptionPill("Add your photo", Icons.Filled.AddPhotoAlternate, on = false, accent = accent, highlight = true) {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    } else {
                        OptionPill("Remove photo", Icons.Filled.Close, on = false, accent = accent, highlight = false, centered = true) {
                            photo = null
                            frame = PhotoFrame()
                            showHint = false
                        }
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
                                Toast.makeText(context, "Saved. Change it any time in Settings > Sharing.", Toast.LENGTH_SHORT).show()
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
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap)
                            val activity = shown as? ShareCard.Activity
                            com.superfit.app.data.AppAnalytics.cardShared(
                                type = when (card) {
                                    is ShareCard.Daily -> if (card.roast) "roast" else "day"
                                    is ShareCard.Activity -> "activity"
                                    is ShareCard.Best -> "best"
                                },
                                photo = photo != null,
                                adjusted = photo != null && frame.adjusted,
                                chips = activity?.let { a -> CardChip.encode(a.chips.filter { it in available }.toSet()).ifEmpty { "numbers" } },
                                statsPlace = if (frame.statsTop) "top" else "bottom",
                                heartLine = activity?.shownHeart != null
                            )
                            onShared()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't make the picture. Please try again.", Toast.LENGTH_SHORT).show()
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
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

/** Saves the picture in the app's cache (replacing the last one) and opens the share sheet. */
suspend fun shareImage(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        File(dir, "superfit-${System.currentTimeMillis()}.png").also { f ->
            f.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

/**
 * Reads a gallery photo turned the right way up, with its longest side at most [MAX_PHOTO_EDGE] so
 * it stays sharp when zoomed in without using too much memory. Returns null if it can't be read.
 */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_PHOTO_EDGE) {
                val shrink = longest / MAX_PHOTO_EDGE.toDouble()
                decoder.setTargetSize((info.size.width / shrink).roundToInt().coerceAtLeast(1), (info.size.height / shrink).roundToInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
} catch (e: Exception) {
    null
}
