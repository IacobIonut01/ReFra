/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.back
import com.dot.gallery.feature.vault.generated.resources.vault_draw_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_enter_password
import com.dot.gallery.feature.vault.generated.resources.vault_enter_pin
import com.dot.gallery.feature.vault.generated.resources.vault_unlock

/**
 * Full-page unlock for a custom lock: Back, the lock badge with [title] and [subtitle], and the
 * entry for [authType] — PIN dots over the keypad, the pattern pad (submitted on lift) or a
 * password field. Falls back to password mode when type is null (legacy).
 *
 * [errorMessage] marks the last attempt wrong; it hides as soon as the user starts the next one.
 */
@Composable
fun VaultPasswordUnlockDialog(
    authType: VaultAuthType? = null,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (secret: String) -> Unit,
    errorMessage: String? = null,
    title: String? = null,
) {
    val type = authType ?: VaultAuthType.PASSWORD
    var editedAfterSubmit by remember { mutableStateOf(false) }
    val shownError = errorMessage.takeUnless { editedAfterSubmit }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = GalleryChrome.ToolbarVerticalPadding),
        ) {
            val drawn = VaultNavigationButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                onClick = onDismiss,
            )
            if (!drawn) Box(Modifier.size(GalleryChrome.ToolbarButtonSize))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .vaultContentWidth()
                .padding(start = Spacing.Medium, end = Spacing.Medium, top = Spacing.Small, bottom = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            VaultHero(
                icon = Icons.Outlined.Lock,
                title = title ?: stringResource(Res.string.vault_unlock),
                body = subtitle ?: stringResource(
                    when (type) {
                        VaultAuthType.PIN -> Res.string.vault_enter_pin
                        VaultAuthType.PATTERN -> Res.string.vault_draw_pattern
                        VaultAuthType.PASSWORD -> Res.string.vault_enter_password
                    }
                ),
                modifier = Modifier.padding(top = Spacing.Medium),
            )
            VaultSecretInput(
                type = type,
                errorMessage = shownError,
                creating = false,
                submitLabel = stringResource(Res.string.vault_unlock),
                onEdit = { editedAfterSubmit = true },
                onSubmit = { secret ->
                    editedAfterSubmit = false
                    onSubmit(secret)
                },
            )
        }
    }
}

/**
 * Bottom-sheet wrapper around [VaultPasswordUnlockDialog]. Presents the type-aware unlock
 * UI inside a [ModalBottomSheet] so it does not overlap the underlying screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPasswordUnlockSheet(
    state: AppBottomSheetState,
    authType: VaultAuthType? = null,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (secret: String) -> Unit,
    errorMessage: String? = null
) {
    val scope = rememberCoroutineScope()
    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch { state.hide() }
                onDismiss()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            sheetMaxWidth = Dp.Unspecified,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            VaultPasswordUnlockDialog(
                authType = authType,
                subtitle = subtitle,
                onDismiss = {
                    scope.launch { state.hide() }
                    onDismiss()
                },
                onSubmit = onSubmit,
                errorMessage = errorMessage
            )
        }
    }
}
