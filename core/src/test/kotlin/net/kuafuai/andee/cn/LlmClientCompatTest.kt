package net.kuafuai.andee.cn

import net.kuafuai.andee.brain.LlmClient
import net.kuafuai.andee.brain.LlmHttpException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The silent retry without `thinking` must only fire when the gateway named one of our two fields. */
class LlmClientCompatTest {

    @Test
    fun namedFieldOn400Retries() {
        assertTrue(LlmClient.rejectsThinkingFields(LlmHttpException(400, """{"error":{"message":"Unrecognized request argument supplied: thinking"}}""")))
        assertTrue(LlmClient.rejectsThinkingFields(LlmHttpException(422, """{"detail":[{"loc":["body","reasoning_effort"],"type":"extra_forbidden"}]}""")))
    }

    @Test
    fun otherFailuresDoNotRetry() {
        assertFalse(LlmClient.rejectsThinkingFields(LlmHttpException(400, """{"error":{"message":"context length exceeded"}}""")))
        assertFalse(LlmClient.rejectsThinkingFields(LlmHttpException(401, """{"error":{"message":"bad key; thinking"}}""")))
        assertFalse(LlmClient.rejectsThinkingFields(LlmHttpException(500, "thinking")))
    }
}
