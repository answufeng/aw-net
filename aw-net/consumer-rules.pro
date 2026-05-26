# aw-net 2.0 consumer R8 rules.
# Keep only public API, runtime annotations, Gson adapters, and Retrofit metadata.

-keepattributes Signature, Exceptions, *Annotation*, EnclosingMethod, InnerClasses
-keep class kotlin.Metadata { *; }

# Retrofit service methods are discovered through runtime annotations.
-keep,allowobfuscation,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# Gson extension points and @SerializedName model fields.
-keep class * implements com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# aw-net runtime annotations.
-keep @interface com.answufeng.net.http.annotations.**
-keep @interface com.answufeng.net.websocket.annotations.**

# Main public entry points.
-keep class com.answufeng.net.AwNet { *; }
-keep class com.answufeng.net.BuildConfig { *; }
-keep class com.answufeng.net.http.config.NetworkConfig { *; }
-keep class com.answufeng.net.http.config.NetworkConfig$Builder { *; }
-keep class com.answufeng.net.http.config.NetworkConfigProvider { *; }
-keep class com.answufeng.net.http.config.NetworkLogLevel { *; }
-keep class com.answufeng.net.http.config.CertificatePin { *; }

-keep interface com.answufeng.net.http.model.BaseResponse { *; }
-keep class com.answufeng.net.http.model.GlobalResponse { *; }
-keep class com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory { *; }
-keep class com.answufeng.net.http.converter.UnwrapResponseConverterFactory { *; }
-keep class com.answufeng.net.http.converter.UnwrapResponseConverterFactory$* { *; }
-keep class com.answufeng.net.http.model.NetworkResult { *; }
-keep class com.answufeng.net.http.model.NetworkResult$* { *; }
-keep class com.answufeng.net.http.model.RequestOption { *; }
-keep class com.answufeng.net.http.model.ResponseFieldMapping { *; }
-keep class com.answufeng.net.http.model.ProgressInfo { *; }
-keepclassmembers class com.answufeng.net.http.model.NetworkResultExtKt {
    public *;
}
-keepclassmembers class com.answufeng.net.http.model.RequestOptionDslKt {
    public *;
}

-keep interface com.answufeng.net.http.auth.TokenProvider { *; }
-keep interface com.answufeng.net.http.auth.UnauthorizedHandler { *; }
-keep class com.answufeng.net.http.auth.InMemoryTokenProvider { *; }

-keep class com.answufeng.net.http.exception.BaseNetException { *; }
-keepclasseswithmembers class com.answufeng.net.http.exception.** {
    <init>(...);
}

-keepclassmembers class com.answufeng.net.http.util.NetworkExecutor {
    public *;
}
-keepclassmembers class com.answufeng.net.http.util.RequestExecutor {
    public *;
}
-keepclassmembers class com.answufeng.net.http.util.DownloadExecutor {
    public *;
}
-keepclassmembers class com.answufeng.net.http.util.UploadExecutor {
    public *;
}
-keep class com.answufeng.net.http.util.DownloadFailureStrategy { *; }
-keep class com.answufeng.net.http.util.HashVerificationStrategy { *; }
-keep interface com.answufeng.net.http.util.NetworkClientFactory { *; }
-keep interface com.answufeng.net.http.logging.NetLogger { *; }
-keep interface com.answufeng.net.http.tracking.NetTracker { *; }
-keepclassmembers class com.answufeng.net.http.util.NetEventDispatcher {
    public *;
}

-keep interface com.answufeng.net.websocket.WebSocketManager { *; }
-keep class com.answufeng.net.websocket.WebSocketManager$* { *; }
-keep class com.answufeng.net.websocket.WebSocketLogLevel { *; }
-keep interface com.answufeng.net.websocket.WebSocketLogger { *; }

-dontwarn okhttp3.internal.**
-dontwarn okio.**
-dontwarn retrofit2.internal.**
-dontwarn dagger.hilt.internal.**
