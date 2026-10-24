package net.kuafuai.andee.cn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxTextTest {

    private fun docx(documentXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<Types/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml")); z.write(documentXml.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun paragraphsTabsBreaksAndEntities() {
        val xml = """<?xml version="1.0"?><w:document><w:body>
            <w:p><w:r><w:t>Nouman Waris</w:t></w:r></w:p>
            <w:p><w:r><w:t>Skills:</w:t></w:r><w:r><w:tab/><w:t>Kotlin &amp; Android</w:t></w:r></w:p>
            <w:p><w:r><w:t>Line one</w:t><w:br/><w:t>Line two &#20013;&#25991;</w:t></w:r></w:p>
            </w:body></w:document>"""
        val text = DocxText.extract(docx(xml))!!
        assertEquals("Nouman Waris\nSkills:\tKotlin & Android\nLine one\nLine two 中文", text)
    }

    @Test
    fun notADocx() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write("x".toByteArray()); z.closeEntry() }
        assertNull(DocxText.extract(out.toByteArray()))
    }

    @Test
    fun toolsAreLocalOnlyAndRouted() {
        assertEquals(2, CnTools.TOOLS.length())
        for (i in 0 until CnTools.TOOLS.length()) assertTrue(CnTools.TOOLS.getJSONObject(i).getBoolean("localOnly"))
        assertEquals(CnTools.PICK, CnTools.methodOf("pick_document"))
        assertEquals(CnTools.READ, CnTools.methodOf("read_document"))
        assertNull(CnTools.methodOf("tap_screen_element"))
    }
}
