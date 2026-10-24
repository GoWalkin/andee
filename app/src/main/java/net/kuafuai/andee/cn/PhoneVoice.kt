package net.kuafuai.andee.cn

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import org.vosk.Model
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone's own speech engines, shared by every [PhoneTts] and [PhoneAsr]
 * (GoWalkin fork). Both are expensive to bring up — a TextToSpeech binding takes
 * a few hundred ms, a Vosk model a second or two and its first copy out of the
 * APK several seconds — and a conversation opens a new engine per utterance, so
 * one of each lives for the whole process.
 */
object PhoneVoice {

    private const val TAG = "Body"

    /** Asset folder the CI fills with `vosk-model-small-en-us`; not in git. */
    const val MODEL_ASSET = "vosk-model-en"

    /** Bump when the bundled model changes, so the copy in files/ is refreshed. */
    private const val MODEL_STAMP = "vosk-model-small-en-us-0.15"

    private val lock = Any()
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var ttsLatch = CountDownLatch(1)
    @Volatile private var model: Model? = null

    /** The bound TextToSpeech, or null when the phone has no engine or it failed to start. */
    fun tts(context: Context, waitMs: Long = 5_000): TextToSpeech? {
        val latch = synchronized(lock) {
            if (tts == null) {
                ttsLatch = CountDownLatch(1)
                val l = ttsLatch
                tts = TextToSpeech(context.applicationContext) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (!ttsReady) Log.w(TAG, "phone tts init failed: $status")
                    l.countDown()
                }
            }
            ttsLatch
        }
        latch.await(waitMs, TimeUnit.MILLISECONDS)
        if (!ttsReady) {
            synchronized(lock) { runCatching { tts?.shutdown() }; tts = null }
            return null
        }
        return tts
    }

    /** Is any TTS engine installed? Cheap; for the self-check. */
    fun hasTtsEngine(context: Context): Boolean =
        context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .isNotEmpty()

    /** Does this APK carry the offline recognition model? */
    fun hasModel(context: Context): Boolean =
        runCatching { context.assets.list(MODEL_ASSET)?.isNotEmpty() == true }.getOrDefault(false)

    /**
     * The Vosk model, copied out of the APK on first use. Blocking — call it off
     * the main thread. Throws with a sentence a person can act on.
     */
    fun model(context: Context): Model {
        model?.let { return it }
        synchronized(lock) {
            model?.let { return it }
            if (!hasModel(context)) {
                throw IllegalStateException(
                    "this build has no offline speech model; install a build from CI, or type instead",
                )
            }
            val dir = File(context.filesDir, MODEL_ASSET)
            val stamp = File(dir, ".stamp")
            if (!stamp.exists() || stamp.readText() != MODEL_STAMP) {
                dir.deleteRecursively()
                copyAssets(context, MODEL_ASSET, dir)
                stamp.writeText(MODEL_STAMP)
            }
            return Model(dir.absolutePath).also { model = it }
        }
    }

    private fun copyAssets(context: Context, path: String, into: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            into.parentFile?.mkdirs()
            context.assets.open(path).use { input -> into.outputStream().use { input.copyTo(it) } }
            return
        }
        into.mkdirs()
        for (c in children) copyAssets(context, "$path/$c", File(into, c))
    }
}
