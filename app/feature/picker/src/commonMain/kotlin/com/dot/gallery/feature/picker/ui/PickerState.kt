/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import kotlinx.coroutines.flow.StateFlow


/**
 * Everything [PickerContent] needs to render — collected view state plus the
 * flow factories that stay owned by the app-side `PickerViewModel`.
 *
 * The picker never holds a `StateFlow` for the main grid directly because the
 * ViewModel rebuilds it whenever the drilled-in album changes: [mediaStateFor]
 * resolves (and re-points) the lazy flow for the requested `albumId`, with
 * `-1L` being the unfiltered timeline. [vaultMediaStateFor] does the same for
 * the currently-open vault.
 */
@Immutable
data class PickerState(
    /** Activity title shown in the tab bar ("Select photos" style, built app-side). */
    val title: String,
    /** Multi-pick mode: grid tap toggles selection instead of single-pick. */
    val allowSelection: Boolean,
    val albumsState: State<AlbumState>,
    val metadataState: State<MediaMetadataState>,
    val privateFolderMediaState: StateFlow<MediaState<Media>>,
    val cloudMediaState: StateFlow<MediaState<Media>>,
    val vaultState: State<VaultState>,
    /** Whether at least one cloud provider is configured — shows the Cloud card. */
    val cloudAvailable: Boolean,
    /** Whether the device supports favorites (`SdkCompat.supportsFavorites`) — shows the Favorites card. */
    val favoritesAvailable: Boolean = false,
    /** Media grid flow for [albumId] (`-1L` = timeline). May re-point the ViewModel lazy. */
    val mediaStateFor: (albumId: Long) -> StateFlow<MediaState<Media>>,
    /** Media flow for [vault]'s encrypted contents — `null` yields the empty state. */
    val vaultMediaStateFor: (vault: Vault?) -> StateFlow<MediaState<Media>>,
)
