# aw-net consumer ProGuard rules

# OkHttp (ships its own rules, only suppress warnings for optional deps)
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit (ships its own rules, keep interface methods for reflection)
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions, *Annotation*

# Gson: keep model fields used with @SerializedName
-keep class * implements com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# Keep Gson @SerializedName annotated fields
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Hilt (ships its own rules)
-dontwarn dagger.hilt.**

# Kotlin metadata (needed for reflection)
-keep class kotlin.Metadata { *; }

# ── aw-net public API ──────────────────────────────────────

# Models: NetworkResult, NetCode, NetEvent, ProgressInfo, etc.
-keep class com.answufeng.net.http.model.** { *; }

# Annotations: NetworkConfig, Retry, Timeout, BaseUrl, etc.
-keep class com.answufeng.net.http.annotations.** { *; }

# Auth interfaces (consumers implement TokenProvider / UnauthorizedHandler)
-keep interface com.answufeng.net.http.auth.TokenProvider { *; }
-keep interface com.answufeng.net.http.auth.UnauthorizedHandler { *; }
-keep class com.answufeng.net.http.auth.InMemoryTokenProvider { *; }
-keep class com.answufeng.net.http.auth.TokenAuthenticator { *; }

# Exception hierarchy (consumers may catch specific subtypes)
-keep class com.answufeng.net.http.exception.** { *; }

# NetworkExecutor: primary entry point
-keep class com.answufeng.net.http.util.NetworkExecutor { *; }

# Executors that consumers may inject directly
-keep class com.answufeng.net.http.util.RequestExecutor { *; }
-keep class com.answufeng.net.http.util.DownloadExecutor { *; }
-keep class com.answufeng.net.http.util.UploadExecutor { *; }

# Utility classes that consumers instantiate directly
-keep class com.answufeng.net.http.util.RequestDedup { *; }
-keep class com.answufeng.net.http.util.RequestThrottle { *; }
-keep class com.answufeng.net.http.util.PollingKt { *; }
-keep class com.answufeng.net.http.util.NetErrorMessage { *; }
-keep class com.answufeng.net.http.util.HashVerificationStrategy { *; }
-keep class com.answufeng.net.http.util.NetworkMonitor { *; }
-keep class com.answufeng.net.http.util.NetworkClientFactory { *; }

# WebSocket public API
-keep interface com.answufeng.net.websocket.WebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager$* { *; }
-keep class com.answufeng.net.websocket.WebSocketLogLevel { *; }
-keep interface com.answufeng.net.websocket.WebSocketLogger { *; }

# NetworkResult extension functions
-keep class com.answufeng.net.http.model.NetworkResultExtKt { *; }

# Kotlin metadata
-keepattributes Signature
-keepattributes *Annotation*
