/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import com.dot.gallery.cloud.core.ProviderType

/**
 * UI-level mirror of the app-side `ProviderCapability` enum (same member names, so the
 * app wrapper can map by name). Only the capabilities a user cares about during setup
 * are surfaced as chips; the rest map to no label.
 */
enum class SetupProviderCapability {
    PEOPLE,
    MAP,
    SMART_SEARCH,
    TEXT_SEARCH,
    SHARE_CREATE,
    SHARE_MANAGE,
    SYNC,
    ALBUM_WRITE,
    ALBUM_MEMBERSHIP,
    REMOTE_ALBUMS,
    REMOTE_ASSETS,
    OCR,
    ARCHIVE,
    MEMORIES,
    FAVORITE,
    TRASH,
    TAGS,
}

/**
 * Fallback capability set for a provider whose live instance hasn't registered yet
 * (e.g. before any account is configured), so the setup card still advertises what
 * the provider can do.
 */
fun defaultSetupCapabilities(type: ProviderType): Set<SetupProviderCapability> = when (type) {
    ProviderType.IMMICH -> setOf(
        SetupProviderCapability.REMOTE_ASSETS,
        SetupProviderCapability.REMOTE_ALBUMS,
        SetupProviderCapability.SYNC,
        SetupProviderCapability.PEOPLE,
        SetupProviderCapability.MAP,
        SetupProviderCapability.SMART_SEARCH,
        SetupProviderCapability.SHARE_CREATE,
        SetupProviderCapability.SHARE_MANAGE,
        SetupProviderCapability.ARCHIVE,
        SetupProviderCapability.MEMORIES
    )
    ProviderType.OWNCLOUD, ProviderType.NEXTCLOUD, ProviderType.WEBDAV -> setOf(
        SetupProviderCapability.REMOTE_ASSETS,
        SetupProviderCapability.REMOTE_ALBUMS,
        SetupProviderCapability.SYNC,
        SetupProviderCapability.SHARE_CREATE
    )
    ProviderType.SMB, ProviderType.NFS -> setOf(
        SetupProviderCapability.REMOTE_ASSETS,
        SetupProviderCapability.REMOTE_ALBUMS,
        SetupProviderCapability.SYNC
    )
    else -> setOf(SetupProviderCapability.REMOTE_ASSETS)
}
