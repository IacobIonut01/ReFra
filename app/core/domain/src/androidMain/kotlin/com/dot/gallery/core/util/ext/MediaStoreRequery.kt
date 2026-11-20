/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Broadcasts "the MediaStore generation moved while we were away" so every live
 * content query re-runs. ContentService delays observer callbacks for background
 * processes by ~10 s and does not bring them forward on return to the foreground,
 * so a version check on foregrounding asks the queries to refresh themselves.
 */
object MediaStoreRequery {
    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requested: SharedFlow<Unit> = requests.asSharedFlow()

    fun request() {
        requests.tryEmit(Unit)
    }
}
