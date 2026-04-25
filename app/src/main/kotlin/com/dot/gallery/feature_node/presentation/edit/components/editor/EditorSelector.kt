package com.dot.gallery.feature_node.presentation.edit.components.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.EditorItems
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding

@Composable
fun EditorSelector(
    modifier: Modifier = Modifier,
    selectedItem: EditorItems? = null,
    isSupportingPanel: Boolean,
    onItemClick: (EditorItems) -> Unit = {}
) {
    if (isSupportingPanel) {
        val padding = remember { PaddingValues(0.dp) }
        SupportiveLazyLayout(
            modifier = modifier
                .safeSystemGesturesPadding(onlyRight = true)
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            isSupportingPanel = true,
            contentPadding = padding
        ) {
            itemsIndexed(
                items = EditorItems.entries,
                key = { _, it -> it.name }
            ) { index, editorItem ->
                EditorItem(
                    imageVector = editorItem.icon,
                    title = editorItem.translatedName,
                    horizontal = true,
                    onItemClick = { onItemClick(editorItem) }
                )
                if (index < EditorItems.entries.size - 1) {
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        }
    } else {
        val scrollState = rememberScrollState()
        // Auto-scroll to selected item when it changes
        LaunchedEffect(selectedItem) {
            if (selectedItem != null) {
                val index = EditorItems.entries.indexOf(selectedItem)
                if (index >= 0) {
                    // Approximate scroll position: each item ~80dp + 4dp spacing
                    val targetPx = (index * 84 * 2.5f).toInt() // rough px approximation
                    scrollState.animateScrollTo(targetPx.coerceAtMost(scrollState.maxValue))
                }
            }
        }
        Row(
            modifier = modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EditorItems.entries.forEach { editorItem ->
                val isSelected = editorItem == selectedItem
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                    else Color.Transparent,
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabText"
                )
                Text(
                    text = editorItem.translatedName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onItemClick(editorItem) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}