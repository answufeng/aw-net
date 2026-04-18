package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.util.concurrent.atomic.AtomicReference

class SuccessCodeInterceptor : Interceptor {

    companion object {
        private val successCodeHolder = AtomicReference<Int?>(null)

        internal fun getAndClearSuccessCode(): Int? {
            return successCodeHolder.getAndSet(null)
        }

        internal fun setSuccessCode(code: Int) {
            successCodeHolder.set(code)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val successCode = invocation?.method()?.getAnnotation(SuccessCode::class.java)?.value

        if (successCode != null) {
            successCodeHolder.set(successCode)
        }

        return chain.proceed(request)
    }
}
