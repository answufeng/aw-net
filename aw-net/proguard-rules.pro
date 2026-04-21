# aw-net ProGuard Rules
# 此文件用于库自身的 release 构建混淆规则
# Consumer-facing rules（供使用者混淆时使用）位于 consumer-rules.pro

# ===========================================================
# 保留公共 API 和网络库相关类
# ===========================================================

# 保留所有公共类
-keep class com.answufeng.net.** { *; }

# 保留 OkHttp 相关类
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 保留 Retrofit 相关类
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# 保留 Kotlin 反射和元数据
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }

# ===========================================================
# 保留枚举和 sealed class
# ===========================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * {
    @kotlin.Metadata *;
}

# ===========================================================
# 保留 Coroutines 相关
# ===========================================================

-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
