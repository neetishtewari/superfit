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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.superfit.app.data.AndroidWorkoutCards
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.UsageEvents
import com.superfit.app.data.WorkoutCardData
import com.superfit.app.data.of
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android's side of the shared share screen: the gallery picker, the share sheet and toasts. */
class AndroidShareCardHost(private val context: Context) : ShareCardHost {
    override val prefs: ShareCardPrefs = ShareCardPrefs.of(context)

    override val workoutCards: WorkoutCardData by lazy { AndroidWorkoutCards.data(context) }

    @Composable
    override fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                    onPicked(loaded?.asImageBitmap())
                }
            }
        }
        return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    override suspend fun share(picture: ImageBitmap) = shareImage(context, picture.asAndroidBitmap())

    override fun notice(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun shared(event: CardShared) {
        UsageEvents.cardShared(
            type = event.type,
            photo = event.photo,
            adjusted = event.adjusted,
            chips = event.chips,
            statsPlace = event.statsPlace,
            heartLine = event.heartLine
        )
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
