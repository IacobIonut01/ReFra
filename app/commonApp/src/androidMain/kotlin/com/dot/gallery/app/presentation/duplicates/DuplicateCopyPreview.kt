/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dot.gallery.core.duplicates.DuplicateSet
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.albums.ui.util.formatSizeShort
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.duplicates_copy_position
import com.dot.gallery.generated.resources.duplicates_dont_remove
import com.dot.gallery.generated.resources.duplicates_keep_this_copy
import com.dot.gallery.generated.resources.duplicates_storage_root
import org.jetbrains.compose.resources.stringResource

/**
 * The copies of one set, full screen: swipe between them, keep the one shown, or leave it alone.
 * Opened by tapping a tile on the Duplicates screen.
 */
@Composable
fun DuplicateCopyPreview(
    set: DuplicateSet<UriMedia>,
    startId: Long,
    choices: DuplicateChoices,
    onKeep: (Long) -> Unit,
    onDontRemove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(
            initialPage = set.items.indexOfFirst { it.id == startId }.coerceAtLeast(0),
            pageCount = { set.items.size },
        )
        val keeperId = set.keeperId(choices) { it.id }
        val root = stringResource(Res.string.duplicates_storage_root)
        val labels = remember(set.items, root) { set.folderLabels(root) }
        val current = set.items.getOrNull(pager.currentPage) ?: set.items.first()
        val chrome = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val media = set.items[page]
                LocalMediaImageRenderer.current.RenderImage(
                    modifier = Modifier.fillMaxSize(),
                    model = media.getUri(),
                    contentScale = ContentScale.Fit,
                    contentDescription = null,
                    signature = media,
                )
            }
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(chrome),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_cancel))
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(chrome)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.duplicates_copy_position, pager.currentPage + 1, set.items.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "${labels.getValue(current.id)} · ${formatSizeShort(current.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(chrome)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isKeeper = current.id == keeperId
                FilledTonalButton(
                    onClick = { onDontRemove(current.id) },
                    enabled = !isKeeper && current.id !in choices.kept,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.duplicates_dont_remove))
                }
                Button(
                    onClick = { onKeep(current.id) },
                    enabled = !isKeeper,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.duplicates_keep_this_copy))
                }
            }
            if (current.id == keeperId) {
                KeepBadge(
                    Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, bottom = 96.dp)
                )
            }
        }
    }
}
