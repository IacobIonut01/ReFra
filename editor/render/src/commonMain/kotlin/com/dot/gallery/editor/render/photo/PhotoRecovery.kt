package com.dot.gallery.editor.render.photo

import kotlinx.collections.immutable.PersistentList

/**
 * A blocked project restore: the saved recipe could not be applied
 * because these referenced media are missing or unreadable — an
 * expired content grant, a deleted file, a moved path. The UI offers
 * Retry (re-probe), Discard (open the source clean) and Relink (pick a
 * replacement for a missing reference) — the recipe is held pending
 * meanwhile, never silently dropped or flattened.
 */
data class PhotoRecovery(
    val missingReferences: PersistentList<String>
)
