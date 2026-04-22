package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Invocation
import java.util.concurrent.atomic.AtomicReference

class SuccessCodeInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private interface Annotated {
        @SuccessCode(201)
        fun withSuccessCode()
    }

    @Test
    fun `applies SuccessCodeTag to request when method has SuccessCode annotation`() {
        val tagHolder = AtomicReference<SuccessCodeInterceptor.SuccessCodeTag?>()
        val method = Annotated::class.java.getMethod("withSuccessCode")
        val invocation = Invocation.of(method, emptyList<Any>())

        val client = OkHttpClient.Builder()
            .addInterceptor(SuccessCodeInterceptor())
            .addInterceptor(Interceptor { chain ->
                tagHolder.set(chain.request().tag(SuccessCodeInterceptor.SuccessCodeTag::class.java))
                chain.proceed(chain.request())
            })
            .build()

        val request = Request.Builder()
            .url(server.url("/"))
            .tag(Invocation::class.java, invocation)
            .build()

        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(request).execute().close()

        assertEquals(201, tagHolder.get()?.code)
    }

    @Test
    fun `does not set tag when no SuccessCode annotation`() {
        val tagHolder = AtomicReference<SuccessCodeInterceptor.SuccessCodeTag?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(SuccessCodeInterceptor())
            .addInterceptor(Interceptor { chain ->
                tagHolder.set(chain.request().tag(SuccessCodeInterceptor.SuccessCodeTag::class.java))
                chain.proceed(chain.request())
            })
            .build()

        val request = Request.Builder().url(server.url("/")).build()
        server.enqueue(MockResponse().setResponseCode(200).setBody("x"))
        client.newCall(request).execute().close()

        assertNull(tagHolder.get())
    }
}
