package com.superfit.app.ui.share

import java.io.File

/**
 * Adds a tappable web link to every page of a PDF that Android's PdfDocument wrote. PdfDocument
 * can draw text but not links, so this appends a standard "incremental update" to the file: one
 * link annotation per page plus updated page objects, a new cross-reference table and trailer.
 * The original bytes stay untouched, so a viewer that ignores the update still shows the page.
 */
object PdfLinks {

    /** [rect] is left, bottom, right, top in PDF points (origin at the bottom-left of the page). */
    fun addLinkToEveryPage(file: File, url: String, rect: FloatArray) {
        val bytes = file.readBytes()
        val update = buildUpdate(bytes, url, rect) ?: return
        file.appendBytes(update)
    }

    /** The bytes to append, or null when the PDF isn't laid out the way we expect (then it's left as is). */
    fun buildUpdate(bytes: ByteArray, url: String, rect: FloatArray): ByteArray? {
        // ISO-8859-1 maps every byte to one char, so string offsets are byte offsets.
        val text = String(bytes, Charsets.ISO_8859_1)
        val startXref = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val trailer = text.lastIndexOf("trailer").takeIf { it >= 0 }?.let { text.substring(it) } ?: return null
        val size = Regex("""/Size\s+(\d+)""").find(trailer)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val root = Regex("""/Root\s+(\d+\s+\d+\s+R)""").find(trailer)?.groupValues?.get(1) ?: return null
        val info = Regex("""/Info\s+(\d+\s+\d+\s+R)""").find(trailer)?.groupValues?.get(1)

        val pages = Regex("""(\d+)\s+0\s+obj\s*(<<.*?)\s*endobj""", RegexOption.DOT_MATCHES_ALL).findAll(text)
            .filter { Regex("""/Type\s*/Page(?![a-zA-Z])""").containsMatchIn(it.groupValues[2]) && !it.groupValues[2].contains("/Annots") }
            .map { it.groupValues[1].toInt() to it.groupValues[2] }
            .toList()
        if (pages.isEmpty()) return null

        val safeUrl = url.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val box = rect.joinToString(" ") { String.format(java.util.Locale.US, "%.1f", it) }
        val out = StringBuilder()
        val offsets = sortedMapOf<Int, Long>()
        var nextId = size
        fun emit(id: Int, body: String) {
            offsets[id] = bytes.size.toLong() + out.length
            out.append(id).append(" 0 obj\n").append(body).append("\nendobj\n")
        }
        out.append('\n')
        pages.forEach { (pageId, dict) ->
            val annotId = nextId++
            emit(annotId, "<< /Type /Annot /Subtype /Link /Rect [$box] /Border [0 0 0] /A << /S /URI /URI ($safeUrl) >> >>")
            emit(pageId, dict.replaceFirst("<<", "<< /Annots [$annotId 0 R]"))
        }

        val xrefAt = bytes.size.toLong() + out.length
        out.append("xref\n")
        offsets.forEach { (id, offset) ->
            out.append(id).append(" 1\n").append(String.format(java.util.Locale.US, "%010d 00000 n \n", offset))
        }
        out.append("trailer\n<< /Size ").append(nextId).append(" /Root ").append(root)
        info?.let { out.append(" /Info ").append(it) }
        out.append(" /Prev ").append(startXref).append(" >>\nstartxref\n").append(xrefAt).append("\n%%EOF\n")
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
