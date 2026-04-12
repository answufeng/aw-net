package com.answufeng.net.http.integration

import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.auth.InMemoryTokenProvider
import com.answufeng.net.http.auth.TokenAuthenticator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.interceptor.ExtraHeadersInterceptor
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.model.IBaseResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ResponseFieldMapping
import com.answufeng.net.http.model.onFailure
import com.answufeng.net.http.model.onSuccess
import com.answufeng.net.http.model.onSuccessNotNull
import com.answufeng.net.http.util.RequestExecutor
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Optional
import java.util.concurrent.TimeUnit

data class TestUser(val name: String, val age: Int)

data class TestResponse(
    override val code: Int,
    override val msg: String,
    override val data: TestUser?
) : IBaseResponse<TestUser>

data class TestStringResponse(
    override val code: Int,
    override val msg: String,
    override val data: String?
) : IBaseResponse<String>

interface TestApi {
    @GET("user")
    suspend fun getUser(): TestResponse

    @GET("raw")
    suspend fun getRawUser(): TestUser

    @POST("login")
    suspend fun login(@Header("Authorization") token: String): TestStringResponse
}

class MockWebServerIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var configProvider: NetworkConfigProvider
    private val gson = GsonBuilder()
        .registerTypeAdapterFactory(GlobalResponseTypeAdapterFactory { ResponseFieldMapping() })
        .create()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        configProvider = NetworkConfigProvider(
            NetworkConfig(baseUrl = server.url("/").toString())
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun buildOkHttpClient(
        tokenProvider: InMemoryTokenProvider? = null,
        unauthorizedHandler: UnauthorizedHandler? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)

        builder.addInterceptor(ExtraHeadersInterceptor(configProvider))

        if (tokenProvider != null) {
            builder.authenticator(
                TokenAuthenticator(tokenProvider, unauthorizedHandler = unauthorizedHandler)
            )
        }

        return builder.build()
    }

    private fun successJson(data: String = """{"name":"Alice","age":25}"""): String {
        return """{"code":0,"msg":"ok","data":$data}"""
    }

    private fun businessErrorJson(code: Int = 1001, msg: String = "error"): String {
        return """{"code":$code,"msg":"$msg","data":null}"""
    }

    private fun unauthorizedJson(): String {
        return """{"code":401,"msg":"unauthorized","data":null}"""
    }

    private fun makeUnauthorizedHandler(flag: BooleanArray): UnauthorizedHandler {
        return object : UnauthorizedHandler {
            override fun onUnauthorized() {
                flag[0] = true
            }
        }
    }

    // ==================== 基本请求流程 ====================

    @Test
    fun `executeRequest - success response returns Success`() = runTest {
        server.enqueue(MockResponse().setBody(successJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertNotNull(data)
        assertEquals("Alice", data?.name)
        assertEquals(25, data?.age)
    }

    @Test
    fun `executeRequest - business failure returns BusinessFailure`() = runTest {
        server.enqueue(MockResponse().setBody(businessErrorJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(1001, (result as NetworkResult.BusinessFailure).code)
        assertEquals("error", result.msg)
    }

    @Test
    fun `executeRequest - HTTP error returns TechnicalFailure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
    }

    @Test
    fun `executeRequest - malformed JSON returns TechnicalFailure`() = runTest {
        server.enqueue(MockResponse().setBody("not valid json{{{"))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
    }

    // ==================== executeRawRequest ====================

    @Test
    fun `executeRawRequest - success returns raw data`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"Bob","age":30}"""))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRawRequest(dispatcher = Dispatchers.Unconfined) {
            api.getRawUser()
        }

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertNotNull(data)
        assertEquals("Bob", data?.name)
        assertEquals(30, data?.age)
    }

    // ==================== 自定义成功码 ====================

    @Test
    fun `executeRequest - custom success code`() = runTest {
        val json = """{"code":200,"msg":"ok","data":{"name":"Charlie","age":35}}"""
        server.enqueue(MockResponse().setBody(json))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(
            successCode = 200,
            dispatcher = Dispatchers.Unconfined
        ) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("Charlie", (result as NetworkResult.Success).data?.name)
    }

    @Test
    fun `executeRequest - default success code 0 treats 200 as business failure`() = runTest {
        val json = """{"code":200,"msg":"ok","data":{"name":"Charlie","age":35}}"""
        server.enqueue(MockResponse().setBody(json))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(200, (result as NetworkResult.BusinessFailure).code)
    }

    // ==================== ExtraHeaders 拦截器 ====================

    @Test
    fun `extra headers are added to request`() = runTest {
        server.enqueue(MockResponse().setBody(successJson()))

        configProvider.updateConfig(
            configProvider.current.copy(
                extraHeaders = mapOf(
                    "X-App-Version" to "1.0.0",
                    "X-Platform" to "android"
                )
            )
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        val request = server.takeRequest()
        assertEquals("1.0.0", request.getHeader("X-App-Version"))
        assertEquals("android", request.getHeader("X-Platform"))
    }

    // ==================== Token 刷新流程 ====================

    @Test
    fun `token refresh on 401 - success`() = runTest {
        lateinit var tokenProvider: InMemoryTokenProvider
        tokenProvider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            tokenProvider.setAccessToken("new_token")
            true
        }

        val unauthorizedFlag = booleanArrayOf(false)
        val handler = makeUnauthorizedHandler(unauthorizedFlag)

        server.enqueue(MockResponse().setBody(unauthorizedJson()))
        server.enqueue(MockResponse().setBody(successJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.of(tokenProvider),
            unauthorizedHandlerOptional = Optional.of(handler)
        )

        val client = buildOkHttpClient(tokenProvider = tokenProvider, unauthorizedHandler = handler)
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.Success)
        assertFalse(unauthorizedFlag[0])
    }

    @Test
    fun `token refresh failure triggers UnauthorizedHandler`() = runTest {
        val tokenProvider = InMemoryTokenProvider(initialAccessToken = "bad_token") { false }

        val unauthorizedFlag = booleanArrayOf(false)
        val handler = makeUnauthorizedHandler(unauthorizedFlag)

        server.enqueue(MockResponse().setBody(unauthorizedJson()))
        server.enqueue(MockResponse().setBody(unauthorizedJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.of(tokenProvider),
            unauthorizedHandlerOptional = Optional.of(handler)
        )

        val client = buildOkHttpClient(tokenProvider = tokenProvider, unauthorizedHandler = handler)
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(401, (result as NetworkResult.BusinessFailure).code)
        assertTrue(unauthorizedFlag[0])
    }

    @Test
    fun `no TokenProvider - 401 returns BusinessFailure without refresh`() = runTest {
        val unauthorizedFlag = booleanArrayOf(false)
        val handler = makeUnauthorizedHandler(unauthorizedFlag)

        server.enqueue(MockResponse().setBody(unauthorizedJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.of(handler)
        )

        val client = buildOkHttpClient(unauthorizedHandler = handler)
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(401, (result as NetworkResult.BusinessFailure).code)
        assertTrue(unauthorizedFlag[0])
    }

    // ==================== NetworkResult 扩展函数 ====================

    @Test
    fun `NetworkResult extensions work with real response`() = runTest {
        server.enqueue(MockResponse().setBody(successJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        var successCalled = false
        var failureCalled = false

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        result.onSuccess { successCalled = true }
        result.onFailure { failureCalled = true }

        assertTrue(successCalled)
        assertFalse(failureCalled)
    }

    @Test
    fun `onSuccessNotNull skips null data`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"ok","data":null}"""))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        var successCalled = false
        var successNotNullCalled = false

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        result.onSuccess { successCalled = true }
        result.onSuccessNotNull { successNotNullCalled = true }

        assertTrue(successCalled)
        assertFalse(successNotNullCalled)
    }

    // ==================== 并发请求 ====================

    @Test
    fun `multiple sequential requests work correctly`() = runTest {
        server.enqueue(MockResponse().setBody(successJson("""{"name":"User1","age":20}""")))
        server.enqueue(MockResponse().setBody(successJson("""{"name":"User2","age":30}""")))
        server.enqueue(MockResponse().setBody(businessErrorJson()))

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val result1 = executor.executeRequest(dispatcher = Dispatchers.Unconfined) { api.getUser() }
        val result2 = executor.executeRequest(dispatcher = Dispatchers.Unconfined) { api.getUser() }
        val result3 = executor.executeRequest(dispatcher = Dispatchers.Unconfined) { api.getUser() }

        assertTrue(result1 is NetworkResult.Success)
        assertEquals("User1", (result1 as NetworkResult.Success).data?.name)

        assertTrue(result2 is NetworkResult.Success)
        assertEquals("User2", (result2 as NetworkResult.Success).data?.name)

        assertTrue(result3 is NetworkResult.BusinessFailure)
    }

    // ==================== 动态配置更新 ====================

    @Test
    fun `config update changes extra headers dynamically`() = runTest {
        server.enqueue(MockResponse().setBody(successJson()))
        server.enqueue(MockResponse().setBody(successJson()))

        configProvider.updateConfig(
            configProvider.current.copy(
                extraHeaders = mapOf("X-Version" to "1.0")
            )
        )

        val client = buildOkHttpClient()
        val api = buildRetrofit(client).create(TestApi::class.java)

        val executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        executor.executeRequest(dispatcher = Dispatchers.Unconfined) { api.getUser() }
        val request1 = server.takeRequest()
        assertEquals("1.0", request1.getHeader("X-Version"))

        configProvider.updateConfig(
            configProvider.current.copy(
                extraHeaders = mapOf("X-Version" to "2.0")
            )
        )

        executor.executeRequest(dispatcher = Dispatchers.Unconfined) { api.getUser() }
        val request2 = server.takeRequest()
        assertEquals("2.0", request2.getHeader("X-Version"))
    }

    // ==================== 网络不可达 ====================

    @Test
    fun `connection failure returns TechnicalFailure`() = runTest {
        val unreachableConfig = NetworkConfigProvider(
            NetworkConfig(baseUrl = "http://192.0.2.1:9999/")
        )

        val executor = RequestExecutor(
            configProvider = unreachableConfig,
            tokenProviderOptional = Optional.empty(),
            unauthorizedHandlerOptional = Optional.empty()
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl("http://192.0.2.1:9999/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(TestApi::class.java)

        val result = executor.executeRequest(dispatcher = Dispatchers.Unconfined) {
            api.getUser()
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
    }
}
