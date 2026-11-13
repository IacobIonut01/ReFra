/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.components.PreferencePreviewStage
import com.dot.gallery.core.ui.components.PreferencePreviewTile
import com.dot.gallery.core.ui.components.PreviewIconBadge
import com.dot.gallery.core.ui.components.RequestInitialSettingsFocus
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.domain.settings.ALIAS_GALLERY
import com.dot.gallery.domain.settings.ALIAS_GALLERY_MONO
import com.dot.gallery.domain.settings.ALIAS_MONO
import com.dot.gallery.domain.settings.ALIAS_REFRA
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.app_icon_and_name
import com.dot.gallery.feature.settings.generated.resources.app_icon_apply
import com.dot.gallery.feature.settings.generated.resources.app_icon_logo_header
import com.dot.gallery.feature.settings.generated.resources.app_icon_name_header
import com.dot.gallery.feature.settings.generated.resources.app_icon_restart_note
import com.dot.gallery.feature.settings.generated.resources.app_logo_description

private val LOGO_ALIASES = listOf(ALIAS_REFRA, ALIAS_GALLERY, ALIAS_MONO, ALIAS_GALLERY_MONO)
private val NAME_ALIASES = listOf(ALIAS_REFRA, ALIAS_GALLERY)

/**
 * Combined launcher icon + name picker: both choices stay pending until **Apply**, which hands
 * the pair to [onApply] once. [appIcon] draws the real launcher icon per alias on
 * Android; without it (docs scenes) tiles fall back to a generic badge.
 */
@Composable
fun AppIconNameDetailScreen(
    nameAlias: String,
    logoAlias: String,
    appIcon: (@Composable (alias: String) -> Unit)?,
    onApply: (name: String, logo: String) -> Unit,
) {
    var pendingName by rememberSaveable(nameAlias) { mutableStateOf(nameAlias) }
    var pendingLogo by rememberSaveable(logoAlias) { mutableStateOf(logoAlias) }
    val dirty = pendingName != nameAlias || pendingLogo != logoAlias

    val listState = rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    RequestInitialSettingsFocus(initialFocusRequester)

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.app_icon_and_name),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(LocalHazeState.current)
                .settingsFocusGroup(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 8.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "description") {
                Text(
                    text = stringResource(Res.string.app_logo_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .settingsContentWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                )
            }

            item(key = "logo_header") {
                SectionLabel(stringResource(Res.string.app_icon_logo_header))
            }
            item(key = "logo_tiles") {
                PreferencePreviewStage {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LOGO_ALIASES.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { alias ->
                                    PreferencePreviewTile(
                                        selected = pendingLogo == alias,
                                        label = alias,
                                        onClick = { pendingLogo = alias },
                                        canvasHeight = 120.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (alias == LOGO_ALIASES.first()) {
                                                    Modifier.focusRequester(initialFocusRequester)
                                                } else Modifier
                                            ),
                                    ) {
                                        if (appIcon != null) appIcon(alias)
                                        else PreviewIconBadge(Icons.Outlined.Apps)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "name_header") {
                SectionLabel(stringResource(Res.string.app_icon_name_header))
            }
            item(key = "name_tiles") {
                PreferencePreviewStage {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NAME_ALIASES.forEach { alias ->
                            PreferencePreviewTile(
                                selected = pendingName == alias,
                                label = alias,
                                onClick = { pendingName = alias },
                                canvasHeight = 120.dp,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                if (appIcon != null) appIcon(pendingLogo)
                                else PreviewIconBadge(Icons.Outlined.Apps)
                            }
                        }
                    }
                }
            }

            item(key = "apply") {
                Button(
                    onClick = { onApply(pendingName, pendingLogo) },
                    enabled = dirty,
                    modifier = Modifier
                        .settingsContentWidth()
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(stringResource(Res.string.app_icon_apply))
                }
            }
            item(key = "restart_note") {
                Text(
                    text = stringResource(Res.string.app_icon_restart_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .settingsContentWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                )
            }
        }
    }
}

/** Quiet in-page section label above a group of preview tiles. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .settingsContentWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}
