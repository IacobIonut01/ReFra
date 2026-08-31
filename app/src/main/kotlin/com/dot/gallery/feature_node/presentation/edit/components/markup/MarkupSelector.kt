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
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
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

internal fun markupPresetColor(color: Color, currentColor: Color): Color =
    color.copy(alpha = currentColor.alpha)

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
    onDetectFaces: () -> Unit = {},
    faceDetectAvailable: Boolean = false,
    isDetectingFaces: Boolean = false,
) {
    if (isSupportingPanel) {
        MarkupSelectorTablet(
            drawMode = drawMode,
            setDrawMode = setDrawMode,
            drawType = drawType,
            setDrawType = setDrawType,
            currentPathProperty = currentPathProperty,
            setCurrentPathProperty = setCurrentPathProperty,
            onRequestTextInput = onRequestTextInput,
            textAnnotations = textAnnotations,
            onTextAnnotationsChange = onTextAnnotationsChange,
            selectedTextIndex = selectedTextIndex,
            onDetectFaces = onDetectFaces,
            faceDetectAvailable = faceDetectAvailable,
            isDetectingFaces = isDetectingFaces
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
            selectedTextIndex = selectedTextIndex,
            onDetectFaces = onDetectFaces,
            faceDetectAvailable = faceDetectAvailable,
            isDetectingFaces = isDetectingFaces
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
    onDetectFaces: () -> Unit = {},
    faceDetectAvailable: Boolean = false,
    isDetectingFaces: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tool type text tabs (Pen / Highlighter / Blur / Mosaic / Text)
        val isPen = drawMode == DrawMode.Draw && drawType == DrawType.Stylus
        val isHighlighter = drawMode == DrawMode.Draw && drawType == DrawType.Highlighter
        val isBlur = drawMode == DrawMode.Draw && drawType == DrawType.Blur
        val isMosaic = drawMode == DrawMode.Draw && drawType == DrawType.Mosaic
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdge(0.06f)
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            MarkupToolTab(
                label = stringResource(R.string.editor_pen),
                selected = isPen,
                onClick = {
                    setDrawMode(DrawMode.Draw)
                    setDrawType(DrawType.Stylus)
                }
            )
            MarkupToolTab(
                label = stringResource(R.string.editor_highlighter),
                selected = isHighlighter,
                onClick = {
                    setDrawMode(DrawMode.Draw)
                    setDrawType(DrawType.Highlighter)
                }
            )
            MarkupToolTab(
                label = stringResource(R.string.type_blur),
                selected = isBlur,
                onClick = {
                    setDrawMode(DrawMode.Draw)
                    setDrawType(DrawType.Blur)
                }
            )
            MarkupToolTab(
                label = stringResource(R.string.type_mosaic),
                selected = isMosaic,
                onClick = {
                    setDrawMode(DrawMode.Draw)
                    setDrawType(DrawType.Mosaic)
                }
            )
            MarkupToolTab(
                label = stringResource(R.string.editor_text),
                selected = drawMode == DrawMode.Text,
                onClick = {
                    setDrawMode(DrawMode.Text)
                    if (textAnnotations.isEmpty()) {
                        onRequestTextInput()
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        val isText = drawMode == DrawMode.Text
        val textColorsEnabled = !isText || selectedTextIndex in textAnnotations.indices

        // Current tool icon
        val toolIcon = when {
            drawMode == DrawMode.Draw && drawType == DrawType.Stylus -> MarkupItems.Stylus.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Highlighter -> MarkupItems.Highlighter.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Marker -> MarkupItems.Marker.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Blur -> MarkupItems.Blur.icon
            drawMode == DrawMode.Draw && drawType == DrawType.Mosaic -> MarkupItems.Mosaic.icon
            isText -> Icons.Outlined.TextFields
            drawMode == DrawMode.Erase -> MarkupItems.Eraser.icon
            else -> Icons.Outlined.Edit
        }

        val isEffectBrush = drawMode == DrawMode.Draw &&
                (drawType == DrawType.Blur || drawType == DrawType.Mosaic)

        // Determine the effective selected color
        val effectiveColor = if (isText && selectedTextIndex in textAnnotations.indices) {
            textAnnotations[selectedTextIndex].color
        } else {
            currentPathProperty.color.copy(alpha = 1f)
        }

        if (isEffectBrush) {
            BrushEffectControls(
                toolIcon = toolIcon,
                currentPathProperty = currentPathProperty,
                setCurrentPathProperty = setCurrentPathProperty,
                onDetectFaces = onDetectFaces,
                faceDetectAvailable = faceDetectAvailable && drawType == DrawType.Blur,
                isDetectingFaces = isDetectingFaces
            )
            return@Column
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
                                        setCurrentPathProperty(
                                            currentPathProperty.copy(
                                                color = markupPresetColor(color, currentPathProperty.color)
                                            )
                                        )
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

/** A single selectable text tab in the phone markup tool row. */
@Composable
private fun MarkupToolTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        ),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
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
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
    onDetectFaces: () -> Unit = {},
    faceDetectAvailable: Boolean = false,
    isDetectingFaces: Boolean = false,
) {
    val padding = remember { PaddingValues(0.dp) }
    val isEffectBrush = drawMode == DrawMode.Draw &&
            (drawType == DrawType.Blur || drawType == DrawType.Mosaic)
    val selectedText = textAnnotations.getOrNull(selectedTextIndex)
    val effectiveColor = selectedText?.color ?: currentPathProperty.color
    val setEffectiveColor: (Color) -> Unit = { color ->
        if (selectedText != null) {
            val updated = textAnnotations.toMutableList()
            updated[selectedTextIndex] = selectedText.copy(color = color)
            onTextAnnotationsChange(updated)
        } else {
            setCurrentPathProperty(currentPathProperty.copy(color = color))
        }
    }

    SupportiveLayout(
        isSupportingPanel = true
    ) {
        if (isEffectBrush) {
            BrushEffectControls(
                modifier = Modifier.width(260.dp),
                toolIcon = if (drawType == DrawType.Blur) MarkupItems.Blur.icon else MarkupItems.Mosaic.icon,
                currentPathProperty = currentPathProperty,
                setCurrentPathProperty = setCurrentPathProperty,
                onDetectFaces = onDetectFaces,
                faceDetectAvailable = faceDetectAvailable && drawType == DrawType.Blur,
                isDetectingFaces = isDetectingFaces
            )
        } else {
            HSVColorBars(
                modifier = Modifier.padding(end = 8.dp),
                enabled = drawMode == DrawMode.Draw || selectedText != null,
                currentColor = effectiveColor,
                isSupportingPanel = true,
                onHueChange = { hue ->
                    val hsv = FloatArray(3)
                    colorToHSV(effectiveColor.toArgb(), hsv)
                    hsv[0] = hue
                    setEffectiveColor(Color(HSVToColor((effectiveColor.alpha * 255).toInt(), hsv)))
                },
                onVibrancyChange = { vibrancy ->
                    val hsv = FloatArray(3)
                    colorToHSV(effectiveColor.toArgb(), hsv)
                    hsv[2] = vibrancy
                    setEffectiveColor(Color(HSVToColor((effectiveColor.alpha * 255).toInt(), hsv)))
                },
                onSaturationChange = { saturation ->
                    val hsv = FloatArray(3)
                    colorToHSV(effectiveColor.toArgb(), hsv)
                    hsv[1] = saturation
                    setEffectiveColor(Color(HSVToColor((effectiveColor.alpha * 255).toInt(), hsv)))
                }
            )
        }

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
                        MarkupItems.Blur -> drawMode == DrawMode.Draw && drawType == DrawType.Blur
                        MarkupItems.Mosaic -> drawMode == DrawMode.Draw && drawType == DrawType.Mosaic
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
                            MarkupItems.Blur -> {
                                setDrawMode(DrawMode.Draw)
                                setDrawType(DrawType.Blur)
                            }
                            MarkupItems.Mosaic -> {
                                setDrawMode(DrawMode.Draw)
                                setDrawType(DrawType.Mosaic)
                            }
                            MarkupItems.Text -> {
                                setDrawMode(DrawMode.Text)
                                if (textAnnotations.isEmpty()) onRequestTextInput()
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

/**
 * Size + strength sliders shown when a blur/mosaic brush is active, replacing the color picker.
 * Size maps to [PathProperties.strokeWidth]; strength maps to [PathProperties.effectStrength].
 */
@Composable
private fun BrushEffectControls(
    modifier: Modifier = Modifier,
    toolIcon: ImageVector,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    onDetectFaces: () -> Unit = {},
    faceDetectAvailable: Boolean = false,
    isDetectingFaces: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = toolIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.editor_brush_size),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Slider(
                value = currentPathProperty.strokeWidth,
                onValueChange = {
                    setCurrentPathProperty(currentPathProperty.copy(strokeWidth = it))
                },
                valueRange = 15f..150f,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.editor_brush_strength),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Slider(
                value = currentPathProperty.effectStrength,
                onValueChange = {
                    setCurrentPathProperty(currentPathProperty.copy(effectStrength = it))
                },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
        if (faceDetectAvailable) {
            TextButton(
                onClick = { if (!isDetectingFaces) onDetectFaces() },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            ) {
                if (isDetectingFaces) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.editor_blur_faces),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}