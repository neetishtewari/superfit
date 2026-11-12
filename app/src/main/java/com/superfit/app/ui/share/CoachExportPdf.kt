package com.superfit.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.superfit.app.R
import com.superfit.app.domain.CoachExport
import com.superfit.app.ui.report.CoachReportPdf
import com.superfit.app.ui.report.PdfFont
import com.superfit.app.ui.report.PdfSurface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a [CoachExport] as a branded A4 PDF with the layout shared with the iPhone (CoachReportPdf),
 * in Manrope with the app icon. Made on the phone and only leaves it through the share sheet.
 */
object CoachExportPdf {

    private const val PAGE_W = CoachReportPdf.PAGE_W
    private const val PAGE_H = CoachReportPdf.PAGE_H

    // Superfit "Fresh Green" colours, matching the app.
    private val GREEN = Color.rgb(14, 159, 117)
    private val INK = Color.rgb(20, 32, 27)

    /** Fonts and logo from the app; plain system fonts and no logo if they can't be loaded. */
    private class Brand(
        val regular: Typeface,
        val medium: Typeface,
        val bold: Typeface,
        val extraBold: Typeface,
        val logo: Bitmap?
    )

    private fun brand(context: Context?): Brand {
        fun font(id: Int, fallback: Typeface) = context?.let { runCatching { ResourcesCompat.getFont(it, id) }.getOrNull() } ?: fallback
        return Brand(
            regular = font(R.font.manrope_regular, Typeface.DEFAULT),
            medium = font(R.font.manrope_medium, Typeface.DEFAULT),
            bold = font(R.font.manrope_bold, Typeface.DEFAULT_BOLD),
            extraBold = font(R.font.manrope_extrabold, Typeface.DEFAULT_BOLD),
            logo = context?.let { runCatching { BitmapFactory.decodeResource(it.resources, R.drawable.ic_superfit_launcher) }.getOrNull() }
                ?.let { Bitmap.createScaledBitmap(it, 96, 96, true) } // the full-size icon made each PDF about 9 MB
        )
    }

    /** Writes the PDF to the app's share cache (replacing the last share) and opens the share sheet. */
    suspend fun share(context: Context, report: CoachExport) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shares").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, CoachReportPdf.fileName(report)).also { write(report, it, context) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        val range = CoachReportPdf.range(report)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "My Superfit report, $range")
            putExtra(Intent.EXTRA_TEXT, "My food, workouts and weight from Superfit, $range.")
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share or save my report"))
    }

    fun write(report: CoachExport, file: File, context: Context? = null) {
        val doc = PdfDocument()
        val surface = AndroidPdfSurface(doc, brand(context))
        CoachReportPdf.draw(report, surface)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        // Make the footer's "Get it on Google Play" a real tappable link (PdfDocument can't draw links itself).
        val link = surface.lastLink ?: return
        runCatching {
            PdfLinks.addLinkToEveryPage(
                file,
                link.url,
                // PDF link boxes count up from the bottom of the page.
                floatArrayOf(link.left, PAGE_H - link.bottom, link.right, PAGE_H - link.top)
            )
        }
    }

    private class Link(val url: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** The shared report layout drawn on Android's PdfDocument, in Manrope with the app icon. */
    private class AndroidPdfSurface(private val doc: PdfDocument, private val brand: Brand) : PdfSurface {
        private var page: PdfDocument.Page? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        var lastLink: Link? = null

        private val canvas: Canvas get() = page!!.canvas

        override fun startPage(number: Int) {
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        }

        override fun finishPage() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float) {
            fill.color = color
            if (radius > 0f) {
                canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, fill)
            } else {
                canvas.drawRect(left, top, right, bottom, fill)
            }
        }

        private fun style(size: Float, font: PdfFont, color: Int, letterSpacing: Float) {
            paint.textSize = size
            paint.typeface = when (font) {
                PdfFont.REGULAR -> brand.regular
                PdfFont.MEDIUM -> brand.medium
                PdfFont.BOLD -> brand.bold
                PdfFont.EXTRA_BOLD -> brand.extraBold
            }
            paint.color = color
            paint.letterSpacing = letterSpacing
        }

        override fun drawText(text: String, x: Float, baseline: Float, size: Float, font: PdfFont, color: Int, letterSpacing: Float) {
            style(size, font, color, letterSpacing)
            canvas.drawText(text, x, baseline, paint)
        }

        override fun measureText(text: String, size: Float, font: PdfFont, letterSpacing: Float): Float {
            style(size, font, INK, letterSpacing)
            return paint.measureText(text)
        }

        override fun drawLogo(left: Float, top: Float, size: Float) {
            val bmp = brand.logo
            val box = RectF(left, top, left + size, top + size)
            if (bmp != null) {
                // The app icon with rounded corners, as it looks on a phone.
                val save = canvas.save()
                canvas.clipPath(android.graphics.Path().apply { addRoundRect(box, size * 0.18f, size * 0.18f, android.graphics.Path.Direction.CW) })
                canvas.drawBitmap(bmp, null, box, paint)
                canvas.restoreToCount(save)
            } else {
                fill.color = Color.WHITE
                canvas.drawRoundRect(box, size * 0.28f, size * 0.28f, fill)
                drawText("S", left + size * 0.3f, top + size * 0.74f, size * 0.62f, PdfFont.EXTRA_BOLD, GREEN, 0f)
            }
        }

        override fun link(url: String, left: Float, top: Float, right: Float, bottom: Float) {
            // Every page has the same footer link; it's added to all pages after the file is written.
            lastLink = Link(url, left, top, right, bottom)
        }
    }
}
