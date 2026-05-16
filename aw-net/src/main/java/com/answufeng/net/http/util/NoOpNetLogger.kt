package com.answufeng.net.http.util

import com.answufeng.net.http.logging.NetLogger

object NoOpNetLogger : NetLogger {
    override fun d(
        tag: String,
        msg: String,
    ) = Unit

    override fun i(
        tag: String,
        msg: String,
    ) = Unit

    override fun w(
        tag: String,
        msg: String,
        throwable: Throwable?,
    ) = Unit

    override fun e(
        tag: String,
        msg: String,
        throwable: Throwable?,
    ) = Unit
}
