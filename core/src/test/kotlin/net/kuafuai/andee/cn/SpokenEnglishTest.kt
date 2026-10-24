package net.kuafuai.andee.cn

import net.kuafuai.andee.brain.SpeechMood
import net.kuafuai.andee.ui.ball.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenEnglishTest {

    private fun say(text: String) = SpokenEnglish.strip(text, emptyList()).first

    @Test
    fun chineseGlossesAreNotSpoken() {
        assertEquals("Tap Confirm ride to call the car.", say("Tap Confirm ride (确认呼叫) to call the car."))
        assertEquals("It says Out of stock.", say("It says Out of stock（已售罄）."))
        assertEquals("Going to Hongqiao Railway Station, about ¥45.", say("Going to Hongqiao Railway Station (虹桥火车站), about ¥45."))
        assertEquals("The recruiter wrote, meaning hello.", say("The recruiter wrote 「您好」, meaning hello."))
    }

    @Test
    fun englishIsUntouched() {
        val s = "Your DiDi is a white Tesla, plate HU A 12345, two minutes away."
        assertEquals(s, say(s))
    }

    @Test
    fun cuesMoveWithTheText() {
        val text = "Found it (找到了). Want me to open it?"
        val cueAt = text.indexOf("Want")
        val (out, cues) = SpokenEnglish.strip(text, listOf(SpeechMood.Cue(0, Mood.HAPPY), SpeechMood.Cue(cueAt, Mood.CALM)))
        assertEquals("Found it. Want me to open it?", out)
        assertEquals(0, cues[0].at)
        assertEquals(out.indexOf("Want"), cues[1].at)
    }

    @Test
    fun englishVoiceIds() {
        assertTrue(SpokenEnglish.isEnglishVoice("en_female_dacey_uranus_bigtts"))
        assertTrue(!SpokenEnglish.isEnglishVoice("zh_female_vv_uranus_bigtts"))
    }
}
