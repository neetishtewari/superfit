package com.superfit.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.CoachExport
import com.superfit.app.domain.CoachExportBuilder
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a [CoachExport] as an A4 PDF: a summary page, a day-by-day table, then each day's meals.
 * Made on the phone and only leaves it through the share sheet.
 */
object CoachExportPdf {

    private const val PAGE_W = 595 // A4 in PDF points
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val FOOTER = "Made with Superfit · Get it on Google Play"

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val rangeFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /** Writes the PDF to the app's share cache (replacing the last share) and opens the share sheet. */
    suspend fun share(context: Context, report: CoachExport) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shares").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, "superfit-report-${report.from}-to-${report.to}.pdf").also { write(report, it) }
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
        context.startActivity(Intent.createChooser(send, "Share with your coach"))
    }

    fun write(report: CoachExport, file: File) {
        val doc = PdfDocument()
        val pen = Pen(doc)
        summary(pen, report)
        dayTable(pen, report)
        mealPages(pen, report)
        pen.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun summary(pen: Pen, r: CoachExport) {
        val h = r.header
        pen.text("Superfit report", 22f, bold = true)
        pen.gap(4f)
        pen.text("${r.from.format(rangeFormat)} to ${r.to.format(rangeFormat)} · ${r.daysLogged} of ${r.totalDays} days logged", 11f, grey = true)
        pen.gap(14f)

        pen.heading("About")
        h.name?.let { pen.row("Name", it) }
        pen.row("Goal", CoachExportBuilder.goalLabel(h.goal))
        pen.row("Start / goal weight", "${kg(h.startKg)} / ${kg(h.targetKg)}")
        pen.row("Age, height, sex", "${h.age} · ${h.heightCm.toInt()} cm · ${if (h.isMale) "Male" else "Female"}")
        pen.gap(10f)

        pen.heading("Eating (average of logged days)")
        if (r.daysLogged == 0) {
            pen.text("No fully logged days in this period.", 11f, grey = true)
        } else {
            pen.row("Calories", "${r.avgKcal} kcal a day (target about ${r.avgKcalTarget})")
            pen.row("Protein", "${r.avgProteinG} g a day, ${r.proteinPct ?: 0}% of the ${r.proteinTargetG} g goal")
            pen.row("Carbs / fat", "${r.avgCarbsG} g / ${r.avgFatG} g a day")
        }
        val scores = r.dietScores.mapIndexed { i, s -> "week ${i + 1}: ${s?.toString() ?: "too few meals"}" }
        if (r.dietScores.any { it != null }) pen.row("Diet Quality (out of 100)", scores.joinToString(", "))
        pen.gap(10f)

        pen.heading("Activity")
        pen.row("Workouts", "${r.workoutCount}${if (r.workoutMinutes > 0) ", ${r.workoutMinutes} min in total" else ""}")
        r.avgSteps?.let { pen.row("Steps", "$it a day on average") }
        pen.gap(10f)

        pen.heading("Weight")
        val start = r.weightStart
        val end = r.weightEnd
        when {
            start == null -> pen.text("No weigh-ins in this period.", 11f, grey = true)
            end == null -> pen.row("Weigh-in", "${kg(start.second)} on ${start.first.format(dayFormat)}")
            else -> {
                val change = end.second - start.second
                pen.row("Start", "${kg(start.second)} on ${start.first.format(dayFormat)}")
                pen.row("End", "${kg(end.second)} on ${end.first.format(dayFormat)}")
                pen.row("Change", "${if (change > 0) "+" else ""}${kg(change)}")
            }
        }
        r.body?.let { b ->
            b.bodyFatPct?.let { pen.row("Body fat", "${BodyReadings.fmt(it)}%" + (b.bodyFatChange?.let { c -> " (${if (c > 0) "+" else ""}${BodyReadings.fmt(c)} pts over 4 weeks)" } ?: "")) }
            b.leanKg?.let { pen.row("Lean mass", "${BodyReadings.fmt(it)} kg") }
            b.source?.let { pen.row("Measured by", it) }
            b.insight?.let { pen.wrapped(it, 10f, indent = 0f) }
        }
    }

    private val columns = listOf("Day" to 0f, "kcal" to 90f, "Protein" to 140f, "Carbs" to 195f, "Fat" to 245f, "Steps" to 290f, "Weight" to 345f, "Workouts" to 400f)

    private fun dayTable(pen: Pen, r: CoachExport) {
        pen.newPage()
        pen.text("Day by day", 16f, bold = true)
        pen.gap(8f)
        pen.cells(columns.map { it.first }, bold = true)
        r.days.forEach { d ->
            val values = if (d.logged || d.meals.isNotEmpty()) {
                listOf("${d.kcal}", "${d.proteinG} g", "${d.carbsG} g", "${d.fatG} g")
            } else {
                listOf("not logged", "", "", "")
            }
            val firstWorkout = d.workouts.firstOrNull() ?: ""
            pen.cells(
                listOf(d.date.format(dayFormat)) + values +
                    listOf(if (d.steps > 0) "${d.steps}" else "", d.weightKg?.let { kg(it) } ?: "", firstWorkout),
                grey = !d.logged
            )
            d.workouts.drop(1).forEach { w -> pen.cells(List(columns.size - 1) { "" } + w) }
        }
        pen.gap(6f)
        pen.text("Days with fewer than two meals logged are greyed out; they aren't counted in averages.", 9f, grey = true)
    }

    private fun mealPages(pen: Pen, r: CoachExport) {
        pen.newPage()
        pen.text("Meals", 16f, bold = true)
        r.days.filter { it.meals.isNotEmpty() }.forEach { d ->
            pen.gap(8f)
            pen.keepRoom(40f)
            pen.text("${d.date.format(dayFormat)} · ${d.kcal} kcal · ${d.proteinG} g protein", 11f, bold = true)
            d.meals.forEach { m -> pen.wrapped("${m.time}  ${m.name}  (${m.kcal} kcal, ${m.proteinG} g protein)", 10f, indent = 10f) }
        }
    }

    private fun kg(value: Double) = String.format(Locale.US, "%.1f kg", value)

    /** Writes lines down the page, starting a new page (with the footer) when one fills up. */
    private class Pen(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            newPage()
        }

        fun newPage() {
            page?.let { closePage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun finish() {
            page?.let { closePage(it) }
            page = null
        }

        private fun closePage(p: PdfDocument.Page) {
            style(8f, bold = false, grey = true)
            canvas.drawText("$FOOTER · page $pageNo", MARGIN, PAGE_H - 20f, paint)
            doc.finishPage(p)
        }

        fun keepRoom(height: Float) {
            if (y + height > PAGE_H - 50f) newPage()
        }

        fun gap(height: Float) {
            y += height
        }

        private fun style(size: Float, bold: Boolean, grey: Boolean) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = if (grey) Color.rgb(120, 120, 120) else Color.rgb(20, 20, 20)
        }

        fun text(s: String, size: Float, bold: Boolean = false, grey: Boolean = false, x: Float = MARGIN) {
            keepRoom(size * 1.5f)
            style(size, bold, grey)
            y += size
            canvas.drawText(s, x, y, paint)
            y += size * 0.5f
        }

        fun heading(s: String) {
            keepRoom(40f)
            text(s.uppercase(Locale.US), 10f, bold = true, grey = true)
            gap(2f)
        }

        fun row(label: String, value: String) {
            keepRoom(16f)
            style(11f, bold = false, grey = true)
            y += 11f
            canvas.drawText(label, MARGIN, y, paint)
            style(11f, bold = false, grey = false)
            canvas.drawText(value, MARGIN + 150f, y, paint)
            y += 5f
        }

        fun cells(values: List<String>, bold: Boolean = false, grey: Boolean = false) {
            keepRoom(14f)
            style(9f, bold, grey)
            y += 9f
            values.forEachIndexed { i, v ->
                val x = MARGIN + columns[i].second
                val maxWidth = (columns.getOrNull(i + 1)?.second ?: (PAGE_W - 2 * MARGIN)) - columns[i].second - 4f
                canvas.drawText(fit(v, maxWidth), x, y, paint)
            }
            y += 5f
        }

        fun wrapped(s: String, size: Float, indent: Float) {
            style(size, bold = false, grey = false)
            val width = PAGE_W - 2 * MARGIN - indent
            var line = ""
            for (word in s.split(" ")) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(next) > width && line.isNotEmpty()) {
                    text(line, size, x = MARGIN + indent)
                    line = word
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) text(line, size, x = MARGIN + indent)
        }

        private fun fit(s: String, width: Float): String {
            if (paint.measureText(s) <= width) return s
            var cut = s
            while (cut.isNotEmpty() && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
            return "$cut…"
        }
    }
}
