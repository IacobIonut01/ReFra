/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot

/** True when state saved by [savedEntryKey] may be restored into the viewer entry [entryKey]. */
internal fun canRestoreViewerEntryState(savedEntryKey: String?, entryKey: String): Boolean =
    savedEntryKey == entryKey

/** [parent] itself when restoring is allowed; otherwise a registry that consumes and drops restored values. */
internal fun viewerEntrySaveableStateRegistry(
    parent: SaveableStateRegistry?,
    restoreAllowed: Boolean,
): SaveableStateRegistry? =
    if (parent == null || restoreAllowed) parent else DiscardRestoredStateRegistry(parent)

private class DiscardRestoredStateRegistry(
    private val parent: SaveableStateRegistry,
) : SaveableStateRegistry by parent {
    override fun consumeRestored(key: String): Any? {
        parent.consumeRestored(key)
        return null
    }
}

/**
 * Scopes the viewer's saveable state to one viewer entry.
 *
 * The overlay viewer is not itself restored when the activity is recreated — for example
 * after the process is killed while the user is inside a share target — but the values it
 * saved into the activity's saveable registry are restored. The next viewer opened at the
 * same composition position would then pick them up: its pager page, frozen entry page,
 * lock, slideshow and chrome flags all belong to the previous entry. Restoring only when
 * the entry key recorded next to that state matches the incoming one keeps a new entry
 * from inheriting them. A missing owner also refuses: a fresh entry has nothing of its own
 * to restore, and leftovers an earlier filtered entry never consumed must not leak into it.
 *
 * Saving still goes through the parent registry; only restoration is filtered.
 */
@Composable
internal fun ViewerEntrySavedStateScope(entryKey: String, content: @Composable () -> Unit) {
    val parent = LocalSaveableStateRegistry.current
    // Key of the entry whose state is saved at this position; null when nothing was restored here.
    val savedOwner = rememberSaveable { mutableStateOf<String?>(null) }
    val restoreAllowed = remember {
        canRestoreViewerEntryState(Snapshot.withoutReadObservation { savedOwner.value }, entryKey)
    }
    SideEffect { savedOwner.value = entryKey }
    val registry = remember(parent, restoreAllowed) { viewerEntrySaveableStateRegistry(parent, restoreAllowed) }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
}
