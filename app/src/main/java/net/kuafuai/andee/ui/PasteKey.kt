package net.kuafuai.andee.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import net.kuafuai.andee.R

/**
 * A **Paste** key beside a field on one of our cards (GoWalkin fork).
 *
 * The cards are bare `TYPE_APPLICATION_OVERLAY` windows owned by the service,
 * not Activities, and the platform's long-press copy/paste toolbar is a popup
 * that never appears in such a window — so on the settings card there was no
 * way to paste an API key at all, short of the keyboard's own clipboard. This
 * key reads the clipboard directly. That read is allowed because the card is
 * the focused window at the moment its key is tapped.
 *
 * Whitespace and newlines are trimmed: a key copied from a web page usually
 * carries a trailing newline, which then fails the backend check as a bad key.
 * An empty clipboard is said on the key itself for a moment — not with
 * `EditText.setError`, whose bubble is the same kind of popup that fails here.
 */
object PasteKey {

    fun button(context: Context, lctx: Context, et: EditText, padH: Int, padV: Int): TextView =
        TextView(context).apply {
            val label = lctx.getString(R.string.common_paste)
            text = label
            textSize = Glass.Type.CAPTION
            setTextColor(Color.parseColor(Glass.ACCENT))
            gravity = Gravity.CENTER
            setPadding(padH, padV, padH / 2, padV)
            isClickable = true
            setOnClickListener {
                val clip = runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                }.getOrNull()?.trim().orEmpty()
                if (clip.isEmpty()) {
                    text = lctx.getString(R.string.common_clipboard_empty)
                    Handler(Looper.getMainLooper()).postDelayed({ text = label }, 2_000)
                } else {
                    et.setText(clip)
                    et.setSelection(et.text?.length ?: 0)
                }
            }
        }
}
