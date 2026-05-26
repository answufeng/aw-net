package com.answufeng.net.http.model

import com.answufeng.net.http.annotations.ResponseFields
import com.answufeng.net.http.config.NetworkConfig

fun ResponseFields.toMapping(base: ResponseFieldMapping): ResponseFieldMapping {
    return base.copy(
        codeKey = codeKey.ifBlank { base.codeKey },
        msgKey = msgKey.ifBlank { base.msgKey },
        dataKey = dataKey.ifBlank { base.dataKey },
        successCode = if (successCode != Int.MIN_VALUE) successCode else base.successCode,
    )
}

fun ResponseFieldMapping.Companion.codeMsgData(successCode: Int = 0): ResponseFieldMapping =
    ResponseFieldMapping(successCode = successCode)

fun ResponseFieldMapping.Companion.statusMessageData(successCode: Int = 200): ResponseFieldMapping =
    ResponseFieldMapping(
        codeKey = "status",
        msgKey = "message",
        dataKey = "data",
        successCode = successCode,
    )

fun ResponseFieldMapping.Companion.booleanStatusMessageData(successCode: Int = 0): ResponseFieldMapping =
    statusMessageData(successCode = successCode)

fun NetworkConfig.Builder.responseFields(
    code: String = "code",
    msg: String = "msg",
    data: String = "data",
    successCode: Int = 0,
): NetworkConfig.Builder {
    responseFieldMapping =
        ResponseFieldMapping(
            codeKey = code,
            msgKey = msg,
            dataKey = data,
            successCode = successCode,
        )
    defaultSuccessCode = successCode
    return this
}
