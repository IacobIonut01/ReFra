/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.settings

import com.dot.gallery.core.logging.printError
import kotlinx.coroutines.CancellationException

/**
 * Runs a preference write made from a UI setter and reports whether it was stored.
 *
 * A setter launches its write in a composition scope, so an exception from the store (for
 * example the encrypted settings store failing a Keystore operation) would escape that
 * coroutine and crash the app. It is logged instead; the caller keeps the value in memory.
 */
suspend fun writePreference(key: String, write: suspend () -> Unit): Boolean = try {
    write()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    printError(
        tag = "app.prefs",
        message = "Preference write failed; keeping the value in memory",
        throwable = e,
        ctx = mapOf("key" to key),
    )
    false
}
