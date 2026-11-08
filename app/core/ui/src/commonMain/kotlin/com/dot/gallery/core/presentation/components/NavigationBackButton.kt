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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.back_cd

/**
 * Portable back affordance. [onBack] defaults to the ambient event handler's
 * navigateUp; on Android that resolves to the same up-navigation the
 * platform back dispatcher reached before the port. [forcedAction] overrides
 * outright (screens whose back does something other than navigate-up).
 */
@Composable
fun NavigationBackButton(
    modifier: Modifier = Modifier,
    paddingModifier: Modifier = Modifier.padding(horizontal = 8.dp),
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = stringResource(Res.string.back_cd),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    containerModifier: Modifier = Modifier,
    forcedAction: (() -> Unit)? = null
) {
    val eventHandler = LocalEventHandler.current
    val railHost = LocalGalleryRailBackHost.current
    if (railHost != null) {
        // The rail shows the back button for this screen; keep its action current there.
        RailBackRegistration(railHost, forcedAction ?: { eventHandler.navigateUpAction() })
        return
    }
    IconButton(
        modifier = modifier
            .then(paddingModifier)
            .then(containerModifier)
            .background(
                color = containerColor,
                shape = CircleShape
            ),
        onClick = {
            forcedAction?.invoke() ?: eventHandler.navigateUpAction()
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
