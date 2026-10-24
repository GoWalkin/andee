package net.kuafuai.andee.cn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainSkillsTest {

    @Test
    fun toolCallIsOk() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"",
            "tool_calls":[{"id":"c1","type":"function","function":{"name":"report_color","arguments":"{\"color\":\"red\"}"}}]}}]}"""
        assertEquals(BrainSkills.Result.Ok, BrainSkills.classify(200, body))
    }

    @Test
    fun proseInsteadOfToolCall() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"The square is red."}}]}"""
        val r = BrainSkills.classify(200, body)
        assertTrue(r is BrainSkills.Result.NoToolCall)
        assertEquals("The square is red.", (r as BrainSkills.Result.NoToolCall).said)
    }

    @Test
    fun imageRefusedIsNoVision() {
        val r = BrainSkills.classify(400, """{"error":{"message":"Model does not support image input"}}""")
        assertTrue(r is BrainSkills.Result.NoVision)
    }

    @Test
    fun toolsRefusedIsNoTools() {
        val r = BrainSkills.classify(400, """{"error":{"message":"tools are not supported for this model"}}""")
        assertTrue(r is BrainSkills.Result.NoTools)
    }

    @Test
    fun throttledAndOther() {
        assertEquals(BrainSkills.Result.Throttled, BrainSkills.classify(429, "slow down"))
        assertTrue(BrainSkills.classify(500, "boom") is BrainSkills.Result.Other)
        assertTrue(BrainSkills.classify(200, "not json") is BrainSkills.Result.Other)
    }

    @Test
    fun requestCarriesImageAndToolButNoThinkingFields() {
        val b = BrainSkills.requestBody("qwen-vl")
        assertEquals("qwen-vl", b.getString("model"))
        assertEquals(1, b.getJSONArray("tools").length())
        val content = b.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertFalse(b.has("thinking"))
        assertFalse(b.has("reasoning_effort"))
    }
}
