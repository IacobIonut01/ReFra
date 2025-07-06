/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("UNCHECKED_CAST")

package com.dot.gallery.feature_node.presentation.timeline.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineNavActions() {
    val eventHandler = LocalEventHandler.current
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryFixed
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryFixed
    val allowBlur by rememberAllowBlur()
    val errorContainer = MaterialTheme.colorScheme.primaryFixed
    val onErrorContainer = MaterialTheme.colorScheme.onPrimaryFixed

    val favoriteBackgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = errorContainer,
                shape = RoundedCornerShape(100)
            )
        } else {
            Modifier
        }
    }
    IconButton(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .then(favoriteBackgroundModifier)
            .hazeEffect(
                state = LocalHazeState.current,
                style = HazeMaterials.regular(
                    containerColor = errorContainer
                )
            ),
        onClick = { eventHandler.navigate(Screen.FavoriteScreen()) }
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = stringResource(R.string.favorites),
            tint = onErrorContainer
        )
    }

    val settingsInteractionSource = remember { MutableInteractionSource() }
    val isPressed = settingsInteractionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(targetValue = if (isPressed.value) 32.dp else 16.dp, label = "cornerRadius")

    val settingsBackgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = tertiaryContainer,
                shape = RoundedCornerShape(cornerRadius)
            )
        } else {
            Modifier
        }
    }

    IconButton(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .then(settingsBackgroundModifier)
            .hazeEffect(
                state = LocalHazeState.current,
                style = HazeMaterials.regular(
                    containerColor = tertiaryContainer
                )
            ),
        interactionSource = settingsInteractionSource,
        onClick = { eventHandler.navigate(Screen.SettingsScreen()) }
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.settings_title),
            tint = onTertiaryContainer
        )
    }
}

