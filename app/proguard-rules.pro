# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile


# -----------------------------------------------------------------
# 1. REMOVE LOGS (Saves Battery & CPU)
# -----------------------------------------------------------------
# This tells R8 that Log.d/v/i/w have no side effects and can be safely deleted.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    # We intentionally keep Log.e so you can still track crashes
    # public static int e(...);
}

# -----------------------------------------------------------------
# 2. SAFETY RULES (Prevent Release Crashes)
# -----------------------------------------------------------------
# Keep your data models. If R8 renames these, JSON parsing and Room DB will fail.
-keep class com.example.juke.models.** { *; }

# Keep Room Database classes
-keep class com.example.juke.database.** { *; }

# Keep JNI/Native methods if you use any (Standard safety)
-keepclasseswithmembernames class * {
    native <methods>;
}




# Keep network API response models (@Serializable). The API classes themselves (SpotifyApi,
# RecommenderApi, ...) are left to R8 so they can be shrunk and optimised; Gson only parses
# into JsonObject, so it needs no keep on our classes.
-keep @kotlinx.serialization.Serializable class com.example.juke.network.** { *; }

# Keep attributes for serialization
-keepattributes *Annotation*, InnerClasses
-keepattributes Signature, Exception

# Kotlinx Serialization rules (if not already included by default)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# -----------------------------------------------------------------
# 3. GSON & SERIALIZATION RULES
# -----------------------------------------------------------------

# Keep generic type signatures (Crucial for Gson parsing List<Track>, etc.)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson specific: Keep the TypeAdapter and TypeToken which are used via reflection
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter


# Gson JsonElement and subclasses (JsonObject, JsonArray)
-keep class com.google.gson.JsonElement { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class com.google.gson.JsonArray { *; }

# Note: an earlier "-assumenosideeffects class androidx.media3.extractor.<fmt>.** { *; }" block
# was removed. It saved nothing (DefaultExtractorsFactory still references every extractor) and
# let R8 delete void calls such as init/seek/release inside the FLAC, OGG, WAV, MKV, TS, FLV and
# AMR extractors, which would break playback of those files.

# -----------------------------------------------------------------
# 4. KOTLIN NULL CHECKS
# -----------------------------------------------------------------
# Parameter null checks on every public Kotlin function: dead weight in release (our own
# code is Kotlin-to-Kotlin). Expression checks on Java return values are kept.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# Size: flatten packages and allow wider inlining
-repackageclasses ""
-allowaccessmodification
