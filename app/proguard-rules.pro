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
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.ai.proposal.** { *; }
-keep class com.example.ai.appfunctions.** { *; }
-keep class com.example.camera.nameplate.NameplateScanResult { *; }
-keep class com.example.camera.nameplate.NameplateEquipmentType { *; }

# ------------------------------------------------------------------------------
# Core Engineering, SLD & PDF Data Models (Precision contracts for serialization)
# ------------------------------------------------------------------------------
# Allow R8 to optimize, inline, and dead-code strip com.example.engine and com.example.sld.
# Only preserve models required for project import/export JSON or PDF generation.
-keep class com.example.sld.SldDiagramDocument { *; }
-keep class com.example.sld.SldNode { *; }
-keep class com.example.sld.SldEdge { *; }
-keep class com.example.sld.SldBusNode { *; }
-keep class com.example.sld.SldSourceNode { *; }
-keep class com.example.sld.SldLoadNode { *; }
-keep class com.example.sld.SldSwitchNode { *; }
-keep class com.example.pdf.** { public <methods>; }
-keep class com.example.hierarchy.** { public <methods>; }

# ------------------------------------------------------------------------------
# jLaTeXMath Android Library
# ------------------------------------------------------------------------------
-keep class ru.noties.jlatexmath.** { *; }


