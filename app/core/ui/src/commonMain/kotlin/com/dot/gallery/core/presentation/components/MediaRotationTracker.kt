/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Per-media rotation state that grid cells animate from. Cells read their own entry through a
 * `derivedStateOf`, so a tick for one item only recomposes the cells whose entry changed.
 */
@Stable
class MediaRotationTracker {
    private val entries = mutableStateMapOf<Long, MediaRotationEntry>()

    fun entry(mediaId: Long): MediaRotationEntry? = entries[mediaId]

    fun queue(mediaIds: Collection<Long>, degrees: Int) {
        mediaIds.forEach { id ->
            entries[id] = (entries[id] ?: MediaRotationEntry())
                .copy(pending = PendingMediaRotation(degrees, PendingMediaRotation.Phase.Queued))
        }
    }

    fun markProcessing(mediaId: Long) = setPhase(mediaId, PendingMediaRotation.Phase.Processing)

    fun markDone(mediaId: Long) {
        val current = entries[mediaId] ?: return
        val pending = current.pending ?: return
        if (pending.phase == PendingMediaRotation.Phase.Done) return
        entries[mediaId] = current.copy(
            revision = current.revision + 1,
            pending = pending.copy(phase = PendingMediaRotation.Phase.Done),
        )
    }

    fun markFailed(mediaId: Long) = setPhase(mediaId, PendingMediaRotation.Phase.Failed)

    /** Ends the animation for [mediaId] while keeping its revision. */
    fun settle(mediaId: Long) {
        val current = entries[mediaId] ?: return
        if (current.pending == null) return
        entries[mediaId] = current.copy(pending = null)
    }

    private fun setPhase(mediaId: Long, phase: PendingMediaRotation.Phase) {
        val current = entries[mediaId] ?: return
        val pending = current.pending ?: return
        if (pending.phase == phase) return
        entries[mediaId] = current.copy(pending = pending.copy(phase = phase))
    }
}

val LocalMediaRotationTracker = staticCompositionLocalOf<MediaRotationTracker?> { null }
