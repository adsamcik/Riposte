# Meme My Mood ProGuard Rules
# Optimized for R8 Full Mode with cutting-edge Android practices

# ============================================================
# KOTLIN
# ============================================================

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable navigation routes
-keep @kotlinx.serialization.Serializable class com.mememymood.core.common.navigation.** { *; }

# ============================================================
# DEPENDENCY INJECTION (Hilt)
# ============================================================

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# ============================================================
# ROOM DATABASE
# ============================================================

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ============================================================
# MACHINE LEARNING (ML Kit & LiteRT)
# ============================================================

# ML Kit (on-device AI)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# LiteRT (formerly TensorFlow Lite) - Google's on-device AI runtime
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keepclassmembers class * implements org.tensorflow.lite.InterpreterApi { *; }

# Local Agents RAG (GenKit AI)
-keep class com.google.ai.edge.localagents.** { *; }
-dontwarn com.google.ai.edge.localagents.**

# ============================================================
# PROTOBUF
# ============================================================

# Protobuf - used by LocalAgents/LiteRT
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ============================================================
# JAVAX ANNOTATIONS (Compile-time only)
# ============================================================

# These are compile-time annotation processors, not needed at runtime
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# ============================================================
# IMAGE LOADING (Coil 3)
# ============================================================

# Coil 3 uses kotlinx.serialization for disk cache
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ============================================================
# DATA MODELS
# ============================================================

# Keep data classes for serialization
-keep class com.mememymood.core.model.** { *; }

# ============================================================
# COMPOSE
# ============================================================

# Compose uses reflection for some features
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose stability annotations
-keep class androidx.compose.runtime.Stable { *; }
-keep class androidx.compose.runtime.Immutable { *; }

# ============================================================
# NAVIGATION
# ============================================================

# Type-safe navigation with serialization
-keep class * extends androidx.navigation.NavArgs { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# ============================================================
# DEBUGGING (for better stack traces in release)
# ============================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# GOOGLE PLAY IN-APP REVIEW
# ============================================================

-keep class com.google.android.play.core.review.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }
