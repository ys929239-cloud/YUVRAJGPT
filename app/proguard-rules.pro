# Production ProGuard / R8 Rules for YUVRAJGPT

# -------------------------------------------------------------
# 1. Strip debug logging completely from release APK
# -------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

# -------------------------------------------------------------
# 2. Moshi & Reflection / Codegen preservation
# -------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }

# Keep data models used for Gemini API requests and responses
-keep class com.example.data.api.** { *; }
-keepclassmembers class com.example.data.api.** {
    <fields>;
    <init>(...);
}

# -------------------------------------------------------------
# 3. Room Database
# -------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Room entities and DAOs
-keep class com.example.data.db.** { *; }
-keepclassmembers class com.example.data.db.** {
    <fields>;
    <init>(...);
}

# -------------------------------------------------------------
# 4. Retrofit & OkHttp
# -------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# -------------------------------------------------------------
# 5. Firebase & Google Identity / Credentials
# -------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# -------------------------------------------------------------
# 6. BuildConfig Preservation
# -------------------------------------------------------------
-keep class com.example.BuildConfig { *; }
-keepclassmembers class com.example.BuildConfig {
    public static final java.lang.String GEMINI_API_KEY;
}

-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# -------------------------------------------------------------
# 6. Kotlin Coroutines & Jetpack Compose
# -------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Preserve line numbers and source file names for stack traces
-keepattributes SourceFile, LineNumberTable
