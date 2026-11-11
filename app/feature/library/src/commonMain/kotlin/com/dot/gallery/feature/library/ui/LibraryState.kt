/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable
import com.dot.gallery.core.ml.ModelStatus


/**
 * Everything [LibraryHeader] needs to render — the persisted [snapshot] plus
 * platform-resolved facts the caller measured app-side.
 */
@Immutable
data class LibraryState(
    val snapshot: LibrarySnapshot,
    val modelStatus: ModelStatus,
    val aiAvailable: Boolean,
    val mapsEnabled: Boolean,
    /** `SdkCompat.supportsTrash` — platform capability, measured app-side. */
    val supportsTrash: Boolean = true,
    /** `SdkCompat.supportsFavorites` — platform capability, measured app-side. */
    val supportsFavorites: Boolean = true,
    /**
     * How many vaults exist, or null while the vault list sits behind a lock. Never cached in
     * [snapshot], so a locked list does not leak its size through the startup cache.
     */
    val vaultCount: Int? = null,
)
