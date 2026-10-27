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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
private val accentNames = listOf("Mint", "Cyan", "Violet", "Amber")

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
    var accent by remember(card) { mutableStateOf(defaultAccent(card)) }
    var busy by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(false) }
    // The user's photo lives only in memory while the sheet is open; it is never saved or uploaded.
    var photo by remember(card) { mutableStateOf<ImageBitmap?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                if (loaded != null) {
                    photo = loaded.asImageBitmap()
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CapturableCard(card, accent, photo, light, layer, Modifier.fillMaxWidth(0.7f))
            if (photo == null) {
                // Dark or light background; a photo card always keeps its dark fade.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, SuperfitTheme.colors.glassBorder, RoundedCornerShape(10.dp))
                ) {
                    listOf(false to "Dark", true to "Light").forEach { (isLight, label) ->
                        val picked = light == isLight
                        Text(
                            label,
                            color = if (picked) Color.Black else SuperfitTheme.colors.textPrimary,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .background(if (picked) accent else Color.Transparent)
                                .clickable { light = isLight }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (card !is ShareCard.Daily) {
                if (photo == null) {
                    OutlinedButton(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add your photo")
                    }
                } else {
                    OutlinedButton(onClick = { photo = null }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove photo")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CardAccents.forEachIndexed { i, color ->
                    val picked = color == accent
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (picked) Modifier.border(3.dp, SuperfitTheme.colors.textPrimary, CircleShape) else Modifier)
                            .clickable { accent = color }
                            .semantics { contentDescription = "${accentNames[i]}${if (picked) ", chosen" else ""}" }
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
                            com.superfit.app.data.AppAnalytics.cardShared(
                                when (card) {
                                    is ShareCard.Daily -> if (card.roast) "roast" else "day"
                                    is ShareCard.Activity -> "activity"
                                    is ShareCard.Best -> "best"
                                }
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CapturableCard(card: ShareCard, accent: Color, photo: ImageBitmap?, light: Boolean, layer: GraphicsLayer, modifier: Modifier) {
    CapturableStory(layer, modifier) { ShareCardView(card, accent, photo = photo, light = light) }
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
 * Reads a gallery photo at roughly card size (about 1080 x 1920), turned the right way up.
 * Returns null if it can't be read.
 */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val shrink = maxOf(1, minOf(info.size.width / CARD_WIDTH_PX, info.size.height / CARD_HEIGHT_PX))
            decoder.setTargetSize(info.size.width / shrink, info.size.height / shrink)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CARD_WIDTH_PX && bounds.outHeight / (sample * 2) >= CARD_HEIGHT_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
} catch (e: Exception) {
    null
}
