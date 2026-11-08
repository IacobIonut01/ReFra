/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppLogoAlias
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.settings.ui.SettingsGeneralActions
import com.dot.gallery.feature.settings.ui.SettingsGeneralContent
import com.dot.gallery.feature.settings.ui.SettingsGeneralState
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.changeAppAlias
import com.dot.gallery.app.presentation.util.restartApplication
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dot.gallery.R

/**
 * Android shell for [SettingsGeneralContent]: owns the launcher-alias prefs and
 * their platform application (`changeAppAlias` + restart), the mipmap-backed
 * previews, trash navigation and the settings-search sink.
 */
@Composable
fun SettingsGeneralScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    var appNameAlias by rememberAppNameAlias()
    var appLogoAlias by rememberAppLogoAlias()

    val actions = SettingsGeneralActions(
        onOpenTrash = { eventHandler.navigate(Screen.TrashedScreen()) },
        onAppAliasChange = { name, logo ->
            appNameAlias = name
            appLogoAlias = logo
            context.changeAppAlias(name, logo)
            scope.launch {
                delay(300)
                context.restartApplication()
            }
        },
        searchSink = SettingsSearchRegistry::register,
        searchRoute = Screen.SettingsGeneralScreen(),
    )

    SettingsGeneralContent(
        state = SettingsGeneralState(
            supportsTrash = SdkCompat.supportsTrash,
            appNameAlias = appNameAlias,
            appLogoAlias = appLogoAlias,
        ),
        actions = actions,
        appIcon = { alias -> LauncherIcon(alias) },
    )
}

/** The round launcher icon of one launcher alias, as the home screen draws it. */
@Composable
private fun LauncherIcon(alias: String) {
    val context = LocalContext.current
    val iconRes = when (alias) {
        Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
        Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
        Settings.Misc.ALIAS_GALLERY_MONO -> R.mipmap.ic_launcher_gallery_mono_round
        else -> R.mipmap.ic_launcher_round
    }
    Image(
        painter = rememberDrawablePainter(
            drawable = AppCompatResources.getDrawable(context, iconRes)
        ),
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
    )
}
