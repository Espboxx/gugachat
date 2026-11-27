package com.gugachat.app.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val CoreAppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

object JsonUtils {
    private const val EPOCH_SECONDS_THRESHOLD = 10_000_000_000L

    fun JsonObject.objOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    fun JsonObject.arrOrNull(key: String): JsonArray? =
        this[key] as? JsonArray

    fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    fun JsonObject.boolOrNull(key: String): Boolean? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        prim.booleanOrNull?.let { return it }
        return when (prim.contentOrNull?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    fun JsonObject.intOrNull(key: String): Int? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.intOrNull ?: prim.contentOrNull?.trim()?.toIntOrNull()
    }

    fun JsonObject.longOrNull(key: String): Long? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.longOrNull ?: prim.contentOrNull?.trim()?.toLongOrNull()
    }

    fun JsonObject.doubleOrNull(key: String): Double? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.doubleOrNull ?: prim.contentOrNull?.trim()?.toDoubleOrNull()
    }

    fun JsonObject.floatOrNull(key: String): Float? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.floatOrNull ?: prim.contentOrNull?.trim()?.toFloatOrNull()
    }

    fun JsonObject.strListOrEmpty(key: String, trimAndFilterEmpty: Boolean = true): List<String> {
        val arr = this[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val raw = (el as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull ?: return@mapNotNull null
            if (trimAndFilterEmpty) raw.trim().takeIf { it.isNotEmpty() } else raw
        }
    }

    inline fun <reified T> decodeOrDefault(raw: String?, fallback: T, json: Json = CoreAppJson): T {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return fallback
        return try {
            json.decodeFromString<T>(clean)
        } catch (_: Exception) {
            fallback
        }
    }

    fun decodeStringListJson(rawJson: String?, trimAndFilterEmpty: Boolean = true, json: Json = CoreAppJson): List<String> {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(trimmed) as? JsonArray ?: return@runCatching emptyList()
            arr.mapNotNull { el ->
                val prim = el as? JsonPrimitive ?: return@mapNotNull null
                if (prim is JsonNull) return@mapNotNull null
                val raw = prim.contentOrNull ?: return@mapNotNull null
                if (trimAndFilterEmpty) raw.trim().takeIf { it.isNotEmpty() } else raw
            }
        }.getOrDefault(emptyList())
    }

    fun parseFlexibleTimestamp(elem: JsonElement?): Long? {
        val prim = elem as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        if (prim.isString) {
            val s = prim.content.trim()
            if (s.isEmpty()) return null
            s.toLongOrNull()?.let { return if (it < EPOCH_SECONDS_THRESHOLD) it * 1000L else it }
            s.toDoubleOrNull()?.let { d ->
                val v = d.toLong()
                return if (v < EPOCH_SECONDS_THRESHOLD) (d * 1000.0).toLong() else v
            }
            runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            runCatching { return Instant.parse(s).toEpochMilli() }
            runCatching {
                val normalized = s.replace(' ', 'T')
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            return null
        }
        prim.longOrNull?.let { return if (it < EPOCH_SECONDS_THRESHOLD) it * 1000L else it }
        prim.doubleOrNull?.let { d ->
            val v = d.toLong()
            return if (v < EPOCH_SECONDS_THRESHOLD) (d * 1000.0).toLong() else v
        }
        return null
    }
}
