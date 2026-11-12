/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

/**
 * Runs the app's own Android Keystore cipher operations one at a time.
 *
 * When KeyMint runs out of operation slots, keystore2 prunes an idle operation and prefers
 * the client holding the most of them, which includes the client asking for a new one. A
 * Keystore cipher begins its operation in `init` and only finishes it in `doFinal`, so an
 * operation this process starts on another thread in between can prune it, and `doFinal`
 * then fails with `INVALID_OPERATION_HANDLE` ("Pruned"). Holding the lock from `init` to
 * `doFinal` keeps the app at one open operation on these paths.
 *
 * Operations are short (a few milliseconds), so callers block rather than suspend.
 */
object KeystoreOperations {
    private val lock = Any()

    fun <T> run(block: () -> T): T = synchronized(lock, block)
}
