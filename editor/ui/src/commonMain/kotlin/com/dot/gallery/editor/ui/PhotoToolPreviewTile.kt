package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** What a catalogue row's preview tile illustrates. Drawn, never from the photo. */
internal enum class CataloguePreview {
    Light, Colour, Curves, WhiteBalance, Details, Develop, Crop, Perspective,
    Looks, Vignette, LensBlur, Grain, ColourTone, Posterize, Edges, Borders,
    Markup, Cutout, RemoveBackground, Layers, DoubleExposure, Transform, Blend,
    Mask, History, OpenIn, Settings
}

/** Tiles whose tool icon sits over the illustration. */
private val ICON_LED_PREVIEWS = setOf(
    CataloguePreview.Light,
    CataloguePreview.Develop,
    CataloguePreview.History,
    CataloguePreview.OpenIn,
    CataloguePreview.Settings
)

/**
 * A 48 dp rounded catalogue tile: a small drawn illustration of the tool on a
 * dark 0xFF161616 tile (surfaceContainerHigh in light mode), with the tool icon
 * on top for the icon-led kinds.
 */
@Composable
internal fun PhotoToolPreviewTile(
    kind: CataloguePreview?,
    icon: ImageVector?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val base = if (dark) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(base),
        contentAlignment = Alignment.Center
    ) {
        if (kind != null) {
            Canvas(Modifier.size(48.dp)) { drawPreview(kind) }
        }
        if (icon != null && kind != null && kind in ICON_LED_PREVIEWS) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.5f),
                modifier = Modifier.size(22.dp)
            )
        } else if (icon != null && kind == null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun DrawScope.drawPreview(kind: CataloguePreview) {
    val w = size.width
    val h = size.height
    val line = 1.5.dp.toPx()
    val white = Color.White
    when (kind) {
        CataloguePreview.Light -> drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF1A1A1A), Color(0xFFEFEFEF)))
        )
        CataloguePreview.Develop -> drawRect(
            Brush.linearGradient(listOf(Color(0xFF2B4C7E), Color(0xFFE8A96B)))
        )
        CataloguePreview.Colour -> drawRect(
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.9f, 0.92f) })
        )
        CataloguePreview.Curves -> {
            drawRect(Color(0xFF232323))
            for (i in 1..3) {
                drawLine(Color(0x33FFFFFF), Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), 1f)
                drawLine(Color(0x33FFFFFF), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f)
            }
            val path = Path().apply {
                moveTo(0f, h * 0.85f)
                cubicTo(w * 0.35f, h * 0.85f, w * 0.65f, h * 0.15f, w, h * 0.15f)
            }
            drawPath(path, white, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        CataloguePreview.WhiteBalance -> drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF3D6FD8), Color(0xFFF2B35C)))
        )
        CataloguePreview.Details -> {
            val n = 10
            for (i in 0 until n) {
                drawRect(
                    color = if (i % 2 == 0) white else Color.Black,
                    topLeft = Offset(w * i / n, 0f),
                    size = Size(w / n, h)
                )
            }
        }
        CataloguePreview.Crop -> {
            drawRect(Color(0xFF2A2F36))
            val l = w * 0.22f
            val o = w * 0.2f
            val stroke = line * 1.5f
            listOf(
                Triple(Offset(o, o), 1f, 1f),
                Triple(Offset(w - o, o), -1f, 1f),
                Triple(Offset(o, h - o), 1f, -1f),
                Triple(Offset(w - o, h - o), -1f, -1f)
            ).forEach { (point, dx, dy) ->
                drawLine(white, point, Offset(point.x + dx * l, point.y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(white, point, Offset(point.x, point.y + dy * l), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        CataloguePreview.Perspective -> {
            drawRect(Color(0xFF2A2F36))
            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.2f)
                lineTo(w * 0.8f, h * 0.2f)
                lineTo(w * 0.95f, h * 0.85f)
                lineTo(w * 0.05f, h * 0.85f)
                close()
            }
            drawPath(path, white, style = Stroke(width = line))
        }
        CataloguePreview.Looks -> {
            val bands = listOf(Color(0xFFC97B4A), Color(0xFF4A8C86), Color(0xFF3E4E7A))
            bands.forEachIndexed { i, c -> drawRect(c, Offset(w * i / 3f, 0f), Size(w / 3f, h)) }
        }
        CataloguePreview.Vignette -> {
            drawRect(Color(0xFF8A8A8A))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    center = Offset(w / 2f, h / 2f),
                    radius = w * 0.7f
                )
            )
        }
        CataloguePreview.LensBlur -> {
            drawRect(Color(0xFF2A2F36))
            drawCircle(
                Brush.radialGradient(
                    listOf(white.copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(w * 0.35f, h * 0.45f),
                    radius = w * 0.22f
                ),
                radius = w * 0.22f,
                center = Offset(w * 0.35f, h * 0.45f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(white.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(w * 0.72f, h * 0.6f),
                    radius = w * 0.18f
                ),
                radius = w * 0.18f,
                center = Offset(w * 0.72f, h * 0.6f)
            )
        }
        CataloguePreview.Grain -> {
            drawRect(Color(0xFF6E6E6E))
            val random = Random(7)
            repeat(140) {
                val dot = if (random.nextBoolean()) white.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                drawCircle(dot, radius = 1f, center = Offset(random.nextFloat() * w, random.nextFloat() * h))
            }
        }
        CataloguePreview.ColourTone -> drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF2A9D9A), Color(0xFFF0A04B)))
        )
        CataloguePreview.Posterize -> {
            val levels = listOf(Color(0xFF222222), Color(0xFF6A6A6A), Color(0xFFB8B8B8), Color(0xFFF0F0F0))
            levels.forEachIndexed { i, c -> drawRect(c, Offset(0f, h * i / 4f), Size(w, h / 4f)) }
        }
        CataloguePreview.Edges -> {
            drawRect(Color(0xFFF2F2F2))
            drawCircle(Color.Black, radius = w * 0.25f, center = Offset(w * 0.4f, h * 0.5f), style = Stroke(width = line))
            drawRect(Color.Black, Offset(w * 0.5f, h * 0.3f), Size(w * 0.35f, w * 0.35f), style = Stroke(width = line))
        }
        CataloguePreview.Borders -> {
            drawRect(Color(0xFF111111))
            val inset = w * 0.18f
            drawRect(Color(0xFF9A9A9A), Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset))
            drawRect(white, Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), style = Stroke(width = line))
        }
        CataloguePreview.Markup -> {
            drawRect(Color(0xFFEDEDED))
            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.7f)
                cubicTo(w * 0.3f, h * 0.2f, w * 0.45f, h * 0.9f, w * 0.6f, h * 0.4f)
                cubicTo(w * 0.7f, h * 0.1f, w * 0.8f, h * 0.6f, w * 0.85f, h * 0.3f)
            }
            drawPath(path, Color(0xFFE53935), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        CataloguePreview.Cutout -> {
            drawRect(Color(0xFF9FB3C8))
            val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            drawCircle(white, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.35f), style = Stroke(width = 2f, pathEffect = dashed))
            val body = Path().apply {
                moveTo(w * 0.28f, h * 0.9f)
                cubicTo(w * 0.28f, h * 0.55f, w * 0.72f, h * 0.55f, w * 0.72f, h * 0.9f)
            }
            drawPath(body, white, style = Stroke(width = 2f, pathEffect = dashed))
        }
        CataloguePreview.RemoveBackground -> {
            val cell = w / 6f
            for (x in 0 until 6) for (y in 0 until 6) {
                drawRect(
                    if ((x + y) % 2 == 0) Color(0xFFD0D0D0) else Color(0xFFF5F5F5),
                    Offset(x * cell, y * cell),
                    Size(cell, cell)
                )
            }
            drawCircle(Color(0xFF2E6FB5), radius = w * 0.22f, center = Offset(w / 2f, h / 2f))
        }
        CataloguePreview.Layers -> {
            drawRect(Color(0xFF7A8BA0), Offset(w * 0.15f, h * 0.15f), Size(w * 0.4f, h * 0.4f))
            drawRect(Color(0xFFB7C4D4), Offset(w * 0.3f, h * 0.3f), Size(w * 0.4f, h * 0.4f))
            drawRect(Color(0xFFE6ECF3), Offset(w * 0.45f, h * 0.45f), Size(w * 0.4f, h * 0.4f))
        }
        CataloguePreview.DoubleExposure -> {
            drawRect(Color(0xFF1F2328))
            drawCircle(Color(0xFFE0A050).copy(alpha = 0.6f), radius = w * 0.25f, center = Offset(w * 0.4f, h * 0.5f))
            drawCircle(Color(0xFF4FA3D9).copy(alpha = 0.6f), radius = w * 0.25f, center = Offset(w * 0.6f, h * 0.5f))
        }
        CataloguePreview.Transform -> {
            drawRect(Color(0xFF2A2F36))
            val rect = Offset(w * 0.2f, h * 0.25f) to Size(w * 0.6f, h * 0.5f)
            drawRect(white, rect.first, rect.second, style = Stroke(width = line))
            val handle = 5.dp.toPx()
            listOf(
                Offset(rect.first.x, rect.first.y),
                Offset(rect.first.x + rect.second.width, rect.first.y),
                Offset(rect.first.x, rect.first.y + rect.second.height),
                Offset(rect.first.x + rect.second.width, rect.first.y + rect.second.height)
            ).forEach { p ->
                drawRect(white, Offset(p.x - handle / 2, p.y - handle / 2), Size(handle, handle))
            }
        }
        CataloguePreview.Blend -> {
            drawRect(Color(0xFFF0E8D8))
            drawRect(Color(0xFFE87B5A), Offset(w * 0.15f, h * 0.2f), Size(w * 0.45f, h * 0.45f))
            drawRect(Color(0xFF4F7FBF), Offset(w * 0.4f, h * 0.35f), Size(w * 0.45f, h * 0.45f), blendMode = BlendMode.Multiply)
        }
        CataloguePreview.Mask -> {
            drawRect(Color(0xFF2A2F36))
            val r = w * 0.3f
            drawCircle(white, radius = r, center = Offset(w / 2f, h / 2f), style = Stroke(width = line))
            drawArc(
                color = white,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w / 2f - r, h / 2f - r),
                size = Size(2 * r, 2 * r)
            )
        }
        CataloguePreview.History, CataloguePreview.OpenIn, CataloguePreview.Settings ->
            drawRect(Color(0xFF3A4048))
    }
}
