/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.setup.components.SetupSectionCard
import com.dot.gallery.feature_node.presentation.setup.components.SetupWizardScaffold
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDisplayStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetType

/**
 * Second step of widget configuration (#1269): pick how the chosen photos are
 * drawn on the home screen — in colour, black & white, or replaced by an
 * emoji/label that still deep-links to the photo on tap.
 */
@Composable
fun WidgetStyleScreen(
    previewUris: List<Uri>,
    widgetType: WidgetType,
    initialStyle: WidgetDisplayStyle,
    initialIcon: String?,
    isReconfigure: Boolean,
    onBack: () -> Unit,
    onDone: (WidgetDisplayStyle, String?) -> Unit,
) {
    var style by remember { mutableStateOf(initialStyle) }
    var iconText by remember {
        mutableStateOf(WidgetStyle.sanitizeIcon(initialIcon) ?: WidgetStyle.DEFAULT_ICON)
    }
    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    BackHandler(onBack = onBack)

    SetupWizardScaffold(
        showBack = true,
        onBack = onBack,
        stepNumber = 2,
        totalSteps = 2,
        title = stringResource(R.string.widget_style_title),
        subtitle = stringResource(R.string.widget_style_subtitle),
        bottomBar = {
            SetupButton(
                text = stringResource(
                    if (isReconfigure) R.string.apply else R.string.widget_style_add
                ),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                applyNavigationPadding = false,
                onClick = {
                    val icon = if (style == WidgetDisplayStyle.ICON) {
                        WidgetStyle.sanitizeIcon(iconText)
                    } else null
                    onDone(style, icon)
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live render of what lands on the home screen
            HomeScreenStage {
                AnimatedContent(
                    targetState = style,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                    },
                    label = "widgetStylePreview"
                ) { targetStyle ->
                    WidgetFace(
                        style = targetStyle,
                        iconText = iconText,
                        uris = previewUris,
                        widgetType = widgetType,
                        grayscaleFilter = grayscaleFilter,
                        emojiSize = 56.sp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Every option previews itself on the real pick — no blind radios
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleTile(
                    label = stringResource(R.string.widget_style_original),
                    selected = style == WidgetDisplayStyle.IMAGE,
                    onClick = { style = WidgetDisplayStyle.IMAGE }
                ) {
                    WidgetFace(
                        style = WidgetDisplayStyle.IMAGE,
                        iconText = iconText,
                        uris = previewUris.take(1),
                        widgetType = WidgetType.SINGLE,
                        grayscaleFilter = grayscaleFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                StyleTile(
                    label = stringResource(R.string.widget_style_bw),
                    selected = style == WidgetDisplayStyle.GRAYSCALE,
                    onClick = { style = WidgetDisplayStyle.GRAYSCALE }
                ) {
                    WidgetFace(
                        style = WidgetDisplayStyle.GRAYSCALE,
                        iconText = iconText,
                        uris = previewUris.take(1),
                        widgetType = WidgetType.SINGLE,
                        grayscaleFilter = grayscaleFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                StyleTile(
                    label = stringResource(R.string.widget_style_icon),
                    selected = style == WidgetDisplayStyle.ICON,
                    onClick = { style = WidgetDisplayStyle.ICON }
                ) {
                    WidgetFace(
                        style = WidgetDisplayStyle.ICON,
                        iconText = iconText,
                        uris = emptyList(),
                        widgetType = WidgetType.SINGLE,
                        grayscaleFilter = grayscaleFilter,
                        emojiSize = 26.sp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AnimatedVisibility(
                visible = style == WidgetDisplayStyle.ICON,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                IconEditor(
                    iconText = iconText,
                    onIconChange = { iconText = it }
                )
            }
        }
    }
}

/**
 * A faux home screen: tinted stage, a floating widget tile the caller fills,
 * and a dock row underneath so the result reads as a launcher page.
 */
@Composable
private fun HomeScreenStage(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

/** What the widget tile draws for a given [WidgetDisplayStyle]. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun WidgetFace(
    style: WidgetDisplayStyle,
    iconText: String,
    uris: List<Uri>,
    widgetType: WidgetType,
    grayscaleFilter: ColorFilter,
    emojiSize: TextUnit = 44.sp,
    modifier: Modifier = Modifier
) {
    when (style) {
        WidgetDisplayStyle.ICON -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconText.ifBlank { WidgetStyle.DEFAULT_ICON },
                fontSize = emojiSize,
                textAlign = TextAlign.Center
            )
        }

        else -> {
            val colorFilter =
                if (style == WidgetDisplayStyle.GRAYSCALE) grayscaleFilter else null
            if (uris.isEmpty()) {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (style == WidgetDisplayStyle.GRAYSCALE) {
                            Icons.Outlined.FilterBAndW
                        } else {
                            Icons.Outlined.Image
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else if (widgetType == WidgetType.GRID) {
                // Mirror the real grid widget: cells with thin gaps between them
                val cells = List(4) { uris.getOrNull(it) }
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (col in 0..1) {
                                val uri = cells[row * 2 + col]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                ) {
                                    if (uri != null) {
                                        GlideImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            colorFilter = colorFilter
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                GlideImage(
                    model = uris.first(),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter
                )
            }
        }
    }
}

/** A selectable card that shows a live render of its own style. */
@Composable
private fun RowScope.StyleTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "styleTileBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        },
        label = "styleTileContainer"
    )
    val checkTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        },
        label = "styleTileCheck"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .background(containerColor)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            preview()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = checkTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconEditor(
    iconText: String,
    onIconChange: (String) -> Unit
) {
    SetupSectionCard(
        title = stringResource(R.string.widget_icon_section),
        subtitle = stringResource(R.string.widget_icon_description)
    ) {
        OutlinedTextField(
            value = iconText,
            onValueChange = { onIconChange(WidgetStyle.sanitizeIcon(it).orEmpty()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            ),
            label = { Text(stringResource(R.string.widget_icon_label)) }
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (suggestion in WidgetStyle.SUGGESTED_ICONS) {
                val chosen = iconText == suggestion
                val chipBorder by animateColorAsState(
                    targetValue = if (chosen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "iconChipBorder"
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, chipBorder, RoundedCornerShape(14.dp))
                        .background(
                            if (chosen) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .clickable { onIconChange(suggestion) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = suggestion, fontSize = 24.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WidgetStyleScreenPreview() {
    PreviewHost {
        WidgetStyleScreen(
            previewUris = emptyList(),
            widgetType = WidgetType.SINGLE,
            initialStyle = WidgetDisplayStyle.IMAGE,
            initialIcon = null,
            isReconfigure = false,
            onBack = {},
            onDone = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WidgetStyleScreenIconPreview() {
    PreviewHost(darkTheme = true) {
        WidgetStyleScreen(
            previewUris = emptyList(),
            widgetType = WidgetType.GRID,
            initialStyle = WidgetDisplayStyle.ICON,
            initialIcon = "📷",
            isReconfigure = true,
            onBack = {},
            onDone = { _, _ -> }
        )
    }
}
