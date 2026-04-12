/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.dot.gallery.feature_node.domain.model.MediaMetadataState

@Composable
internal fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
) {
    ListLocationsContent(metadataState = metadataState)
}
