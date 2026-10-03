/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderCapability {
    PEOPLE,
    MAP,
    SMART_SEARCH,
    TEXT_SEARCH,
    /** Can create a public link for selected remote assets. */
    SHARE_CREATE,
    /** Can list, edit, and revoke previously created share links. */
    SHARE_MANAGE,
    SYNC,
    ALBUM_WRITE,
    REMOTE_ALBUMS,
    REMOTE_ASSETS,
    OCR,
    ARCHIVE,
    MEMORIES,

    /** Server-side favorites that survive across devices (not local-only). */
    FAVORITE,

    /** A recoverable trash/bin: [RemoteMediaProvider.trashAsset] soft-deletes and
     * [RemoteMediaProvider.restoreAsset] can bring the item back. Providers that only
     * hard-delete (WebDAV/SMB/NFS, where trashAsset == deleteAsset) do NOT declare this. */
    TRASH,

    /** Server-side user tags/labels that can be synced read-only into the local DB. */
    TAGS
}

/**
 * Whether a provider with [capabilities] may serve a user-facing trash
 * ([trash] = true) or restore ([trash] = false) request.
 *
 * Providers without a real bin implement `trashAsset` as a permanent delete
 * and do not declare [TRASH]; a trash-labelled call must never reach them —
 * their items go through `deleteMedia` under the dialog's permanent-delete
 * warning instead (#1279). Restores are unaffected: a provider without a bin
 * never holds restorable items.
 */
fun remoteTrashPermitted(
    trash: Boolean,
    capabilities: Set<ProviderCapability>,
): Boolean = !trash || ProviderCapability.TRASH in capabilities
