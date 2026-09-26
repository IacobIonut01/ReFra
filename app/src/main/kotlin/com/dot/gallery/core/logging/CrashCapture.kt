/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import kotlin.system.exitProcess

/**
 * Chains onto the default uncaught-exception handler: when developer mode is
 * on, synchronously writes a `crash-<ts>.log` (stacktrace + device info +
 * trailing breadcrumbs/recent entries), then delegates to the previous handler
 * so the normal crash flow is untouched. Lives in the main process only.
 */
object CrashCapture {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { AppLog.writeCrashSync(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }
}
