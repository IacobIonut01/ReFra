package com.dot.gallery.feature_node.presentation.edit.components.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.EditorItems
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding

@Composable
fun EditorSelector(
    modifier: Modifier = Modifier,
    isSupportingPanel: Boolean,
    onItemClick: (EditorItems) -> Unit = {}
) {

    val padding = remember(isSupportingPanel) {
        if (isSupportingPanel) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp, vertical = 2.dp)
    }

    SupportiveLazyLayout(
        modifier = modifier
            .then(
                if (isSupportingPanel) Modifier
                    .safeSystemGesturesPadding(onlyRight = true)
                    .clipToBounds()
                    .clip(RoundedCornerShape(16.dp))
                else Modifier
            ),
        isSupportingPanel = isSupportingPanel,
        contentPadding = padding
    ) {
        itemsIndexed(
            items = EditorItems.entries,
            key = { _, it -> it.name }
        ) { index, editorItem ->
            EditorItem(
                imageVector = editorItem.icon,
                title = editorItem.translatedName,
                horizontal = isSupportingPanel,
                onItemClick = {
                    onItemClick(editorItem)
                }
            )
            if (isSupportingPanel && index < EditorItems.entries.size - 1) {
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}