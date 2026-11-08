/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.mediaview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.PrefKey

/** Persisted `viewer_action_bar_config` — JSON-encoded [ViewerActionBarConfig]. */
val viewerActionBarConfigKey = PrefKey.serializable(
    name = "viewer_action_bar_config",
    default = ViewerActionBarConfig(),
    serializer = ViewerActionBarConfig.serializer(),
)

@Composable
fun rememberViewerActionBarConfig(): MutableState<ViewerActionBarConfig> =
    rememberPreference(key = viewerActionBarConfigKey)
