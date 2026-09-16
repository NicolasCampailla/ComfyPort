# Jetpack Compose support
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Gson Rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod, InnerClasses
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Keep App Data Structures and Models to prevent Gson reflection issues
-keep class com.comfyport.data.** { *; }
-keepclassmembers class com.comfyport.data.** { *; }

# OkHttp Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Ensure network operations (ComfyClient, SshClient, etc.) and UI logic are fully obfuscated
# We do not add -keep rules for com.comfyport.network.** or com.comfyport.ui.**,
# letting R8 perform full renaming, obfuscation, and dead code stripping.

# JSch Rules
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
