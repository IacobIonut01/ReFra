/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.dot.gallery.cloud.ui.backup.UploadDetailsUiState
import com.dot.gallery.app.presentation.main.MainScreenTopBarContent
import com.dot.gallery.core.presentation.components.rememberGalleryHeaderRevealState
import androidx.compose.runtime.SideEffect
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.more_options
import com.dot.gallery.generated.resources.sort_and_filter
import org.jetbrains.compose.resources.stringResource

@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 320, fontScale = 1.5f)
@Composable
private fun TimelineHomeTopBarPreview(
    @PreviewParameter(TimelineBackupPreviewProvider::class) state: UploadDetailsUiState,
) {
    PreviewHost(darkTheme = isSystemInDarkTheme()) {
        val reveal = rememberGalleryHeaderRevealState()
        SideEffect {
            reveal.enabled = state.isWorkerRunning
            if (state.isWorkerRunning) reveal.expand()
        }
        MainScreenTopBarContent(
            backup = state.takeIf { it.isWorkerRunning },
            revealState = reveal,
            onBrandClick = {},
            onOpenBackupDetails = {},
            filter = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.FilterList, stringResource(Res.string.sort_and_filter))
                }
            },
            menu = {
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.MoreVert, stringResource(Res.string.more_options))
                }
            },
        )
    }
}
