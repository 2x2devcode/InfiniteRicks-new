# --- Gson / wallet persistence (release minify) ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

-keep class com.google.gson.** { *; }
-keep class com.infinitericks.wallet.core.** { *; }

# --- EncryptedSharedPreferences ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# --- JNI certificate pins ---
-keep class com.infinitericks.wallet.security.NativePinProvider { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
