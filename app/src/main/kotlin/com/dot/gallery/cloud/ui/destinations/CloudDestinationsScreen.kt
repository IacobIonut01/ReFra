/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

/**
 * Stable accent color per account, keyed by display order, derived from the theme so it
 * matches the backup dashboard legend.
 */
@Composable
private fun rememberDestinationPalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        listOf(
            cs.primary, cs.tertiary, cs.secondary, cs.error,
            cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer, cs.inversePrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDestinationsScreen(
    onAddAccount: () -> Unit = {},
    viewModel: CloudDestinationsViewModel = hiltViewModel()
) {
    val allAccounts by viewModel.accounts.collectAsStateWithLifecycle()
    val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
    val enabledByAlbum by viewModel.enabledByAlbum.collectAsStateWithLifecycle()
    val deleteLocalByAlbum by viewModel.deleteLocalByAlbum.collectAsStateWithLifecycle()

    // When scoped to a single service (opened from its "Select albums" action) the
    // screen acts as that account's album picker: each album gets a direct on/off
    // toggle for this one cloud instead of the multi-cloud bottom sheet.
    val filterConfigId = viewModel.filterConfigId
    val singleAccount = remember(allAccounts, filterConfigId) {
        if (filterConfigId > 0) allAccounts.firstOrNull { it.configId == filterConfigId } else null
    }
    val singleMode = filterConfigId > 0
    val accounts = if (singleMode) listOfNotNull(singleAccount) else allAccounts

    val palette = rememberDestinationPalette()
    // Colors keyed off ALL accounts so a scoped account keeps its dashboard color.
    val colorByConfig = remember(allAccounts, palette) {
        allAccounts.mapIndexed { index, account -> account.configId to palette[index % palette.size] }.toMap()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    // The album whose destination bottom sheet is open, if any (multi-cloud mode).
    var sheetAlbum by remember { mutableStateOf<Album?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = {
                    Text(
                        if (singleMode) singleAccount?.label
                            ?: stringResource(R.string.cloud_destinations_title)
                        else stringResource(R.string.cloud_destinations_title)
                    )
                },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (accounts.isEmpty()) {
                item(key = "empty_accounts") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        message = stringResource(R.string.cloud_destinations_no_accounts),
                        actionText = stringResource(R.string.cloud_destinations_add_account),
                        onAction = onAddAccount
                    )
                }
                return@LazyColumn
            }

            item(key = "intro") {
                if (singleMode) {
                    IntroSubtitle(text = stringResource(R.string.cloud_destinations_pick_subtitle))
                } else {
                    IntroLegend(
                        accounts = accounts,
                        colorByConfig = colorByConfig,
                        enabledByAlbum = enabledByAlbum
                    )
                }
            }

            if (albums.isEmpty()) {
                item(key = "empty_albums") {
                    EmptyState(
                        icon = Icons.Outlined.PhotoLibrary,
                        message = stringResource(R.string.cloud_destinations_no_albums)
                    )
                }
            } else {
                items(albums, key = { it.id }) { album ->
                    val targets = enabledByAlbum[album.id] ?: emptySet()
                    if (singleMode && singleAccount != null) {
                        AlbumPickerCard(
                            album = album,
                            enabled = singleAccount.configId in targets,
                            onToggle = { enabled ->
                                viewModel.setDestination(album, singleAccount.configId, enabled)
                            }
                        )
                    } else {
                        AlbumDestinationCard(
                            album = album,
                            accounts = accounts,
                            targets = targets,
                            colorByConfig = colorByConfig,
                            onClick = { sheetAlbum = album }
                        )
                    }
                }
            }
        }
    }

    // Album-first destination editor: tapping an album opens a multi-select sheet
    // asking where it should back up. Replaces the old chip dropdown + inline expand.
    sheetAlbum?.let { album ->
        val targets = enabledByAlbum[album.id] ?: emptySet()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetAlbum = null },
            sheetState = sheetState
        ) {
            DestinationSheetContent(
                album = album,
                accounts = accounts,
                targets = targets,
                colorByConfig = colorByConfig,
                deleteLocalEnabled = deleteLocalByAlbum[album.id] == true,
                onToggle = { configId, enabled -> viewModel.setDestination(album, configId, enabled) },
                onToggleAll = { enabled -> viewModel.setAlbumAllClouds(album, enabled) },
                onToggleDeleteLocal = { enabled -> viewModel.setDeleteLocal(album, enabled) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroLegend(
    accounts: List<DestinationAccount>,
    colorByConfig: Map<Long, Color>,
    enabledByAlbum: Map<Long, Set<Long>>
) {
    // Count, per account, how many albums currently target it.
    val albumsPerConfig = remember(enabledByAlbum) {
        val counts = mutableMapOf<Long, Int>()
        enabledByAlbum.values.forEach { set -> set.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        counts
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.cloud_destinations_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.forEach { account ->
                AccountLegendChip(
                    account = account,
                    color = colorByConfig[account.configId] ?: MaterialTheme.colorScheme.primary,
                    albumCount = albumsPerConfig[account.configId] ?: 0
                )
            }
        }
    }
}

@Composable
private fun AccountLegendChip(
    account: DestinationAccount,
    color: Color,
    albumCount: Int
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CloudBadge(providerType = account.providerType, color = color)
        Column {
            Text(
                text = account.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.cloud_destinations_account_albums, albumCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A small rounded badge carrying a provider's brand icon, tinted with its accent [color]. */
@Composable
private fun CloudBadge(
    providerType: com.dot.gallery.cloud.core.ProviderType,
    color: Color,
    modifier: Modifier = Modifier,
    badgeSize: Int = 28,
    iconSize: Int = 16
) {
    Box(
        modifier = modifier
            .size(badgeSize.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        ProviderBrandIcon(
            providerType = providerType,
            tint = color,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Simple explanatory subtitle card, used in single-service picker mode. */
@Composable
private fun IntroSubtitle(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Album row used when the screen is scoped to a single service: a direct on/off
 * [Switch] toggles whether this album backs up to that one cloud.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumPickerCard(
    album: Album,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onToggle(!enabled) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            model = album.uri,
            contentDescription = album.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            requestBuilderTransform = { it.signature(GlideInvalidation.signature(album)) }
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (enabled) stringResource(R.string.cloud_destinations_album_backing_up, album.count)
                else stringResource(R.string.cloud_destinations_album_none, album.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumDestinationCard(
    album: Album,
    accounts: List<DestinationAccount>,
    targets: Set<Long>,
    colorByConfig: Map<Long, Color>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            model = album.uri,
            contentDescription = album.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            requestBuilderTransform = { it.signature(GlideInvalidation.signature(album)) }
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val summary = if (targets.isEmpty()) {
                stringResource(R.string.cloud_destinations_album_none, album.count)
            } else {
                stringResource(R.string.cloud_destinations_album_summary, album.count, targets.size)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Brand-icon badges for the clouds this album currently targets.
        if (targets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                accounts.filter { it.configId in targets }.take(4).forEach { account ->
                    CloudBadge(
                        providerType = account.providerType,
                        color = colorByConfig[account.configId] ?: Color.Gray,
                        badgeSize = 24,
                        iconSize = 14
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DestinationSheetContent(
    album: Album,
    accounts: List<DestinationAccount>,
    targets: Set<Long>,
    colorByConfig: Map<Long, Color>,
    deleteLocalEnabled: Boolean,
    onToggle: (configId: Long, enabled: Boolean) -> Unit,
    onToggleAll: (enabled: Boolean) -> Unit,
    onToggleDeleteLocal: (enabled: Boolean) -> Unit
) {
    val allEnabled = accounts.isNotEmpty() && targets.size == accounts.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: album identity + the guiding question.
        Text(
            text = album.label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.cloud_destinations_sheet_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))

        // All clouds master toggle.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.cloud_destinations_all_clouds),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = allEnabled, onCheckedChange = onToggleAll)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

        accounts.forEach { account ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloudBadge(
                    providerType = account.providerType,
                    color = colorByConfig[account.configId] ?: Color.Gray
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = account.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = account.configId in targets,
                    onCheckedChange = { enabled -> onToggle(account.configId, enabled) }
                )
            }
        }

        // Global per-album delete-local. Only meaningful once the album has a destination.
        if (targets.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cloud_destinations_delete_local),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.cloud_destinations_delete_local_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = deleteLocalEnabled, onCheckedChange = onToggleDeleteLocal)
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionText != null && onAction != null) {
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}
