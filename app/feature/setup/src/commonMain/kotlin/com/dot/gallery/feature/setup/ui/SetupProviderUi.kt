/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.cloud.core.ProviderType

/**
 * One selectable provider row on the cloud setup page: the provider type (for the
 * setup callback), its brand/fallback icon resolved app-side from the descriptor
 * registry, and the advertised capabilities rendered as chips.
 */
data class SetupProviderUi(
    val type: ProviderType,
    val icon: ImageVector,
    val capabilities: Set<SetupProviderCapability> = emptySet(),
    /** One-line description, the same copy the add-account provider list uses. */
    val description: String? = null,
    /** The provider's tinted badge from the add-account list; replaces [icon] when set. */
    val badge: (@Composable () -> Unit)? = null,
)
