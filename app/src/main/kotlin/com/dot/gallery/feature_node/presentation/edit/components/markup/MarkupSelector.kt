package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Color.HSVToColor
import android.graphics.Color.colorToHSV
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.MarkupItems
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.SelectableItem
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLayout
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.horizontalFadingEdge

private val presetColors = listOf(
    Color(0xFF1A1A1A),
    Color.Red,
    Color(0xFFFF6D00),
    Color.Yellow,
    Color(0xFF00C853),
    Color(0xFF00BFA5),
    Color(0xFF2962FF),
    Color(0xFF6200EA),
    Color.Magenta,
    Color(0xFFFF80AB),
    Color(0xFF8D6E63),
    Color(0xFF78909C),
    Color.White
)

@Composable
fun MarkupSelector(
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    isSupportingPanel: Boolean,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    onRequestTextInput: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
) {
    if (isSupportingPanel) {
        MarkupSelectorTablet(
            drawMode = drawMode,
            setDrawMode = setDrawMode,
            drawType = drawType,
            setDrawType = setDrawType,
            currentPathProperty = currentPathProperty,
            setCurrentPathProperty = setCurrentPathProperty,
            onRequestTextInput = onRequestTextInput
        )
    } else {
        MarkupSelectorPhone(
            drawMode = drawMode,
            setDrawMode = setDrawMode,
            drawType = drawType,
            setDrawType = setDrawType,
            currentPathProperty = currentPathProperty,
            setCurrentPathProperty = setCurrentPathProperty,
            onRequestTextInput = onRequestTextInput,
            textAnnotations = textAnnotations,
            onTextAnnotationsChange = onTextAnnotationsChange,
            selectedTextIndex = selectedTextIndex
        )
    }
}

@Composable
private fun MarkupSelectorPhone(
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    onRequestTextInput: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tool type text tabs (Pen / Highlighter / Text)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            val isPen = drawMode == DrawMode.Draw && drawType == DrawType.Stylus
            val isHighlighter = drawMode == DrawMode.Draw && drawType == DrawType.Highlighter
            val isText = drawMode == DrawMode.Text
            // Pen tab
            Text(
                text = stringResource(R.string.editor_pen),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isPen) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = if (isPen) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        setDrawMode(DrawMode.Draw)
                        setDrawType(DrawType.Stylus)
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            // Highlighter tab
            Text(
                text = stringResource(R.string.editor_highlighter),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighter) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = if (isHighlighter) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        setDrawMode(DrawMode.Draw)
                        setDrawType(DrawType.Highlighter)
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            // Text tab
            Text(
                text = stringResource(R.string.editor_text),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isText) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = if (isText) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        setDrawMode(DrawMode.Text)
                        if (textAnnotations.isEmpty()) {
                            onRequestTextInput()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        val isText = drawMode == DrawMode.Text
        val textColorsEnabled = !isText || selectedTextIndex in textAnnotations.indices

        // Current tool icon
        val toolIcon = when {
            drawMode == DrawMode.Draw && drawType == DrawType.Stylus -> MarkupItems.Stylus.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Highlighter -> MarkupItems.Highlighter.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Marker -> MarkupItems.Marker.icon
            isText -> Icons.Outlined.TextFields
            drawMode == DrawMode.Erase -> MarkupItems.Eraser.icon
            else -> Icons.Outlined.Edit
        }

        // Determine the effective selected color
        val effectiveColor = if (isText && selectedTextIndex in textAnnotations.indices) {
            textAnnotations[selectedTextIndex].color
        } else {
            currentPathProperty.color.copy(alpha = 1f)
        }

        // Color dots row in dark rounded container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool icon reflecting the current tool
            Icon(
                imageVector = toolIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (textColorsEnabled) 1f else 0.4f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isText && !textColorsEnabled) {
                // Hint text when no text is selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.editor_add_or_select_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Scrollable preset color dots with fading edges
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalFadingEdge(0.06f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    presetColors.forEach { color ->
                        val isSelected = effectiveColor == color ||
                                (color == Color(0xFF1A1A1A) && effectiveColor == Color.Black)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .padding(3.dp)
                                .background(color = color, shape = CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    if (isText && selectedTextIndex in textAnnotations.indices) {
                                        // Change selected text annotation's color
                                        val updated = textAnnotations.toMutableList()
                                        updated[selectedTextIndex] = updated[selectedTextIndex].copy(color = color)
                                        onTextAnnotationsChange(updated)
                                    } else {
                                        setCurrentPathProperty(currentPathProperty.copy(color = color))
                                    }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun MarkupSelectorTablet(
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    onRequestTextInput: () -> Unit = {},
) {
    val padding = remember { PaddingValues(0.dp) }

    SupportiveLayout(
        isSupportingPanel = true
    ) {
        HSVColorBars(
            modifier = Modifier.padding(end = 8.dp),
            enabled = drawMode == DrawMode.Draw,
            currentColor = currentPathProperty.color,
            isSupportingPanel = true,
            onHueChange = { hue ->
                val hsv = FloatArray(3)
                colorToHSV(currentPathProperty.color.toArgb(), hsv)
                hsv[0] = hue
                val newColor = Color(
                    HSVToColor((currentPathProperty.color.alpha * 255).toInt(), hsv)
                )
                setCurrentPathProperty(currentPathProperty.copy(color = newColor))
            },
            onVibrancyChange = { vibrancy ->
                val hsv = FloatArray(3)
                colorToHSV(currentPathProperty.color.toArgb(), hsv)
                hsv[2] = vibrancy
                val newColor = Color(
                    HSVToColor((currentPathProperty.color.alpha * 255).toInt(), hsv)
                )
                setCurrentPathProperty(currentPathProperty.copy(color = newColor))
            },
            onSaturationChange = { saturation ->
                val hsv = FloatArray(3)
                colorToHSV(currentPathProperty.color.toArgb(), hsv)
                hsv[1] = saturation
                val newColor = Color(
                    HSVToColor((currentPathProperty.color.alpha * 255).toInt(), hsv)
                )
                setCurrentPathProperty(currentPathProperty.copy(color = newColor))
            }
        )

        SupportiveLazyLayout(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            isSupportingPanel = true,
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
                        MarkupItems.Text -> drawMode == DrawMode.Text
                        MarkupItems.Eraser -> drawMode == DrawMode.Erase
                        MarkupItems.Pan -> drawMode == DrawMode.Touch
                    }
                }
                SelectableItem(
                    icon = item.icon,
                    title = item.translatedName,
                    selected = isSelected,
                    horizontal = true,
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
                            MarkupItems.Text -> {
                                setDrawMode(DrawMode.Text)
                                onRequestTextInput()
                            }
                            MarkupItems.Eraser -> {
                                setDrawMode(DrawMode.Erase)
                            }
                            MarkupItems.Pan -> {
                                setDrawMode(DrawMode.Touch)
                            }
                        }
                    }
                )
                if (index < MarkupItems.entries.size - 1) {
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}