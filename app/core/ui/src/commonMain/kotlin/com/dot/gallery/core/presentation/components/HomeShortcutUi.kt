/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.home_shortcut_favorites
import com.dot.gallery.core.ui.generated.resources.home_shortcut_none
import com.dot.gallery.core.ui.generated.resources.home_shortcut_trash
import com.dot.gallery.core.ui.generated.resources.home_shortcut_vault
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import org.jetbrains.compose.resources.stringResource

val HomeShortcut.icon: ImageVector
    get() = when (this) {
        HomeShortcut.FAVORITES -> Icons.Outlined.FavoriteBorder
        HomeShortcut.TRASH -> Icons.Outlined.DeleteOutline
        HomeShortcut.VAULT -> GalleryIcons.Encrypted
        HomeShortcut.NONE -> Icons.Outlined.Block
    }

@Composable
fun HomeShortcut.label(): String = stringResource(
    when (this) {
        HomeShortcut.FAVORITES -> Res.string.home_shortcut_favorites
        HomeShortcut.TRASH -> Res.string.home_shortcut_trash
        HomeShortcut.VAULT -> Res.string.home_shortcut_vault
        HomeShortcut.NONE -> Res.string.home_shortcut_none
    },
)

/** The Timeline shortcut chosen in Settings › Timeline & albums › Top bar. */
@Composable
fun rememberHomeShortcut(): HomeShortcut {
    val stored by rememberPreference(SettingsKeys.homeShortcut)
    val legacyShowFavorites by rememberPreference(SettingsKeys.showHomeFavoritesButton)
    return HomeShortcut.resolve(stored, legacyShowFavorites)
}
