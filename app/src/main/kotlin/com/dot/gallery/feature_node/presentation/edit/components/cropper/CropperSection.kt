package com.dot.gallery.feature_node.presentation.edit.components.cropper

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
import com.dot.gallery.feature_node.domain.model.editor.CropperAction
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.SelectableItem
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout

@Composable
fun CropperSection(
    modifier: Modifier = Modifier,
    isSupportingPanel: Boolean,
    onActionClick: (CropperAction) -> Unit = {}
) {
    val actions = remember {
        CropperAction.entries.toList()
    }

    val padding = remember(isSupportingPanel) {
        if (isSupportingPanel) PaddingValues(0.dp) else PaddingValues(16.dp)
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
            items = actions,
            key = { it.name }
        ) { item ->
            SelectableItem(
                icon = item.icon,
                title = item.name,
                selected = item == CropperAction.APPLY_CROP,
                onItemClick = { onActionClick(item) },
                horizontal = isSupportingPanel
            )
        }
    }
}