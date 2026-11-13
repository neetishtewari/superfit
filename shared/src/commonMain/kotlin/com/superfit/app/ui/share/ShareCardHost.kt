package com.superfit.app.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.WorkoutCardData

/** What a shared card was like, for the usage counts. */
data class CardShared(
    val type: String,
    val photo: Boolean,
    val adjusted: Boolean,
    val chips: String?,
    val statsPlace: String,
    val heartLine: Boolean
)

/**
 * The phone's side of the share screen: where its choices are kept, the photo picker, the share
 * sheet and short notices. Android and iPhone each provide one through [LocalShareCardHost].
 */
interface ShareCardHost {
    val prefs: ShareCardPrefs

    /** Fills in workout cards (heart rate, journey, strength); null where the phone can't yet. */
    val workoutCards: WorkoutCardData?

    /**
     * Returns what opens the phone's photo picker. The picked photo arrives in [onPicked], at most
     * [MAX_PHOTO_EDGE] on its longest side, or null when it couldn't be read. It is never saved.
     */
    @Composable
    fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit

    /** Saves [picture] in the app's own temporary folder (replacing the last one) and opens the share sheet. */
    suspend fun share(picture: ImageBitmap)

    /** A short message, such as "Couldn't open that photo." */
    fun notice(message: String)

    fun shared(event: CardShared) {}
}

/** A host that can't do anything, until the app provides its own. */
private object NoShareCardHost : ShareCardHost {
    override val prefs = ShareCardPrefs(MemoryPrefs())
    override val workoutCards: WorkoutCardData? = null

    @Composable
    override fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit = { onPicked(null) }

    override suspend fun share(picture: ImageBitmap) = Unit

    override fun notice(message: String) = Unit
}

val LocalShareCardHost = staticCompositionLocalOf<ShareCardHost> { NoShareCardHost }
