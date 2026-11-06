package com.superfit.app.domain

import kotlin.math.max

/**
 * How the user's photo sits on a share card. [scale] 1 means the photo just fills the card (the
 * old fixed crop); [offsetX] and [offsetY] move it, as fractions of the card's width and height,
 * from centred. Kept in card units so the small preview and the full-size picture always match.
 */
data class PhotoFrame(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Stats block under the header instead of at the bottom, for photos with a face low down. */
    val statsTop: Boolean = false
) {
    val adjusted: Boolean get() = scale != 1f || offsetX != 0f || offsetY != 0f

    /** Zoomed by [zoom] and moved by [dx], [dy] (fractions of the card), kept so no edge shows. */
    fun moved(zoom: Float, dx: Float, dy: Float, imageW: Int, imageH: Int, cardW: Int, cardH: Int): PhotoFrame =
        copy(scale = scale * zoom, offsetX = offsetX + dx, offsetY = offsetY + dy).clamped(imageW, imageH, cardW, cardH)

    /** Scale between filling the card and [MAX_SCALE]; the photo always covers the whole card. */
    fun clamped(imageW: Int, imageH: Int, cardW: Int, cardH: Int): PhotoFrame {
        if (imageW <= 0 || imageH <= 0 || cardW <= 0 || cardH <= 0) return copy(scale = 1f, offsetX = 0f, offsetY = 0f)
        val s = scale.coerceIn(1f, MAX_SCALE)
        val (w, h) = drawnSize(imageW, imageH, cardW, cardH, s)
        // How far the photo can move before an edge of the card shows, as a fraction of the card.
        val maxX = ((w - cardW) / 2f / cardW).coerceAtLeast(0f)
        val maxY = ((h - cardH) / 2f / cardH).coerceAtLeast(0f)
        return copy(scale = s, offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** Back to filling the card, centred; the stats keep their place. */
    fun reset(): PhotoFrame = copy(scale = 1f, offsetX = 0f, offsetY = 0f)

    companion object {
        const val MAX_SCALE = 4f

        /** The photo's size on a [cardW] x [cardH] card: just covering it at scale 1, times [scale]. */
        fun drawnSize(imageW: Int, imageH: Int, cardW: Int, cardH: Int, scale: Float): Pair<Float, Float> {
            val cover = max(cardW.toFloat() / imageW, cardH.toFloat() / imageH) * scale
            return imageW * cover to imageH * cover
        }
    }
}
