package com.dot.gallery.editor.io

/**
 * Binds the packaged native editor library so [NativeColorBridge] resolves.
 * Implementations are host-specific: Android calls `System.loadLibrary`,
 * desktop extracts the bundled resource to a private cache and loads it by
 * absolute path. Called once per [NativeColorEffect], inside export work —
 * never on a UI thread.
 */
fun interface EditorNativeLoader {

    /**
     * Makes the library's JNI methods resolvable. Throws [java.io.IOException]
     * for a missing/unreadable library, [UnsatisfiedLinkError] for an
     * unsupported architecture or symbol mismatch, or
     * [SecurityException]/[com.dot.gallery.editor.render.BackendUnavailableException]
     * when the host declines the load.
     */
    fun load()
}
