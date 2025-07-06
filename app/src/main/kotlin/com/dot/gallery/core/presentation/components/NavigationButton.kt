/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigateUp

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
                contentDescription = stringResource(R.string.back_cd),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}