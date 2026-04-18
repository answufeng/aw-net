package com.answufeng.net.demo

import android.app.Application
import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkLogLevel
import com.answufeng.net.http.auth.InMemoryTokenProvider
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.websocket.WebSocketLogger
import com.answufeng.net.websocket.annotation.WebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@HiltAndroidApp
class DemoApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object DemoNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig.builder("https://jsonplaceholder.typicode.com/").apply {
            networkLogLevel = NetworkLogLevel.BODY
        }.build()
    }

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider {
        return InMemoryTokenProvider()
    }

    @Provides
    @Singleton
    fun provideUnauthorizedHandler(): UnauthorizedHandler {
        return object : UnauthorizedHandler {
            override fun onUnauthorized() {
                android.util.Log.w("DemoApp", "onUnauthorized: Token 刷新失败或未授权，应跳转登录页")
            }
        }
    }

    /**
     * 选配：提供 HTTP 日志实现（如接入 AwLog、Timber 等）
     */
    @Provides
    @Singleton
    fun provideNetLogger(): NetLogger = object : NetLogger {
        override fun d(tag: String, msg: String) {
            android.util.Log.d("[HTTP] $tag", msg)
        }

        override fun e(tag: String, msg: String, throwable: Throwable?) {
            android.util.Log.e("[HTTP] $tag", msg, throwable)
        }
    }

    /**
     * 选配：提供 WebSocket OkHttpClient；不提供时库内会使用默认配置。
     */
//    @Provides
//    @Singleton
//    @WebSocketClient
//    fun provideWebSocketOkHttpClient(): OkHttpClient {
//        return OkHttpClient.Builder()
//            .connectTimeout(10, TimeUnit.SECONDS)
//            .readTimeout(60, TimeUnit.SECONDS)
//            .writeTimeout(60, TimeUnit.SECONDS)
//            .pingInterval(30, TimeUnit.SECONDS)
//            .build()
//    }

    /**
     * 选配：提供 WebSocket 日志实现；与 HTTP 日志完全独立。
     */
    @Provides
    @Singleton
    fun provideWebSocketLogger(): WebSocketLogger = object : WebSocketLogger {

        override fun i(tag: String, msg: String) {
            super.i(tag, msg)
            android.util.Log.i("[WEBSOCKET] $tag", msg)
        }

        override fun w(tag: String, msg: String, throwable: Throwable?) {
            super.w(tag, msg, throwable)
            android.util.Log.w("[WEBSOCKET] $tag", msg)
        }

        override fun d(tag: String, msg: String) {
            android.util.Log.d("[WEBSOCKET] $tag", msg)
        }

        override fun e(tag: String, msg: String, throwable: Throwable?) {
            android.util.Log.e("[WEBSOCKET] $tag", msg, throwable)
        }
    }
}
