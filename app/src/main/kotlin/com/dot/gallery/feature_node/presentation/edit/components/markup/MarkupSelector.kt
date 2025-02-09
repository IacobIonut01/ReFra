package com.dot.gallery.feature_node.presentation.edit.components.markup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.MarkupItems
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.SelectableItem
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLayout
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.edit.components.filters.horizontalSystemGesturesPadding
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState

@Composable
fun MarkupSelector(
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    isSupportingPanel: Boolean,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
) {

    val padding = remember(isSupportingPanel) {
        if (isSupportingPanel) PaddingValues(0.dp) else PaddingValues(16.dp)
    }

    SupportiveLayout(
        isSupportingPanel = isSupportingPanel
    ) {
        HueBar(
            modifier = if (!isSupportingPanel) Modifier.padding(WindowInsets.horizontalSystemGesturesPadding()) else Modifier.padding(end = 8.dp),
            isSupportingPanel = isSupportingPanel,
            currentColor = currentPathProperty.color,
            enabled = drawMode == DrawMode.Draw
        ) { hue ->
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(currentPathProperty.color.toArgb(), hsv)
            hsv[0] = hue
            val newColor = Color(
                android.graphics.Color.HSVToColor(
                    (currentPathProperty.color.alpha * 255).toInt(),
                    hsv
                )
            )
            val pathProperties = currentPathProperty.copy(color = newColor)
            setCurrentPathProperty(pathProperties)
        }

        SupportiveLazyLayout(
            modifier = Modifier
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
                items = MarkupItems.entries,
                key = { _, it -> it.name }
            ) { index, item ->
                val isSelected by rememberedDerivedState(item, drawMode, drawType) {
                    when (item) {
                        MarkupItems.Stylus -> drawMode == DrawMode.Draw && drawType == DrawType.Stylus
                        MarkupItems.Highlighter -> drawMode == DrawMode.Draw && drawType == DrawType.Highlighter
                        MarkupItems.Marker -> drawMode == DrawMode.Draw && drawType == DrawType.Marker
                        MarkupItems.Eraser -> drawMode == DrawMode.Erase
                    }
                }
                SelectableItem(
                    icon = item.icon,
                    title = item.translatedName,
                    selected = isSelected,
                    horizontal = isSupportingPanel,
                    onItemClick = {
                        when (item) {
                            MarkupItems.Stylus -> {
                                setDrawMode(DrawMode.Draw)
                                setDrawType(DrawType.Stylus)
                            }

                            MarkupItems.Highlighter -> {
                                setDrawMode(DrawMode.Draw)
                                setDrawType(DrawType.Highlighter)
                            }

                            MarkupItems.Marker -> {
                                setDrawMode(DrawMode.Draw)
                                setDrawType(DrawType.Marker)
                            }

                            MarkupItems.Eraser -> {
                                setDrawMode(DrawMode.Erase)
                            }
                        }
                    }
                )
                if (isSupportingPanel && index < MarkupItems.entries.size - 1) {
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}