package com.gugachat.app.core.util

import com.gugachat.app.core.util.JsonUtils.arrOrNull
import com.gugachat.app.core.util.JsonUtils.boolOrNull
import com.gugachat.app.core.util.JsonUtils.doubleOrNull
import com.gugachat.app.core.util.JsonUtils.floatOrNull
import com.gugachat.app.core.util.JsonUtils.intOrNull
import com.gugachat.app.core.util.JsonUtils.longOrNull
import com.gugachat.app.core.util.JsonUtils.objOrNull
import com.gugachat.app.core.util.JsonUtils.strListOrEmpty
import com.gugachat.app.core.util.JsonUtils.strOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {

    @Serializable
    private data class SamplePayload(val name: String = "default", val count: Int = 1)

    @Test
    fun jsonObjectExtractors_handlePrimitivesStringCoercionsAndNulls() {
        val obj = CoreAppJson.parseToJsonElement(
            """
            {
              "nested": {"k": "v"},
              "items": [" a ", "", "b", null],
              "flagBool": true,
              "flagStrTrue": "1",
              "flagStrFalse": "FALSE",
              "flagInvalid": "maybe",
              "intVal": 42,
              "intStr": " 19 ",
              "longVal": 9876543210,
              "dblStr": "3.25",
              "fltVal": 1.5,
              "nullField": null
            }
            """.trimIndent(),
        ) as JsonObject

        assertEquals("v", obj.objOrNull("nested")?.strOrNull("k"))
        assertNull(obj.objOrNull("items"))
        assertEquals(4, obj.arrOrNull("items")?.size)
        assertEquals(true, obj.boolOrNull("flagBool"))
        assertEquals(true, obj.boolOrNull("flagStrTrue"))
        assertEquals(false, obj.boolOrNull("flagStrFalse"))
        assertNull(obj.boolOrNull("flagInvalid"))
        assertNull(obj.boolOrNull("nullField"))
        assertEquals(42, obj.intOrNull("intVal"))
        assertEquals(19, obj.intOrNull("intStr"))
        assertEquals(9876543210L, obj.longOrNull("longVal"))
        assertEquals(3.25, obj.doubleOrNull("dblStr")!!, 0.0001)
        assertEquals(1.5f, obj.floatOrNull("fltVal")!!, 0.0001f)
        assertEquals(listOf("a", "b"), obj.strListOrEmpty("items", trimAndFilterEmpty = true))
        assertEquals(listOf(" a ", "", "b"), obj.strListOrEmpty("items", trimAndFilterEmpty = false))
    }

    @Test
    fun decodeHelpers_andFlexibleTimestamps_behaveDeterministically() {
        val decoded = JsonUtils.decodeOrDefault("""{"name":"ok","count":7}""", SamplePayload())
        assertEquals(SamplePayload("ok", 7), decoded)
        assertEquals(SamplePayload(), JsonUtils.decodeOrDefault("not json", SamplePayload()))
        assertEquals(SamplePayload(), JsonUtils.decodeOrDefault("   ", SamplePayload()))

        assertEquals(
            listOf("alpha", "beta"),
            JsonUtils.decodeStringListJson("""[" alpha ", "", "beta"]""", trimAndFilterEmpty = true),
        )
        assertEquals(
            listOf(" alpha ", "", "beta"),
            JsonUtils.decodeStringListJson("""[" alpha ", "", "beta"]""", trimAndFilterEmpty = false),
        )
        assertTrue(JsonUtils.decodeStringListJson("bad").isEmpty())

        assertEquals(1700000000000L, JsonUtils.parseFlexibleTimestamp(JsonPrimitive(1700000000L)))
        assertEquals(1700000000123L, JsonUtils.parseFlexibleTimestamp(JsonPrimitive("1700000000123")))
        assertEquals(1700000000000L, JsonUtils.parseFlexibleTimestamp(JsonPrimitive("2023-11-14T22:13:20Z")))
        assertNull(JsonUtils.parseFlexibleTimestamp(JsonNull))
        assertNull(JsonUtils.parseFlexibleTimestamp(JsonPrimitive("not-a-timestamp")))
    }
}
