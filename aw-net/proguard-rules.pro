
# aw-net ProGuard Rules
# 此文件用于库自身的 release 构建混淆规则
# Consumer-facing rules（供使用者混淆时使用）位于 consumer-rules.pro

# ===========================================================
# 保留 Kotlin 元数据和注解
# ===========================================================

-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }

# ===========================================================
# 保留枚举
# ===========================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

