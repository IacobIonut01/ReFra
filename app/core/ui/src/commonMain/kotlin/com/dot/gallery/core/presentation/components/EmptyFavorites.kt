/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.empty_favorites_title


@Composable
fun EmptyFavorites(
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.empty_favorites_title),
) {
    ExpressiveEmptyState(
        icon = Icons.Outlined.FavoriteBorder,
        title = title,
        modifier = modifier.padding(top = 32.dp),
    )
}
