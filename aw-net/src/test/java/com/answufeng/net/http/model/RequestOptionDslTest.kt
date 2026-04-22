package com.answufeng.net.http.model

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestOptionDslTest {

    @Test
    fun `requestOption builds expected RequestOption`() {
        val opt = requestOption {
            tag = "t1"
            successCode = 200
            retryOnFailure = 2
            retryDelayMs = 500L
            retryOnBusiness = true
            dispatcher = Dispatchers.Unconfined
        }
        assertEquals("t1", opt.tag)
        assertEquals(200, opt.successCode)
        assertEquals(2, opt.retryOnFailure)
        assertEquals(500L, opt.retryDelayMs)
        assertEquals(true, opt.retryOnBusiness)
        assertEquals(Dispatchers.Unconfined, opt.dispatcher)
    }
}
