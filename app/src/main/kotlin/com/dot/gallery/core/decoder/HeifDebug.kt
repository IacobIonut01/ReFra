/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.util.Log
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogLevel

/**
 * Lightweight, toggleable logging for the HEIC/HEIF decode + subsampling pipeline.
 *
 * Filter it in logcat with:  `adb logcat -s HeifZoom`
 * Flip [enabled] to false to silence it.
 */
internal object HeifDebug {

    @Volatile
    var enabled = true

    const val TAG = "HeifZoom"

    fun d(msg: String) {
        if (enabled) {
            // Rule exception (AGENTS.md §2): raw Log kept for the `adb logcat -s HeifZoom` stream; AppLog persists alongside.
            runCatching { Log.d(TAG, msg) }
            AppLog.log(LogLevel.DEBUG, TAG, msg)
        }
    }

    fun w(msg: String, t: Throwable? = null) {
        if (enabled) {
            // Rule exception (AGENTS.md §2): raw Log kept for the `adb logcat -s HeifZoom` stream; AppLog persists alongside.
            runCatching { Log.w(TAG, msg, t) }
            AppLog.log(LogLevel.WARN, TAG, msg, t)
        }
    }
}
