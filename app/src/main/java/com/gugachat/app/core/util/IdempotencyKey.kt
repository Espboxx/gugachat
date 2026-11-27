package com.gugachat.app.core.util

import java.util.UUID

fun newIdempotencyKey(): String = UUID.randomUUID().toString()

