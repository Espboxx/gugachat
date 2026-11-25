package com.gugachat.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun parseDataPayload_extractsContentReasoningUsageAndDone() {
        assertNull(SseParser.parseDataPayload("   "))
        assertNull(SseParser.parseDataPayload("{not-json}"))

        val done = SseParser.parseDataPayload(" [DONE]\r\n")
        assertNotNull(done)
        assertTrue(done!!.done)

        val reasoning = SseParser.parseDataPayload("""{"choices":[{"delta":{"reasoning_content":"step1"}}]}""")!!
        assertFalse(reasoning.done)
        assertEquals("step1", reasoning.deltaReasoning)
        assertEquals("", reasoning.deltaText)

        val altReasoning = SseParser.parseDataPayload("""{"choices":[{"delta":{"reasoning":"step2"}}]}""")!!
        assertEquals("step2", altReasoning.deltaReasoning)

        val thinking = SseParser.parseDataPayload("""{"choices":[{"delta":{"thinking":"step3","content":"Hello"}}]}""")!!
        assertEquals("step3", thinking.deltaReasoning)
        assertEquals("Hello", thinking.deltaText)

        val usageChunk = SseParser.parseDataPayload("""{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"cache_read_input_tokens":4}}""")!!
        assertEquals(15, usageChunk.usage?.total_tokens)
        assertEquals(4, usageChunk.usage?.cache_read_input_tokens)
    }

    @Test
    fun mergeUsage_preservesPriorFieldsWhenMissing() {
        val first = ChatTokenUsageDto(prompt_tokens = 100, cache_read_input_tokens = 40)
        val second = ChatTokenUsageDto(completion_tokens = 25, total_tokens = 125)
        val merged = SseParser.mergeUsage(first, second)
        assertEquals(100, merged?.prompt_tokens)
        assertEquals(40, merged?.cache_read_input_tokens)
        assertEquals(25, merged?.completion_tokens)
        assertEquals(125, merged?.total_tokens)
    }

    @Test
    fun parseDataPayload_andSingleJson_throwOnUpstreamError() {
        val sseErr = assertThrows(ApiException::class.java) {
            SseParser.parseDataPayload("""{"error":{"code":"rate_limit","message":"Too many requests"}}""")
        }
        assertEquals("rate_limit", sseErr.code)
        assertEquals("Too many requests", sseErr.message)

        val single = SseParser.parseSingleCompletionJson(
            """{"choices":[{"message":{"reasoning":"thought","content":"final answer"}}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}""",
            200,
        )
        assertEquals("final answer", single.content)
        assertEquals("thought", single.reasoning)
        assertEquals(5, single.usage?.total_tokens)

        val jsonErr = assertThrows(ApiException::class.java) {
            SseParser.parseSingleCompletionJson("""{"error":{"type":"invalid_request_error","message":"maximum context length exceeded"}}""", 400)
        }
        assertTrue(jsonErr.isContextTooLong)
    }
}
