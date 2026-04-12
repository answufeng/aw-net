# brick-net consumer ProGuard rules

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

# aw-net public API and models
-keep class com.answufeng.net.http.model.** { *; }
-keep class com.answufeng.net.http.annotations.** { *; }
-keep interface com.answufeng.net.websocket.IWebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager$* { *; }
-keep class com.answufeng.net.http.util.NetworkExecutor { *; }
-keep class com.answufeng.net.http.auth.TokenProvider { *; }
-keep class com.answufeng.net.http.auth.UnauthorizedHandler { *; }
-keep class com.answufeng.net.http.auth.InMemoryTokenProvider { *; }
-keep class com.answufeng.net.http.auth.TokenAuthenticator { *; }
-keep class com.answufeng.net.http.exception.BaseNetException { *; }
-keep class com.answufeng.net.http.interceptor.PrettyNetLogger { *; }
-keep class com.answufeng.net.http.util.RequestDedup { *; }
-keep class com.answufeng.net.http.util.RequestThrottle { *; }
-keep class com.answufeng.net.websocket.WebSocketClientImpl { *; }
-keep class com.answufeng.net.websocket.WebSocketLogger { *; }

# Kotlin metadata
-keepattributes Signature
-keepattributes *Annotation*
