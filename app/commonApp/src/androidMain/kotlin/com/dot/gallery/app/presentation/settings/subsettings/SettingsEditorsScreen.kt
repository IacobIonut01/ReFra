/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.app.presentation.util.getEditImageCapableApps
import com.dot.gallery.app.presentation.util.getEditVideoCapableApps
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.feature.settings.ui.EditAppEntry
import com.dot.gallery.feature.settings.ui.SettingsEditorsContent
import com.dot.gallery.feature.settings.ui.SettingsEditorsState
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * App-side shell for the Editors settings page: owns the platform-only seams
 * (edit-app discovery via PackageManager, launcher/package icons for the choice
 * tiles) and delegates everything else to [SettingsEditorsContent] in
 * `:feature:settings`.
 */
@Composable
fun SettingsEditorsScreen() {
    val context = LocalContext.current
    val state = remember(context) {
        SettingsEditorsState(
            editApps = context.getEditImageCapableApps().map {
                EditAppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(context.packageManager).toString(),
                )
            },
            videoEditApps = context.getEditVideoCapableApps().map {
                EditAppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(context.packageManager).toString(),
                )
            },
        )
    }

    SettingsEditorsContent(
        state = state,
        appIcon = { EditorAppIcon(it) },
        searchSink = SettingsSearchRegistry::register,
        searchRoute = Screen.SettingsEditorsScreen(),
    )
}

/**
 * Icon for one editor choice tile: the app's own launcher artwork for the
 * built-in/ReFra sentinels, the package's icon for external editors.
 */
@Composable
private fun EditorAppIcon(value: String) {
    val context = LocalContext.current
    val drawable = remember(value) {
        if (value == EDITOR_BUILTIN || value == EDITOR_STUDIO) {
            AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
        } else {
            context.packageIcon(value)
        }
    }
    if (drawable != null) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.packageIcon(packageName: String) =
    runCatching { packageManager.getApplicationIcon(packageName) }
        .onFailure {
            printWarn(
                "settings.editors",
                "no launcher icon for editor package $packageName",
                ctx = mapOf("package" to packageName),
            )
        }
        .getOrNull()
