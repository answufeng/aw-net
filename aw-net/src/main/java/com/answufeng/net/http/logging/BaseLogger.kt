package com.answufeng.net.http.logging

interface BaseLogger {
    fun d(
        tag: String,
        msg: String,
    )

    fun i(
        tag: String,
        msg: String,
    ) {}

    fun w(
        tag: String,
        msg: String,
        throwable: Throwable? = null,
    ) {}

    fun e(
        tag: String,
        msg: String,
        throwable: Throwable? = null,
    )
}
