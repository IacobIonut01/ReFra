/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.uri.emptyUri
import com.dot.gallery.feature.ignored.generated.resources.Res
import com.dot.gallery.feature.ignored.generated.resources.albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_location_albums
import com.dot.gallery.feature.ignored.generated.resources.ignored_location_both
import com.dot.gallery.feature.ignored.generated.resources.ignored_location_timeline
import com.dot.gallery.feature.ignored.generated.resources.ignored_rule_hidden_from
import com.dot.gallery.feature.ignored.generated.resources.ignored_rule_name
import com.dot.gallery.feature.ignored.generated.resources.ignored_rule_name_unset
import com.dot.gallery.feature.ignored.generated.resources.ignored_rule_pattern
import com.dot.gallery.feature.ignored.generated.resources.ignored_stop
import com.dot.gallery.feature.ignored.generated.resources.ignored_stopped
import com.dot.gallery.feature.ignored.generated.resources.ignored_undo
import com.dot.gallery.feature.ignored.generated.resources.unknown_album
import com.dot.gallery.feature.ignored.ui.components.GroupedRow
import com.dot.gallery.feature.ignored.ui.components.RuleThumbnail
import com.dot.gallery.feature.ignored.ui.components.SheetButton
import com.dot.gallery.feature.ignored.ui.components.SheetButtonStyle
import com.dot.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** What a row of the rule sheet opens in the ignore sheet. */
enum class IgnoredRuleEdit { LOCATION, ALBUMS, NAME }

/**
 * Sheet for one ignore rule: what it hides and where, each row opening the ignore sheet on that
 * part, and Stop ignoring. The sheet hides itself before calling [onEdit] or [onStopIgnoring].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredRuleSheet(
    sheetState: AppBottomSheetState,
    rule: IgnoredAlbum?,
    albums: List<Album>,
    onEdit: (IgnoredAlbum, IgnoredRuleEdit) -> Unit,
    onStopIgnoring: (IgnoredAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (sheetState.isVisible && rule != null) {
        val hideThen: (() -> Unit) -> Unit = { next ->
            scope.launch {
                sheetState.hide()
                next()
            }
        }
        ModalBottomSheet(
            modifier = Modifier.statusBarsPadding(),
            sheetState = sheetState.sheetState,
            onDismissRequest = { hideThen(onDismiss) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            IgnoredRuleSheetContent(
                rule = rule,
                albums = albums,
                onEdit = { edit -> hideThen { onEdit(rule, edit) } },
                onStopIgnoring = { hideThen { onStopIgnoring(rule) } }
            )
        }
    }
}

@Composable
fun IgnoredRuleSheetContent(
    rule: IgnoredAlbum,
    albums: List<Album>,
    onEdit: (IgnoredRuleEdit) -> Unit,
    onStopIgnoring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumsById = albums.associateBy { it.id }
    val albumIds = rule.ruleAlbumIds
    val isPattern = rule.kind == IgnoredRuleKind.PATTERN
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal, bottom = Spacing.ExtraLarge)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.Small, end = Spacing.Small, bottom = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            RuleThumbnail(
                album = albumIds.firstNotNullOfOrNull { albumsById[it] },
                isPattern = isPattern,
                extraCount = albumIds.size - 1,
                size = 64.dp,
                cornerRadius = 20.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = rule.displayTitle(stringResource(Res.string.unknown_album)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ruleKindLabel(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RuleDetailRow(
            label = stringResource(Res.string.ignored_rule_hidden_from),
            value = stringResource(
                when (rule.location) {
                    IgnoredAlbum.ALBUMS_ONLY -> Res.string.ignored_location_albums
                    IgnoredAlbum.TIMELINE_ONLY -> Res.string.ignored_location_timeline
                    else -> Res.string.ignored_location_both
                }
            ),
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            position = Position.Top,
            onClick = { onEdit(IgnoredRuleEdit.LOCATION) }
        )
        RuleDetailRow(
            label = stringResource(if (isPattern) Res.string.ignored_rule_pattern else Res.string.albums),
            value = if (isPattern) rule.wildcard.orEmpty()
            else rule.matchedAlbums.joinToString().ifEmpty { ruleSummary(rule, albumsById) },
            monospace = isPattern,
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            position = Position.Middle,
            onClick = { onEdit(IgnoredRuleEdit.ALBUMS) }
        )
        RuleDetailRow(
            label = stringResource(Res.string.ignored_rule_name),
            value = rule.customLabel ?: stringResource(Res.string.ignored_rule_name_unset),
            icon = Icons.Outlined.Edit,
            position = Position.Bottom,
            onClick = { onEdit(IgnoredRuleEdit.NAME) }
        )
        SheetButton(
            text = stringResource(Res.string.ignored_stop),
            onClick = onStopIgnoring,
            style = SheetButtonStyle.Error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Large)
        )
    }
}

/** Confirms that [rule] was removed, with Undo. Returns true when the user tapped Undo. */
suspend fun SnackbarHostState.showStoppedIgnoring(rule: IgnoredAlbum): Boolean {
    val title = rule.displayTitle(getString(Res.string.unknown_album))
    return showSnackbar(
        message = getString(Res.string.ignored_stopped, title),
        actionLabel = getString(Res.string.ignored_undo),
        duration = SnackbarDuration.Long
    ) == SnackbarResult.ActionPerformed
}

@Composable
private fun RuleDetailRow(
    label: String,
    value: String,
    icon: ImageVector,
    position: Position,
    onClick: () -> Unit,
    monospace: Boolean = false,
) {
    GroupedRow(
        position = position,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        modifier = Modifier.padding(bottom = 2.dp),
        trailing = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = value,
            style = if (monospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
private fun IgnoredRuleSheetPreview() {
    IgnoredPreviewHost {
        IgnoredRuleSheetContent(
            rule = IgnoredAlbum(
                id = 100,
                label = "Work documents",
                albumIds = listOf(1, 2),
                location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                matchedAlbums = listOf("Scans", "Receipts")
            ),
            albums = listOf(
                Album(id = 1, label = "Scans", uri = emptyUri, pathToThumbnail = "", relativePath = "", timestamp = 0, count = 40),
                Album(id = 2, label = "Receipts", uri = emptyUri, pathToThumbnail = "", relativePath = "", timestamp = 0, count = 12),
            ),
            onEdit = {},
            onStopIgnoring = {}
        )
    }
}
