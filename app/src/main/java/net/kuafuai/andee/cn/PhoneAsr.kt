package net.kuafuai.andee.cn

import android.content.Context
import net.kuafuai.andee.asr.AsrEngine
import org.json.JSONObject
import org.vosk.Recognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hearing with an offline model on the phone — no key, no network, no Google
 * services (GoWalkin fork). Huawei / Honor phones have no speech recogniser
 * behind Android's `SpeechRecognizer`, and that API wants the microphone for
 * itself while `AudioIO` already holds it; Vosk instead takes the same 16 kHz
 * PCM chunks the cloud engines are fed, so it slots into [AsrEngine] as is.
 *
 * The bundled model is English (`vosk-model-small-en-us`), because the person
 * this build is for speaks English. Vosk commits a result at each pause; those
 * are accumulated so [Listener.onPartial] always carries the whole utterance so
 * far, which is what the controller's silence rule reads.
 */
class PhoneAsr(
    private val context: Context,
    private val listener: AsrEngine.Listener,
) : AsrEngine {

    override val finalWaitMs: Long = 1_500L
    override val silenceMs: Long = 2_500L

    private var recognizer: Recognizer? = null
    private val committed = StringBuilder()
    private val finished = CountDownLatch(1)
    @Volatile private var closed = false

    override fun start(sampleRate: Int, channels: Int) {
        val model = runCatching { PhoneVoice.model(context) }.getOrElse {
            listener.onError("offline speech model: ${it.message}")
            finished.countDown()
            return
        }
        recognizer = Recognizer(model, sampleRate.toFloat())
        listener.onReady()
    }

    override fun sendAudio(pcm: ByteArray, isLast: Boolean) {
        val rec = recognizer ?: return
        if (closed) return
        synchronized(this) {
            if (pcm.isNotEmpty()) {
                if (rec.acceptWaveForm(pcm, pcm.size)) {
                    append(textOf(rec.result, "text"))
                    listener.onPartial(committed.toString())
                } else {
                    val partial = textOf(rec.partialResult, "partial")
                    if (partial.isNotEmpty()) listener.onPartial(join(committed.toString(), partial))
                }
            }
            if (isLast) {
                append(textOf(rec.finalResult, "text"))
                listener.onFinal(committed.toString())
                finished.countDown()
            }
        }
    }

    override fun waitFinished(timeoutMs: Long): Boolean = finished.await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun close() {
        closed = true
        synchronized(this) { runCatching { recognizer?.close() }; recognizer = null }
        finished.countDown()
        listener.onClosed()
    }

    private fun append(t: String) {
        if (t.isEmpty()) return
        if (committed.isNotEmpty()) committed.append(' ')
        committed.append(t)
    }

    private fun join(a: String, b: String) = if (a.isEmpty()) b else "$a $b"

    private fun textOf(json: String?, key: String): String =
        runCatching { JSONObject(json ?: "{}").optString(key) }.getOrDefault("").trim()
}
