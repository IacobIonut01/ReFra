/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class PendingMediaRotation(val degrees: Int, val phase: Phase) {
    enum class Phase { Queued, Processing, Done, Failed }
}

/**
 * [revision] counts completed rotations this session and joins the thumbnail signature, so a
 * rewritten file is re-decoded even when its timestamp and size did not change.
 */
@Immutable
data class MediaRotationEntry(val revision: Int = 0, val pending: PendingMediaRotation? = null)

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
