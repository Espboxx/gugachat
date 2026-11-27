package com.gugachat.app.domain.chat

/**
 * Strips optional markdown code fences and extracts the outermost JSON object or array
 * from free-form LLM structured output blocks.
 */
internal object LlmJsonExtractor {
    fun extractFirstJsonObject(raw: String): String? =
        extractFirstDelimited(raw, '{', '}')

    fun extractFirstJsonArray(raw: String): String? =
        extractFirstDelimited(raw, '[', ']')

    private fun stripMarkdownFences(raw: String): String =
        raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    private fun extractFirstDelimited(raw: String, openChar: Char, closeChar: Char): String? {
        val stripped = stripMarkdownFences(raw)
        val start = stripped.indexOf(openChar)
        val end = stripped.lastIndexOf(closeChar)
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }
}
