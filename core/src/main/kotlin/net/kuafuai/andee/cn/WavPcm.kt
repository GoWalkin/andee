package net.kuafuai.andee.cn

/**
 * Just enough WAV for the phone's own text-to-speech (GoWalkin fork).
 *
 * `TextToSpeech.synthesizeToFile` writes a RIFF/WAVE file whose sample rate is
 * the engine's choice (16, 22.05 or 24 kHz, depending on the phone), but a
 * `TtsEngine` has to name its rate in `onOpen` before any audio exists. So the
 * phone engine opens at one fixed rate and everything it synthesises is
 * brought to that rate here: mono, 16-bit little-endian, the same PCM the rest
 * of the voice pipeline already plays.
 */
object WavPcm {

    class Pcm(val sampleRate: Int, val channels: Int, val data: ByteArray)

    /** Parse a PCM16 WAV; null when it is not one (wrong magic, compressed, 8-bit). */
    fun parse(wav: ByteArray): Pcm? {
        if (wav.size < 12 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(wav, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) return null
        var pos = 12
        var rate = 0
        var channels = 0
        var bits = 0
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4, Charsets.US_ASCII)
            val size = le32(wav, pos + 4)
            val body = pos + 8
            if (size < 0 || body > wav.size) return null
            when (id) {
                "fmt " -> {
                    if (le16(wav, body) != 1) return null // PCM only
                    channels = le16(wav, body + 2)
                    rate = le32(wav, body + 4)
                    bits = le16(wav, body + 14)
                }
                "data" -> {
                    if (rate <= 0 || channels <= 0 || bits != 16) return null
                    // Engines that stream to file sometimes leave size 0 or too big.
                    val end = if (size <= 0 || body + size > wav.size) wav.size else body + size
                    return Pcm(rate, channels, wav.copyOfRange(body, end))
                }
            }
            pos = body + size + (size and 1)
        }
        return null
    }

    /** Mix to mono and resample (linear) to [targetRate]; 16-bit little-endian in and out. */
    fun toMono(pcm: Pcm, targetRate: Int): ByteArray {
        val frames = pcm.data.size / (2 * pcm.channels)
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until pcm.channels) {
                val i = (f * pcm.channels + c) * 2
                sum += ((pcm.data[i].toInt() and 0xff) or (pcm.data[i + 1].toInt() shl 8)).toShort().toInt()
            }
            mono[f] = (sum / pcm.channels).toShort()
        }
        if (pcm.sampleRate == targetRate || frames == 0) return shortsToBytes(mono)
        val outFrames = (frames.toLong() * targetRate / pcm.sampleRate).toInt()
        val out = ShortArray(outFrames)
        val step = pcm.sampleRate.toDouble() / targetRate
        for (i in 0 until outFrames) {
            val src = i * step
            val a = src.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val t = src - a
            out[i] = (mono[a] * (1 - t) + mono[b] * t).toInt().toShort()
        }
        return shortsToBytes(out)
    }

    private fun shortsToBytes(s: ShortArray): ByteArray {
        val b = ByteArray(s.size * 2)
        for (i in s.indices) {
            b[2 * i] = (s[i].toInt() and 0xff).toByte()
            b[2 * i + 1] = (s[i].toInt() shr 8 and 0xff).toByte()
        }
        return b
    }

    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, i: Int) = le16(b, i) or (le16(b, i + 2) shl 16)
}
