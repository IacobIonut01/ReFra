package com.dot.gallery.editor.render.video

import kotlinx.collections.immutable.PersistentList

/**
 * A blocked project restore: the saved sequence could not be applied
 * because these referenced media are missing or unreadable — an
 * expired content grant, a deleted file, a moved path. The UI offers
 * Retry (re-probe), Discard (keep the freshly opened source) and
 * Relink (pick a replacement for a missing reference) — the snapshot
 * is held pending meanwhile, never silently flattened to what loads.
 */
data class VideoRecovery(
    val missingReferences: PersistentList<String>
)
