package com.dot.gallery.editor.android

import com.dot.gallery.editor.io.EditorNativeLoader

/**
 * Loads the packaged `librefra_editor_jni.so` through the platform class
 * loader — the library ships inside the AAR/APK `jni/<abi>` directories, so
 * the OS resolves the right ABI slice itself. Stateless; the effect calls it
 * once inside export IO work.
 */
class AndroidEditorNativeLoader : EditorNativeLoader {
    override fun load() {
        System.loadLibrary("refra_editor_jni")
    }
}
