package com.dot.gallery.feature_node.presentation.edit.components.filters

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.presentation.edit.adjustments.filters.ImageFilterTypes
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.util.rememberBitmapPainter
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding

@Composable
fun WindowInsets.Companion.horizontalSystemGesturesPadding(): PaddingValues {
    val padding = WindowInsets.systemGestures.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return remember(padding, layoutDirection) {
        PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection)
        )
    }
}

@Composable
fun FiltersSelector(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    isSupportingPanel: Boolean,
    appliedAdjustments: List<Adjustment> = emptyList(),
    onClick: (ImageFilter) -> Unit = {},
) {
    val painter by rememberBitmapPainter(bitmap)
    val noFilterApplied by remember(appliedAdjustments) {
        derivedStateOf {
            appliedAdjustments.none { it is ImageFilter }
        }
    }

    if (isSupportingPanel) {
        LazyVerticalGrid(
            modifier = modifier
                .fillMaxWidth()
                .safeSystemGesturesPadding(onlyRight = true)
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(
                items = ImageFilterTypes.entries,
                key = { it.name }
            ) { filter ->
                val isSelected by remember(appliedAdjustments, filter) {
                    derivedStateOf {
                        appliedAdjustments.any { it.name == filter.name } || (noFilterApplied && filter.name == "None")
                    }
                }
                val strokeSize by animateDpAsState(
                    if (isSelected) 4.dp else 0.dp,
                    label = "strokeSize"
                )
                val strokeAlpha by animateFloatAsState(
                    if (isSelected) 1f else 0f,
                    label = "strokeAlpha"
                )
                val imageFilter = remember(filter) {
                    filter.createImageFilter()
                }
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = strokeSize,
                                color = MaterialTheme.colorScheme.tertiary.copy(strokeAlpha),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (!isSelected) onClick(imageFilter)
                            },
                        painter = painter,
                        colorFilter = remember(imageFilter) {
                            imageFilter.colorMatrix()?.let { ColorFilter.colorMatrix(it) }
                        },
                        contentDescription = imageFilter.name,
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        SupportiveLazyLayout(
            modifier = modifier
                .fillMaxWidth()
                .safeSystemGesturesPadding()
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            contentPadding = PaddingValues(0.dp),
            isSupportingPanel = false
        ) {
            items(
                items = ImageFilterTypes.entries,
                key = { it.name }
            ) { filter ->
                val isSelected by remember(appliedAdjustments, filter) {
                    derivedStateOf {
                        appliedAdjustments.any { it.name == filter.name } || (noFilterApplied && filter.name == "None")
                    }
                }
                val strokeSize by animateDpAsState(
                    if (isSelected) 4.dp else 0.dp,
                    label = "strokeSize"
                )
                val strokeAlpha by animateFloatAsState(
                    if (isSelected) 1f else 0f,
                    label = "strokeAlpha"
                )
                val imageFilter = remember(filter) {
                    filter.createImageFilter()
                }
                Column(
                    modifier = Modifier.padding(bottom = 16.dp).padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = strokeSize,
                                color = MaterialTheme.colorScheme.tertiary.copy(strokeAlpha),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (!isSelected) onClick(imageFilter)
                            },
                        painter = painter,
                        colorFilter = remember(imageFilter) {
                            imageFilter.colorMatrix()?.let { ColorFilter.colorMatrix(it) }
                        },
                        contentDescription = imageFilter.name,
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }


}