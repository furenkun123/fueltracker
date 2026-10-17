# ===================================================================
# 1. 基础与堆栈追踪保留 (保留行号与签名，方便崩溃排查)
# ===================================================================
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# ===================================================================
# 2. 项目数据模型精准保留 (取代 com.fueltracker.data.** { *; })
# ===================================================================
# 方案 A：保留标注了 @Keep 的类与字段 (推荐在 Data Class 上加 @Keep 注解)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# 方案 B：针对 Room 数据库实体的精准保留
-keep @androidx.room.Entity class * {
    <fields>;
}
-keep @androidx.room.Dao interface *

# 方案 C：针对 kotlinx.serialization 的精准保留 (仅保留 Companion 和构造方法)
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
    *** Companion;
}


# ===================================================================
# 3. 补充说明 (已移除第三方库全量 Keep)
# ===================================================================
# 注意：已移除 -keep class io.ktor.**, -keep class okhttp3.** 等全量规则。
# Ktor, OkHttp, Compose, Room 会自动通过其内置的 Consumer Rules 介入混淆，
# 无需在此重复添加包级通配规则。