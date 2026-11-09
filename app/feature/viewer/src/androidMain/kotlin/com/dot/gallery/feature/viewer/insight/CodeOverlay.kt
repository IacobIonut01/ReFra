/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.insight.CodeInsight
import com.dot.gallery.core.insight.CodePayload
import com.dot.gallery.core.insight.CodePayloadParser
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.code_chip_cd
import com.dot.gallery.feature.viewer.generated.resources.code_count
import com.dot.gallery.feature.viewer.generated.resources.code_found_announcement
import com.dot.gallery.feature.viewer.generated.resources.code_kind_contact
import com.dot.gallery.feature.viewer.generated.resources.code_kind_email
import com.dot.gallery.feature.viewer.generated.resources.code_kind_event
import com.dot.gallery.feature.viewer.generated.resources.code_kind_link
import com.dot.gallery.feature.viewer.generated.resources.code_kind_location
import com.dot.gallery.feature.viewer.generated.resources.code_kind_phone
import com.dot.gallery.feature.viewer.generated.resources.code_kind_product
import com.dot.gallery.feature.viewer.generated.resources.code_kind_sms
import com.dot.gallery.feature.viewer.generated.resources.code_kind_text
import com.dot.gallery.feature.viewer.generated.resources.code_kind_wifi
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The label resource for a payload's type ("Link", "Wi-Fi network", ...). */
fun CodePayload.kindLabel(): StringResource = when (this) {
    is CodePayload.Url -> Res.string.code_kind_link
    is CodePayload.Wifi -> Res.string.code_kind_wifi
    is CodePayload.Contact -> Res.string.code_kind_contact
    is CodePayload.Email -> Res.string.code_kind_email
    is CodePayload.Phone -> Res.string.code_kind_phone
    is CodePayload.Sms -> Res.string.code_kind_sms
    is CodePayload.Geo -> Res.string.code_kind_location
    is CodePayload.Event -> Res.string.code_kind_event
    is CodePayload.Product -> Res.string.code_kind_product
    is CodePayload.Text -> Res.string.code_kind_text
}

fun CodePayload.icon(): ImageVector = when (this) {
    is CodePayload.Url -> Icons.Outlined.Link
    is CodePayload.Wifi -> Icons.Outlined.Wifi
    is CodePayload.Contact -> Icons.Outlined.ContactPage
    is CodePayload.Email -> Icons.Outlined.Email
    is CodePayload.Phone -> Icons.Outlined.Call
    is CodePayload.Sms -> Icons.Outlined.Sms
    is CodePayload.Geo -> Icons.Outlined.Place
    is CodePayload.Event -> Icons.Outlined.Event
    is CodePayload.Product -> Icons.Outlined.ShoppingBag
    is CodePayload.Text -> Icons.Outlined.Description
}

/**
 * Brackets on each code found in the photo plus one chip that opens the result sheet.
 *
 * Positions are read from [zoomable] in the draw and layout phases only, so pinching and panning
 * move the overlay without recomposing it. [showChip] is false while the viewer chrome is hidden;
 * the chip also steps aside while a zoom or pan gesture is in progress.
 */
@Composable
fun CodeOverlay(
    insights: List<CodeInsight>,
    zoomable: ZoomableState,
    showChip: Boolean,
    animationsEnabled: Boolean,
    bottomReserve: androidx.compose.ui.unit.Dp,
    translationY: () -> Float,
    onOpen: (index: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val reveal = remember(insights) { Animatable(if (animationsEnabled) 0f else 1f) }
    val chipAlpha = remember(insights) { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(insights, animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        reveal.animateTo(1f, tween(durationMillis = 220))
    }
    val chipVisible = showChip && zoomable.continuousTransformType == 0
    LaunchedEffect(chipVisible, animationsEnabled) {
        val target = if (chipVisible) 1f else 0f
        if (animationsEnabled) {
            chipAlpha.animateTo(target, tween(durationMillis = if (chipVisible) 180 else 90, delayMillis = if (chipVisible) 60 else 0))
        } else {
            chipAlpha.snapTo(target)
        }
    }
    val fillPath = remember { Path() }
    val bracketPath = remember { Path() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stroke = remember(density) {
        with(density) { Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) }
    }
    val first = insights.first()
    val kind = stringResource(first.payload.kindLabel())
    val label = if (insights.size == 1) {
        kind
    } else {
        stringResource(Res.string.code_count, insights.size)
    }
    val value = if (insights.size == 1) {
        CodePayloadParser.summary(first.payload)
    } else {
        insights.joinToString(" · ") { CodePayloadParser.summary(it.payload) }
    }
    val announcement = stringResource(Res.string.code_found_announcement, label)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.translationY = translationY() }
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = zoomable.contentDisplayRectF
            if (rect.width <= 0f || zoomable.transform.rotation % 360f != 0f) return@Canvas
            val progress = reveal.value
            insights.forEachIndexed { index, insight ->
                val quad = insight.quad
                fillPath.reset()
                for (i in 0..3) {
                    val p = screenPoint(quad, i, rect)
                    if (i == 0) fillPath.moveTo(p.x, p.y) else fillPath.lineTo(p.x, p.y)
                }
                fillPath.close()
                drawPath(fillPath, primary.copy(alpha = 0.16f * progress))

                bracketPath.reset()
                val arm = 0.24f * progress
                for (i in 0..3) {
                    val corner = screenPoint(quad, i, rect)
                    val prev = screenPoint(quad, (i + 3) % 4, rect)
                    val next = screenPoint(quad, (i + 1) % 4, rect)
                    bracketPath.moveTo(corner.x + (prev.x - corner.x) * arm, corner.y + (prev.y - corner.y) * arm)
                    bracketPath.lineTo(corner.x, corner.y)
                    bracketPath.lineTo(corner.x + (next.x - corner.x) * arm, corner.y + (next.y - corner.y) * arm)
                }
                drawPath(bracketPath, primary.copy(alpha = progress), style = stroke)

                if (insights.size > 1) {
                    val badge = screenPoint(quad, 1, rect)
                    drawCircle(primary.copy(alpha = progress), radius = 14.dp.toPx(), center = badge)
                    drawIndexDigit(index + 1, badge, onPrimary.copy(alpha = progress))
                }
            }
        }

        CodeChipLayout(
            insights = insights,
            zoomable = zoomable,
            bottomReserve = bottomReserve,
            alpha = { chipAlpha.value },
        ) {
            CodeChip(
                icon = if (insights.size == 1) first.payload.icon() else Icons.Outlined.QrCode2,
                kind = label,
                value = value,
                contentDescription = stringResource(Res.string.code_chip_cd, label, value),
                enabled = chipVisible,
                onClick = { onOpen(if (insights.size == 1) 0 else null) },
            )
        }
    }
}

private fun screenPoint(quad: ImageQuad, index: Int, rect: Rect): Offset =
    Offset(rect.left + quad.x(index) * rect.width, rect.top + quad.y(index) * rect.height)

/** Draws the code number with a shared native Paint, so no text layout is created per frame. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIndexDigit(
    digit: Int,
    center: Offset,
    color: androidx.compose.ui.graphics.Color,
) {
    drawContext.canvas.nativeCanvas.drawText(
        digit.toString(),
        center.x,
        center.y + 5.dp.toPx(),
        digitPaint.apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).roundToInt(),
                (color.red * 255).roundToInt(),
                (color.green * 255).roundToInt(),
                (color.blue * 255).roundToInt(),
            )
            textSize = 14.dp.toPx()
        },
    )
}

private val digitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
}

/**
 * Places the chip under the codes (or above them when there is no room), centred on them and
 * clamped inside the screen. Reads zoom geometry during layout only.
 */
@Composable
private fun CodeChipLayout(
    insights: List<CodeInsight>,
    zoomable: ZoomableState,
    bottomReserve: androidx.compose.ui.unit.Dp,
    alpha: () -> Float,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val placeable = measurables.first().measure(
            Constraints(maxWidth = (width - margin * 2).coerceAtLeast(0), maxHeight = height)
        )
        layout(width, height) {
            val rect = zoomable.contentDisplayRectF
            if (rect.width <= 0f) return@layout
            var top = Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            insights.forEach { insight ->
                for (i in 0..3) {
                    val p = screenPoint(insight.quad, i, rect)
                    top = min(top, p.y); bottom = max(bottom, p.y)
                    left = min(left, p.x); right = max(right, p.x)
                }
            }
            val reserveBottom = bottomReserve.roundToPx()
            val below = bottom.roundToInt() + gap
            val y = when {
                below + placeable.height <= height - reserveBottom -> below
                top.roundToInt() - gap - placeable.height >= margin * 4 -> top.roundToInt() - gap - placeable.height
                else -> (height - reserveBottom - placeable.height).coerceAtLeast(margin)
            }
            val x = ((left + right) / 2f - placeable.width / 2f).roundToInt()
                .coerceIn(margin, (width - margin - placeable.width).coerceAtLeast(margin))
            placeable.placeRelative(x, y)
        }
    }
}

/** The pill under a found code: type, short value and a chevron. */
@Composable
fun CodeChip(
    icon: ImageVector,
    kind: String,
    value: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .heightIn(min = 52.dp)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.onPrimary)
        }
        Column(modifier = Modifier.widthIn(max = 240.dp)) {
            Text(
                text = kind,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
        )
    }
}

/** Index of the code under [point] (page coordinates), or null. */
fun hitTestCodes(insights: List<CodeInsight>, zoomable: ZoomableState, point: Offset): Int? {
    val rect = zoomable.contentDisplayRectF
    if (rect.width <= 0f || rect.height <= 0f || zoomable.transform.rotation % 360f != 0f) return null
    val nx = (point.x - rect.left) / rect.width
    val ny = (point.y - rect.top) / rect.height
    val index = insights.indexOfFirst { it.quad.contains(nx, ny) }
    return index.takeIf { it >= 0 }
}
