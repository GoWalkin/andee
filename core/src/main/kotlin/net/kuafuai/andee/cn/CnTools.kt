package net.kuafuai.andee.cn

import org.json.JSONArray
import org.json.JSONObject

/**
 * The tools this fork adds (GoWalkin), kept out of `ToolSchemas` on purpose:
 * that table is pinned byte-for-byte by `GoldenTextTest`, and every line of it
 * is a merge conflict waiting for the next upstream sync. Same shape and same
 * wiring as `PeerTasks.TOOLS` — listed by `AndroidBrainHost.toolLists()`,
 * routed by [methodOf], dispatched through the ordinary `CommandDispatcher`, so
 * the turn guards and the lock-screen guard apply exactly as for any tool.
 *
 * Both are `localOnly`: a document on this phone is nobody's business but the
 * brain's that runs here.
 */
object CnTools {

    const val PICK = "doc.pick"
    const val READ = "doc.read"

    val TOOLS: JSONArray by lazy {
        fun tool(name: String, method: String, description: String, props: JSONObject, required: List<String>) =
            JSONObject().put("name", name).put("method", method).put("description", description)
                .put("localOnly", true)
                .put(
                    "parameters",
                    JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)),
                )
        JSONArray()
            .put(
                tool(
                    "pick_document", PICK, """
                    Open the phone's own file picker so a document can be chosen. Use it when the user tells you where a file is — "my CV is in Downloads, it's called Nouman_CV.pdf" — or asks you to read one.

                    It returns at once, with the picker open on the folder you named. Then pick the file yourself: the picker is an ordinary screen, so get_screen_element, open the folder if needed, and tap the file by its name (use the picker's own search when the folder is long). If you cannot find it, ask the user to tap it. When the picker has closed, call read_document.

                    Accepts PDF, Word (.docx), plain text and images. Nothing leaves the phone except what read_document hands back to you.
                    """.trimIndent(),
                    JSONObject().put(
                        "folder",
                        JSONObject().put("type", "string").put("enum", JSONArray(listOf("downloads", "documents", "any")))
                            .put("description", "Where the picker opens. Default downloads."),
                    ),
                    emptyList(),
                ),
            )
            .put(
                tool(
                    "read_document", READ, """
                    Read the document chosen with pick_document. Returns its name and type, and:
                    - Word (.docx) and text files: the text itself.
                    - PDF: the pages as pictures, two pages per picture, four pages per call. Pass start_page to read further (5, 9, …); `pages_total` says how many there are.
                    - An image: the image.

                    Pictures drop out of the conversation after a few steps, so read carefully and save what matters at once. For a CV: remember name=cv_profile, type=user, with target roles, skills, years of experience per area, past titles and employers, education, languages and Chinese level, cities, salary expectation and work-permit status; and remember name=cv_text, type=user, with a faithful full transcription. Then tell the user, in their language, what you understood and ask about anything missing that a job search needs.
                    """.trimIndent(),
                    JSONObject().put(
                        "start_page",
                        JSONObject().put("type", "integer").put("description", "First PDF page to return, 1-based. Default 1."),
                    ),
                    emptyList(),
                ),
            )
    }

    fun methodOf(toolName: String): String? = when (toolName) {
        "pick_document" -> PICK
        "read_document" -> READ
        else -> null
    }
}
