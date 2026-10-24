package net.kuafuai.andee.cn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavPcmTest {

    private fun wav(rate: Int, channels: Int, samples: ShortArray, extraChunk: Boolean = false): ByteArray {
        val data = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s -> data[2 * i] = (s.toInt() and 0xff).toByte(); data[2 * i + 1] = (s.toInt() shr 8).toByte() }
        val out = ByteArrayOutputStream()
        fun s(x: String) = out.write(x.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { for (k in 0 until 4) out.write(v shr (8 * k) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
        s("RIFF"); i32(36 + data.size); s("WAVE")
        s("fmt "); i32(16); i16(1); i16(channels); i32(rate); i32(rate * channels * 2); i16(channels * 2); i16(16)
        if (extraChunk) { s("LIST"); i32(3); out.write(byteArrayOf(1, 2, 3)); out.write(0) }
        s("data"); i32(data.size); out.write(data)
        return out.toByteArray()
    }

    @Test
    fun parsesMonoAndSkipsUnknownChunks() {
        val p = WavPcm.parse(wav(22050, 1, shortArrayOf(1, -2, 300), extraChunk = true))!!
        assertEquals(22050, p.sampleRate)
        assertEquals(1, p.channels)
        assertEquals(6, p.data.size)
    }

    @Test
    fun sameRateMonoIsUnchanged() {
        val p = WavPcm.parse(wav(24000, 1, shortArrayOf(100, -100, 32000)))!!
        assertEquals(p.data.toList(), WavPcm.toMono(p, 24000).toList())
    }

    @Test
    fun stereoIsMixedAndRateConverted() {
        // 4 stereo frames at 16 kHz → 6 mono frames at 24 kHz.
        val p = WavPcm.parse(wav(16000, 2, shortArrayOf(100, 300, 100, 300, 100, 300, 100, 300)))!!
        val out = WavPcm.toMono(p, 24000)
        assertEquals(12, out.size)
        val first = ((out[0].toInt() and 0xff) or (out[1].toInt() shl 8)).toShort()
        assertEquals(200.toShort(), first)
    }

    @Test
    fun rejectsNonWav() {
        assertNull(WavPcm.parse("hello world, not a wav".toByteArray()))
    }
}
