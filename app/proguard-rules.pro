# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep class com.dot.gallery.feature_node.presentation.edit.adjustments.** { *; }
-keep class com.drew.** { *; }
-keep class java.io.** { *; }
-keep class com.adobe.** { *; }
-keep class ai.onnxruntime.** { *; }

-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options

# Keep custom Glide decoders and model loaders (HEIF/JXL/Encrypted)
-keep class com.dot.gallery.core.decoder.glide.** { *; }
-keep class com.radzivon.bartoshyk.avif.** { *; }
-keep class com.awxkee.jxlcoder.** { *; }
-dontwarn com.radzivon.bartoshyk.avif.**
-dontwarn com.awxkee.jxlcoder.**