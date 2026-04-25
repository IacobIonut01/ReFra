package com.dot.gallery.feature_node.presentation.edit.components.lighting

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
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.LightingTool
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.SelectableItem
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.edit.utils.isApplied
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes

@Composable
fun LightingSection(
    modifier: Modifier = Modifier,
    appliedAdjustments: List<Adjustment> = emptyList(),
    isSupportingPanel: Boolean,
    onItemClick: (LightingTool) -> Unit = {},
    onLongItemClick: (LightingTool) -> Unit = {}
) {
    val tools = remember { LightingTool.entries.toList() }

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
        ) { tool ->
            val filterType = tool.toVariableFilterType()
            SelectableItem(
                icon = tool.icon,
                title = tool.name,
                selected = appliedAdjustments.isApplied(filterType),
                horizontal = isSupportingPanel,
                onItemClick = { onItemClick(tool) },
                onLongItemClick = { onLongItemClick(tool) }
            )
        }
    }
}

fun LightingTool.toVariableFilterType(): VariableFilterTypes = when (this) {
    LightingTool.Brightness -> VariableFilterTypes.Brightness
    LightingTool.Tone -> VariableFilterTypes.Tone
    LightingTool.Contrast -> VariableFilterTypes.Contrast
    LightingTool.BlackPoint -> VariableFilterTypes.BlackPoint
    LightingTool.WhitePoint -> VariableFilterTypes.WhitePoint
    LightingTool.Highlights -> VariableFilterTypes.Highlights
    LightingTool.Shadows -> VariableFilterTypes.Shadows
    LightingTool.Vignette -> VariableFilterTypes.Vignette
}
