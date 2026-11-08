/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.content.Context
import com.dot.gallery.core.Settings
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printWarn
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Keeps the server-side index consistent with local deletions.
 *
 * Deleting media locally must remove its vector from the remote index, or
 * /query keeps returning ids the app can no longer resolve. Deletes are
 * recorded in a persistent pending set first (DataStore), then flushed —
 * so deletions made while offline or before the server is configured still
 * propagate on the next successful connection.
 */
@Singleton
class HandoverIndexMirror @Inject constructor(
    private val context: Context,
    private val client: HandoverClient,
) {
    /** Records [ids] for remote deletion and attempts an immediate flush. */
    suspend fun onMediaDeleted(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        Settings.Handover.addPendingIndexDeletes(context, ids.map { it.toString() })
        flush()
    }

    /** Flushes with a fresh config — no-op when handover is off/unconfigured. */
    suspend fun flush() {
        val config = client.config() ?: return
        flush(config)
    }

    suspend fun flush(config: HandoverConfig) {
        val pending = Settings.Handover.pendingIndexDeletes(context).first()
        if (pending.isEmpty()) return
        pending.toList().chunked(DELETE_BATCH).forEach { batch ->
            try {
                client.indexDelete(config, batch)
            } catch (e: HandoverException) {
                // Keep the rest queued — a fixed secret or restored connectivity
                // lets the next flush finish the backlog.
                printWarn(
                    "handover.index-mirror",
                    "index delete flush failed: ${e.code} — ${batch.size} id(s) stay queued",
                    ctx = mapOf("code" to e.code, "retryable" to e.retryable.toString()),
                )
                if (!e.retryable && e.code != "unauthorized") {
                    Settings.Handover.removePendingIndexDeletes(context, batch)
                }
                return
            }
            Settings.Handover.removePendingIndexDeletes(context, batch)
        }
    }

    private companion object {
        const val DELETE_BATCH = 2_000
    }
}
