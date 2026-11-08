/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.feature.ignored.generated.resources.Res
import com.dot.gallery.feature.ignored.generated.resources.album_items_count
import com.dot.gallery.feature.ignored.generated.resources.ignored_add_action
import com.dot.gallery.feature.ignored.generated.resources.ignored_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_albums_hidden_count
import com.dot.gallery.feature.ignored.generated.resources.ignored_albums_hidden_label
import com.dot.gallery.feature.ignored.generated.resources.ignored_empty_body
import com.dot.gallery.feature.ignored.generated.resources.ignored_filter_all
import com.dot.gallery.feature.ignored.generated.resources.ignored_filter_empty
import com.dot.gallery.feature.ignored.generated.resources.ignored_filter_patterns
import com.dot.gallery.feature.ignored.generated.resources.ignored_filter_several
import com.dot.gallery.feature.ignored.generated.resources.ignored_filter_single
import com.dot.gallery.feature.ignored.generated.resources.ignored_footnote
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_albums_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_both
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_both_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_timeline
import com.dot.gallery.feature.ignored.generated.resources.ignored_group_timeline_summary
import com.dot.gallery.feature.ignored.generated.resources.ignored_kind_pattern
import com.dot.gallery.feature.ignored.generated.resources.ignored_kind_several
import com.dot.gallery.feature.ignored.generated.resources.ignored_kind_single
import com.dot.gallery.feature.ignored.generated.resources.ignored_rules_count
import com.dot.gallery.feature.ignored.generated.resources.ignored_rules_label
import com.dot.gallery.feature.ignored.generated.resources.ignored_summary_body
import com.dot.gallery.feature.ignored.generated.resources.ignored_summary_title
import com.dot.gallery.feature.ignored.generated.resources.no_ignored_albums
import com.dot.gallery.feature.ignored.generated.resources.no_matches
import com.dot.gallery.feature.ignored.generated.resources.unknown_album
import com.dot.gallery.feature.ignored.ui.components.GroupedRow
import com.dot.gallery.feature.ignored.ui.components.IgnoredSectionLabel
import com.dot.gallery.feature.ignored.ui.components.RuleThumbnail
import com.dot.gallery.feature.ignored.ui.components.SheetButton
import com.dot.gallery.feature.ignored.ui.components.positionOf
import com.dot.gallery.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

private val ContentMaxWidth = 640.dp

private enum class RuleFilter(val label: StringResource, val kind: IgnoredRuleKind?) {
    ALL(Res.string.ignored_filter_all, null),
    SINGLE(Res.string.ignored_filter_single, IgnoredRuleKind.SINGLE),
    SEVERAL(Res.string.ignored_filter_several, IgnoredRuleKind.SEVERAL),
    PATTERN(Res.string.ignored_filter_patterns, IgnoredRuleKind.PATTERN),
}

private data class RuleGroup(val location: Int, val title: StringResource, val summary: StringResource)

private val ruleGroups = listOf(
    RuleGroup(IgnoredAlbum.ALBUMS_AND_TIMELINE, Res.string.ignored_group_both, Res.string.ignored_group_both_summary),
    RuleGroup(IgnoredAlbum.ALBUMS_ONLY, Res.string.ignored_group_albums, Res.string.ignored_group_albums_summary),
    RuleGroup(IgnoredAlbum.TIMELINE_ONLY, Res.string.ignored_group_timeline, Res.string.ignored_group_timeline_summary),
)

private fun Modifier.contentWidth() = widthIn(max = ContentMaxWidth).fillMaxWidth()

/**
 * The Ignored albums page: a summary of what is hidden, filter chips by rule kind, and the rules
 * grouped by where they hide their albums. Tapping a rule calls [onAlbumClick]; the top bar's add
 * action and the empty state's button call [onAddClick].
 */
@Composable
fun IgnoredContent(
    state: IgnoredState,
    albumsState: State<AlbumState>,
    onAddClick: () -> Unit,
    onAlbumClick: (IgnoredAlbum) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val rules = state.albums
    val albumsById = remember(albumsState.value.albumsWithBlacklisted) {
        albumsState.value.albumsWithBlacklisted.associateBy { it.id }
    }
    val hazeState = LocalHazeState.current
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.ignored_albums),
                count = rules.size.takeIf { it > 0 }?.toString(),
                hazeState = hazeState,
                scrolled = rules.isNotEmpty() && rememberScrolledUnderTopBar(listState),
                actions = if (rules.isEmpty()) null else {
                    {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.Add,
                            contentDescription = stringResource(Res.string.ignored_add_action),
                            onClick = onAddClick
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // The page's only haze source, around both branches.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (rules.isEmpty()) {
                NoIgnoredAlbums(onAddClick = onAddClick, modifier = Modifier.padding(padding))
            } else {
                IgnoredRuleList(
                    rules = rules,
                    albumsById = albumsById,
                    padding = padding,
                    listState = listState,
                    onRuleClick = onAlbumClick,
                )
            }
        }
    }
}

@Composable
private fun IgnoredRuleList(
    rules: List<IgnoredAlbum>,
    albumsById: Map<Long, Album>,
    padding: PaddingValues,
    listState: LazyListState,
    onRuleClick: (IgnoredAlbum) -> Unit,
) {
    var filterName by rememberSaveable { mutableStateOf(RuleFilter.ALL.name) }
    val filter = RuleFilter.valueOf(filterName)
    val visible = rules.filter { filter.kind == null || it.kind == filter.kind }
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection) + Spacing.ScreenHorizontal,
            end = padding.calculateEndPadding(layoutDirection) + Spacing.ScreenHorizontal,
            top = padding.calculateTopPadding() + Spacing.Small,
            bottom = padding.calculateBottomPadding() + Spacing.Large
        )
    ) {
        item(key = "summary") {
            IgnoredSummaryCard(rules = rules, modifier = Modifier.contentWidth())
        }
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .contentWidth()
                    .padding(top = Spacing.Medium)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                RuleFilter.entries.forEach { entry ->
                    GalleryFilterChip(
                        label = stringResource(entry.label),
                        selected = entry == filter,
                        onClick = { filterName = entry.name }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "filter_empty") {
                Text(
                    text = stringResource(Res.string.ignored_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.contentWidth().padding(vertical = Spacing.ExtraLarge)
                )
            }
        }
        ruleGroups.forEach { group ->
            val groupRules = visible.filter { it.location == group.location }
            if (groupRules.isEmpty()) return@forEach
            item(key = "group_${group.location}") {
                IgnoredSectionLabel(
                    title = stringResource(group.title),
                    description = stringResource(group.summary),
                    modifier = Modifier.contentWidth()
                )
            }
            groupRules.forEachIndexed { index, rule ->
                item(key = "rule_${rule.id}") {
                    IgnoredRuleRow(
                        rule = rule,
                        albumsById = albumsById,
                        position = positionOf(index, groupRules.size),
                        onClick = { onRuleClick(rule) },
                        modifier = Modifier.contentWidth().padding(bottom = 2.dp)
                    )
                }
            }
        }
        item(key = "footnote") {
            Row(
                modifier = Modifier
                    .contentWidth()
                    .padding(start = Spacing.Small, end = Spacing.Small, top = Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.ignored_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Rule and hidden-album counts over the primary container, in the Intelligence Hub style. */
@Composable
private fun IgnoredSummaryCard(rules: List<IgnoredAlbum>, modifier: Modifier = Modifier) {
    val hiddenAlbums = rules.flatMap { it.matchedAlbums }.distinct().size
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.ignored_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(Res.string.ignored_summary_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            SummaryStat(
                value = pluralStringResource(Res.plurals.ignored_rules_count, rules.size, rules.size),
                label = stringResource(Res.string.ignored_rules_label),
                modifier = Modifier.weight(1f)
            )
            SummaryStat(
                value = pluralStringResource(Res.plurals.ignored_albums_hidden_count, hiddenAlbums, hiddenAlbums),
                label = stringResource(Res.string.ignored_albums_hidden_label),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = Spacing.Medium, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Summary line of a rule: item count for one album, the album names otherwise. */
@Composable
internal fun ruleSummary(rule: IgnoredAlbum, albumsById: Map<Long, Album>): String {
    val names = rule.matchedAlbums.joinToString()
    return when (rule.kind) {
        IgnoredRuleKind.SINGLE -> albumsById[rule.ruleAlbumIds.first()]
            ?.let { album ->
                val count = stringResource(Res.string.album_items_count, album.count.toInt())
                if (rule.customLabel != null) "${album.label} · $count" else count
            }
            ?: names.ifEmpty { stringResource(Res.string.unknown_album) }
        IgnoredRuleKind.SEVERAL -> names
        IgnoredRuleKind.PATTERN -> names.ifEmpty { stringResource(Res.string.no_matches) }
    }
}

@Composable
internal fun ruleKindLabel(rule: IgnoredAlbum): String = when (rule.kind) {
    IgnoredRuleKind.SINGLE -> stringResource(Res.string.ignored_kind_single)
    IgnoredRuleKind.SEVERAL -> rule.ruleAlbumIds.size.let {
        pluralStringResource(Res.plurals.ignored_kind_several, it, it)
    }
    IgnoredRuleKind.PATTERN -> rule.matchedAlbums.size.let {
        pluralStringResource(Res.plurals.ignored_kind_pattern, it, it)
    }
}

@Composable
private fun IgnoredRuleRow(
    rule: IgnoredAlbum,
    albumsById: Map<Long, Album>,
    position: Position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumIds = rule.ruleAlbumIds
    GroupedRow(
        position = position,
        onClick = onClick,
        modifier = modifier,
        leading = {
            RuleThumbnail(
                album = albumIds.firstNotNullOfOrNull { albumsById[it] },
                isPattern = rule.kind == IgnoredRuleKind.PATTERN,
                extraCount = albumIds.size - 1
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    ) {
        Text(
            text = rule.displayTitle(stringResource(Res.string.unknown_album)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = ruleSummary(rule, albumsById),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = ruleKindLabel(rule),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun NoIgnoredAlbums(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.ExtraLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = stringResource(Res.string.no_ignored_albums),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = stringResource(Res.string.ignored_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(Spacing.Large))
        SheetButton(text = stringResource(Res.string.ignored_add_action), onClick = onAddClick)
        Spacer(Modifier.height(64.dp))
    }
}

@Preview
@Composable
private fun IgnoredContentEmptyPreview() {
    IgnoredPreviewHost {
        IgnoredContent(
            state = IgnoredState(),
            albumsState = remember { mutableStateOf(AlbumState()) },
            onAddClick = {},
            onAlbumClick = {}
        )
    }
}

@Preview
@Composable
private fun IgnoredContentMixedItemsPreview() {
    IgnoredPreviewHost {
        IgnoredContent(
            state = IgnoredState(
                albums = listOf(
                    IgnoredAlbum(
                        id = 1,
                        location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                        matchedAlbums = listOf("Screenshots")
                    ),
                    IgnoredAlbum(
                        id = 100,
                        label = "Work documents",
                        albumIds = listOf(1, 2, 3),
                        location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                        matchedAlbums = listOf("Scans", "Receipts", "Whiteboard")
                    ),
                    IgnoredAlbum(
                        id = 200,
                        wildcard = ".*/Telegram/.*",
                        location = IgnoredAlbum.TIMELINE_ONLY,
                        matchedAlbums = listOf("Telegram Images", "Telegram Video")
                    )
                )
            ),
            albumsState = remember { mutableStateOf(AlbumState()) },
            onAddClick = {},
            onAlbumClick = {}
        )
    }
}
