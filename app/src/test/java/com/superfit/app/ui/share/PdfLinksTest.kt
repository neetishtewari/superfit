package com.superfit.app.ui.share

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLinksTest {

    private fun minimalPdf(): ByteArray {
        val parts = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = parts.map { part -> sb.length.also { sb.append(part) } }
        val xref = sb.length
        sb.append("xref\n0 4\n0000000000 65535 f \n")
        offsets.forEach { sb.append(String.format("%010d 00000 n \n", it)) }
        sb.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun addsALinkAnnotationToEachPageAndChainsTheXref() {
        val pdf = minimalPdf()
        val update = PdfLinks.buildUpdate(pdf, "https://example.com/a?b=c", floatArrayOf(40f, 10f, 555f, 24f))
        assertNotNull(update)
        val text = String(update!!, Charsets.ISO_8859_1)
        assertTrue(text.contains("4 0 obj\n<< /Type /Annot /Subtype /Link /Rect [40.0 10.0 555.0 24.0]"))
        assertTrue(text.contains("/URI (https://example.com/a?b=c)"))
        assertTrue(text.contains("3 0 obj\n<< /Annots [4 0 R] /Type /Page"))
        assertTrue(text.contains("/Size 5 /Root 1 0 R /Prev "))
        // Each xref offset must point at its object in the combined file.
        val whole = String(pdf + update, Charsets.ISO_8859_1)
        Regex("""(\d+) 1\n(\d{10}) 00000 n""").findAll(text).forEach { m ->
            assertTrue(whole.startsWith("${m.groupValues[1]} 0 obj", m.groupValues[2].toInt()))
        }
    }

    @Test
    fun leavesAnUnexpectedFileAlone() {
        assertNull(PdfLinks.buildUpdate("not a pdf".toByteArray(), "https://example.com", floatArrayOf(0f, 0f, 1f, 1f)))
    }
}
