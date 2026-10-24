package net.kuafuai.andee.cn

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * `pick_document` / `read_document`: the user says where a document is, the
 * brain opens the system picker, picks it, and reads it. (GoWalkin fork.)
 *
 * No storage permission: the Storage Access Framework hands back a grant for
 * exactly the one file the picker returned, which is also the honest scope —
 * the brain sees the CV it was asked to read and nothing else in Downloads.
 *
 * **Two calls, not one, because tool calls run one at a time.** A
 * `pick_document` that waited for the choice would hold the only thread the
 * brain has, and the brain could not then tap the file in the picker. So the
 * first call opens the picker and returns, the brain (or the user) chooses,
 * and the second call reads whatever was chosen.
 *
 * Reading: Word and text become text ([DocxText] needs no library); a PDF
 * becomes pictures rendered with the platform's own [PdfRenderer] — no PDF
 * text library, and a vision model reads a rendered CV's layout better than an
 * extracted text stream anyway. Two pages per picture and two pictures per call,
 * because `LocalBrain` attaches at most two images from one tool result.
 */
object DocumentImport {

    private const val TAG = "Body"
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_TEXT = 40_000
    private const val PAGE_WIDTH_PX = 900
    private const val PAGES_PER_IMAGE = 2
    private const val IMAGES_PER_CALL = 2
    private const val IMAGE_LONG_SIDE = 1600

    val MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain", "text/markdown",
        "image/*",
    )

    private class Picked(val uri: Uri, val name: String, val mime: String)

    @Volatile private var last: Picked? = null
    @Volatile private var pickerOpen = false
    @Volatile private var lastProblem: String? = null

    fun pick(context: Context, folder: String?): JSONObject {
        pickerOpen = true
        lastProblem = null
        val i = Intent(context, DocumentPickActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DocumentPickActivity.EXTRA_FOLDER, folder ?: "downloads")
        context.startActivity(i)
        return JSONObject()
            .put("picker", "open")
            .put(
                "next",
                "The system file picker is opening. Find the file in it (get_screen_element; tap folders, then the " +
                    "file by its name; use the picker's search if the list is long) or ask the user to tap it. " +
                    "When it has closed, call read_document.",
            )
    }

    /** From [DocumentPickActivity]: what was chosen, or null and why not. */
    fun onPicked(context: Context, uri: Uri?, problem: String?) {
        pickerOpen = false
        lastProblem = problem
        if (uri == null) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val cr = context.contentResolver
        val name = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "document"
        last = Picked(uri, name, cr.getType(uri) ?: guessMime(name))
        Log.i(TAG, "document picked: $name (${last?.mime})")
    }

    fun read(context: Context, startPage: Int): JSONObject {
        val p = last ?: throw IllegalStateException(
            when {
                pickerOpen -> "The picker is still open: choose the file in it, then call read_document again."
                lastProblem != null -> "No document: $lastProblem. Call pick_document again, or ask the user where the file is."
                else -> "No document has been picked yet. Call pick_document first."
            },
        )
        val out = JSONObject().put("name", p.name).put("mime", p.mime)
        val lower = p.name.lowercase()
        when {
            p.mime == "application/pdf" || lower.endsWith(".pdf") -> readPdf(context, p.uri, startPage.coerceAtLeast(1), out)
            p.mime.contains("wordprocessingml") || lower.endsWith(".docx") -> {
                val text = DocxText.extract(readBytes(context, p.uri))
                    ?: throw IllegalStateException("${p.name} is not a readable .docx file. Ask the user for a PDF or .docx.")
                out.put("text", text)
            }
            p.mime.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md") ->
                out.put("text", readBytes(context, p.uri).toString(Charsets.UTF_8).take(MAX_TEXT))
            p.mime.startsWith("image/") -> out.put("image", JSONObject().put("png_base64", readImage(context, p.uri)))
            lower.endsWith(".doc") -> throw IllegalStateException(
                "${p.name} is an old Word .doc file, which cannot be read here. Ask the user to save it as PDF or .docx.",
            )
            else -> throw IllegalStateException("${p.name} (${p.mime}) is not a type that can be read: PDF, .docx, text or images.")
        }
        return out
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "could not open the document" }
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
                if (buf.size() > MAX_BYTES) throw IllegalStateException("the document is larger than 20 MB")
            }
            return buf.toByteArray()
        }
    }

    private fun readPdf(context: Context, uri: Uri, startPage: Int, out: JSONObject) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("could not open the PDF")
        val pdf = PdfRenderer(pfd) // takes ownership of pfd; close() closes both
        try {
            val total = pdf.pageCount
            out.put("pages_total", total)
            if (startPage > total) throw IllegalStateException("start_page $startPage is past the end: the PDF has $total pages.")
            val images = JSONArray()
            var page = startPage - 1
            repeat(IMAGES_PER_CALL) {
                if (page >= total) return@repeat
                val from = page + 1
                val bitmaps = ArrayList<Bitmap>()
                while (bitmaps.size < PAGES_PER_IMAGE && page < total) {
                    val pg = pdf.openPage(page)
                    try {
                        val scale = PAGE_WIDTH_PX.toFloat() / pg.width
                        val bmp = Bitmap.createBitmap(PAGE_WIDTH_PX, (pg.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE) // PdfRenderer draws onto transparent
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps += bmp
                    } finally {
                        pg.close()
                    }
                    page++
                }
                val sheet = stack(bitmaps)
                images.put(JSONObject().put("pages", "$from-$page").put("png_base64", png(sheet)))
                sheet.recycle()
                bitmaps.forEach { it.recycle() }
            }
            out.put("images", images)
            if (page < total) out.put("more", "Pages ${page + 1}-$total not shown yet: call read_document with start_page=${page + 1}.")
        } finally {
            pdf.close()
        }
    }

    private fun stack(pages: List<Bitmap>): Bitmap {
        val gap = 12
        val h = pages.sumOf { it.height } + gap * (pages.size - 1)
        val sheet = Bitmap.createBitmap(PAGE_WIDTH_PX, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.LTGRAY)
        var y = 0
        for (b in pages) {
            canvas.drawBitmap(b, 0f, y.toFloat(), null)
            y += b.height + gap
        }
        return sheet
    }

    private fun readImage(context: Context, uri: Uri): String {
        val bytes = readBytes(context, uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > IMAGE_LONG_SIDE * 2) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("the image could not be decoded")
        val long = maxOf(bmp.width, bmp.height)
        val scaled = if (long > IMAGE_LONG_SIDE) {
            val f = IMAGE_LONG_SIDE.toFloat() / long
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true).also { bmp.recycle() }
        } else bmp
        return png(scaled).also { scaled.recycle() }
    }

    private fun png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        name.endsWith(".txt", true) || name.endsWith(".md", true) -> "text/plain"
        name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/*"
        else -> "application/octet-stream"
    }
}
