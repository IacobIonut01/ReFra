/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.ui.mediaview.KnownVisualSearchProvider
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.core.ui.mediaview.icon
import com.dot.gallery.core.ui.mediaview.tintIcon
import com.dot.gallery.domain.settings.VISUAL_SEARCH_CONVERT_ASK
import com.dot.gallery.domain.settings.VISUAL_SEARCH_FORMAT_JPEG
import com.dot.gallery.domain.settings.VISUAL_SEARCH_PROVIDER_AUTO
import com.dot.gallery.feature.settings.ui.VisualSearchSettingsContent

/** An installed Google Lens target, as the app's share-target query reports it. */
private val DOCS_LENS_TARGET = VisualSearchTarget(
    packageName = "com.google.ar.lens",
    activityName = "com.google.ar.lens.LensActivity",
    label = "Lens",
    known = KnownVisualSearchProvider.GOOGLE_LENS,
)

/**
 * The real [VisualSearchSettingsContent] inside the same
 * [SwitchPreferenceDetailScreen] shell the media-viewer settings pane uses.
 * Every control mutates local state so the live preview stays interactive;
 * `off` starts with the feature switch cleared, collapsing the options.
 */
@Composable
fun DocsVisualSearchOptionsScene(state: String?) {
    var enabled by rememberSaveable { mutableStateOf(state != "off") }
    var provider by rememberSaveable { mutableStateOf(VISUAL_SEARCH_PROVIDER_AUTO) }
    var allowVault by rememberSaveable { mutableStateOf(true) }
    var convertMode by rememberSaveable { mutableStateOf(VISUAL_SEARCH_CONVERT_ASK) }
    var convertFormat by rememberSaveable { mutableStateOf(VISUAL_SEARCH_FORMAT_JPEG) }

    // feature.settings Res accessors are module-internal; literals mirror
    // visual_search_title / visual_search_description verbatim.
    SwitchPreferenceDetailScreen(
        title = "Visual search",
        isChecked = enabled,
        onCheckedChange = { enabled = it },
        description = "Shows a button in the media viewer that sends the current image — or the current video frame — to the selected app, the way Google Lens works in other galleries.\n\nThe image leaves this device through the provider you choose.",
        customContent = {
            VisualSearchSettingsContent(
                targets = listOf(DOCS_LENS_TARGET),
                providerIcon = { packageName ->
                    listOf(DOCS_LENS_TARGET)
                        .firstOrNull { it.packageName == packageName }
                        ?.let { target ->
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = target.icon,
                                    contentDescription = target.displayName,
                                    tint = if (target.tintIcon) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color.Unspecified
                                    },
                                )
                            }
                        }
                },
                provider = provider,
                onProviderChange = { provider = it },
                allowVault = allowVault,
                onAllowVaultChange = { allowVault = it },
                convertMode = convertMode,
                onConvertModeChange = { convertMode = it },
                convertFormat = convertFormat,
                onConvertFormatChange = { convertFormat = it },
            )
        },
    )
}
