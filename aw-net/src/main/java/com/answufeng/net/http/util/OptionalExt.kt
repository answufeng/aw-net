@file:Suppress("NewApi")

package com.answufeng.net.http.util

import java.util.Optional

internal fun <T> Optional<T>.getOrNull(): T? = if (isPresent) get() else null

internal fun <T> Optional<T>.orDefault(default: T): T = if (isPresent) get() else default

