# R8 Full Mode configuration for BatitongTV

# Strip all android.util.Log in release builds (§7-C2)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Preserve native JNI methods & native bridge
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class id.batitong.batitongtv.security.NativeSecurityBridge { *; }

# Preserve Data Models (JSON Gson serialization)
-keep class id.batitong.batitongtv.data.model.** { *; }
-keepclassmembers class id.batitong.batitongtv.data.model.** { <fields>; }

# OkHttp & Media3 Keep rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }

# Coil image loader
-keep class io.coilkt.** { *; }
