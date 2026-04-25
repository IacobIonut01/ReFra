package com.dot.gallery.feature_node.presentation.edit.components.markup

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.MarkupItems
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.SelectableItem
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout

@Composable
fun MarkupToolSelector(
    modifier: Modifier = Modifier,
    isSupportingPanel: Boolean,
    onToolClick: (MarkupItems) -> Unit = {}
) {
    val tools = remember {
        listOf(MarkupItems.Stylus, MarkupItems.Highlighter, MarkupItems.Text)
    }

    val padding = remember(isSupportingPanel) {
        if (isSupportingPanel) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp)
    }

    SupportiveLazyLayout(
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth()
            .then(
                if (isSupportingPanel) Modifier
                    .clipToBounds()
                    .clip(RoundedCornerShape(16.dp))
                else Modifier
            ),
        contentPadding = padding,
        isSupportingPanel = isSupportingPanel
    ) {
        items(
            items = tools,
            key = { it.name }
        ) { item ->
            SelectableItem(
                icon = item.icon,
                title = item.translatedName,
                selected = false,
                horizontal = isSupportingPanel,
                onItemClick = { onToolClick(item) }
            )
        }
    }
}
