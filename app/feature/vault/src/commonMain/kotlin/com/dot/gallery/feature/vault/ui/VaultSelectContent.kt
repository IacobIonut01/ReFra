/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.VaultContentMaxWidth
import com.dot.gallery.feature.vault.ui.components.VaultSectionLabel
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlin.uuid.Uuid
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.create_vault_cd
import com.dot.gallery.feature.vault.generated.resources.vault_list_summary_custom
import com.dot.gallery.feature.vault.generated.resources.vault_list_summary_device
import com.dot.gallery.feature.vault.generated.resources.vault_list_summary_none
import com.dot.gallery.feature.vault.generated.resources.vault_manage_security
import com.dot.gallery.feature.vault.generated.resources.vault_method_device
import com.dot.gallery.feature.vault.generated.resources.vault_new_vault_meta
import com.dot.gallery.feature.vault.generated.resources.vault_new_vault_title
import com.dot.gallery.feature.vault.generated.resources.vault_title
import com.dot.gallery.feature.vault.generated.resources.vault_type_password
import com.dot.gallery.feature.vault.generated.resources.vault_type_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_type_pin
import com.dot.gallery.feature.vault.generated.resources.vault_your_vaults
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalLayoutDirection
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/** Name of a vault's lock: its custom type, or device security when it has none. */
@Composable
internal fun vaultLockLabel(type: VaultAuthType?): String = stringResource(
    when (type) {
        null -> Res.string.vault_method_device
        VaultAuthType.PIN -> Res.string.vault_type_pin
        VaultAuthType.PATTERN -> Res.string.vault_type_pattern
        VaultAuthType.PASSWORD -> Res.string.vault_type_password
    }
)

/**
 * Vault selector: a summary of how the list is locked, then one tile per vault and a tile to
 * create another. Tiles never show thumbnails before the vault is unlocked. Pure Compose — all
 * persistence/navigation is delegated to callbacks.
 *
 * [gateMode] is the vault-list lock shown in the summary (null reads as device security);
 * [lockTypes] holds each vault's custom lock (a null value is device security; a missing
 * key leaves the tile without a lock label).
 */
@Composable
fun VaultSelectContent(
    vaultState: VaultState,
    onVaultSelected: (Vault) -> Unit,
    onCreateVault: () -> Unit,
    onChangeGateSecurity: () -> Unit,
    onNavigateUp: () -> Unit,
    gateMode: GateMode? = null,
    lockTypes: Map<Uuid, VaultAuthType?> = emptyMap(),
) {
    val vaults = vaultState.vaults
    val hazeState = LocalHazeState.current
    val gridState = rememberLazyGridState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.vault_title),
                count = vaults.size.takeIf { it > 0 }?.toString(),
                onBack = onNavigateUp,
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(gridState),
                actions = {
                    GalleryPageTopBarAction(
                        icon = Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.create_vault_cd),
                        onClick = onCreateVault,
                    )
                    GalleryPageTopBarAction(
                        icon = Icons.Outlined.Settings,
                        contentDescription = stringResource(Res.string.vault_manage_security),
                        onClick = onChangeGateSecurity,
                    )
                },
            )
        },
    ) { padding ->
        // The grid scrolls under the bar; this Box is the page's only haze source.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current), end = padding.calculateEndPadding(LocalLayoutDirection.current)),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier
                    .widthIn(max = VaultContentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                    VaultListSummary(gateMode = gateMode)
                }
                item(key = "label", span = { GridItemSpan(maxLineSpan) }) {
                    VaultSectionLabel(title = stringResource(Res.string.vault_your_vaults))
                }
                itemsIndexed(vaults, key = { _, vault -> vault.uuid.toString() }) { index, vault ->
                    val hasLabel = lockTypes.containsKey(vault.uuid)
                    VaultTile(
                        name = vault.name,
                        meta = if (hasLabel) vaultLockLabel(lockTypes[vault.uuid]) else null,
                        palette = index,
                        onClick = { onVaultSelected(vault) },
                    )
                }
                item(key = "create_new") {
                    NewVaultTile(onClick = onCreateVault)
                }
                item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

/** One quiet line under the top bar saying how the vault list is locked. */
@Composable
private fun VaultListSummary(gateMode: GateMode?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (gateMode == GateMode.NONE) Icons.Outlined.LockOpen else GalleryIcons.Encrypted,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(
                when (gateMode) {
                    GateMode.CUSTOM -> Res.string.vault_list_summary_custom
                    GateMode.NONE -> Res.string.vault_list_summary_none
                    else -> Res.string.vault_list_summary_device
                }
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Cover colours cycle tertiary, secondary and primary containers by vault position. */
@Composable
private fun tilePalette(index: Int): Pair<Color, Color> = when (index % 3) {
    0 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun VaultTile(name: String, meta: String?, palette: Int, onClick: () -> Unit) {
    val (container, content) = tilePalette(palette)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.tileClickable(interaction, onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(container)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(40.dp),
            )
        }
        TileTexts(name = name, meta = meta)
    }
}

@Composable
private fun NewVaultTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.tileClickable(interaction, onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .indication(interaction, ripple())
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        TileTexts(
            name = stringResource(Res.string.vault_new_vault_title),
            meta = stringResource(Res.string.vault_new_vault_meta),
        )
    }
}

/**
 * The whole tile takes the tap, but nothing is clipped: the press ripple draws on the rounded
 * cover only, so the labels under it are never cut by the corner.
 */
private fun Modifier.tileClickable(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    fillMaxWidth().clickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onClick = onClick,
    )

@Composable
private fun TileTexts(name: String, meta: String?) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
