package com.dot.gallery.editor.io

/**
 * Which implementation a [NativeColorEffect] resolved to — diagnostics and
 * tests only, never surfaced in the UI.
 */
enum class NativeColorBackend {

    /** The library has not been probed yet (no render has run). */
    Uninitialized,

    /** `refra_editor_jni` loaded and its ABI matched — renders go to C. */
    Native,

    /** The library could not be used; renders run through BasicColorEffect. */
    KotlinFallback
}
