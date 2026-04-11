package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.net.http.auth.TokenProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    @Inject lateinit var tokenProvider: TokenProvider

    private val tv by lazy { TextView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Token Auth"
        val scrollView = ScrollView(this)
        scrollView.setPadding(24, 24, 24, 24)
        scrollView.addView(tv)
        setContentView(scrollView)

        val sb = StringBuilder()
        sb.appendLine("=== Token Auth Demo ===")
        sb.appendLine()
        sb.appendLine("1. TokenProvider provides access token for requests")
        sb.appendLine("2. When server returns 401, TokenAuthenticator auto-refreshes token")
        sb.appendLine("3. After refresh, original request is retried with new token")
        sb.appendLine("4. If refresh fails, UnauthorizedHandler.onUnauthorized() is called")
        sb.appendLine()
        sb.appendLine("Current token: ${tokenProvider.getAccessToken() ?: "null"}")
        sb.appendLine()
        sb.appendLine("Usage:")
        sb.appendLine("""
            |@Module
            |@InstallIn(SingletonComponent::class)
            |object AuthModule {
            |    @Provides @Singleton
            |    fun provideTokenProvider(): TokenProvider {
            |        return InMemoryTokenProvider().apply {
            |            setAccessToken("your-access-token")
            |        }
            |    }
            |
            |    @Provides @Singleton
            |    fun provideUnauthorizedHandler(): UnauthorizedHandler {
            |        return object : UnauthorizedHandler {
            |            override fun onUnauthorized() {
            |                // Navigate to login
            |            }
            |        }
            |    }
            |}
        """.trimMargin())

        tv.text = sb.toString()
    }
}
