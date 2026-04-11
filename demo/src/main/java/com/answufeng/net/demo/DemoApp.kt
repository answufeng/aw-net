package com.answufeng.net.demo

import android.app.Application
import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkLogLevel
import com.answufeng.net.http.auth.InMemoryTokenProvider
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class DemoApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object DemoNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig(
            baseUrl = "https://jsonplaceholder.typicode.com/",
            isLogEnabled = true,
            networkLogLevel = NetworkLogLevel.BODY
        )
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
            override fun onUnauthorized() {}
        }
    }
}
