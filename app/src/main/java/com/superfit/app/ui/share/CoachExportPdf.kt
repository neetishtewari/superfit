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
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.CoachExport
import com.superfit.app.domain.CoachExportBuilder
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a [CoachExport] as a branded A4 PDF: a summary page with headline numbers, a day-by-day table,
 * then each day's meals. Every page carries the Superfit header and footer, because coaches who receive
 * it are people we'd like to hear about Superfit. Made on the phone and only leaves it through the share sheet.
 */
object CoachExportPdf {

    private const val PAGE_W = 595 // A4 in PDF points
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val BAND_H = 64f // green header band on later pages
    private const val FOOTER_TOP = PAGE_H - 46f

    // Superfit "Fresh Green" colours, matching the app.
    private val GREEN = Color.rgb(14, 159, 117)
    private val GREEN_DEEP = Color.rgb(8, 122, 89)
    private val GREEN_SOFT = Color.rgb(230, 245, 239)
    private val INK = Color.rgb(20, 32, 27)
    private val MUTED = Color.rgb(91, 107, 100)
    private val LINE = Color.rgb(227, 232, 230)
    private val ZEBRA = Color.rgb(246, 248, 247)
    private val AMBER = Color.rgb(178, 107, 0)

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val rangeFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

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
            File(dir, "superfit-report-${report.from}-to-${report.to}.pdf").also { write(report, it, context) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        val range = "${report.from.format(rangeFormat)} to ${report.to.format(rangeFormat)}"
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
        val range = "${report.from.format(rangeFormat)} to ${report.to.format(rangeFormat)}"
        val pen = Pen(doc, brand(context), range)
        summary(pen, report)
        dayTable(pen, report)
        mealPages(pen, report)
        pen.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun summary(pen: Pen, r: CoachExport) {
        val h = r.header
        pen.coverHeader()
        pen.text(h.name?.takeIf { it.isNotBlank() } ?: "${CoachExportBuilder.goalLabel(h.goal)} · ${kg(h.startKg)} → ${kg(h.targetKg)}", 24f, font = Font.EXTRA_BOLD)
        pen.gap(2f)
        pen.text(
            "${r.daysLogged} of ${r.totalDays} days fully logged",
            11f,
            color = MUTED
        )
        pen.gap(14f)

        // Headline numbers a coach looks at first.
        val change = r.weightStart?.let { s -> r.weightEnd?.let { e -> e.second - s.second } }
        pen.tiles(
            listOf(
                Tile("Calories a day", r.avgKcal?.let { num(it) } ?: "–", if (r.daysLogged > 0) "from ${r.daysLogged} logged ${if (r.daysLogged == 1) "day" else "days"}" else "not enough logged"),
                Tile("Protein goal", r.proteinPct?.let { "$it%" } ?: "–", r.avgProteinG?.let { "$it g of ${r.proteinTargetG} g a day" } ?: "not enough logged"),
                Tile("Workouts", "${r.workoutCount}", if (r.walkCount > 0) "plus ${r.walkCount} walks" else "in this period"),
                Tile("Weight, this period", change?.let { signedKg(it) } ?: r.weightStart?.let { kg(it.second) } ?: "–", sinceStart(h.startKg, (r.weightEnd ?: r.weightStart)?.second) ?: if (change != null) "${kg(r.weightStart!!.second)} → ${kg(r.weightEnd!!.second)}" else "too few weigh-ins")
            )
        )
        pen.gap(18f)

        pen.heading("About")
        pen.row("Goal", CoachExportBuilder.goalLabel(h.goal))
        pen.row("Start / goal weight", "${kg(h.startKg)} / ${kg(h.targetKg)}")
        pen.row("Age, height, sex", "${h.age} · ${h.heightCm.toInt()} cm · ${if (h.isMale) "Male" else "Female"}")
        pen.gap(12f)

        pen.heading("Eating · average of fully logged days")
        if (r.daysLogged == 0) {
            pen.text("No fully logged days in this period.", 11f, color = MUTED)
        } else {
            pen.row("Calories", "${num(r.avgKcal ?: 0)} kcal a day (target about ${num(r.avgKcalTarget ?: 0)})")
            pen.row("Protein", "${r.avgProteinG} g a day, ${r.proteinPct ?: 0}% of the ${r.proteinTargetG} g goal")
            pen.row("Carbs / fat", "${r.avgCarbsG} g / ${r.avgFatG} g a day")
        }
        if (r.dietScores.any { it != null }) {
            pen.gap(4f)
            pen.scoreBars("Diet Quality, out of 100", r.dietScores)
        }
        pen.gap(12f)

        pen.heading("Activity")
        pen.row("Workouts", "${r.workoutCount}" + if (r.walkCount > 0) ", plus ${r.walkCount} walks" else "")
        if (r.workoutMinutes > 0) pen.row("Time moving", duration(r.workoutMinutes) + " in total")
        r.avgSteps?.let { pen.row("Steps", "%,d a day on average".format(Locale.US, it)) }
        pen.gap(12f)

        pen.heading("Weight and body")
        val start = r.weightStart
        val end = r.weightEnd
        when {
            start == null -> pen.text("No weigh-ins in this period.", 11f, color = MUTED)
            end == null -> pen.row("Weigh-in", "${kg(start.second)} on ${start.first.format(dayFormat)}")
            else -> {
                pen.row("First weigh-in", "${kg(start.second)} on ${start.first.format(dayFormat)}")
                pen.row("Latest weigh-in", "${kg(end.second)} on ${end.first.format(dayFormat)}")
                pen.row("Change", signedKg(end.second - start.second))
            }
        }
        r.body?.let { b ->
            b.bodyFatPct?.let { pen.row("Body fat", "${BodyReadings.fmt(it)}%" + (b.bodyFatChange?.let { c -> " (${if (c > 0) "+" else ""}${BodyReadings.fmt(c)} pts over 4 weeks)" } ?: "")) }
            b.leanKg?.let { pen.row("Lean mass", "${BodyReadings.fmt(it)} kg") }
            b.source?.let { pen.row("Measured by", it) }
            b.insight?.let { pen.wrapped(it, 10f, color = MUTED) }
        }
    }

    private val columns = listOf("Day" to 0f, "kcal" to 78f, "Protein" to 126f, "Carbs" to 178f, "Fat" to 226f, "Steps" to 268f, "Weight" to 322f, "Workouts" to 380f)

    private fun dayTable(pen: Pen, r: CoachExport) {
        pen.newPage()
        pen.title("Day by day")
        pen.tableHeader(columns.map { it.first })
        r.days.forEachIndexed { i, d ->
            val values = if (d.logged || d.meals.isNotEmpty()) {
                listOf(num(d.kcal), "${d.proteinG} g", "${d.carbsG} g", "${d.fatG} g")
            } else {
                listOf(NOT_LOGGED, "", "", "")
            }
            val firstWorkout = d.workouts.firstOrNull() ?: ""
            val over = d.logged && d.kcalTarget > 0 && d.kcal > d.kcalTarget * 1.1
            pen.tableRow(
                listOf(d.date.format(dayFormat)) + values +
                    listOf(if (d.steps > 0) "%,d".format(Locale.US, d.steps) else "", d.weightKg?.let { kg(it) } ?: "", firstWorkout),
                striped = i % 2 == 1,
                muted = !d.logged,
                highlightKcal = over
            )
            d.workouts.drop(1).forEach { w -> pen.tableRow(List(columns.size - 1) { "" } + w, striped = i % 2 == 1, muted = !d.logged) }
        }
        pen.gap(8f)
        pen.wrapped("Days with grey food numbers had fewer than two meals logged and aren't counted in averages. Amber calories are more than 10% over that day's target.", 9f, color = MUTED)
    }

    private fun mealPages(pen: Pen, r: CoachExport) {
        val days = r.days.filter { it.meals.isNotEmpty() }
        if (days.isEmpty()) return
        pen.newPage()
        pen.title("Meals")
        days.forEach { d ->
            pen.keepRoom(48f)
            pen.dayBand(d.date.format(dayFormat), "%,d kcal · %d g protein".format(Locale.US, d.kcal, d.proteinG))
            d.meals.forEach { m -> pen.mealLine(m.time, m.name.replaceFirstChar { it.uppercase() }, "${m.kcal} kcal · ${m.proteinG} g") }
            pen.gap(8f)
        }
    }

    private const val NOT_LOGGED = "Food not logged"

    private fun num(value: Int) = "%,d".format(Locale.US, value)

    private fun duration(minutes: Long) = if (minutes < 120) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

    /** "4.1 kg down since start": the whole journey, next to this period's change. */
    private fun sinceStart(startKg: Double, latestKg: Double?): String? {
        if (latestKg == null || startKg <= 0) return null
        val diff = latestKg - startKg
        if (kotlin.math.abs(diff) < 0.1) return "same as at the start"
        return String.format(Locale.US, "%.1f kg %s since start", kotlin.math.abs(diff), if (diff < 0) "down" else "up")
    }

    private fun kg(value: Double) = String.format(Locale.US, "%.1f kg", value)
    private fun signedKg(value: Double) = (if (value > 0) "+" else "") + kg(value)

    private data class Tile(val label: String, val value: String, val note: String)

    private enum class Font { REGULAR, MEDIUM, BOLD, EXTRA_BOLD }

    /** Writes down the page, starting a new branded page when one fills up. */
    private class Pen(private val doc: PdfDocument, private val brand: Brand, private val range: String) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        init {
            newPage(cover = true)
        }

        fun newPage(cover: Boolean = false) {
            page?.let { closePage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            if (cover) {
                y = MARGIN
            } else {
                smallHeader()
                y = BAND_H + 24f
            }
        }

        fun finish() {
            page?.let { closePage(it) }
            page = null
        }

        private fun typeface(font: Font) = when (font) {
            Font.REGULAR -> brand.regular
            Font.MEDIUM -> brand.medium
            Font.BOLD -> brand.bold
            Font.EXTRA_BOLD -> brand.extraBold
        }

        private fun style(size: Float, font: Font = Font.REGULAR, color: Int = INK) {
            paint.textSize = size
            paint.typeface = typeface(font)
            paint.color = color
            paint.letterSpacing = 0f
        }

        private fun logo(x: Float, top: Float, size: Float) {
            val bmp = brand.logo
            if (bmp != null) {
                // The app icon with rounded corners, as it looks on a phone.
                val inset = 0f
                val save = canvas.save()
                val clip = android.graphics.Path().apply {
                    addRoundRect(RectF(x + inset, top + inset, x + size - inset, top + size - inset), size * 0.18f, size * 0.18f, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clip)
                canvas.drawBitmap(bmp, null, RectF(x + inset, top + inset, x + size - inset, top + size - inset), paint)
                canvas.restoreToCount(save)
            } else {
                fill.color = Color.WHITE
                canvas.drawRoundRect(RectF(x, top, x + size, top + size), size * 0.28f, size * 0.28f, fill)
                style(size * 0.62f, Font.EXTRA_BOLD, GREEN)
                canvas.drawText("S", x + size * 0.3f, top + size * 0.74f, paint)
            }
        }

        private fun wordmark(x: Float, baseline: Float, size: Float) {
            style(size, Font.EXTRA_BOLD, Color.WHITE)
            paint.letterSpacing = 0.12f
            canvas.drawText("SUPERFIT", x, baseline, paint)
            paint.letterSpacing = 0f
        }

        /** Tall green band on page 1: logo, name and what this document is. */
        fun coverHeader() {
            val bandH = 118f
            fill.color = GREEN
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), bandH, fill)
            logo(MARGIN, 30f, 40f)
            wordmark(MARGIN + 52f, 57f, 18f)
            style(10f, Font.MEDIUM, Color.argb(230, 255, 255, 255))
            canvas.drawText("Your AI coach for food and fitness", MARGIN + 52f, 72f, paint)
            style(9f, Font.BOLD, Color.argb(220, 255, 255, 255))
            paint.letterSpacing = 0.08f
            val label = "PROGRESS REPORT"
            canvas.drawText(label, PAGE_W - MARGIN - paint.measureText(label), 50f, paint)
            paint.letterSpacing = 0f
            style(13f, Font.BOLD, Color.WHITE)
            canvas.drawText(range, PAGE_W - MARGIN - paint.measureText(range), 68f, paint)
            y = bandH + 30f
        }

        /** Slim green band on later pages. */
        private fun smallHeader() {
            fill.color = GREEN
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), BAND_H, fill)
            logo(MARGIN, 18f, 28f)
            wordmark(MARGIN + 38f, 38f, 13f)
            style(10f, Font.MEDIUM, Color.WHITE)
            val t = "Progress report · $range"
            canvas.drawText(t, PAGE_W - MARGIN - paint.measureText(t), 37f, paint)
        }

        private fun closePage(p: PdfDocument.Page) {
            fill.color = GREEN
            canvas.drawRect(MARGIN, FOOTER_TOP, PAGE_W - MARGIN, FOOTER_TOP + 1.2f, fill)
            style(8.5f, Font.BOLD, GREEN_DEEP)
            canvas.drawText("Made with Superfit", MARGIN, FOOTER_TOP + 16f, paint)
            val bw = paint.measureText("Made with Superfit")
            style(8.5f, Font.REGULAR, MUTED)
            canvas.drawText(" · Clients log meals by photo or voice · Get it on Google Play", MARGIN + bw, FOOTER_TOP + 16f, paint)
            val num = "$pageNo"
            canvas.drawText(num, PAGE_W - MARGIN - paint.measureText(num), FOOTER_TOP + 16f, paint)
            doc.finishPage(p)
        }

        fun keepRoom(height: Float) {
            if (y + height > FOOTER_TOP - 12f) newPage()
        }

        fun gap(height: Float) {
            y += height
        }

        fun text(s: String, size: Float, font: Font = Font.REGULAR, color: Int = INK, x: Float = MARGIN) {
            keepRoom(size * 1.5f)
            style(size, font, color)
            y += size
            canvas.drawText(s, x, y, paint)
            y += size * 0.45f
        }

        fun title(s: String) {
            text(s, 20f, Font.EXTRA_BOLD)
            gap(10f)
        }

        /** Green small-caps label with a hairline, starting each section. */
        fun heading(s: String) {
            keepRoom(44f)
            style(9.5f, Font.BOLD, GREEN_DEEP)
            paint.letterSpacing = 0.08f
            y += 10f
            canvas.drawText(s.uppercase(Locale.US), MARGIN, y, paint)
            paint.letterSpacing = 0f
            y += 6f
            fill.color = LINE
            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 0.8f, fill)
            y += 6f
        }

        fun row(label: String, value: String) {
            keepRoom(18f)
            y += 11f
            style(10.5f, Font.MEDIUM, MUTED)
            canvas.drawText(label, MARGIN, y, paint)
            style(10.5f, Font.REGULAR, INK)
            canvas.drawText(fit(value, CONTENT_W - 150f), MARGIN + 150f, y, paint)
            y += 6f
        }

        fun tiles(items: List<Tile>) {
            val gapX = 10f
            val w = (CONTENT_W - gapX * (items.size - 1)) / items.size
            val h = 74f
            keepRoom(h)
            items.forEachIndexed { i, t ->
                val x = MARGIN + i * (w + gapX)
                fill.color = GREEN_SOFT
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, fill)
                style(8.5f, Font.BOLD, GREEN_DEEP)
                canvas.drawText(fit(t.label, w - 20f), x + 10f, y + 18f, paint)
                style(20f, Font.EXTRA_BOLD, INK)
                canvas.drawText(fit(t.value, w - 20f), x + 10f, y + 44f, paint)
                style(8.5f, Font.REGULAR, MUTED)
                canvas.drawText(fit(t.note, w - 20f), x + 10f, y + 62f, paint)
            }
            y += h
        }

        /** One small bar per week, so a coach sees the trend at a glance. */
        fun scoreBars(label: String, scores: List<Int?>) {
            keepRoom(20f + scores.size * 16f)
            y += 11f
            style(10.5f, Font.MEDIUM, MUTED)
            canvas.drawText(label, MARGIN, y, paint)
            y += 4f
            val barX = MARGIN + 150f
            val barW = CONTENT_W - 150f - 40f
            scores.forEachIndexed { i, s ->
                style(9.5f, Font.REGULAR, MUTED)
                canvas.drawText("Week ${i + 1}", MARGIN + 10f, y + 10f, paint)
                fill.color = ZEBRA
                canvas.drawRoundRect(RectF(barX, y + 3f, barX + barW, y + 11f), 4f, 4f, fill)
                if (s != null) {
                    fill.color = GREEN
                    canvas.drawRoundRect(RectF(barX, y + 3f, barX + barW * (s.coerceIn(0, 100) / 100f), y + 11f), 4f, 4f, fill)
                    style(9.5f, Font.BOLD, INK)
                    canvas.drawText("$s", barX + barW + 8f, y + 10f, paint)
                } else {
                    style(9f, Font.REGULAR, MUTED)
                    canvas.drawText("too few meals logged", barX + 6f, y + 10f, paint)
                }
                y += 16f
            }
        }

        fun tableHeader(values: List<String>) {
            keepRoom(22f)
            fill.color = GREEN
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 20f), 6f, 6f, fill)
            style(9f, Font.BOLD, Color.WHITE)
            values.forEachIndexed { i, v -> canvas.drawText(v, MARGIN + 6f + columns[i].second, y + 13.5f, paint) }
            y += 22f
        }

        fun tableRow(values: List<String>, striped: Boolean, muted: Boolean, highlightKcal: Boolean = false) {
            if (y + 17f > FOOTER_TOP - 12f) {
                newPage()
                tableHeader(columns.map { it.first })
            }
            if (striped) {
                fill.color = ZEBRA
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 17f, fill)
            }
            values.forEachIndexed { i, v ->
                val color = when {
                    muted && i in 1..4 -> MUTED
                    i == 1 && highlightKcal -> AMBER
                    else -> INK
                }
                style(9f, if (i == 0) Font.MEDIUM else Font.REGULAR, color)
                val x = MARGIN + 6f + columns[i].second
                val end = if (v == NOT_LOGGED) columns[5].second else columns.getOrNull(i + 1)?.second ?: CONTENT_W
                val maxWidth = end - columns[i].second - 8f
                canvas.drawText(fit(v, maxWidth), x, y + 12f, paint)
            }
            y += 17f
        }

        fun dayBand(left: String, right: String) {
            fill.color = GREEN_SOFT
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 22f), 6f, 6f, fill)
            style(10.5f, Font.BOLD, INK)
            canvas.drawText(left, MARGIN + 8f, y + 15f, paint)
            style(9.5f, Font.MEDIUM, GREEN_DEEP)
            canvas.drawText(right, PAGE_W - MARGIN - 8f - paint.measureText(right), y + 15f, paint)
            y += 26f
        }

        fun mealLine(time: String, name: String, numbers: String) {
            style(9.5f, Font.MEDIUM, MUTED)
            val numW = paint.measureText(numbers)
            val nameX = MARGIN + 46f
            val nameW = CONTENT_W - 46f - numW - 16f
            val lines = wrap(name, 9.5f, Font.REGULAR, nameW)
            keepRoom(lines.size * 13f + 2f)
            y += 10f
            style(9.5f, Font.MEDIUM, MUTED)
            canvas.drawText(time, MARGIN + 8f, y, paint)
            canvas.drawText(numbers, PAGE_W - MARGIN - 8f - numW, y, paint)
            style(9.5f, Font.REGULAR, INK)
            lines.forEachIndexed { i, l ->
                if (i > 0) y += 13f
                canvas.drawText(l, nameX, y, paint)
            }
            y += 4f
        }

        fun wrapped(s: String, size: Float, color: Int = INK) {
            wrap(s, size, Font.REGULAR, CONTENT_W).forEach { text(it, size, color = color) }
        }

        private fun wrap(s: String, size: Float, font: Font, width: Float): List<String> {
            style(size, font)
            val lines = mutableListOf<String>()
            var line = ""
            for (word in s.split(" ")) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(next) > width && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }

        private fun fit(s: String, width: Float): String {
            if (paint.measureText(s) <= width) return s
            var cut = s
            while (cut.isNotEmpty() && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
            return "$cut…"
        }
    }
}
