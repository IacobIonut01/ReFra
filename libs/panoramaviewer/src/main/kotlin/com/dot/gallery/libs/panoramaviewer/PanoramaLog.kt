/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.util.Log
import com.dot.gallery.libs.panoramaviewer.PanoramaLog.enabled

/**
 * Toggleable debug logger for the panorama viewer library.
 *
 * All internal classes route their log output through this singleton so that
 * logging can be enabled or disabled globally at runtime.
 *
 * ```kotlin
 * // Enable before creating the PanoramaViewer
 * PanoramaLog.enabled = true
 * ```
 *
 * Messages are written to Android logcat under the tag **`PanoramaViewer`**.
 * When [enabled] is `false` (the default for release builds), all log calls
 * are no-ops with zero overhead beyond a boolean check.
 */
object PanoramaLog {

    /**
     * Master switch for debug logging.
     *
     * Set to `true` before creating a [PanoramaViewer] to see detailed
     * lifecycle, touch, texture-upload, and region-load messages in logcat.
     * Defaults to `false`.
     */
    var enabled: Boolean = false

    private const val TAG = "PanoramaViewer"

    /** Logs a debug-level message. */
    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    /** Logs a warning-level message with an optional [throwable]. */
    fun w(message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(TAG, message, throwable)
    }

    /** Logs an error-level message with an optional [throwable]. */
    fun e(message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(TAG, message, throwable)
    }
}
