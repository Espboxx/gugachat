package com.gugachat.app.domain.chat

/**
 * Shared stripper for internal structured XML/HTML-like control tags emitted by LLM prose
 * (novel `<status_hud>/<lore_sync>/<chapter_end>/<choices>` and RP `<rp_state>/<rp_choices>/...`),
 * including truncated tail prefixes while SSE chunks are still arriving.
 */
internal object StreamingTagStripper {
    fun strip(
        raw: String,
        completeTagRe: Regex,
        unclosedTagRe: Regex,
        knownTagTokens: List<String>,
        maxTailLength: Int = 16,
    ): String {
        if (raw.isEmpty()) return ""
        var s = completeTagRe.replace(raw, "")
        s = unclosedTagRe.replace(s, "")
        val lastLt = s.lastIndexOf('<')
        if (lastLt >= 0) {
            val tail = s.substring(lastLt).lowercase()
            if (tail.length <= maxTailLength) {
                val matchesPrefix = if (tail == "<") {
                    lastLt == 0 || s[lastLt - 1].isWhitespace()
                } else {
                    knownTagTokens.any { it.startsWith(tail) }
                }
                if (matchesPrefix) {
                    s = s.substring(0, lastLt)
                }
            }
        }
        return s.trim()
    }

    fun isStreamingPartialMeta(
        raw: String,
        unclosedTagRe: Regex,
        knownTagTokens: List<String>,
        maxTailLength: Int = 16,
    ): Boolean {
        if (raw.isEmpty()) return false
        if (unclosedTagRe.containsMatchIn(raw)) return true
        val lastLt = raw.lastIndexOf('<')
        if (lastLt >= 0) {
            val tail = raw.substring(lastLt).lowercase()
            if (tail.length in 2..maxTailLength && knownTagTokens.any { it.startsWith(tail) }) {
                return true
            }
        }
        return false
    }
}
