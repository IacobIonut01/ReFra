/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import com.dot.gallery.core.work.WorkInput
import kotlinx.serialization.json.JsonObject

/**
 * Runs a serialized editor export recipe inside [EditorExportWorker]. The
 * implementation lives in the app module — `editor:android` is not on this
 * module's classpath — and is installed at startup through
 * [EditorExportBridgeHolder]. [export] performs the real encode+publish and
 * returns the output payload the worker records (and the enqueue side
 * replays into the session's result).
 */
interface EditorExportBridge {
    suspend fun export(input: WorkInput, onProgress: (Float) -> Unit): JsonObject
}

object EditorExportBridgeHolder {
    @Volatile
    var bridge: EditorExportBridge? = null
}
