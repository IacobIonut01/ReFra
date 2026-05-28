package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLayout
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding

@Composable
fun MarkupColorSelector(
    modifier: Modifier = Modifier,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    isSupportingPanel: Boolean
) {
    SupportiveLayout(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSupportingPanel) Modifier.padding(top = 16.dp)
                else Modifier.padding(bottom = 16.dp)
            )
            .safeSystemGesturesPadding(onlyRight = isSupportingPanel),
        isSupportingPanel = isSupportingPanel
    ) {
        MarkupPathPreview(
            pathProperties = currentPathProperty,
            isSupportingPanel = isSupportingPanel
        )
        HueBar(
            modifier = Modifier,
            isSupportingPanel = isSupportingPanel,
            currentColor = currentPathProperty.color,
        ) { hue ->
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(currentPathProperty.color.toArgb(), hsv)
            hsv[0] = hue
            val newColor = Color(
                AndroidColor.HSVToColor(
                    (currentPathProperty.color.alpha * 255).toInt(),
                    hsv
                )
            )
            val pathProperties = currentPathProperty.copy(color = newColor)
            setCurrentPathProperty(pathProperties)
        }
        SaturationBar(
            modifier = Modifier,
            isSupportingPanel = isSupportingPanel,
            currentColor = currentPathProperty.color
        ) { saturation ->
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(currentPathProperty.color.toArgb(), hsv)
            hsv[1] = saturation
            val newColor = Color(
                AndroidColor.HSVToColor(
                    (currentPathProperty.color.alpha * 255).toInt(),
                    hsv
                )
            )
            val pathProperties = currentPathProperty.copy(color = newColor)
            setCurrentPathProperty(pathProperties)
        }
        AlphaBar(
            modifier = Modifier,
            isSupportingPanel = isSupportingPanel,
            currentColor = currentPathProperty.color
        ) { alpha ->
            val newColor = currentPathProperty.color.copy(alpha = alpha)
            setCurrentPathProperty(currentPathProperty.copy(color = newColor))
        }
    }
}