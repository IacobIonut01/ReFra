/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.runtime.mutableStateListOf
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.dot.gallery.core.presentation.components.OptionPosition.ALONE
import com.dot.gallery.core.presentation.components.OptionPosition.BOTTOM
import com.dot.gallery.core.presentation.components.OptionPosition.MIDDLE
import com.dot.gallery.core.presentation.components.OptionPosition.TOP


private val GridItemShape = RoundedCornerShape(20.dp)

@Composable
fun OptionLayout(
    modifier: Modifier = Modifier,
    optionList: List<OptionItem>,
    style: OptionLayoutStyle = OptionLayoutStyle.Column
) {
    when (style) {
        OptionLayoutStyle.Grid -> OptionGridLayout(modifier, optionList)
        OptionLayoutStyle.Column -> OptionColumnLayout(modifier, optionList)
    }
}

@OptIn(ExperimentalGridApi::class)
@Composable
private fun OptionGridLayout(
    modifier: Modifier = Modifier,
    optionList: List<OptionItem>
) {
    Grid(
        config = {
            repeat(2) { column(GridTrackSize.MinMax(min = 0.dp, max = 1.fr)) }
            columnGap(8.dp)
            rowGap(8.dp)
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        val isOdd = optionList.size % 2 != 0
        optionList.forEachIndexed { index, item ->
            val isLastOdd = isOdd && index == optionList.lastIndex
            OptionGridItem(
                item = item,
                modifier = if (isLastOdd) Modifier.gridItem(columnSpan = 2) else Modifier
            )
        }
    }
}

@Composable
private fun OptionColumnLayout(
    modifier: Modifier = Modifier,
    optionList: List<OptionItem>
) {
    Column(
        modifier = modifier
            .clip(OptionShape.Alone)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = OptionShape.Alone
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        optionList.forEachIndexed { index, item ->
            val position: OptionPosition = remember(index, item) {
                when (index) {
                    0 -> {
                        if (optionList.size == 1) ALONE
                        else TOP
                    }

                    optionList.lastIndex -> BOTTOM
                    else -> MIDDLE
                }
            }
            val summary: (@Composable () -> Unit)? = if (item.summary.isNullOrBlank()) null else {
                {
                    Text(text = item.summary)
                }
            }
            OptionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = item.icon,
                textContainer = {
                    Text(text = item.text)
                },
                summaryContainer = summary,
                enabled = item.enabled,
                attention = item.attention,
                containerColor = item.containerColor
                    ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = item.contentColor
                    ?: MaterialTheme.colorScheme.onSurface,
                position = position,
                onClick = {
                    item.onClick(item.summary.toString())
                }
            )
        }
    }
}

@Composable
private fun OptionGridItem(
    item: OptionItem,
    modifier: Modifier = Modifier
) {
    val containerColor = item.containerColor
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = item.contentColor
        ?: MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .defaultMinSize(minHeight = 80.dp)
            .background(containerColor, GridItemShape)
            .clip(GridItemShape)
            .clickable(enabled = item.enabled) { item.onClick(item.summary.toString()) }
            .alpha(if (item.enabled) 1f else 0.4f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.summary.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.summary,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionLayoutAlonePreview() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            val list = remember {
                mutableStateListOf(
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Alone Option",
                        onClick = {}
                    )
                )
            }
            OptionLayout(optionList = list)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionLayoutTwoItemsPreview() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            val list = remember {
                mutableStateListOf(
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "First Option",
                        onClick = {}
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Second Option",
                        onClick = {}
                    )
                )
            }
            OptionLayout(optionList = list)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionLayoutGridPreview() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            val customContainerColor = MaterialTheme.colorScheme.primaryContainer
            val customContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            val list = remember(customContainerColor, customContentColor) {
                mutableStateListOf(
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Delete",
                        onClick = {}
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Pin",
                        onClick = {}
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Change cover",
                        onClick = {}
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Lock album",
                        onClick = {}
                    ),
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = "Odd Item",
                        containerColor = customContainerColor,
                        contentColor = customContentColor,
                        onClick = {}
                    )
                )
            }
            OptionLayout(optionList = list)
        }
    }
}
