# ProGuard / R8 Rules for VoltAtlas Release Build

# Preserve line numbers for diagnostic crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations used by Room, Moshi, and Compose
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# AndroidX Lifecycle
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ------------------------------------------------------------------------------
# Room Database & Entities
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.**Impl { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# Moshi & JSON Serialization Data Models
# ------------------------------------------------------------------------------
-keep class com.example.data.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ------------------------------------------------------------------------------
# Core Engineering, SLD & PDF Data Models
# ------------------------------------------------------------------------------
-keep class com.example.sld.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.pdf.** { *; }
-keep class com.example.hierarchy.** { *; }

# ------------------------------------------------------------------------------
# jLaTeXMath Android Library
# ------------------------------------------------------------------------------
-keep class ru.noties.jlatexmath.** { *; }

