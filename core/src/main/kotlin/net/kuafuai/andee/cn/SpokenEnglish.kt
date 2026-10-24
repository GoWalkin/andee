package net.kuafuai.andee.cn

import net.kuafuai.andee.brain.SpeechMood

/**
 * What an English voice should actually say. (GoWalkin fork.)
 *
 * With the bridge on, the model writes "tap Confirm ride (确认呼叫)" — the
 * Chinese is there so the user can find the label on the screen, which is a
 * job for the eyes. Read aloud by an English voice it is either garbled or a
 * pause, so the spoken copy drops Chinese: a bracket holding only Chinese goes
 * whole, and any other run of Chinese characters goes too. The scrollback and
 * the subtitles keep every character; this is only what reaches the speaker.
 *
 * The mood cues are offsets into the text (see [SpeechMood]), so they are moved
 * with it: a cue lands on the first character that survives at or after its
 * old position.
 */
object SpokenEnglish {

    fun strip(text: String, cues: List<SpeechMood.Cue>): Pair<String, List<SpeechMood.Cue>> {
        val drop = BooleanArray(text.length)
        // 1. Brackets whose content is only Chinese, punctuation and spaces.
        for (m in CJK_BRACKET.findAll(text)) for (i in m.range) drop[i] = true
        // 2. Any remaining Chinese run, with the CJK punctuation stuck to it.
        for (m in CJK_RUN.findAll(text)) for (i in m.range) drop[i] = true

        val kept = StringBuilder()
        val newIndex = IntArray(text.length + 1)
        for (i in text.indices) {
            newIndex[i] = kept.length
            if (!drop[i]) kept.append(text[i])
        }
        newIndex[text.length] = kept.length

        // Tidy what the removals leave behind, keeping the index map honest by
        // only ever deleting characters (never inserting).
        val tidy = StringBuilder()
        val tidyIndex = IntArray(kept.length + 1)
        var i = 0
        while (i < kept.length) {
            tidyIndex[i] = tidy.length
            val c = kept[i]
            val prev = tidy.lastOrNull()
            val skip = (c == ' ' && (prev == null || prev == ' ')) ||
                (c in ",.;:!?" && prev == ' ' && tidy.isNotEmpty())
            if (skip && c != ' ') tidy.setLength(tidy.length - 1) // "word ," → "word,"
            if (!(skip && c == ' ')) tidy.append(c)
            i++
        }
        tidyIndex[kept.length] = tidy.length
        val out = tidy.toString().trim()
        val lead = tidy.length - tidy.trimStart().length

        val moved = cues.map { cue ->
            val at = cue.at.coerceIn(0, text.length)
            val pos = (tidyIndex[newIndex[at]] - lead).coerceIn(0, out.length)
            cue.copy(at = pos)
        }
        return out to moved
    }

    /** True for a 火山 speaker id that speaks English (`en_female_…`, `en_male_…`). */
    fun isEnglishVoice(speaker: String): Boolean = speaker.startsWith("en_")

    private const val CJK = "\\u3400-\\u9fff\\uf900-\\ufaff"
    private const val CJK_PUNCT = "\\u3000-\\u303f\\uff00-\\uffef"
    private val CJK_BRACKET = Regex(
        "\\s?[(（「『“]\\s*[$CJK$CJK_PUNCT\\s·•\\-0-9]*[$CJK][$CJK$CJK_PUNCT\\s·•\\-0-9]*\\s*[)）」』”]",
    )
    private val CJK_RUN = Regex("[$CJK][$CJK$CJK_PUNCT]*")
}
