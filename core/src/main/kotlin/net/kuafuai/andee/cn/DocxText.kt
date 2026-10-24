package net.kuafuai.andee.cn

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The text of a Word (.docx) file, with no library: a .docx is a zip whose body
 * is `word/document.xml`, and a CV needs paragraphs, tabs and line breaks out
 * of it, not layout. (GoWalkin fork.)
 */
object DocxText {

    /** Null when [bytes] is not a .docx (no `word/document.xml` inside). */
    fun extract(bytes: ByteArray, maxChars: Int = 40_000): String? {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == "word/document.xml" }
                ?.let { zip.readBytes().toString(Charsets.UTF_8) }
        } ?: return null
        return fromDocumentXml(xml).take(maxChars)
    }

    internal fun fromDocumentXml(xml: String): String {
        // Whitespace *between* tags is the file's own pretty-printing, never text
        // (text lives inside <w:t>); dropping it keeps one newline per paragraph.
        val withBreaks = xml
            .replace(Regex(">\\s+<"), "><")
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:tab\\s*/>"), "\t")
            .replace(Regex("<w:(br|cr)\\b[^>]*/>"), "\n")
        val text = withBreaks.replace(Regex("<[^>]+>"), "")
        return unescape(text)
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun unescape(s: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> runCatching {
                    val cp = if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()
                    String(Character.toChars(cp))
                }.getOrDefault(m.value)
            }
        }
}
