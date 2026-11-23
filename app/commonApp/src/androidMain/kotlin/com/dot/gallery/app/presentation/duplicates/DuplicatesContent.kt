/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.duplicates.DuplicateSet
import com.dot.gallery.feature.timeline.TimelineFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.feature.albums.ui.util.formatSizeShort
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.duplicates_check_again
import com.dot.gallery.generated.resources.duplicates_checking
import com.dot.gallery.generated.resources.duplicates_checking_progress
import com.dot.gallery.generated.resources.duplicates_checking_summary
import com.dot.gallery.generated.resources.duplicates_copy_kept
import com.dot.gallery.generated.resources.duplicates_copy_not_selected
import com.dot.gallery.generated.resources.duplicates_copy_selected
import com.dot.gallery.generated.resources.duplicates_delete
import com.dot.gallery.generated.resources.duplicates_filter_all
import com.dot.gallery.generated.resources.duplicates_filter_photos
import com.dot.gallery.generated.resources.duplicates_filter_videos
import com.dot.gallery.generated.resources.duplicates_found_so_far
import com.dot.gallery.generated.resources.duplicates_just_now
import com.dot.gallery.generated.resources.duplicates_keep
import com.dot.gallery.generated.resources.duplicates_large_files
import com.dot.gallery.generated.resources.duplicates_large_files_summary
import com.dot.gallery.generated.resources.duplicates_last_checked
import com.dot.gallery.generated.resources.duplicates_move_to_trash
import com.dot.gallery.generated.resources.duplicates_none_some_skipped
import com.dot.gallery.generated.resources.duplicates_none_summary
import com.dot.gallery.generated.resources.duplicates_none_title
import com.dot.gallery.generated.resources.duplicates_not_checked
import com.dot.gallery.generated.resources.duplicates_set_folders
import com.dot.gallery.generated.resources.duplicates_set_header
import com.dot.gallery.generated.resources.duplicates_set_same_folder
import com.dot.gallery.generated.resources.duplicates_settings
import com.dot.gallery.generated.resources.duplicates_storage_root
import com.dot.gallery.generated.resources.duplicates_summary
import com.dot.gallery.generated.resources.duplicates_title
import com.dot.gallery.core.util.SdkCompat
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/** At or above this width the summary sits in a column beside the sets. */
private val TwoPaneMinWidth = 600.dp
private val SummaryPaneWidth = 360.dp
private val SetMinWidth = 320.dp
private val SetMaxWidth = 420.dp
private const val TILES_PER_ROW = 3

@Composable
fun DuplicatesContent(
    state: DuplicatesUiState,
    progress: DuplicateCheckProgress?,
    onFilter: (DuplicateFilter) -> Unit,
    onToggle: (DuplicateSet<UriMedia>, Long) -> Unit,
    onOpen: (DuplicateSet<UriMedia>, Long) -> Unit,
    onOpenSettings: () -> Unit,
    onCheckAgain: () -> Unit,
    onCheckLargeOnce: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.duplicates_title),
                actions = {
                    GalleryPageTopBarAction(
                        icon = Icons.Outlined.Tune,
                        contentDescription = stringResource(Res.string.duplicates_settings),
                        onClick = onOpenSettings,
                    )
                    OverflowMenu(onCheckAgain = onCheckAgain, enabled = progress == null)
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val checking = progress != null || !state.loaded
            val selectedCount = state.selected.size
            val summary: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(state = state, progress = progress)
                    if (!checking && state.sets.isNotEmpty()) {
                        FilterRow(state = state, onFilter = onFilter)
                    }
                    if (!checking && state.check.skippedLarge > 0) {
                        LargeFilesRow(count = state.check.skippedLarge, onClick = onCheckLargeOnce)
                    }
                }
            }
            val sets = state.visibleSets
            if (twoPane) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    summary(
                        Modifier
                            .width(SummaryPaneWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 96.dp)
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (checking && sets.isNotEmpty()) SectionLabel(stringResource(Res.string.duplicates_found_so_far))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            sets.forEach { set ->
                                SetBlock(
                                    set = set,
                                    state = state,
                                    onToggle = onToggle,
                                    onOpen = onOpen,
                                    modifier = Modifier.widthIn(min = SetMinWidth, max = SetMaxWidth),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "summary") { summary(Modifier.fillMaxWidth()) }
                    if (checking && sets.isNotEmpty()) {
                        item(key = "found") { SectionLabel(stringResource(Res.string.duplicates_found_so_far)) }
                    }
                    items(sets, key = { it.sha1 }) { set ->
                        SetBlock(set = set, state = state, onToggle = onToggle, onOpen = onOpen)
                    }
                }
            }

            BottomAction(
                checking = checking,
                hasSets = state.sets.isNotEmpty(),
                selectedCount = selectedCount,
                twoPane = twoPane,
                onMoveToTrash = onMoveToTrash,
                onCheckAgain = onCheckAgain,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun OverflowMenu(onCheckAgain: () -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        GalleryPageTopBarAction(
            icon = Icons.Outlined.MoreVert,
            contentDescription = null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.duplicates_check_again)) },
                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                enabled = enabled,
                onClick = {
                    open = false
                    onCheckAgain()
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(state: DuplicatesUiState, progress: DuplicateCheckProgress?) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val checking = progress != null || !state.loaded
    val none = !checking && state.sets.isEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (none) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(4.dp))
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Text(
            text = when {
                checking -> stringResource(Res.string.duplicates_checking)
                none -> stringResource(Res.string.duplicates_none_title)
                else -> formatSizeShort(state.extraBytes)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = onContainer,
        )
        Text(
            text = when {
                checking -> stringResource(Res.string.duplicates_checking_summary)
                none && state.check.skippedLarge > 0 -> stringResource(Res.string.duplicates_none_some_skipped)
                none -> stringResource(Res.string.duplicates_none_summary)
                else -> pluralStringResource(
                    Res.plurals.duplicates_summary,
                    state.sets.size,
                    state.sets.size,
                    state.extraCopies,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
        )
        if (progress != null) {
            val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
            )
            if (progress.total > 0) {
                Text(
                    text = stringResource(
                        Res.string.duplicates_checking_progress,
                        numbers.format(progress.done),
                        numbers.format(progress.total),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer,
                )
            }
        } else if (state.check.lastCheckedMillis > 0) {
            val now = System.currentTimeMillis()
            val ago = if (now - state.check.lastCheckedMillis < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(Res.string.duplicates_just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(
                    state.check.lastCheckedMillis,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            }
            Text(
                text = stringResource(
                    Res.string.duplicates_last_checked,
                    numbers.format(state.check.checkedCount),
                    ago.replaceFirstChar { it.lowercase() },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
            )
        }
    }
}

@Composable
private fun FilterRow(state: DuplicatesUiState, onFilter: (DuplicateFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            DuplicateFilter.ALL to stringResource(Res.string.duplicates_filter_all, state.sets.size),
            DuplicateFilter.PHOTOS to stringResource(Res.string.duplicates_filter_photos),
            DuplicateFilter.VIDEOS to stringResource(Res.string.duplicates_filter_videos),
        ).forEach { (filter, label) ->
            TimelineFilterChip(
                label = label,
                selected = state.filter == filter,
                role = Role.RadioButton,
                onClick = { onFilter(filter) },
            )
        }
    }
}

@Composable
private fun LargeFilesRow(count: Int, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(Res.string.duplicates_not_checked))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(Res.plurals.duplicates_large_files, count, count),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(Res.string.duplicates_large_files_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

/** Folder names for the copies of this set, by media id; [root] names the top of the storage. */
internal fun DuplicateSet<UriMedia>.folderLabels(root: String): Map<Long, String> =
    folderLabels(items, id = { it.id }, album = { it.albumLabel }, relativePath = { it.relativePath })
        .mapValues { (_, label) -> label.ifBlank { root } }

@Composable
private fun SetBlock(
    set: DuplicateSet<UriMedia>,
    state: DuplicatesUiState,
    onToggle: (DuplicateSet<UriMedia>, Long) -> Unit,
    onOpen: (DuplicateSet<UriMedia>, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keeperId = set.keeperId(state.choices) { it.id }
    val root = stringResource(Res.string.duplicates_storage_root)
    val labels = remember(set.items, root) { set.folderLabels(root) }
    val keeperLabel = labels.getValue(keeperId)
    val others = set.items.filter { it.id != keeperId }.map { labels.getValue(it.id) }
        .filter { it != keeperLabel }.distinct()
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.duplicates_set_header, set.items.size, formatSizeShort(set.itemSize)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (others.isEmpty()) {
                    stringResource(Res.string.duplicates_set_same_folder, keeperLabel)
                } else {
                    stringResource(Res.string.duplicates_set_folders, keeperLabel, others.joinToString(", "))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        set.items.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { media ->
                    val position = set.items.indexOf(media) + 1
                    CopyTile(
                        media = media,
                        folder = labels.getValue(media.id),
                        position = position,
                        count = set.items.size,
                        isKeeper = media.id == keeperId,
                        selected = media.id != keeperId && media.id !in state.choices.kept,
                        onToggle = { onToggle(set, media.id) },
                        onOpen = { onOpen(set, media.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CopyTile(
    media: UriMedia,
    folder: String,
    position: Int,
    count: Int,
    isKeeper: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val description = when {
        isKeeper -> stringResource(Res.string.duplicates_copy_kept, position, count, folder)
        selected -> stringResource(Res.string.duplicates_copy_selected, position, count, folder)
        else -> stringResource(Res.string.duplicates_copy_not_selected, position, count, folder)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (selected) Modifier.border(BorderStroke(3.dp, primary), RoundedCornerShape(16.dp)) else Modifier)
            .semantics { contentDescription = description }
            .clickable(onClick = onOpen),
    ) {
        LocalMediaImageRenderer.current.RenderImage(
            modifier = Modifier.fillMaxSize(),
            model = media.getUri(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
            signature = media,
        )
        if (media.isVideo) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
        if (isKeeper) {
            KeepBadge(Modifier.padding(8.dp))
        } else {
            // A 48 dp target in the corner toggles the copy; the rest of the tile opens it.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clearAndSetSemantics {
                        role = Role.Checkbox
                        stateDescription = description
                    }
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (selected) primary else Color.Black.copy(alpha = 0.2f))
                        .then(if (selected) Modifier else Modifier.border(2.dp, Color.White, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The pill on the copy that stays: a check and "Keep". */
@Composable
internal fun KeepBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.duplicates_keep),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomAction(
    checking: Boolean,
    hasSets: Boolean,
    selectedCount: Int,
    twoPane: Boolean,
    onMoveToTrash: () -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = if (twoPane) Modifier else Modifier.fillMaxWidth()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = if (twoPane) Alignment.CenterEnd else Alignment.Center,
    ) {
        when {
            checking -> FilledTonalButton(onClick = {}, enabled = false, modifier = width.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.duplicates_checking))
            }
            hasSets && selectedCount > 0 -> Button(onClick = onMoveToTrash, modifier = width.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (SdkCompat.supportsTrash) {
                        pluralStringResource(Res.plurals.duplicates_move_to_trash, selectedCount, selectedCount)
                    } else {
                        pluralStringResource(Res.plurals.duplicates_delete, selectedCount, selectedCount)
                    }
                )
            }
            !hasSets -> FilledTonalButton(onClick = onCheckAgain, modifier = width.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.duplicates_check_again))
            }
            else -> Unit
        }
    }
}
