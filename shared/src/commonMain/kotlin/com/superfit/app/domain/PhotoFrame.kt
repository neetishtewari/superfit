package com.superfit.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What fills the card around a photo that has been pinched smaller than the card. */
enum class PhotoFill(val key: String) { BLUR("blur"), COLOUR("colour") }

/**
 * How the user's photo sits on a share card. [scale] 1 means the photo just fills the card (the
 * old fixed crop); below 1 it is smaller than the card and [fill] shows around it. [offsetX] and
 * [offsetY] move it, as fractions of the card's width and height, from centred. Kept in card units
 * so the small preview and the full-size picture always match.
 */
data class PhotoFrame(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Stats block under the header instead of at the bottom, for photos with a face low down. */
    val statsTop: Boolean = false,
    val fill: PhotoFill = PhotoFill.BLUR
) {
    val adjusted: Boolean get() = scale != 1f || offsetX != 0f || offsetY != 0f

    /** Zoomed by [zoom] and moved by [dx], [dy] (fractions of the card), kept within its limits. */
    fun moved(zoom: Float, dx: Float, dy: Float, imageW: Int, imageH: Int, cardW: Int, cardH: Int): PhotoFrame =
        copy(scale = scale * zoom, offsetX = offsetX + dx, offsetY = offsetY + dy).clamped(imageW, imageH, cardW, cardH)

    /**
     * Scale between [minScale] (a little smaller than the whole photo fitting) and [MAX_SCALE]. Along
     * a side where the photo is bigger than the card it can't move far enough to show an edge; along
     * a side where it's smaller it stays fully on the card.
     */
    fun clamped(imageW: Int, imageH: Int, cardW: Int, cardH: Int): PhotoFrame {
        if (imageW <= 0 || imageH <= 0 || cardW <= 0 || cardH <= 0) return copy(scale = 1f, offsetX = 0f, offsetY = 0f)
        val s = scale.coerceIn(minScale(imageW, imageH, cardW, cardH), MAX_SCALE)
        val (w, h) = drawnSize(imageW, imageH, cardW, cardH, s)
        val maxX = abs(w - cardW) / 2f / cardW
        val maxY = abs(h - cardH) / 2f / cardH
        return copy(scale = s, offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** True when some of the card shows around the photo, so [fill] is drawn. */
    fun showsFill(imageW: Int, imageH: Int, cardW: Int, cardH: Int): Boolean {
        if (imageW <= 0 || imageH <= 0 || cardW <= 0 || cardH <= 0) return false
        val (w, h) = drawnSize(imageW, imageH, cardW, cardH, clamped(imageW, imageH, cardW, cardH).scale)
        return w < cardW - 0.5f || h < cardH - 0.5f
    }

    /** Back to filling the card, centred; the stats and fill keep their place. */
    fun reset(): PhotoFrame = copy(scale = 1f, offsetX = 0f, offsetY = 0f)

    companion object {
        const val MAX_SCALE = 4f

        /** How much smaller than "the whole photo fits" it may go. */
        const val BELOW_FIT = 0.7f

        /** The photo's size on a [cardW] x [cardH] card: just covering it at scale 1, times [scale]. */
        fun drawnSize(imageW: Int, imageH: Int, cardW: Int, cardH: Int, scale: Float): Pair<Float, Float> {
            val cover = max(cardW.toFloat() / imageW, cardH.toFloat() / imageH) * scale
            return imageW * cover to imageH * cover
        }

        /** The smallest scale: the whole photo fits inside the card, times [BELOW_FIT]. */
        fun minScale(imageW: Int, imageH: Int, cardW: Int, cardH: Int): Float {
            val cover = max(cardW.toFloat() / imageW, cardH.toFloat() / imageH)
            val fit = min(cardW.toFloat() / imageW, cardH.toFloat() / imageH)
            return fit / cover * BELOW_FIT
        }
    }
}
