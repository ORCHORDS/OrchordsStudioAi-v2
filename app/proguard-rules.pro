# OrchordsAI release keep rules for R8 / resource shrinking.
#
# These rules are intentionally conservative: they keep the reflection surfaces
# that libraries rely on, while still allowing method-body optimization on
# everything else. New reflective lookups should be added here with a comment
# citing the calling site.

# Keep crash-reporting-friendly attributes so future symbolication works.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Hide the original source filename in stack traces but keep line numbers.
-renamesourcefileattribute SourceFile

# === kotlinx.serialization ===
# Generated $serializer companions resolve classes by name; keep them.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    public static ** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Room (AndroidX) ===
# Room's generated DAO impls reference @Dao / @Query annotations and call
# @TypeConverter methods by name. Keep the annotations on entities and DAOs
# so the generated code can resolve them.
-keep class androidx.room.** { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclasseswithmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# === Orchords AI / MCP runtime ===
# The MCP loader resolves tool/handler class names from JSON config at runtime.
-keep class com.orchords.ai.** { *; }
-keep class com.orchords.orchordsai.data.ai.tools.** { *; }

# === JNI surface ===
# Anything called from native code must keep its declared name + signature.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# === Coroutines ===
# Internal continuation debug metadata is fine to keep for crash reports.
-dontwarn kotlinx.coroutines.**
