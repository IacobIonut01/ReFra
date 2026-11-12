/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.mediaview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.flow.first

/** Persisted `viewer_action_bar_config` — JSON-encoded [ViewerActionBarConfig]. */
val viewerActionBarConfigKey = PrefKey.serializable(
    name = "viewer_action_bar_config",
    default = ViewerActionBarConfig(),
    serializer = ViewerActionBarConfig.serializer(),
)

/** One-time marker for the retired `show_favorite_button` → action-bar migration. */
private val viewerActionBarFavoriteMigratedKey = PrefKey.bool(
    name = "viewer_action_bar_favorite_switch_migrated",
    default = false,
)

@Composable
fun rememberViewerActionBarConfig(): MutableState<ViewerActionBarConfig> {
    val config = rememberPreference(key = viewerActionBarConfigKey)
    val store = checkNotNull(LocalSettingsStore.current) {
        "rememberViewerActionBarConfig requires LocalSettingsStore — provide a SettingsStore at the composition root"
    }
    LaunchedEffect(store) {
        if (store.observe(viewerActionBarFavoriteMigratedKey).first()) return@LaunchedEffect
        // The retired "Favorite button in viewer" switch mapped onto the FAVORITE action:
        // users who had it off get it removed from their saved bar once.
        val legacyHidden = !store.observe(SettingsKeys.showFavoriteButton).first()
        store.put(viewerActionBarFavoriteMigratedKey, true)
        if (legacyHidden) config.value = config.value.withoutFavorite()
    }
    return config
}
