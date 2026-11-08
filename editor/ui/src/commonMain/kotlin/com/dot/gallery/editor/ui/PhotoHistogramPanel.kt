package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_card_description
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_blue
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_green
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_red
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_close
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_description
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_highlights_clipped
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_median
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_luma
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_shadows_clipped
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_source
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_title
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_zone_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_zone_midtones
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_zone_shadows
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.GraphicEq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal const val TAG_PHOTO_HISTOGRAM_PANEL = "editor.photo.histogramPanel"
internal const val TAG_PHOTO_HISTOGRAM_PANEL_CLOSE = "editor.photo.histogramPanel.close"
internal const val TAG_PHOTO_HISTOGRAM_PANEL_CHART = "editor.photo.histogramPanel.chart"
internal const val TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP = "editor.photo.histogramPanel.shadowsClip"
internal const val TAG_PHOTO_HISTOGRAM_HIGHLIGHTS_CLIP = "editor.photo.histogramPanel.highlightsClip"

internal fun photoHistogramPanelModeTag(mode: PhotoHistogramMode) =
    "editor.photo.histogramPanel.mode.${mode.name}"

internal fun photoHistogramStatTag(mode: PhotoHistogramMode) =
    "editor.photo.histogramPanel.stat.${mode.name}"

internal fun histogramChannelColor(mode: PhotoHistogramMode): Color = when (mode) {
    PhotoHistogramMode.Red -> Color(0xFFFF6B6B)
    PhotoHistogramMode.Green -> Color(0xFF53D98A)
    PhotoHistogramMode.Blue -> Color(0xFF6FA8FF)
    PhotoHistogramMode.Luma, PhotoHistogramMode.Rgb -> Color(0xFFE8EAED)
}

private fun panelModeLabelRes(mode: PhotoHistogramMode): StringResource = when (mode) {
    PhotoHistogramMode.Rgb -> Res.string.editor_histogram_mode_rgb
    PhotoHistogramMode.Red -> Res.string.editor_histogram_channel_red
    PhotoHistogramMode.Green -> Res.string.editor_histogram_channel_green
    PhotoHistogramMode.Blue -> Res.string.editor_histogram_channel_blue
    PhotoHistogramMode.Luma -> Res.string.editor_histogram_mode_luma
}

/**
 * The share of [weight] in [count] with one decimal. A non-zero share that
 * would round to 0.0 % reads "<0.1 %" so a few clipped pixels never show as
 * none.
 */
internal fun formatClipShare(count: Long, weight: Long): String {
    if (weight <= 0L || count <= 0L) return "0.0%"
    val tenths = (count.toDouble() * 1000.0 / weight.toDouble()).roundToInt()
    if (tenths == 0) return "<0.1%"
    return "${tenths / 10}.${tenths % 10}%"
}

/**
 * The opened histogram: a read-out of the displayed preview's pixels. The
 * channel selector shares [prefs] with the collapsed pill; the clip chips
 * and the per-channel cards are exact 8-bit figures from
 * [PhotoHistogramStats]. Nothing on the panel edits the photo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoHistogramPanel(
    pixels: SrgbPixels?,
    prefs: PhotoHistogramPrefs,
    modifier: Modifier = Modifier
) {
    val histogram by produceState<PhotoHistogram?>(null, pixels) {
        value = pixels?.let {
            withContext(Dispatchers.Default) { calculatePhotoHistogram(it) }
        }
    }
    val mode = prefs.mode
    val close = { prefs.expanded = false }
    EditorFloatingPanel(
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .testTag(TAG_PHOTO_HISTOGRAM_PANEL)
    ) {
        // The panel scrolls inside whatever height the canvas leaves it.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelHeader(pixels = pixels, onClose = close)
            ChannelSelector(selected = mode, onSelect = { prefs.mode = it })
            val stats = histogram?.stats
            if (stats != null) {
                val (low, high) = stats.clipping(mode)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClipChip(
                        text = stringResource(
                            Res.string.editor_histogram_shadows_clipped,
                            formatClipShare(low, histogram!!.alphaWeight)
                        ),
                        accent = Color(0xFF6FA8FF),
                        modifier = Modifier.testTag(TAG_PHOTO_HISTOGRAM_SHADOWS_CLIP)
                    )
                    ClipChip(
                        text = stringResource(
                            Res.string.editor_histogram_highlights_clipped,
                            formatClipShare(high, histogram!!.alphaWeight)
                        ),
                        accent = Color(0xFFFFB74D),
                        modifier = Modifier.testTag(TAG_PHOTO_HISTOGRAM_HIGHLIGHTS_CLIP)
                    )
                }
            }
            PanelChart(histogram = histogram, mode = mode)
            if (stats != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    listOf(
                        PhotoHistogramMode.Luma to stats.luma,
                        PhotoHistogramMode.Red to stats.red,
                        PhotoHistogramMode.Green to stats.green,
                        PhotoHistogramMode.Blue to stats.blue
                    ).forEach { (channel, channelStats) ->
                        StatCard(
                            channel = channel,
                            stats = channelStats,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(pixels: SrgbPixels?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.editor_histogram_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (pixels != null) {
                Text(
                    text = stringResource(
                        Res.string.editor_histogram_source,
                        pixels.size.width,
                        pixels.size.height
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val closeLabel = stringResource(Res.string.editor_histogram_close)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onClose)
                .semantics { contentDescription = closeLabel }
                .testTag(TAG_PHOTO_HISTOGRAM_PANEL_CLOSE),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ChannelSelector(
    selected: PhotoHistogramMode,
    onSelect: (PhotoHistogramMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PhotoHistogramMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(20.dp)
                                )
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(mode) }
                    )
                    .padding(horizontal = 8.dp)
                    .testTag(photoHistogramPanelModeTag(mode)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(histogramChannelColor(mode))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(panelModeLabelRes(mode)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ClipChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PanelChart(histogram: PhotoHistogram?, mode: PhotoHistogramMode) {
    val description = stringResource(Res.string.editor_histogram_description)
    val zoneColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(LocalEditorColors.current.canvas)
            .semantics { contentDescription = description }
            .testTag(TAG_PHOTO_HISTOGRAM_PANEL_CHART)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            for (zone in 1..2) {
                val x = size.width * zone / 3f
                drawLine(
                    color = zoneColor.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
            }
            val h = histogram ?: return@Canvas
            val channels = listOf(
                PhotoHistogramMode.Luma to h.luma,
                PhotoHistogramMode.Red to h.red,
                PhotoHistogramMode.Green to h.green,
                PhotoHistogramMode.Blue to h.blue
            ).filter { (channel, _) ->
                mode == channel ||
                    (mode == PhotoHistogramMode.Rgb && channel != PhotoHistogramMode.Luma)
            }
            val top = 12.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            channels.forEach { (channel, values) ->
                val line = Path()
                values.forEachIndexed { index, value ->
                    val x = index.toFloat() / values.lastIndex * size.width
                    val y = bottom - value.coerceIn(0f, 1f) * (bottom - top)
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(size.width, bottom)
                    lineTo(0f, bottom)
                    close()
                }
                val color = histogramChannelColor(channel)
                drawPath(fill, color.copy(alpha = 0.18f))
                drawPath(line, color.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clearAndSetSemantics {}
        ) {
            listOf(
                Res.string.editor_histogram_zone_shadows,
                Res.string.editor_histogram_zone_midtones,
                Res.string.editor_histogram_zone_highlights
            ).forEach { zone ->
                FitOrDrop(Modifier.weight(1f), alignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(zone).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    channel: PhotoHistogramMode,
    stats: PhotoChannelStats,
    modifier: Modifier = Modifier
) {
    val accent = histogramChannelColor(channel)
    val label = stringResource(panelModeLabelRes(channel))
    val mean = stats.mean.roundToInt()
    val description = stringResource(
        Res.string.editor_histogram_card_description, label, mean, stats.median
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .testTag(photoHistogramStatTag(channel))
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (channel == PhotoHistogramMode.Luma) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                accent
            },
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = mean.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Wraps to two lines on a narrow card rather than clipping.
        Text(
            text = stringResource(Res.string.editor_histogram_median, stats.median),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
