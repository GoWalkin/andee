package net.kuafuai.andee.cn

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import net.kuafuai.andee.tts.TtsEngine
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Speaking with the phone's own text-to-speech engine — no key, no network
 * (GoWalkin fork). On Huawei / Honor that is the system's own engine; elsewhere
 * whichever one is set in Settings → Text-to-speech.
 *
 * It fits the streaming [TtsEngine] shape by synthesising each chunk of text to
 * a WAV in the cache and handing its PCM to the same player the cloud voices
 * use, so the ball's mouth and mood cues work unchanged. The engine names its
 * rate in [open] before any audio exists, so everything is resampled to
 * [SAMPLE_RATE] ([WavPcm]).
 *
 * Language follows the text: mostly-Latin text is read with an English voice,
 * anything with Chinese in it with a Chinese one, when the engine has them.
 */
class PhoneTts(
    private val context: Context,
    private val listener: TtsEngine.Listener,
) : TtsEngine {

    private val text = StringBuilder()
    @Volatile private var cancelled = false

    override fun open() {
        listener.onOpen(SAMPLE_RATE)
    }

    override fun push(text: String) {
        this.text.append(text)
    }

    override fun finish() {
        val tts = PhoneVoice.tts(context) ?: run {
            listener.onError("the phone has no text-to-speech engine (Settings → Accessibility → Text-to-speech)")
            return
        }
        val chunks = split(text.toString(), tts.maxInputLength())
        for ((i, chunk) in chunks.withIndex()) {
            if (cancelled) return
            val pcm = synthesize(tts, chunk, i) ?: return
            var off = 0
            while (off < pcm.size && !cancelled) {
                val n = minOf(CHUNK_BYTES, pcm.size - off)
                listener.onAudio(pcm.copyOfRange(off, off + n))
                off += n
            }
        }
        if (!cancelled) listener.onDone()
    }

    override fun cancel() {
        cancelled = true
        runCatching { PhoneVoice.tts(context, 0)?.stop() }
        listener.onClose(TtsEngine.CloseReason.USER)
    }

    private fun synthesize(tts: TextToSpeech, chunk: String, index: Int): ByteArray? {
        val locale = if (CJK.containsMatchIn(chunk)) Locale.SIMPLIFIED_CHINESE else Locale.US
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        val file = File(context.cacheDir, "phone_tts_$index.wav")
        val id = "andee-${System.nanoTime()}"
        val done = CountDownLatch(1)
        var error: String? = null
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.countDown() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) { error = "synthesis failed"; done.countDown() } }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) { error = "synthesis failed ($errorCode)"; done.countDown() }
            }
        })
        if (tts.synthesizeToFile(chunk, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            listener.onError("the phone's text-to-speech refused the text")
            return null
        }
        // Generous: a slow engine on a long sentence. A stall still ends the turn.
        if (!done.await(15_000L + chunk.length * 100L, TimeUnit.MILLISECONDS)) {
            listener.onError("the phone's text-to-speech did not answer")
            return null
        }
        error?.let { listener.onError(it); return null }
        val wav = runCatching { file.readBytes() }.getOrNull().also { file.delete() }
        val parsed = wav?.let { WavPcm.parse(it) } ?: run {
            listener.onError("the phone's text-to-speech wrote audio this app cannot read")
            return null
        }
        return WavPcm.toMono(parsed, SAMPLE_RATE)
    }

    private fun TextToSpeech.maxInputLength(): Int =
        runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000).coerceAtMost(1000)

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val CHUNK_BYTES = 8_192
        private val CJK = Regex("[\\u3400-\\u9fff]")

        /** Split at sentence ends so each piece fits the engine and the first one starts sooner. */
        internal fun split(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for (sentence in Regex("(?<=[.!?。！？\\n])").split(text)) {
                if (sb.length + sentence.length > max && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                var s = sentence
                while (s.length > max) { out += s.substring(0, max); s = s.substring(max) }
                sb.append(s)
            }
            if (sb.isNotBlank()) out += sb.toString()
            return out.filter { it.isNotBlank() }
        }
    }
}
