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

# brick-net public API and models
-keep class com.ail.brick.net.http.model.** { *; }
-keep class com.ail.brick.net.http.annotations.** { *; }
-keep interface com.ail.brick.net.websocket.IWebSocketManager { *; }
-keep class com.ail.brick.net.websocket.WebSocketManager { *; }
-keep class com.ail.brick.net.websocket.WebSocketManager$* { *; }
-keep class com.ail.brick.net.http.NetworkExecutor { *; }
-keep class com.ail.brick.net.http.auth.TokenProvider { *; }

# Kotlin metadata
-keepattributes Signature
-keepattributes *Annotation*
