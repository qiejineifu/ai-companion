# === Phase 4: Production ProGuard Rules ===

# General
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# Live2D Cubism SDK
-keep class com.live2d.** { *; }
-keep class jp.live2d.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-dontwarn dagger.internal.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aicompanion.**$$serializer { *; }
-keepclassmembers class com.aicompanion.** {
    *** Companion;
}
-keepclasseswithmembers class com.aicompanion.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson
-keep class com.google.gson.** { *; }
-keep class com.aicompanion.domain.model.** { *; }
-keep class com.aicompanion.data.mapper.** { *; }
-keepclassmembers class com.aicompanion.domain.model.** {
    <fields>;
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Compose
-keepclassmembers class androidx.compose.** {
    <init>(...);
    *** remember*(...);
}

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
