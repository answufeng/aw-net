package com.answufeng.net.http.annotations

import java.util.concurrent.TimeUnit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timeout(
    val connect: Int = -1,
    val read: Int = -1,
    val write: Int = -1,
    val unit: TimeUnit = TimeUnit.SECONDS,
    val totalMs: Long = -1L,
)
