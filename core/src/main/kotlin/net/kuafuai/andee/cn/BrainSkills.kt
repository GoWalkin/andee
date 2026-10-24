package net.kuafuai.andee.cn

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Can the configured model do the two things this body cannot work without:
 * **read a screenshot** and **call a tool**? (GoWalkin fork.)
 *
 * The backend row in the self-check answers "is the URL, key and model name
 * right". That is not the same question. Every successful tap hands the model a
 * screenshot, and every action is a tool call, so a text-only model, or one
 * whose tool calling is broken, answers the first `hi` perfectly and then fails
 * on the first real step, in a way that reads as "Andee is broken". Behind a
 * router that stacks free tiers (FreeLLMAPI) the model in play can be any of
 * dozens, so this has to be asked of the model actually configured, not assumed.
 *
 * One small real request: a 64×64 picture of a red square, one tool, and an
 * instruction to call it. It costs a few dozen tokens, which is why it only runs
 * after the backend row has already said the endpoint answers.
 *
 * Deliberately sends **no** `thinking` / `reasoning_effort` fields: they are not
 * OpenAI schema, and a strict gateway refusing them would read here as "this
 * model cannot use tools", the false negative this row must not produce.
 */
object BrainSkills {

    sealed interface Result {
        /** Saw the picture and called the tool. */
        object Ok : Result

        /** Answered, but in prose rather than a tool call. Tool calling is weak or off. */
        data class NoToolCall(val said: String) : Result

        /** The gateway refused the image. A text-only model. */
        data class NoVision(val detail: String) : Result

        /** The gateway refused the tool list. */
        data class NoTools(val detail: String) : Result

        /** Rate-limited: says nothing about the model either way. */
        object Throttled : Result

        /** Anything else: unreachable, a 5xx, a body we could not read. */
        data class Other(val detail: String) : Result
    }

    fun probe(baseUrl: String, apiKey: String, model: String, readTimeoutS: Long = 30): Result {
        val http = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(readTimeoutS, TimeUnit.SECONDS)
            .callTimeout(readTimeoutS + 10, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody(model).toString().toRequestBody(JSON))
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                classify(resp.code, resp.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            Result.Other(e.message ?: e.javaClass.simpleName)
        }
    }

    internal fun requestBody(model: String): JSONObject {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "text").put(
                    "text",
                    "This is a capability check. Look at the picture and call the tool " +
                        "`report_color` with the colour of the square. Do not answer in text.",
                ),
            )
            .put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/png;base64,$TEST_PNG")),
            )
        val tool = JSONObject().put("type", "function").put(
            "function",
            JSONObject()
                .put("name", "report_color")
                .put("description", "Report the colour of the square in the picture.")
                .put(
                    "parameters",
                    JSONObject().put("type", "object")
                        .put("properties", JSONObject().put("color", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("color")),
                ),
        )
        return JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", 64)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("tools", JSONArray().put(tool))
            .put("tool_choice", "auto")
    }

    internal fun classify(code: Int, body: String): Result {
        if (code == 429) return Result.Throttled
        if (code !in 200..299) {
            val b = body.lowercase()
            val detail = "HTTP $code: ${body.take(200)}"
            return when {
                VISION_WORDS.any { it in b } -> Result.NoVision(detail)
                TOOL_WORDS.any { it in b } -> Result.NoTools(detail)
                else -> Result.Other(detail)
            }
        }
        val msg = runCatching {
            JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        }.getOrNull() ?: return Result.Other("unreadable reply: ${body.take(200)}")
        val calls = msg.optJSONArray("tool_calls")
        return if (calls != null && calls.length() > 0) Result.Ok
        else Result.NoToolCall(msg.optString("content").take(120))
    }

    private val VISION_WORDS = listOf("image", "vision", "multimodal", "multi-modal")
    private val TOOL_WORDS = listOf("tool", "function")

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 64×64 white PNG with a red square in the middle (144 bytes). Big enough for VL models' minimum sizes. */
    private const val TEST_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAV0lEQVR42u3YQQkAQAwDwUqof3XnpDVROAKzxMB8UxNeAQAA" +
            "AAAAAAAAAAAAAAB8ALzukwEAAAAAAAAAAAAAAAAAAAAAZAE8cwAAAAAAAAAAAAAAAADBLSM0WUb6RKFKAAAAAElFTkSuQmCC"
}
