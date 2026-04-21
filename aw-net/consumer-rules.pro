# aw-net consumer ProGuard rules
# 此文件通过 AAR 自动传递给消费方，保留库的公共 API 不被混淆。

# ===========================================================
# 第三方库规则
# ===========================================================

# OkHttp / Okio（自带混淆规则，仅抑制可选依赖警告）
-dontwarn okhttp3.internal.**
-dontwarn okio.**

# Retrofit（自带混淆规则，保留反射所需属性）
-dontwarn retrofit2.internal.**

# Hilt（自带混淆规则）
-dontwarn dagger.hilt.internal.**

# 保留 Gson 序列化所需的注解和字段
-keep class * implements com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留 Kotlin 元数据和反射所需属性
-keep class kotlin.Metadata { *; }
-keepattributes Signature, Exceptions, *Annotation*, EnclosingMethod, InnerClasses

# ===========================================================
# aw-net 公共 API 保留规则
# ===========================================================

# ── 核心配置类 ─────────────────────────────────────────────

# NetworkConfig：配置入口类，消费方直接引用
-keep class com.answufeng.net.http.config.NetworkConfig { *; }
-keep class com.answufeng.net.http.config.NetworkConfig$Builder { *; }
-keep class com.answufeng.net.http.config.NetworkConfigProvider { *; }
-keep class com.answufeng.net.http.config.NetworkLogLevel { *; }
-keep class com.answufeng.net.http.config.CertificatePin { *; }

# ── 注解类（运行时反射需要） ────────────────────────────────

-keep @interface com.answufeng.net.http.annotations.*
-keep class com.answufeng.net.http.annotations.** { *; }

# ── 响应模型类 ─────────────────────────────────────────────

# GlobalResponse：最常用的响应实现
-keep class com.answufeng.net.http.model.GlobalResponse { *; }

# BaseResponse 接口（消费方可能自行实现）
-keep interface com.answufeng.net.http.model.BaseResponse { *; }

# NetworkResult 密封类及其子类（消费方直接使用）
-keep class com.answufeng.net.http.model.NetworkResult { *; }
-keep class com.answufeng.net.http.model.NetworkResult$Success { *; }
-keep class com.answufeng.net.http.model.NetworkResult$TechnicalFailure { *; }
-keep class com.answufeng.net.http.model.NetworkResult$BusinessFailure { *; }

# 其他模型类（消费方直接引用）
-keep class com.answufeng.net.http.model.NetCode { *; }
-keep class com.answufeng.net.http.model.NetEvent { *; }
-keep class com.answufeng.net.http.model.ProgressInfo { *; }
-keep class com.answufeng.net.http.model.RequestOption { *; }
-keep class com.answufeng.net.http.model.ResponseFieldMapping { *; }

# 扩展函数所在文件
-keep class com.answufeng.net.http.model.NetworkResultExtKt { *; }
-keep class com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory { *; }

# ── 认证模块 ───────────────────────────────────────────────

-keep interface com.answufeng.net.http.auth.TokenProvider { *; }
-keep interface com.answufeng.net.http.auth.UnauthorizedHandler { *; }
-keep class com.answufeng.net.http.auth.InMemoryTokenProvider { *; }
-keep class com.answufeng.net.http.auth.TokenAuthenticator { *; }
-keep class com.answufeng.net.http.auth.TokenRefreshCoordinator { *; }

# ── 异常体系 ───────────────────────────────────────────────

-keep class com.answufeng.net.http.exception.BaseNetException { *; }
-keepclasseswithmembers class com.answufeng.net.http.exception.** {
    <init>(...);
}

# ── 执行器入口 ─────────────────────────────────────────────

-keep class com.answufeng.net.http.util.NetworkExecutor { *; }
-keep class com.answufeng.net.http.util.RequestExecutor { *; }
-keep class com.answufeng.net.http.util.DownloadExecutor { *; }
-keep class com.answufeng.net.http.util.UploadExecutor { *; }

# ── 工具类 ─────────────────────────────────────────────────

-keep class com.answufeng.net.http.util.RequestCanceller { *; }
-keep class com.answufeng.net.http.util.RequestDedup { *; }
-keep class com.answufeng.net.http.util.RequestThrottle { *; }
-keep class com.answufeng.net.http.util.PollingKt { *; }
-keep class com.answufeng.net.http.util.NetErrorMessage { *; }
-keep class com.answufeng.net.http.util.HashVerificationStrategy { *; }
-keep class com.answufeng.net.http.util.NetworkMonitor { *; }
-keep class com.answufeng.net.http.util.NetworkClientFactory { *; }
-keep class com.answufeng.net.http.util.PersistentCookieJar { *; }
-keep class com.answufeng.net.http.util.ProgressRequestBody { *; }
-keep class com.answufeng.net.http.util.ProgressResponseBody { *; }
-keep interface com.answufeng.net.http.util.RetryStrategy { *; }
-keep class com.answufeng.net.http.util.DefaultRetryStrategy { *; }
-keep class com.answufeng.net.http.util.MockInterceptor { *; }
-keep class com.answufeng.net.http.util.NetTracker { *; }
-keep class com.answufeng.net.http.util.OptionalExtKt { *; }

# ── 拦截器 ─────────────────────────────────────────────────

-keep class com.answufeng.net.http.interceptor.SuccessCodeInterceptor$SuccessCodeTag { *; }

# ── WebSocket 公共 API ────────────────────────────────────

-keep interface com.answufeng.net.websocket.WebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager$Config { *; }
-keep interface com.answufeng.net.websocket.WebSocketManager$WebSocketListener { *; }
-keep class com.answufeng.net.websocket.WebSocketManager$State { *; }
-keep class com.answufeng.net.websocket.WebSocketLogLevel { *; }
-keep interface com.answufeng.net.websocket.WebSocketLogger { *; }

# ── Retrofit 服务接口 ──────────────────────────────────────

-keep,allowobfuscation,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
