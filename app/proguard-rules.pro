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
#-renamesourcefileattribute SourceFile

# --- AndroidX Media3 / ExoPlayer ---
# Media3 ships its own consumer ProGuard rules inside the AAR.
# Only preserve the reflection-accessed surface; let R8 optimise everything else.
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.exoplayer.ExoPlayer$Builder { *; }
-keepclassmembers class androidx.media3.exoplayer.** {
    @androidx.media3.common.util.UnstableApi <methods>;
}
-keep class androidx.media3.session.MediaSession { *; }
-keep class androidx.media3.session.MediaController { *; }
-keep class androidx.media3.session.MediaBrowser { *; }
-dontwarn androidx.media3.**

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# --- General ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod