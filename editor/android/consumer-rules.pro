# JNI entry points resolved by name from librefra_editor_jni.
-keep class com.dot.gallery.editor.io.NativeColorBridge { *; }

# JavaCPP/ByteDeco resolve classes, native peer initializers and loader
# hooks reflectively at runtime — the upstream-recommended keep for the
# packaged FFmpeg stack (no proprietary code is pulled: the dependency is
# non-transitive already).
-keep class org.bytedeco.** { *; }
