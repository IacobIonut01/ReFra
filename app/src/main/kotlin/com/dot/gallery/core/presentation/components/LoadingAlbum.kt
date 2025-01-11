/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.ui.theme.Dimens
import com.valentinilk.shimmer.shimmer

@Composable
fun LoadingAlbum(
    modifier: Modifier = Modifier,
    shouldShimmer: Boolean = true,
    bottomContent: @Composable (() -> Unit)? = null,
) {
    val gridSize by rememberAlbumGridSize()
    val grid = remember(gridSize) { albumCellsList.size - gridSize }
    val shape = remember { RoundedCornerShape(16.dp) }
    val canShimmer = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp)
            .then(if (shouldShimmer && canShimmer) Modifier.shimmer() else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(grid) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .size(Dimens.Album())
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = shape
                            )
                    )
                }
            }
        }
        if (shouldShimmer) {
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(grid) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .size(Dimens.Album())
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = shape
                                )
                        )
                    }
                }
            }
        }

        if (bottomContent != null) {
            bottomContent()
        }
    }
}