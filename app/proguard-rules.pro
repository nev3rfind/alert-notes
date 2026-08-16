# =============================================================================
# Alert Notes — R8 keep rules
# =============================================================================
#
# Most of what this app needs is already covered by consumer rules shipped
# inside the libraries themselves (Room, Hilt, kotlinx-serialization, Firebase,
# Compose). What follows is only the delta: places where this codebase reaches
# something by name rather than by symbol, so R8 cannot see the reference.
#
# Verified against the R8 output rather than assumed:
#   ./gradlew assembleRelease
#   app/build/outputs/mapping/release/usage.txt   <- what was removed
#   app/build/outputs/mapping/release/seeds.txt   <- what was kept as an entry point
# =============================================================================

# -----------------------------------------------------------------------------
# Crash reports
# -----------------------------------------------------------------------------
# Without this a stack trace from the field has no line numbers, and mapping.txt
# cannot be applied to it. SourceFile is renamed rather than kept, so no
# original filenames leak into the shipped binary.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Navigation routes (kotlinx-serialization)
# -----------------------------------------------------------------------------
# androidx.navigation resolves a route's serializer reflectively from its KType.
# The serialization plugin's own rules already keep the generated
# `Companion.serializer()` and `$$serializer` members — this makes the guarantee
# explicit for the route classes specifically, because losing one turns into a
# crash on navigation that only reproduces in a release build.
-keep,allowobfuscation,allowshrinking class com.alertnotes.core.navigation.** { *; }

# -----------------------------------------------------------------------------
# Backup / share payload DTOs
# -----------------------------------------------------------------------------
# These cross a process and a network boundary as JSON: the ZIP export, and the
# `payload` field of a reminder share. Field NAMES are the wire format, so they
# must survive obfuscation or a share written by one build cannot be read by
# another.
-keepclassmembers class com.alertnotes.data.backup.** {
    <fields>;
}
-keepclassmembers class com.alertnotes.domain.model.ReminderDrawing** {
    <fields>;
}

# Keep the synthetic members the serialization plugin generates for every
# @Serializable class in this app.
-keepclassmembers class com.alertnotes.** {
    *** Companion;
}
-keepclasseswithmembers class com.alertnotes.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# -----------------------------------------------------------------------------
# Enums persisted by name
# -----------------------------------------------------------------------------
# Every persisted enum in this app round-trips through `Enum.name` — Room
# columns, Firestore string fields and DataStore keys all store the constant's
# name, and the mappers look it up with `entries.firstOrNull { it.name == … }`.
# Obfuscating the constants would silently reset every stored value to its
# default on the first read.
-keepclassmembers enum com.alertnotes.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# -----------------------------------------------------------------------------
# Firebase
# -----------------------------------------------------------------------------
# This app maps every Firestore document by hand (getString/getLong) and never
# calls toObject(), so no POJO keep rules are needed. If that ever changes, the
# data classes involved must be kept here or deserialisation returns nulls in
# release builds only.
-dontwarn com.google.firebase.**

# Hilt-generated components reference these at CLASS retention only.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
