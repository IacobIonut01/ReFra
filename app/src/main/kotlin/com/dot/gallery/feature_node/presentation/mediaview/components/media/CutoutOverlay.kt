/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy

/**
 * Which prompt-point tool is currently armed while refining a subject cutout.
 * [ADD] seeds an "include" point, [REMOVE] an "exclude" point, [NONE] disables point tapping.
 */
enum class ZoomablePagerImagePointTool { NONE, ADD, REMOVE }

/** Semantic accents for include (+) / exclude (-) prompt points, shared by markers and controls. */
private val CutoutIncludeColor = Color(0xFF34C759)
private val CutoutExcludeColor = Color(0xFFFF3B30)

/**
 * Full-screen overlay drawn on top of the media viewer while a subject-cutout session is active.
 * Handles the dim scrim and the cutout subject + animated contour + prompt markers (on a Canvas).
 * The interactive controls live in the [MediaViewScreen] bottom bar ([CutoutControlsBar]); this
 * overlay is purely visual so image zoom/pan gestures keep working.
 */
@Composable
internal fun CutoutOverlay(
    state: CutoutState,
    zoomState: com.github.panpf.zoomimage.SketchZoomState,
    glowRadius: Float,
    // Vertical translation (px) applied by the swipe-to-dismiss gesture; the subject + markers
    // follow the image while the scrim stays anchored to the screen.
    translationY: Float = 0f,
) {
    val session = state.session ?: return
    val result = state.result
    val displayRect = zoomState.zoomable.contentDisplayRect

    val strokePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            colorFilter = android.graphics.PorterDuffColorFilter(
                android.graphics.Color.WHITE,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }
    val imagePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
        }
    }
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)

    if (displayRect.width > 0 && session.widthOrig > 0) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dim background (no pointerInput so zoom/pan gestures still pass through to the image).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )

            // Cutout subject, animated glowing contour and prompt-point markers. Translated to
            // follow the image during a swipe-to-dismiss drag.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.translationY = translationY }
            ) {
                val rect = zoomState.zoomable.contentDisplayRect
                if (rect.width > 0 && session.widthOrig > 0) {
                    if (result != null) {
                        val screenStartX = rect.left + (result.originalBounds.left.toFloat() / session.widthOrig.toFloat()) * rect.width
                        val screenStartY = rect.top + (result.originalBounds.top.toFloat() / session.heightOrig.toFloat()) * rect.height
                        val screenWidth = ((result.originalBounds.right - result.originalBounds.left).toFloat() / session.widthOrig.toFloat()) * rect.width
                        val screenHeight = ((result.originalBounds.bottom - result.originalBounds.top).toFloat() / session.heightOrig.toFloat()) * rect.height

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            val srcRect = android.graphics.Rect(0, 0, result.bitmap.width, result.bitmap.height)

                            val strokeWidthPx = glowRadius.dp.toPx()
                            val diagOffset = (strokeWidthPx / 1.4142f).toInt()
                            val strokeWidthInt = strokeWidthPx.toInt()

                            val offsets = listOf(
                                Pair(strokeWidthInt, 0),
                                Pair(-strokeWidthInt, 0),
                                Pair(0, strokeWidthInt),
                                Pair(0, -strokeWidthInt),
                                Pair(diagOffset, diagOffset),
                                Pair(-diagOffset, diagOffset),
                                Pair(diagOffset, -diagOffset),
                                Pair(-diagOffset, -diagOffset)
                            )

                            offsets.forEach { (dx, dy) ->
                                val dstRectShifted = android.graphics.Rect(
                                    screenStartX.toInt() + dx,
                                    screenStartY.toInt() + dy,
                                    (screenStartX + screenWidth).toInt() + dx,
                                    (screenStartY + screenHeight).toInt() + dy
                                )
                                nativeCanvas.drawBitmap(result.bitmap, srcRect, dstRectShifted, strokePaint)
                            }

                            val dstRectNormal = android.graphics.Rect(
                                screenStartX.toInt(),
                                screenStartY.toInt(),
                                (screenStartX + screenWidth).toInt(),
                                (screenStartY + screenHeight).toInt()
                            )
                            nativeCanvas.drawBitmap(result.bitmap, srcRect, dstRectNormal, imagePaint)
                        }
                    }

                    state.promptPoints.forEach { pt ->
                        val screenX = rect.left + (pt.x / session.widthOrig.toFloat()) * rect.width
                        val screenY = rect.top + (pt.y / session.heightOrig.toFloat()) * rect.height

                        val dotColor = if (pt.isPositive) CutoutIncludeColor else CutoutExcludeColor
                        drawCircle(
                            color = dotColor.copy(alpha = 0.3f),
                            radius = 10.dp.toPx(),
                            center = Offset(screenX, screenY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(screenX, screenY)
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(screenX, screenY)
                        )
                    }
                }
            }

        }
    }
}

/**
 * Redesigned cutout controls, rendered in the [MediaViewScreen] bottom bar (replacing the quick
 * actions) while a session is active. Two tiers: a tools row (close, include/exclude, undo/redo/
 * reset) and — once a mask exists — an export row (copy/share/save) that animates in below it.
 */
@Composable
internal fun CutoutControlsBar(
    controller: CutoutController,
    modifier: Modifier = Modifier
) {
    val state = controller.state
    // "Animate media items" off — the bar resizes and reveals its action tier instantly.
    val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.animateContentSize(
            animationSpec = if (animationsEnabled) spring() else snap()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Tier 1 — editing tools.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CutoutToolButton(
                    onClick = controller.onClose,
                    icon = Icons.Outlined.Close,
                    label = stringResource(R.string.cutout_close)
                )

                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                CutoutToolButton(
                    onClick = {
                        controller.onToolChange(
                            if (state.activeTool == ZoomablePagerImagePointTool.ADD) ZoomablePagerImagePointTool.NONE
                            else ZoomablePagerImagePointTool.ADD
                        )
                    },
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.cutout_include),
                    selected = state.activeTool == ZoomablePagerImagePointTool.ADD,
                    selectedColor = CutoutIncludeColor
                )
                CutoutToolButton(
                    onClick = {
                        controller.onToolChange(
                            if (state.activeTool == ZoomablePagerImagePointTool.REMOVE) ZoomablePagerImagePointTool.NONE
                            else ZoomablePagerImagePointTool.REMOVE
                        )
                    },
                    icon = Icons.Outlined.Remove,
                    label = stringResource(R.string.cutout_exclude),
                    selected = state.activeTool == ZoomablePagerImagePointTool.REMOVE,
                    selectedColor = CutoutExcludeColor
                )

                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                CutoutToolButton(
                    onClick = controller.onUndo,
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    label = stringResource(R.string.cutout_undo),
                    enabled = state.canUndo
                )
                CutoutToolButton(
                    onClick = controller.onRedo,
                    icon = Icons.AutoMirrored.Outlined.Redo,
                    label = stringResource(R.string.cutout_redo),
                    enabled = state.canRedo
                )
                CutoutToolButton(
                    onClick = controller.onReset,
                    icon = Icons.Outlined.Refresh,
                    label = stringResource(R.string.cutout_reset)
                )
            }

            // Tier 2 — export actions, available once a mask exists.
            AnimatedVisibility(
                visible = state.hasResult,
                enter = if (animationsEnabled) {
                    fadeIn() + expandVertically()
                } else {
                    EnterTransition.None
                },
                exit = if (animationsEnabled) {
                    fadeOut() + shrinkVertically()
                } else {
                    ExitTransition.None
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .width(220.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CutoutToolButton(
                            onClick = controller.onCopy,
                            icon = Icons.Outlined.ContentCopy,
                            label = stringResource(R.string.cutout_copy)
                        )
                        CutoutToolButton(
                            onClick = controller.onShare,
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.cutout_share)
                        )
                        CutoutToolButton(
                            onClick = controller.onSave,
                            icon = Icons.Outlined.Save,
                            label = stringResource(R.string.cutout_save)
                        )
                    }
                }
            }
        }
    }
}

/** A single toolbar button with a tooltip, used inside [CutoutControlsBar]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutoutToolButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            4.dp
        ),
        tooltip = { PlainTooltip { Text(text = label) } },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) selectedColor else Color.Transparent,
                contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

