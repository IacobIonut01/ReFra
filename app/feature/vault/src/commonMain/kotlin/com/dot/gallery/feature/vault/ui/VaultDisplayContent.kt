/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.components.DeleteVaultSheet
import com.dot.gallery.feature.vault.ui.components.RemovePasswordSheet
import com.dot.gallery.feature.vault.ui.components.RestoreVaultSheet
import com.dot.gallery.feature.vault.ui.components.SelectVaultSheet
import com.dot.gallery.feature.vault.ui.components.VaultButton
import com.dot.gallery.feature.vault.ui.components.VaultButtonStyle
import com.dot.gallery.feature.vault.ui.components.VaultHero
import com.dot.gallery.feature.vault.ui.components.VaultPasswordSetupSheet
import com.dot.gallery.feature.vault.ui.components.VaultRow
import com.dot.gallery.feature.vault.ui.components.VaultRowTrailing
import com.dot.gallery.feature.vault.ui.components.vaultContentWidth
import com.dot.gallery.feature.vault.ui.components.vaultRowPosition
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.action_cancel
import com.dot.gallery.feature.vault.generated.resources.add_media_to_vault_cd
import com.dot.gallery.feature.vault.generated.resources.delete_vault
import com.dot.gallery.feature.vault.generated.resources.more_options
import com.dot.gallery.feature.vault.generated.resources.switch_vault_cd
import com.dot.gallery.feature.vault.generated.resources.unknown_vault
import com.dot.gallery.feature.vault.generated.resources.vault_add_photos
import com.dot.gallery.feature.vault.generated.resources.vault_change_custom_password
import com.dot.gallery.feature.vault.generated.resources.vault_change_security_summary
import com.dot.gallery.feature.vault.generated.resources.vault_custom_password
import com.dot.gallery.feature.vault.generated.resources.vault_empty_body
import com.dot.gallery.feature.vault.generated.resources.vault_empty_title
import com.dot.gallery.feature.vault.generated.resources.vault_encrypting_percent
import com.dot.gallery.feature.vault.generated.resources.vault_encrypting_progress
import com.dot.gallery.feature.vault.generated.resources.encrypting_media
import com.dot.gallery.feature.vault.generated.resources.vault_method_device
import com.dot.gallery.feature.vault.generated.resources.vault_password_removed
import com.dot.gallery.feature.vault.generated.resources.vault_password_set
import com.dot.gallery.feature.vault.generated.resources.vault_remove_custom_password
import com.dot.gallery.feature.vault.generated.resources.vault_remove_custom_security_summary
import com.dot.gallery.feature.vault.generated.resources.vault_restore_summary
import com.dot.gallery.feature.vault.generated.resources.vault_restore_title
import com.dot.gallery.feature.vault.generated.resources.vault_unlocks_with
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode

/**
 * Vault media display chrome: the Library-style top bar (vault name with the switcher when there
 * is more than one vault, item count, Add and More), an inline encryption progress card, and the
 * vault menu and management sheets.
 *
 * The media grid itself is supplied by the host through [gridContent]
 * (pinch-zoom + [com.dot.gallery.core.presentation.components.MediaGridView]
 * wiring, haze source), and the selection toolbar through [selectionContent]
 * — both stay app-side while the shared grid chrome settles. Use [VaultEmptyState] as the
 * grid's empty content.
 *
 * Platform operations are callbacks: media picking ([onAddMediaClick]), credential
 * persistence ([onSecretSet]/[onRemovePassword]), and vault mutations.
 */
@Composable
fun VaultDisplayContent(
    vaultState: VaultState,
    currentVault: Vault?,
    isSelectionActive: Boolean,
    workerIsRunning: Boolean,
    workerProgress: Float,
    pendingEncryptCount: Int,
    hasCustomPassword: Boolean,
    canScroll: Boolean,
    modifier: Modifier = Modifier,
    /** The grid has scrolled under the top bar, which then turns into the blur bar. */
    scrolled: Boolean = false,
    mediaCount: Int? = null,
    userMessage: Flow<String>? = null,
    onNavigateUp: () -> Unit,
    onVaultDeleted: () -> Unit,
    onResyncVault: (Vault) -> Unit,
    onAuthenticateVault: (Vault) -> Unit,
    onDeleteVault: (Vault) -> Unit,
    onRestoreVault: (Vault) -> Unit,
    onSecretSet: suspend (type: VaultAuthType, secret: String) -> Unit,
    onRemovePassword: suspend () -> Unit,
    onAddMediaClick: () -> Unit,
    onEncryptSheetCancel: (dropCount: Int) -> Unit,
    gridContent: @Composable (PaddingValues) -> Unit,
    selectionContent: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Keep current vault in sync; if deleted or null, navigate back
    LaunchedEffect(vaultState, currentVault) {
        val current = currentVault
        val vaults = vaultState.vaults
        if (current != null && vaults.any { it.uuid == current.uuid }) {
            onResyncVault(current)
        } else if (current == null && vaults.isNotEmpty()) {
            // Vault was deleted but others remain — go to vault selector for auth
            onVaultDeleted()
        } else if (current == null && vaults.isEmpty()) {
            // Last vault deleted — exit to library
            onNavigateUp()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(userMessage) {
        userMessage?.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val passwordSetupSheetState = rememberAppBottomSheetState()
    val passwordSetMsg = stringResource(Res.string.vault_password_set)
    val passwordRemovedMsg = stringResource(Res.string.vault_password_removed)
    val removePasswordSheetState = rememberAppBottomSheetState()
    RemovePasswordSheet(
        state = removePasswordSheetState,
        onConfirm = {
            if (currentVault == null) return@RemovePasswordSheet
            scope.launch {
                onRemovePassword()
                snackbarHostState.showSnackbar(passwordRemovedMsg)
            }
        }
    )
    VaultPasswordSetupSheet(
        state = passwordSetupSheetState,
        onSecretSet = { type, secret ->
            if (currentVault == null) return@VaultPasswordSetupSheet
            scope.launch {
                onSecretSet(type, secret)
                snackbarHostState.showSnackbar(passwordSetMsg)
            }
        }
    )

    val decryptVaultSheetState = rememberAppBottomSheetState()
    val deleteVaultSheetState = rememberAppBottomSheetState()
    val actionsSheetState = rememberAppBottomSheetState()
    val switcherSheetState = rememberAppBottomSheetState()

    LaunchedEffect(vaultState) {
        if (vaultState.isLoading) return@LaunchedEffect
        if (vaultState.vaults.isNotEmpty()) return@LaunchedEffect
        onNavigateUp()
    }

    val canSwitch = vaultState.vaults.size > 1
    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Opaque at rest; once the grid scrolls the bar blurs it and the encrypting card
                // floats over it.
                Column(modifier = if (scrolled) Modifier else Modifier.background(MaterialTheme.colorScheme.surface)) {
                    CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                    GalleryPageTopBar(
                        title = currentVault?.name ?: stringResource(Res.string.unknown_vault),
                        count = mediaCount?.takeIf { it > 0 }?.toString(),
                        onBack = onNavigateUp,
                        hazeState = LocalHazeState.current,
                        scrolled = scrolled,
                        onTitleClick = if (canSwitch) {
                            { scope.launch { switcherSheetState.show() } }
                        } else null,
                        titleTrailing = if (canSwitch) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = stringResource(Res.string.switch_vault_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else null,
                        actions = {
                            if (!isSelectionActive) {
                                GalleryPageTopBarAction(
                                    icon = Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = stringResource(Res.string.add_media_to_vault_cd),
                                    onClick = onAddMediaClick,
                                )
                            }
                            GalleryPageTopBarAction(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(Res.string.more_options),
                                onClick = { scope.launch { actionsSheetState.show() } },
                            )
                        },
                    )
                    }
                    AnimatedVisibility(
                        visible = workerIsRunning,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        EncryptingCard(
                            progress = workerProgress,
                            total = pendingEncryptCount,
                            onCancel = {
                                val indexesToDrop = (workerProgress * pendingEncryptCount / 100).roundToInt()
                                onEncryptSheetCancel(indexesToDrop)
                            },
                        )
                    }
                }
            }
        ) { scaffoldPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                gridContent(scaffoldPadding)
            }
        }

        selectionContent()

        SelectVaultSheet(
            state = switcherSheetState,
            vaultState = vaultState,
            excludeVault = currentVault,
            requireGateAuth = false
        ) { vault ->
            onAuthenticateVault(vault)
        }
        DeleteVaultSheet(
            state = deleteVaultSheetState,
            vaultName = currentVault?.name
        ) {
            val vault = currentVault ?: vaultState.vaults.firstOrNull()
            vault?.let { onDeleteVault(it) }
        }
        RestoreVaultSheet(
            state = decryptVaultSheetState
        ) {
            val vault = currentVault ?: vaultState.vaults.firstOrNull()
            vault?.let { onRestoreVault(it) }
        }
        VaultActionsSheet(
            state = actionsSheetState,
            vaultName = currentVault?.name ?: stringResource(Res.string.unknown_vault),
            hasCustomPassword = hasCustomPassword,
            onDecryptVault = { scope.launch { decryptVaultSheetState.show() } },
            onSetPassword = { scope.launch { passwordSetupSheetState.show() } },
            onRemovePassword = { scope.launch { removePasswordSheetState.show() } },
            onDeleteVault = { scope.launch { deleteVaultSheetState.show() } }
        )
    }
}

/** Non-blocking encryption progress shown under the top bar while the encrypt worker runs. */
@Composable
private fun EncryptingCard(progress: Float, total: Int, onCancel: () -> Unit) {
    val fraction = (progress / 100f).fastCoerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .vaultContentWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = GalleryIcons.Encrypted,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (total > 0) {
                        val done = (fraction * total).roundToInt().coerceIn(0, total)
                        stringResource(Res.string.vault_encrypting_progress, done, total)
                    } else stringResource(Res.string.encrypting_media),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.vault_encrypting_percent, (fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) {
                Text(text = stringResource(Res.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/**
 * Empty grid content of an unlocked vault: badge, title naming the vault and an Add photos
 * button wired to the media picker.
 */
@Composable
fun VaultEmptyState(
    vaultName: String,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VaultHero(
            icon = Icons.Outlined.AddPhotoAlternate,
            title = stringResource(Res.string.vault_empty_title, vaultName),
            body = stringResource(Res.string.vault_empty_body),
            badgeIconSize = 40.dp,
        )
        VaultButton(text = stringResource(Res.string.vault_add_photos), onClick = onAddMedia)
    }
}

@Composable
private fun VaultActionsSheet(
    state: AppBottomSheetState,
    vaultName: String,
    hasCustomPassword: Boolean,
    onDecryptVault: () -> Unit,
    onSetPassword: () -> Unit,
    onRemovePassword: () -> Unit,
    onDeleteVault: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun run(action: () -> Unit) {
        scope.launch { state.hide() }
        action()
    }
    val lockLabel = if (hasCustomPassword) stringResource(Res.string.vault_custom_password)
    else stringResource(Res.string.vault_method_device)
    ModalSheet(
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = vaultName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.vault_unlocks_with, lockLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                val rows = buildList<@Composable (Int, Int) -> Unit> {
                    add { index, size ->
                        VaultRow(
                            icon = Icons.Outlined.Key,
                            title = stringResource(
                                if (hasCustomPassword) Res.string.vault_change_custom_password
                                else Res.string.vault_custom_password
                            ),
                            summary = if (hasCustomPassword) null else stringResource(Res.string.vault_change_security_summary),
                            position = vaultRowPosition(index, size),
                            trailing = VaultRowTrailing.Chevron,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { run(onSetPassword) },
                        )
                    }
                    if (hasCustomPassword) add { index, size ->
                        VaultRow(
                            icon = Icons.Outlined.LockOpen,
                            title = stringResource(Res.string.vault_remove_custom_password),
                            summary = stringResource(Res.string.vault_remove_custom_security_summary),
                            position = vaultRowPosition(index, size),
                            trailing = VaultRowTrailing.Chevron,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { run(onRemovePassword) },
                        )
                    }
                    add { index, size ->
                        VaultRow(
                            icon = Icons.Outlined.Restore,
                            title = stringResource(Res.string.vault_restore_title),
                            summary = stringResource(Res.string.vault_restore_summary),
                            position = vaultRowPosition(index, size),
                            trailing = VaultRowTrailing.Chevron,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { run(onDecryptVault) },
                        )
                    }
                }
                rows.forEachIndexed { index, row -> row(index, rows.size) }
            }
            VaultButton(
                text = stringResource(Res.string.delete_vault),
                style = VaultButtonStyle.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                onClick = { run(onDeleteVault) },
            )
        },
    )
}
