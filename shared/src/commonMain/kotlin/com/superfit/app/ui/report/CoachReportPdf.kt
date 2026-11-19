package com.superfit.app.ui.report

import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.CoachExport
import com.superfit.app.domain.CoachExportBuilder
import com.superfit.app.domain.Decimals
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** The four weights of Manrope the report uses. */
enum class PdfFont { REGULAR, MEDIUM, BOLD, EXTRA_BOLD }

/**
 * Where the report is drawn: Android's PdfDocument or the iPhone's PDF renderer. Positions are PDF
 * points from the top left of an A4 page; colours are ARGB; letter spacing is in ems, like Android.
 */
interface PdfSurface {
    fun startPage(number: Int)
    fun finishPage()
    fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float)
    fun drawText(text: String, x: Float, baseline: Float, size: Float, font: PdfFont, color: Int, letterSpacing: Float)
    fun measureText(text: String, size: Float, font: PdfFont, letterSpacing: Float): Float

    /** The app icon with rounded corners, as it looks on a phone. */
    fun drawLogo(left: Float, top: Float, size: Float)

    /** Makes the area a tappable link to [url] on the current page. */
    fun link(url: String, left: Float, top: Float, right: Float, bottom: Float)
}

/**
 * The "Share my report" PDF, laid out once for both apps: a branded summary page with headline
 * numbers, a day-by-day table, then each day's meals. Every page carries the Superfit header and
 * footer, because coaches who receive it are people we'd like to hear about Superfit.
 */
object CoachReportPdf {
    const val PAGE_W = 595 // A4 in PDF points
    const val PAGE_H = 842
    const val MARGIN = 40f
    const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val BAND_H = 64f // green header band on later pages
    const val FOOTER_TOP = PAGE_H - 50f

    val GOOGLE_PLAY = StoreLink.GOOGLE_PLAY

    /** This phone's store, for the footer (Swift can't use Kotlin's default arguments). */
    val store: StoreLink get() = ThisAppStore

    // Superfit "Fresh Green" colours, matching the app.
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GREEN = 0xFF0E9F75.toInt()
    private const val GREEN_DEEP = 0xFF087A59.toInt()
    private const val GREEN_SOFT = 0xFFE6F5EF.toInt()
    private const val INK = 0xFF14201B.toInt()
    private const val MUTED = 0xFF5B6B64.toInt()
    private const val LINE = 0xFFE3E8E6.toInt()
    private const val ZEBRA = 0xFFF6F8F7.toInt()
    private const val AMBER = 0xFFB26B00.toInt()

    /** "12 Oct 2026 to 8 Nov 2026", for the header, the file's subject and the share text. */
    fun range(report: CoachExport): String = "${longDate(report.from)} to ${longDate(report.to)}"

    fun fileName(report: CoachExport): String = "superfit-report-${report.from}-to-${report.to}.pdf"

    /** Draws the whole report on [surface]. */
    fun draw(report: CoachExport, surface: PdfSurface, store: StoreLink = ThisAppStore) {
        val pen = Pen(surface, range(report), store)
        summary(pen, report)
        dayTable(pen, report)
        mealPages(pen, report)
        pen.finish()
    }

    private fun summary(pen: Pen, r: CoachExport) {
        val h = r.header
        pen.coverHeader()
        pen.text(h.name?.takeIf { it.isNotBlank() } ?: "${CoachExportBuilder.goalLabel(h.goal)} · ${kg(h.startKg)} → ${kg(h.targetKg)}", 24f, font = PdfFont.EXTRA_BOLD)
        pen.gap(2f)
        pen.text("${r.daysLogged} of ${r.totalDays} days fully logged", 11f, color = MUTED)
        pen.gap(14f)

        // Headline numbers a coach looks at first.
        val change = r.weightStart?.let { s -> r.weightEnd?.let { e -> e.second - s.second } }
        pen.tiles(
            listOf(
                Tile("Calories a day", r.avgKcal?.let { num(it) } ?: "–", if (r.daysLogged > 0) "from ${r.daysLogged} logged ${if (r.daysLogged == 1) "day" else "days"}" else "not enough logged"),
                Tile("Protein goal", r.proteinPct?.let { "$it%" } ?: "–", r.avgProteinG?.let { "$it g of ${r.proteinTargetG} g a day" } ?: "not enough logged"),
                Tile("Workouts", "${r.workoutCount}", if (r.walkCount > 0) "plus ${r.walkCount} walks" else "in this period"),
                Tile(
                    "Weight, this period",
                    change?.let { signedKg(it) } ?: r.weightStart?.let { kg(it.second) } ?: "–",
                    sinceStart(h.startKg, (r.weightEnd ?: r.weightStart)?.second)
                        ?: if (change != null) "${kg(r.weightStart!!.second)} → ${kg(r.weightEnd!!.second)}" else "too few weigh-ins"
                )
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
        r.avgSteps?.let { pen.row("Steps", "${num(it)} a day on average") }
        pen.gap(12f)

        pen.heading("Weight and body")
        val start = r.weightStart
        val end = r.weightEnd
        when {
            start == null -> pen.text("No weigh-ins in this period.", 11f, color = MUTED)
            end == null -> pen.row("Weigh-in", "${kg(start.second)} on ${shortDate(start.first)}")
            else -> {
                pen.row("First weigh-in", "${kg(start.second)} on ${shortDate(start.first)}")
                pen.row("Latest weigh-in", "${kg(end.second)} on ${shortDate(end.first)}")
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
                listOf(shortDate(d.date)) + values +
                    listOf(if (d.steps > 0) num(d.steps) else "", d.weightKg?.let { kg(it) } ?: "", firstWorkout),
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
            pen.dayBand(shortDate(d.date), "${num(d.kcal)} kcal · ${d.proteinG} g protein")
            d.meals.forEach { m -> pen.mealLine(m.time, m.name.replaceFirstChar { it.uppercase() }, "${m.kcal} kcal · ${m.proteinG} g") }
            pen.gap(8f)
        }
    }

    private const val NOT_LOGGED = "Food not logged"

    private fun num(value: Int) = Decimals.grouped(value.toLong())

    private fun duration(minutes: Long) = if (minutes < 120) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

    /** "4.1 kg down since start": the whole journey, next to this period's change. */
    private fun sinceStart(startKg: Double, latestKg: Double?): String? {
        if (latestKg == null || startKg <= 0) return null
        val diff = latestKg - startKg
        if (abs(diff) < 0.1) return "same as at the start"
        return "${Decimals.one(abs(diff))} kg ${if (diff < 0) "down" else "up"} since start"
    }

    private fun kg(value: Double) = "${Decimals.one(value)} kg"
    private fun signedKg(value: Double) = (if (value > 0) "+" else "") + kg(value)

    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Wed 4 Nov" (EEE d MMM, English). */
    fun shortDate(d: LocalDate): String = "${DAYS[d.dayOfWeek.ordinal]} ${d.day} ${MONTHS[d.month.ordinal]}"

    /** "4 Nov 2026" (d MMM yyyy, English). */
    fun longDate(d: LocalDate): String = "${d.day} ${MONTHS[d.month.ordinal]} ${d.year}"

    private data class Tile(val label: String, val value: String, val note: String)

    /** Writes down the page, starting a new branded page when one fills up. */
    private class Pen(private val surface: PdfSurface, private val range: String, private val store: StoreLink) {
        private var pageNo = 0
        private var open = false
        private var y = 0f

        // The current text style, as Android's Paint keeps it.
        private var size = 10f
        private var font = PdfFont.REGULAR
        private var color = INK
        private var spacing = 0f

        init {
            newPage(cover = true)
        }

        fun newPage(cover: Boolean = false) {
            if (open) closePage()
            pageNo++
            surface.startPage(pageNo)
            open = true
            if (cover) {
                y = MARGIN
            } else {
                smallHeader()
                y = BAND_H + 24f
            }
        }

        fun finish() {
            if (open) closePage()
            open = false
        }

        private fun style(size: Float, font: PdfFont = PdfFont.REGULAR, color: Int = INK) {
            this.size = size
            this.font = font
            this.color = color
            spacing = 0f
        }

        private fun draw(value: String, x: Float, baseline: Float) = surface.drawText(value, x, baseline, size, font, color, spacing)
        private fun measure(value: String) = surface.measureText(value, size, font, spacing)

        private fun wordmark(x: Float, baseline: Float, size: Float) {
            style(size, PdfFont.EXTRA_BOLD, WHITE)
            spacing = 0.12f
            draw("SUPERFIT", x, baseline)
            spacing = 0f
        }

        /** Tall green band on page 1: logo, name and what this document is. */
        fun coverHeader() {
            val bandH = 118f
            surface.fillRect(0f, 0f, PAGE_W.toFloat(), bandH, GREEN, 0f)
            surface.drawLogo(MARGIN, 30f, 40f)
            wordmark(MARGIN + 52f, 57f, 18f)
            style(10f, PdfFont.MEDIUM, 0xE6FFFFFF.toInt())
            draw("The simplest app to crush your fitness goals", MARGIN + 52f, 72f)
            style(9f, PdfFont.BOLD, 0xDCFFFFFF.toInt())
            spacing = 0.08f
            val label = "PROGRESS REPORT"
            draw(label, PAGE_W - MARGIN - measure(label), 50f)
            spacing = 0f
            style(13f, PdfFont.BOLD, WHITE)
            draw(range, PAGE_W - MARGIN - measure(range), 68f)
            y = bandH + 30f
        }

        /** Slim green band on later pages. */
        private fun smallHeader() {
            surface.fillRect(0f, 0f, PAGE_W.toFloat(), BAND_H, GREEN, 0f)
            surface.drawLogo(MARGIN, 18f, 28f)
            wordmark(MARGIN + 38f, 38f, 13f)
            style(10f, PdfFont.MEDIUM, WHITE)
            val t = "Progress report · $range"
            draw(t, PAGE_W - MARGIN - measure(t), 37f)
        }

        private fun closePage() {
            surface.fillRect(MARGIN, FOOTER_TOP, PAGE_W - MARGIN, FOOTER_TOP + 1.2f, GREEN, 0f)
            style(8.5f, PdfFont.BOLD, GREEN_DEEP)
            draw("Made with Superfit", MARGIN, FOOTER_TOP + 16f)
            val bw = measure("Made with Superfit")
            style(8.5f, PdfFont.REGULAR, MUTED)
            draw(" · Effortless one-tap logging of meals and workouts", MARGIN + bw, FOOTER_TOP + 16f)
            val num = "$pageNo"
            draw(num, PAGE_W - MARGIN - measure(num), FOOTER_TOP + 16f)
            // A short green link label, made tappable.
            style(9f, PdfFont.BOLD, GREEN)
            if (store.url.isBlank()) {
                draw(store.label, MARGIN, FOOTER_TOP + 30f)
            } else {
                val label = "${store.label} ›"
                draw(label, MARGIN, FOOTER_TOP + 30f)
                val w = measure(label)
                surface.fillRect(MARGIN, FOOTER_TOP + 32f, MARGIN + w, FOOTER_TOP + 32.8f, GREEN, 0f)
                surface.link(store.url, MARGIN - 2f, FOOTER_TOP + 19f, MARGIN + w + 2f, FOOTER_TOP + 36f)
            }
            surface.finishPage()
        }

        fun keepRoom(height: Float) {
            if (y + height > FOOTER_TOP - 12f) newPage()
        }

        fun gap(height: Float) {
            y += height
        }

        fun text(value: String, size: Float, font: PdfFont = PdfFont.REGULAR, color: Int = INK, x: Float = MARGIN) {
            keepRoom(size * 1.5f)
            style(size, font, color)
            y += size
            draw(value, x, y)
            y += size * 0.45f
        }

        fun title(value: String) {
            text(value, 20f, PdfFont.EXTRA_BOLD)
            gap(10f)
        }

        /** Green small-caps label with a hairline, starting each section. */
        fun heading(value: String) {
            keepRoom(44f)
            style(9.5f, PdfFont.BOLD, GREEN_DEEP)
            spacing = 0.08f
            y += 10f
            draw(value.uppercase(), MARGIN, y)
            spacing = 0f
            y += 6f
            surface.fillRect(MARGIN, y, PAGE_W - MARGIN, y + 0.8f, LINE, 0f)
            y += 6f
        }

        fun row(label: String, value: String) {
            keepRoom(18f)
            y += 11f
            style(10.5f, PdfFont.MEDIUM, MUTED)
            draw(label, MARGIN, y)
            style(10.5f, PdfFont.REGULAR, INK)
            draw(fit(value, CONTENT_W - 150f), MARGIN + 150f, y)
            y += 6f
        }

        fun tiles(items: List<Tile>) {
            val gapX = 10f
            val w = (CONTENT_W - gapX * (items.size - 1)) / items.size
            val h = 74f
            keepRoom(h)
            items.forEachIndexed { i, t ->
                val x = MARGIN + i * (w + gapX)
                surface.fillRect(x, y, x + w, y + h, GREEN_SOFT, 10f)
                style(8.5f, PdfFont.BOLD, GREEN_DEEP)
                draw(fit(t.label, w - 20f), x + 10f, y + 18f)
                style(20f, PdfFont.EXTRA_BOLD, INK)
                draw(fit(t.value, w - 20f), x + 10f, y + 44f)
                style(8.5f, PdfFont.REGULAR, MUTED)
                draw(fit(t.note, w - 20f), x + 10f, y + 62f)
            }
            y += h
        }

        /** One small bar per week, so a coach sees the trend at a glance. */
        fun scoreBars(label: String, scores: List<Int?>) {
            keepRoom(20f + scores.size * 16f)
            y += 11f
            style(10.5f, PdfFont.MEDIUM, MUTED)
            draw(label, MARGIN, y)
            y += 4f
            val barX = MARGIN + 150f
            val barW = CONTENT_W - 150f - 40f
            scores.forEachIndexed { i, score ->
                style(9.5f, PdfFont.REGULAR, MUTED)
                draw("Week ${i + 1}", MARGIN + 10f, y + 10f)
                surface.fillRect(barX, y + 3f, barX + barW, y + 11f, ZEBRA, 4f)
                if (score != null) {
                    surface.fillRect(barX, y + 3f, barX + barW * (score.coerceIn(0, 100) / 100f), y + 11f, GREEN, 4f)
                    style(9.5f, PdfFont.BOLD, INK)
                    draw("$score", barX + barW + 8f, y + 10f)
                } else {
                    style(9f, PdfFont.REGULAR, MUTED)
                    draw("too few meals logged", barX + 6f, y + 10f)
                }
                y += 16f
            }
        }

        fun tableHeader(values: List<String>) {
            keepRoom(22f)
            surface.fillRect(MARGIN, y, PAGE_W - MARGIN, y + 20f, GREEN, 6f)
            style(9f, PdfFont.BOLD, WHITE)
            values.forEachIndexed { i, v -> draw(v, MARGIN + 6f + columns[i].second, y + 13.5f) }
            y += 22f
        }

        fun tableRow(values: List<String>, striped: Boolean, muted: Boolean, highlightKcal: Boolean = false) {
            if (y + 17f > FOOTER_TOP - 12f) {
                newPage()
                tableHeader(columns.map { it.first })
            }
            if (striped) surface.fillRect(MARGIN, y, PAGE_W - MARGIN, y + 17f, ZEBRA, 0f)
            values.forEachIndexed { i, v ->
                val c = when {
                    muted && i in 1..4 -> MUTED
                    i == 1 && highlightKcal -> AMBER
                    else -> INK
                }
                style(9f, if (i == 0) PdfFont.MEDIUM else PdfFont.REGULAR, c)
                val x = MARGIN + 6f + columns[i].second
                val end = if (v == NOT_LOGGED) columns[5].second else columns.getOrNull(i + 1)?.second ?: CONTENT_W
                val maxWidth = end - columns[i].second - 8f
                draw(fit(v, maxWidth), x, y + 12f)
            }
            y += 17f
        }

        fun dayBand(left: String, right: String) {
            surface.fillRect(MARGIN, y, PAGE_W - MARGIN, y + 22f, GREEN_SOFT, 6f)
            style(10.5f, PdfFont.BOLD, INK)
            draw(left, MARGIN + 8f, y + 15f)
            style(9.5f, PdfFont.MEDIUM, GREEN_DEEP)
            draw(right, PAGE_W - MARGIN - 8f - measure(right), y + 15f)
            y += 26f
        }

        fun mealLine(time: String, name: String, numbers: String) {
            style(9.5f, PdfFont.MEDIUM, MUTED)
            val numW = measure(numbers)
            val nameX = MARGIN + 46f
            val nameW = CONTENT_W - 46f - numW - 16f
            val lines = wrap(name, 9.5f, PdfFont.REGULAR, nameW)
            keepRoom(lines.size * 13f + 2f)
            y += 10f
            style(9.5f, PdfFont.MEDIUM, MUTED)
            draw(time, MARGIN + 8f, y)
            draw(numbers, PAGE_W - MARGIN - 8f - numW, y)
            style(9.5f, PdfFont.REGULAR, INK)
            lines.forEachIndexed { i, l ->
                if (i > 0) y += 13f
                draw(l, nameX, y)
            }
            y += 4f
        }

        fun wrapped(value: String, size: Float, color: Int = INK) {
            wrap(value, size, PdfFont.REGULAR, CONTENT_W).forEach { text(it, size, color = color) }
        }

        private fun wrap(value: String, size: Float, font: PdfFont, width: Float): List<String> {
            style(size, font)
            val lines = mutableListOf<String>()
            var line = ""
            for (word in value.split(" ")) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (measure(next) > width && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }

        private fun fit(value: String, width: Float): String {
            if (measure(value) <= width) return value
            var cut = value
            while (cut.isNotEmpty() && measure("$cut…") > width) cut = cut.dropLast(1)
            return "$cut…"
        }
    }
}
