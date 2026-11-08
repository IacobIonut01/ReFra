/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.metadata.MetadataTag
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.SheetIconBadge
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.feature.albums.ui.util.formatSize
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.all_metadata
import com.dot.gallery.feature.exif.generated.resources.clear_metadata_filter
import com.dot.gallery.feature.exif.generated.resources.copy_all_metadata
import com.dot.gallery.feature.exif.generated.resources.filter_metadata_fields
import com.dot.gallery.feature.exif.generated.resources.loading_metadata
import com.dot.gallery.feature.exif.generated.resources.metadata_field_count
import com.dot.gallery.feature.exif.generated.resources.no_matching_metadata_fields
import com.dot.gallery.feature.exif.generated.resources.no_metadata_found
import com.dot.gallery.feature.exif.generated.resources.no_metadata_found_summary
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "All metadata" page: a card for the file, a filter field, then one grouped section per
 * metadata directory. Loading and empty states keep the file card so the page still says which
 * file it describes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataViewContent(
    state: MetadataViewState,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(state.directories, query) { state.directories.filterMetadata(query) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val hasFields = !state.isLoading && state.directories.isNotEmpty()

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.all_metadata),
                onBack = onBack,
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
                actions = if (hasFields) {
                    {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(Res.string.copy_all_metadata),
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(state.directories.toClipboardText())
                                )
                            },
                        )
                    }
                } else null,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(LocalHazeState.current),
            contentPadding = PaddingValues(
                start = paddingValues.calculateStartPadding(layoutDirection) + 16.dp,
                end = paddingValues.calculateEndPadding(layoutDirection) + 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
        ) {
            state.file?.let { file ->
                item(key = "file") {
                    MetadataFileCard(
                        file = file,
                        fieldCount = state.fieldCount.takeUnless { state.isLoading },
                    )
                }
            }
            when {
                state.isLoading -> item(key = "loading") {
                    MetadataPlaceholder(modifier = Modifier.fillParentMaxHeight(0.7f)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            text = stringResource(Res.string.loading_metadata),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.directories.isEmpty() -> item(key = "empty") {
                    MetadataPlaceholder(modifier = Modifier.fillParentMaxHeight(0.7f)) {
                        SheetIconBadge(icon = Icons.Outlined.HideImage, tone = SheetTone.Secondary)
                        Text(
                            text = stringResource(Res.string.no_metadata_found),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.no_metadata_found_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    item(key = "filter") {
                        MetadataFilterField(
                            query = query,
                            onQueryChange = { query = it },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (filtered.isEmpty()) {
                        item(key = "no_match") {
                            Text(
                                text = stringResource(Res.string.no_matching_metadata_fields, query.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    }
                    filtered.forEachIndexed { index, directory ->
                        val dirKey = "${index}_${directory.name}"
                        item(key = "header_$dirKey") {
                            Text(
                                text = directory.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
                            )
                        }
                        items(
                            count = directory.tags.size,
                            key = { tagIndex -> "${dirKey}_${tagIndex}_${directory.tags[tagIndex].name}" }
                        ) { tagIndex ->
                            val tag = directory.tags[tagIndex]
                            MetadataFieldRow(
                                tag = tag,
                                position = when {
                                    directory.tags.size == 1 -> Position.Alone
                                    tagIndex == 0 -> Position.Top
                                    tagIndex == directory.tags.lastIndex -> Position.Bottom
                                    else -> Position.Middle
                                },
                                onCopy = { clipboardManager.setText(AnnotatedString(tag.description)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataFileCard(
    file: MetadataFileInfo,
    fieldCount: Int?,
    modifier: Modifier = Modifier,
) {
    val summary = listOfNotNull(
        file.sizeBytes?.takeIf { it > 0 }?.let { formatSize(it) },
        if ((file.width ?: 0) > 0 && (file.height ?: 0) > 0) "${file.width} × ${file.height}" else null,
        fieldCount?.let { pluralStringResource(Res.plurals.metadata_field_count, it, it) },
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            file.thumbnailModel?.let { model ->
                LocalMediaImageRenderer.current.RenderImage(
                    modifier = Modifier.matchParentSize(),
                    model = model,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    signature = model,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            file.name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetadataFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.filter_metadata_fields),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.clear_metadata_filter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetadataFieldRow(
    tag: MetadataTag,
    position: Position,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = 24.dp
    val inner = 8.dp
    val shape = when (position) {
        Position.Alone -> RoundedCornerShape(outer)
        Position.Top -> RoundedCornerShape(outer, outer, inner, inner)
        Position.Middle -> RoundedCornerShape(inner)
        Position.Bottom -> RoundedCornerShape(inner, inner, outer, outer)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (position == Position.Top || position == Position.Alone) 0.dp else 2.dp)
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = {}, onLongClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = tag.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetadataPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) { content() }
}
