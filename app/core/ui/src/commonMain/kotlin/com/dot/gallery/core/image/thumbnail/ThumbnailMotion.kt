/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest


/**
 * scroll-aware two-stage thumbnail scheduling.
 *
 * A grid publishes a debounced "is the list currently in motion" flag through
 * [LocalThumbnailMotion]. The shared image renderer reads it to decide the request tier:
 * during motion, thumbnails without a completed refined load use the cheap MOTION tier (small,
 * static — no full-size sibling and no animation); already-loaded thumbnails remain refined.
 * Once the list has been idle for [DEFAULT_IDLE_DEBOUNCE_MS], new thumbnails load the REFINED tier
 * at the cell size, reusing the already-cached MOTION bitmap as an instant placeholder.
 *
 * Null default means "treat as idle/refined" so any surface that does not publish motion state
 * keeps its previous full-quality behavior.
 */
val LocalThumbnailMotion = compositionLocalOf<State<Boolean>?> { null }

/** Idle debounce before a settled list is allowed to refine (tuned from device measurements). */
const val DEFAULT_IDLE_DEBOUNCE_MS = 200L

fun shouldUseMotionThumbnail(isMoving: Boolean, hasLoadedRefined: Boolean): Boolean =
    isMoving && !hasLoadedRefined

/**
 * How a REFINED request bridges from the MOTION bitmap already on screen.
 *
 * Every refined request needs a bridge: without one the image library clears the cell to its
 * placeholder while the refined bitmap loads, so every cell that refines when a scroll settles
 * flashes for a frame (#1385).
 */
enum class MotionThumbnailBridge {
    /** Reuse the cached MOTION bitmap, decoding it if it is missing. */
    DECODE,

    /**
     * Reuse the MOTION bitmap only when it is already cached. For expensive codecs, so a cell that
     * never showed the MOTION tier does not pay a second decode just to bridge.
     */
    CACHED_ONLY,
}

fun motionThumbnailBridge(isHeavyCodec: Boolean): MotionThumbnailBridge =
    if (isHeavyCodec) MotionThumbnailBridge.CACHED_ONLY else MotionThumbnailBridge.DECODE
class RefinedThumbnailLoadTracker(private val capacity: Int) {
    // Insertion-ordered map; a hit re-inserts at the tail so keys.first() is always the LRU entry.
    private val loadedKeys = LinkedHashMap<RefinedThumbnailKey, Unit>()

    init {
        require(capacity > 0)
    }

    fun hasLoaded(key: RefinedThumbnailKey): Boolean {
        if (key.model == null && key.signature.isEmpty()) return false
        return loadedKeys.remove(key)?.let { loadedKeys[key] = Unit; true } ?: false
    }

    fun markLoaded(key: RefinedThumbnailKey) {
        if (key.model == null && key.signature.isEmpty()) return
        loadedKeys.remove(key)
        loadedKeys[key] = Unit
        while (loadedKeys.size > capacity) {
            loadedKeys.remove(loadedKeys.keys.first())
        }
    }
}

/**
 * Derive a debounced motion flag from a scroll container's `isScrollInProgress`.
 *
 * Motion turns on immediately when scrolling starts (so newly-visible, unloaded cells use the
 * cheap tier without delay) and turns off only after [idleDebounceMs] of no scrolling (so a pause
 * mid-fling does not thrash between tiers). [collectLatest] cancels the pending idle timer the
 * moment scrolling resumes.
 */
@Composable
fun rememberThumbnailMotionState(
    isScrollInProgress: () -> Boolean,
    idleDebounceMs: Long = DEFAULT_IDLE_DEBOUNCE_MS,
): State<Boolean> {
    val moving = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { isScrollInProgress() }.collectLatest { scrolling ->
            if (scrolling) {
                moving.value = true
            } else {
                delay(idleDebounceMs)
                moving.value = false
            }
        }
    }
    return moving
}
