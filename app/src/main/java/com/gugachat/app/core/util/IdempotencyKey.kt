package com.gugachat.app.core.util

import java.util.UUID

fun newIdempotencyKey(): String = UUID.randomUUID().toString()

fun newShortId(prefix: String): String {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
    return "${prefix}_${System.currentTimeMillis()}_$suffix"
}
