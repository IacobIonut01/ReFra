/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dot.gallery.app.presentation.settings.SettingsScreen
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.presentation.components.GalleryRailBackHost
import com.dot.gallery.core.presentation.components.LocalGalleryRailBackHost
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.settings_pane_placeholder
import org.jetbrains.compose.resources.stringResource

const val SettingsHubPaneTag = "Settings.HubPane"
const val SettingsPanePlaceholderTag = "Settings.PanePlaceholder"

/**
 * The settings hub kept beside the open settings page on wide windows. Its own back button is
 * hidden (the rail's belongs to the visible page), and picking a page replaces the visible page.
 */
@Composable
fun SettingsHubPane(navController: NavController, modifier: Modifier = Modifier) {
    val base = LocalEventHandler.current
    val handler = remember(base, navController) {
        SettingsHubEventHandler(base) { route ->
            navController.navigate(route) {
                popUpTo(Screen.SettingsScreen()) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val hiddenBack = remember { GalleryRailBackHost(acceptsRegistrations = false) }
    Box(modifier.testTag(SettingsHubPaneTag)) {
        CompositionLocalProvider(
            LocalEventHandler provides handler,
            LocalGalleryRailBackHost provides hiddenBack,
        ) {
            SettingsScreen()
        }
    }
}

/** Shown beside the hub until a settings page is picked. */
@Composable
fun SettingsPanePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // The hub underneath stays composed for navigation; it must not receive touches.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .testTag(SettingsPanePlaceholderTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_pane_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
