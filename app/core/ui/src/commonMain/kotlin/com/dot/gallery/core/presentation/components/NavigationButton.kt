/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigateUp
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.back_cd


@Composable
fun NavigationButton(
    albumId: Long,
    target: String?,
    alwaysGoBack: Boolean,
) {
    val eventHandler = LocalEventHandler.current
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    val isChildRoute = albumId != -1L || target != null
    val onClick: () -> Unit =
        if (isChildRoute && !isSelectionActive) eventHandler::navigateUp
        else selector::clearSelection
    val icon = if (isChildRoute && !isSelectionActive) Icons.AutoMirrored.Filled.ArrowBack
    else Icons.Default.Close
    val railHost = LocalGalleryRailBackHost.current
    if (railHost != null) {
        // The rail owns back (and the selection's close button) while it is visible.
        if (isChildRoute || alwaysGoBack) RailBackRegistration(railHost) { eventHandler.navigateUp() }
        return
    }
    if (isChildRoute || isSelectionActive || alwaysGoBack) {
        IconButton(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ),
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(Res.string.back_cd),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
