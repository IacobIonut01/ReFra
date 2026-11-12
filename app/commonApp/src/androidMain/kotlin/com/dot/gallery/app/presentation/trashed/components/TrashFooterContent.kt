/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.trashed.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_delete_all_permanently
import com.dot.gallery.generated.resources.trash
import com.dot.gallery.generated.resources.trash_deletion_warning
import com.dot.gallery.generated.resources.trash_restore_all
import org.jetbrains.compose.resources.stringResource

/**
 * The pure rendering of the trash header above the grid: the retention card and, while trash has
 * items, Restore all / Delete permanently. The launcher/ViewModel/dialog wiring stays in
 * [AutoDeleteFooter]; docs scenes supply in-memory callbacks.
 */
@Composable
fun TrashFooterContent(
    hasMedia: Boolean,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.trash),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(Res.string.trash_deletion_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        AnimatedVisibility(
            visible = hasMedia,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Buttons share the tallest intrinsic height so a two-line label
                    // cannot leave its sibling shorter.
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SheetButton(
                    text = stringResource(Res.string.trash_restore_all),
                    onClick = onRestoreAll,
                    style = SheetButtonStyle.Tonal,
                    icon = Icons.Outlined.Restore,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SheetButton(
                    text = stringResource(Res.string.action_delete_all_permanently),
                    onClick = onDeleteAll,
                    style = SheetButtonStyle.Error,
                    icon = Icons.Outlined.DeleteForever,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}
