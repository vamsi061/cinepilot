# Add project specific ProGuard rules here.
# Keep source file names for debugging
-keepattributes SourceFile,LineNumberTable

# Keep enum fields
-keepclassmembers enum * {
    public static **[] values();
    public static * valueOf(java.lang.String);
}

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# OkHttp3 built-in platform
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**