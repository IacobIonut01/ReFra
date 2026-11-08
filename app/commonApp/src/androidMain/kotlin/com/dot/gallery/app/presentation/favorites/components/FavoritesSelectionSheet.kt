/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.favorites.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.logging.logEvent
import com.dot.gallery.core.presentation.components.SelectAllAddon
import com.dot.gallery.core.presentation.components.SelectionAddon
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.util.shareMediaWithVaultSupport
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.favorites_unfavorite
import com.dot.gallery.generated.resources.selection_dialog_close_cd
import com.dot.gallery.generated.resources.share
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Selection bar of the Favorites page: the count with Close and Select all above a Share and
 * an Unfavorite button.
 */
@Composable
fun <T : Media> BoxScope.FavoritesSelectionSheet(
    modifier: Modifier = Modifier,
    allMedia: MediaState<T>,
    selectedMedia: SnapshotStateList<T>,
) {
    val selector = LocalMediaSelector.current
    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    // Ids whose unfavorite waits on the system favorite request; their overrides are rolled
    // back if the request is canceled.
    var pendingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val result = rememberActivityResult(
        onResultCanceled = {
            pendingIds.forEach { distributor.clearFavoriteOverride(it) }
            pendingIds = emptySet()
        },
        onResultOk = {
            pendingIds = emptySet()
            selector.clearSelection()
        },
    )

    AnimatedVisibility(
        modifier = modifier.align(Alignment.BottomCenter),
        visible = isSelectionActive,
        enter = slideInVertically { it * 2 },
        exit = slideOutVertically { it * 2 },
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SelectionAddon(
                    onClick = { selector.clearSelection() },
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.selection_dialog_close_cd),
                    text = selectedMedia.size.toString(),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SelectAllAddon(allMedia = allMedia)
                }
            }
            FavoritesSelectionActions(
                onShare = {
                    logEvent("selection.share", mapOf("count" to selectedMedia.size.toString()), scope = "media-ops")
                    scope.launch { context.shareMediaWithVaultSupport(selectedMedia) }
                },
                onUnfavorite = {
                    logEvent("selection.unfavorite", mapOf("count" to selectedMedia.size.toString()), scope = "media-ops")
                    val targets = selectedMedia.toList()
                    // Instant feedback: pin the new state until MediaStore or the provider lands it.
                    targets.forEach { distributor.setFavoriteOverride(it.id, false) }
                    pendingIds = targets.mapTo(HashSet()) { it.id }
                    scope.launch { handler.toggleFavorite(result, targets, favorite = false) }
                },
            )
        }
    }
}

/** The Share (tonal) and Unfavorite (filled) pair of the Favorites selection bar. */
@Composable
fun FavoritesSelectionActions(
    onShare: () -> Unit,
    onUnfavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetButton(
            text = stringResource(Res.string.share),
            onClick = onShare,
            style = SheetButtonStyle.Tonal,
            icon = Icons.Outlined.Share,
            modifier = Modifier.weight(1f),
        )
        SheetButton(
            text = stringResource(Res.string.favorites_unfavorite),
            onClick = onUnfavorite,
            style = SheetButtonStyle.Filled,
            icon = Icons.Outlined.FavoriteBorder,
            modifier = Modifier.weight(1f),
        )
    }
}
