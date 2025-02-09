package com.dot.gallery.feature_node.presentation.edit.components.adjustment

import androidx.annotation.Keep
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.edit.utils.isApplied

@Keep
@Composable
fun AdjustSection(
    modifier: Modifier = Modifier,
    appliedAdjustments: List<Adjustment>,
    onLongItemClick: (VariableFilterTypes) -> Unit,
    onItemClick: (VariableFilterTypes) -> Unit,
    isSupportingPanel: Boolean = false,
) {

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
        isSupportingPanel = isSupportingPanel,
        contentPadding = padding
    ) {
        itemsIndexed(
            items = VariableFilterTypes.entries,
            key = { _, it -> it.name }
        ) { index, item ->
            SelectableItem(
                icon = item.icon,
                title = item.name,
                horizontal = isSupportingPanel,
                selected = appliedAdjustments.isApplied(item),
                onLongItemClick = {
                    onLongItemClick(item)
                },
                onItemClick = {
                    onItemClick(item)
                }
            )
            if (isSupportingPanel && index < VariableFilterTypes.entries.size - 1) {
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

