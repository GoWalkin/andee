package net.kuafuai.andee.cn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract

/**
 * A window with nothing in it, whose only job is to own the system file
 * picker's result. (GoWalkin fork.)
 *
 * The body is a service and a set of overlays, and `ACTION_OPEN_DOCUMENT`
 * answers only to an Activity. Translucent and excluded from recents, so the
 * user sees the picker and nothing of this. The picker itself is DocumentsUI —
 * another package with an ordinary accessibility tree — which is what lets the
 * brain navigate it to the file the user named.
 */
class DocumentPickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated while the picker is up (rotation, memory): the result still
        // comes back here, so do not open a second picker.
        if (savedInstanceState != null) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, DocumentImport.MIME_TYPES)
        initialFolder(getIntent().getStringExtra(EXTRA_FOLDER))?.let {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
        }
        runCatching { startActivityForResult(intent, REQUEST) }
            .onFailure {
                DocumentImport.onPicked(this, null, "no file picker on this device: ${it.message}")
                finish()
            }
    }

    @Deprecated("Platform Activity API; this class has no AndroidX dependency on purpose.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            DocumentImport.onPicked(this, uri, if (uri == null) "the picker was closed without choosing a file" else null)
            finish()
        }
    }

    private fun initialFolder(folder: String?): Uri? = when (folder) {
        "any" -> null
        "documents" -> Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")
        else -> Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
    }

    companion object {
        const val EXTRA_FOLDER = "folder"
        private const val REQUEST = 4711
    }
}
