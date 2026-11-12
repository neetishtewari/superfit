package com.superfit.app.ui.report

import com.superfit.app.domain.CoachExport
import com.superfit.app.domain.CoachExportDay
import com.superfit.app.domain.CoachExportHeader
import com.superfit.app.domain.CoachExportMeal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class CoachReportPdfTest {
    /** Records what the layout draws; every character is 5 points wide. */
    private class FakeSurface : PdfSurface {
        val texts = mutableListOf<String>()
        val links = mutableListOf<Int>()
        var pages = 0
        var open = false

        override fun startPage(number: Int) {
            assertTrue(!open, "a page started before the last one finished")
            open = true
            pages = number
        }

        override fun finishPage() {
            open = false
        }

        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float) {}

        override fun drawText(text: String, x: Float, baseline: Float, size: Float, font: PdfFont, color: Int, letterSpacing: Float) {
            assertTrue(open)
            assertTrue(baseline < CoachReportPdf.PAGE_H, "text below the page: $text")
            texts += text
        }

        override fun measureText(text: String, size: Float, font: PdfFont, letterSpacing: Float): Float = text.length * 5f

        override fun drawLogo(left: Float, top: Float, size: Float) {}

        override fun link(url: String, left: Float, top: Float, right: Float, bottom: Float) {
            links += pages
        }
    }

    private val header = CoachExportHeader("Sam", "LOSE_WEIGHT", 34, 175.0, true, 82.0, 75.0)

    private fun day(date: LocalDate, meals: Int) = CoachExportDay(
        date = date,
        logged = meals >= 2,
        kcal = 1850,
        kcalTarget = 2000,
        proteinG = 110,
        carbsG = 200,
        fatG = 60,
        steps = 12345,
        workouts = listOf("Run 30 min"),
        weightKg = null,
        meals = List(meals) { CoachExportMeal("0${8 + it}:00", "dal rice and a long list of sides that needs wrapping onto more lines", 600, 30) }
    )

    private fun report(days: Int) = CoachExport(
        header = header,
        from = LocalDate(2026, 10, 15),
        to = LocalDate(2026, 11, 11),
        daysLogged = days,
        avgKcal = 1850,
        avgKcalTarget = 2000,
        avgProteinG = 110,
        proteinTargetG = 120,
        proteinPct = 92,
        avgCarbsG = 200,
        avgFatG = 60,
        workoutCount = 3,
        walkCount = 2,
        workoutMinutes = 150,
        avgSteps = 12345,
        weightStart = LocalDate(2026, 10, 16) to 82.0,
        weightEnd = LocalDate(2026, 11, 10) to 80.6,
        dietScores = listOf(70, null, 75, 80),
        days = List(days) { day(LocalDate(2026, 10, 15).plus(DatePeriod(days = it)), 3) }
    )

    @Test
    fun everyPageHasTheStoreLinkAndPagesNeverOverlap() {
        val surface = FakeSurface()
        CoachReportPdf.draw(report(28), surface, CoachReportPdf.GOOGLE_PLAY)
        assertTrue(surface.pages >= 3)
        assertEquals((1..surface.pages).toList(), surface.links)
        assertTrue(!surface.open)
    }

    @Test
    fun wordsAndNumbersMatchAndroid() {
        val surface = FakeSurface()
        CoachReportPdf.draw(report(14), surface, CoachReportPdf.GOOGLE_PLAY)
        val all = surface.texts
        assertTrue("15 Oct 2026 to 11 Nov 2026" in all)
        assertTrue("Thu 15 Oct" in all)
        assertTrue("12,345" in all)
        assertTrue("-1.4 kg" in all)
        assertTrue("Get it on Google Play ›" in all)
        assertTrue(all.any { it == "1.4 kg down since start" || it.startsWith("1.4 kg down") })
    }

    @Test
    fun aStoreWithoutALinkShowsJustItsName() {
        val surface = FakeSurface()
        CoachReportPdf.draw(report(2), surface, StoreLink("Download on the App Store", ""))
        assertTrue("Download on the App Store" in surface.texts)
        assertEquals(emptyList<Int>(), surface.links)
    }

    @Test
    fun anotherStoreCanBeNamed() {
        val surface = FakeSurface()
        CoachReportPdf.draw(report(2), surface, StoreLink("Get Superfit", "https://example.com"))
        assertTrue("Get Superfit ›" in surface.texts)
    }

    @Test
    fun datesInEnglish() {
        assertEquals("Wed 4 Nov", CoachReportPdf.shortDate(LocalDate(2026, 11, 4)))
        assertEquals("4 Nov 2026", CoachReportPdf.longDate(LocalDate(2026, 11, 4)))
    }
}
