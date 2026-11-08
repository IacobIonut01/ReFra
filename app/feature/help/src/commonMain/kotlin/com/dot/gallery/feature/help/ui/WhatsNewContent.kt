/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.sheetGroupedShape
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.feature.help.domain.HelpRepository
import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.ReleaseNotes
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.help_latest
import com.dot.gallery.feature.help.generated.resources.help_whats_new
import com.dot.gallery.feature.help.generated.resources.version_name_format
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

/**
 * "What's new" changelog feed. [releases] are loaded app-side from the bundled
 * changelog assets and passed in newest-first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewContent(
    releases: List<ReleaseNotes>,
    onNavigate: (HelpRoute) -> Unit,
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.help_whats_new),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize().hazeSource(LocalHazeState.current)
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = padding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(releases, key = { it.versionCode }) { release ->
                ReleaseCard(
                    release = release,
                    isLatest = release == releases.firstOrNull(),
                    onTipClick = { tipId -> onNavigate(HelpRoute.Tip(tipId)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseCard(
    release: ReleaseNotes,
    isLatest: Boolean,
    onTipClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Deep-link chips to related tips
    val tips = remember(release.versionCode) {
        release.tipIds.mapNotNull { id -> HelpRepository.getTip(id)?.let { id to it } }
    }
    val segmentCount = if (tips.isNotEmpty()) 3 else 2
    val segment: @Composable (index: Int, content: @Composable () -> Unit) -> Unit = { index, content ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetGroupedShape(sheetRowPosition(index, segmentCount)))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) { content() }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segment(0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.version_name_format, release.versionName),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (release.releaseDate.isNotBlank()) {
                        Text(
                            text = release.releaseDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isLatest) {
                    Text(
                        text = stringResource(Res.string.help_latest),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
        segment(1) {
            MarkdownText(markdown = release.markdown)
        }
        if (tips.isNotEmpty()) {
            segment(2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tips.forEach { (id, tip) ->
                        AssistChip(
                            onClick = { onTipClick(id) },
                            label = { Text(stringResource(tip.title)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
        }
    }
}
