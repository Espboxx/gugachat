package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTagStripperAndJsonExtractorTest {

    @Test
    fun streamingTagStripper_stripsCompleteUnclosedAndPartialTailTokens() {
        val completeRe = Regex("<(status_hud|choices)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
        val unclosedRe = Regex("<(status_hud|choices)\\b[^>]*>[\\s\\S]*$", RegexOption.IGNORE_CASE)
        val tokens = listOf("<status_hud>", "</status_hud>", "<choices>", "</choices>")

        assertEquals(
            "Visible prose.",
            StreamingTagStripper.strip(
                "Visible prose.\n<status_hud>{\"location\":\"Hall\"}</status_hud><ch",
                completeRe,
                unclosedRe,
                tokens,
            ),
        )
        assertTrue(StreamingTagStripper.isStreamingPartialMeta("Body <status", unclosedRe, tokens))
        assertTrue(StreamingTagStripper.isStreamingPartialMeta("Body <choices>[\"a\"]", unclosedRe, tokens))
        assertFalse(StreamingTagStripper.isStreamingPartialMeta("Body a < b", unclosedRe, tokens))
    }

    @Test
    fun llmJsonExtractor_extractsObjectAndArrayInsideCodeFences() {
        val fencedObj = """
            ```json
            Here is the payload:
            {"title":"Arc 1","summary":"Summary"}
            ```
        """.trimIndent()
        assertEquals(
            """{"title":"Arc 1","summary":"Summary"}""",
            LlmJsonExtractor.extractFirstJsonObject(fencedObj),
        )

        val fencedArr = """
            ```
            [{"title":"A"}]
            ```
        """.trimIndent()
        assertEquals("""[{"title":"A"}]""", LlmJsonExtractor.extractFirstJsonArray(fencedArr))
        assertNull(LlmJsonExtractor.extractFirstJsonObject("no braces here"))
        assertNull(LlmJsonExtractor.extractFirstJsonArray("no brackets here"))
    }
}
