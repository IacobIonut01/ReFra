package com.dot.gallery.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_description
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_blue
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_green
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_luma
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_red
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_summary
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_open
import com.dot.gallery.editor.ui.generated.resources.editor_show_clipping
import com.dot.gallery.editor.ui.generated.resources.editor_show_clipping_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.GraphicEq
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import org.jetbrains.compose.resources.stringResource

internal const val TAG_PHOTO_HISTOGRAM = "editor.photo.histogram"
internal const val TAG_PHOTO_HISTOGRAM_LEGEND = "editor.photo.histogram.legend"
internal const val PHOTO_HISTOGRAM_BINS = 32

/**
 * Whether the adjustment sheet is expanded far enough to host the
 * histogram — provided by the sheet scaffold while its state is (or is
 * settling toward) Expanded, so the chart never composes into the
 * collapsed peek.
 */
internal val LocalPhotoHistogramVisible = compositionLocalOf { false }

/**
 * Which channel group the chart draws. The cycle order mirrors the
 * legend — RGB (every channel), then each single channel, then luma —
 * so a graph tap walks the legend left to right.
 */
enum class PhotoHistogramMode {
    Rgb, Red, Green, Blue, Luma;

    fun next(): PhotoHistogramMode =
        entries[(ordinal + 1) % entries.size]
}

/**
 * The histogram's display preferences — owned by the adjustment sheet
 * so the chart, its graph tap and the settings rows at the sheet's foot
 * all read and write one instance. [showClipping] overlays clipped
 * shadow/highlight mass; [mode] picks the drawn channels; [expanded]
 * swaps the floating pill for the opened panel.
 */
internal class PhotoHistogramPrefs {
    var mode by mutableStateOf(PhotoHistogramMode.Rgb)
    var expanded by mutableStateOf(false)
    var showClipping by mutableStateOf(false)
    fun cycleMode() {
        mode = mode.next()
    }
}

internal val LocalPhotoHistogramPrefs =
    compositionLocalOf<PhotoHistogramPrefs?> { null }

/**
 * Display-referred RGB and Rec.709-luma distributions. Every channel shares
 * one peak normalization, so their relative population remains meaningful.
 * Transparent pixels contribute nothing; partial alpha contributes its alpha
 * byte as a deterministic integer weight. Zero is a valid channel value:
 * dark-channel samples remain in bin zero rather than being discarded.
 */
internal class PhotoHistogram internal constructor(
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val luma: FloatArray,
    val alphaWeight: Long,
    val stats: PhotoHistogramStats = PhotoHistogramStats.Empty
) {
    init {
        val bins = red.size
        require(bins >= 2)
        require(green.size == bins && blue.size == bins && luma.size == bins)
    }
}

/**
 * Exact 8-bit figures for one channel, alpha-weighted like the bins:
 * [mean] and [median] on the 0–255 scale, and the weight sitting exactly
 * at 0 ([clippedLow]) and 255 ([clippedHigh]).
 */
internal data class PhotoChannelStats(
    val mean: Float,
    val median: Int,
    val clippedLow: Long,
    val clippedHigh: Long
) {
    companion object {
        val Empty = PhotoChannelStats(0f, 0, 0L, 0L)
    }
}

/**
 * Per-channel [PhotoChannelStats] plus the RGB clip counts, where a pixel
 * counts when any of its channels is at the limit.
 */
internal data class PhotoHistogramStats(
    val red: PhotoChannelStats,
    val green: PhotoChannelStats,
    val blue: PhotoChannelStats,
    val luma: PhotoChannelStats,
    val anyClippedLow: Long,
    val anyClippedHigh: Long
) {
    fun clipping(mode: PhotoHistogramMode): Pair<Long, Long> = when (mode) {
        PhotoHistogramMode.Rgb -> anyClippedLow to anyClippedHigh
        PhotoHistogramMode.Red -> red.clippedLow to red.clippedHigh
        PhotoHistogramMode.Green -> green.clippedLow to green.clippedHigh
        PhotoHistogramMode.Blue -> blue.clippedLow to blue.clippedHigh
        PhotoHistogramMode.Luma -> luma.clippedLow to luma.clippedHigh
    }

    companion object {
        val Empty = PhotoHistogramStats(
            PhotoChannelStats.Empty, PhotoChannelStats.Empty,
            PhotoChannelStats.Empty, PhotoChannelStats.Empty, 0L, 0L
        )
    }
}

private fun channelStats(exact: LongArray, weight: Long): PhotoChannelStats {
    if (weight == 0L) return PhotoChannelStats.Empty
    var sum = 0.0
    for (value in exact.indices) sum += value.toDouble() * exact[value]
    // The lower median: the first value whose cumulative weight reaches half.
    var median = 0
    var cumulative = 0L
    for (value in exact.indices) {
        cumulative += exact[value]
        if (cumulative * 2 >= weight) {
            median = value
            break
        }
    }
    return PhotoChannelStats(
        mean = (sum / weight).toFloat(),
        median = median,
        clippedLow = exact[0],
        clippedHigh = exact[255]
    )
}

/**
 * Calculates a histogram from the exact displayed sRGB pixels. Luma uses
 * fixed-point Rec.709 coefficients summing to 65,536; binning stays integer
 * and stable across Android and desktop.
 */
internal fun calculatePhotoHistogram(
    pixels: SrgbPixels,
    bins: Int = PHOTO_HISTOGRAM_BINS
): PhotoHistogram {
    require(bins >= 2) { "histogram needs at least two bins" }
    val counts = Array(4) { LongArray(bins) }
    val exact = Array(4) { LongArray(256) }
    var anyLow = 0L
    var anyHigh = 0L
    var alphaWeight = 0L

    for (argb in pixels.copyPixels()) {
        val alpha = argb ushr 24 and 0xFF
        if (alpha == 0) continue
        val red = argb ushr 16 and 0xFF
        val green = argb ushr 8 and 0xFF
        val blue = argb and 0xFF
        val luma = (
            13_933 * red + 46_871 * green + 4_732 * blue + 32_768
            ) ushr 16

        counts[0][red * bins ushr 8] += alpha.toLong()
        counts[1][green * bins ushr 8] += alpha.toLong()
        counts[2][blue * bins ushr 8] += alpha.toLong()
        counts[3][luma * bins ushr 8] += alpha.toLong()
        exact[0][red] += alpha.toLong()
        exact[1][green] += alpha.toLong()
        exact[2][blue] += alpha.toLong()
        exact[3][luma] += alpha.toLong()
        if (red == 0 || green == 0 || blue == 0) anyLow += alpha.toLong()
        if (red == 255 || green == 255 || blue == 255) anyHigh += alpha.toLong()
        alphaWeight += alpha.toLong()
    }

    val peak = counts.maxOf { channel -> channel.maxOrNull() ?: 0L }
    fun normalized(channel: LongArray): FloatArray =
        if (peak == 0L) {
            FloatArray(bins)
        } else {
            FloatArray(bins) { channel[it].toFloat() / peak.toFloat() }
        }

    return PhotoHistogram(
        red = normalized(counts[0]),
        green = normalized(counts[1]),
        blue = normalized(counts[2]),
        luma = normalized(counts[3]),
        alphaWeight = alphaWeight,
        stats = PhotoHistogramStats(
            red = channelStats(exact[0], alphaWeight),
            green = channelStats(exact[1], alphaWeight),
            blue = channelStats(exact[2], alphaWeight),
            luma = channelStats(exact[3], alphaWeight),
            anyClippedLow = anyLow,
            anyClippedHigh = anyHigh
        )
    )
}

@Composable
internal fun PhotoHistogramChart(
    pixels: SrgbPixels?,
    modifier: Modifier = Modifier
) {
    val description = stringResource(Res.string.editor_histogram_description)
    val cycleDescription =
        stringResource(Res.string.editor_histogram_mode_summary)
    val prefs = LocalPhotoHistogramPrefs.current
    val mode = prefs?.mode ?: PhotoHistogramMode.Rgb
    val showClipping = prefs?.showClipping == true
    val histogram by produceState<PhotoHistogram?>(null, pixels) {
        value = pixels?.let {
            withContext(Dispatchers.Default) { calculatePhotoHistogram(it) }
        }
    }
    val allChannels = histogram?.let {
        listOf(
            Triple(PhotoHistogramMode.Red, it.red, Color(0xFFFF6B6B)),
            Triple(PhotoHistogramMode.Green, it.green, Color(0xFF53D98A)),
            Triple(PhotoHistogramMode.Blue, it.blue, Color(0xFF6FA8FF)),
            Triple(PhotoHistogramMode.Luma, it.luma, Color(0xFFE8EAED))
        )
    }.orEmpty()
    // RGB draws every channel; a single-channel mode keeps only its own.
    val channels = allChannels.filter { (channelMode, _, _) ->
        mode == PhotoHistogramMode.Rgb || channelMode == mode
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LocalEditorColors.current.canvas)
            .clickable(
                enabled = prefs != null,
                role = Role.Button,
                onClickLabel = cycleDescription
            ) { prefs?.cycleMode() }
            .semantics { contentDescription = description }
            .testTag(TAG_PHOTO_HISTOGRAM)
    ) {
        Canvas(Modifier.matchParentSize()) {
            channels.forEach { (_, values, color) ->
                if (values.isEmpty()) return@forEach
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = index.toFloat() / (values.lastIndex) * size.width
                    val y = size.height - 8.dp.toPx() -
                        value.coerceIn(0f, 1f) *
                        (size.height - 16.dp.toPx())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color.copy(
                        alpha = if (color == Color(0xFFE8EAED)) {
                            0.9f
                        } else {
                            0.76f
                        }
                    ),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            if (channels.isEmpty()) {
                drawLine(
                    color = Color(0xFFB8B8B8),
                    start = Offset(0f, size.height - 1.dp.toPx()),
                    end = Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Clipping overlay: mass parked in the extreme bins is clipped
            // — shadows wedge in from the left, highlights from the right,
            // each sized by the largest clipped channel population.
            if (showClipping && histogram != null) {
                val clipWidth = 6.dp.toPx()
                val shadowMass = allChannels.maxOf { (_, v, _) ->
                    v.firstOrNull() ?: 0f
                }
                val highlightMass = allChannels.maxOf { (_, v, _) ->
                    v.lastOrNull() ?: 0f
                }
                if (shadowMass > 0f) {
                    drawRect(
                        color = Color(0xFF6FA8FF),
                        topLeft = Offset.Zero,
                        size = Size(
                            clipWidth * shadowMass.coerceIn(0.25f, 1f),
                            size.height
                        )
                    )
                }
                if (highlightMass > 0f) {
                    val width = clipWidth * highlightMass.coerceIn(0.25f, 1f)
                    drawRect(
                        color = Color(0xFFFF6B6B),
                        topLeft = Offset(size.width - width, 0f),
                        size = Size(width, size.height)
                    )
                }
            }
        }
        // The channel legend — a dot+label per drawn channel parked at the
        // card's top end, exactly the R G B L readout of the reference.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 10.dp)
                .testTag(TAG_PHOTO_HISTOGRAM_LEGEND),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            channels.forEach { (channelMode, _, color) ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = when (channelMode) {
                        PhotoHistogramMode.Red -> "R"
                        PhotoHistogramMode.Green -> "G"
                        PhotoHistogramMode.Blue -> "B"
                        else -> "L"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = color
                )
            }
        }
    }
}

internal const val TAG_PHOTO_SETTINGS_ROWS = "editor.photo.settings.rows"
internal const val TAG_PHOTO_SHOW_CLIPPING = "editor.photo.showClipping"
internal const val TAG_PHOTO_HISTOGRAM_MODE = "editor.photo.histogramMode"

private fun histogramModeLabelRes(mode: PhotoHistogramMode) = when (mode) {
    PhotoHistogramMode.Rgb -> Res.string.editor_histogram_mode_rgb
    PhotoHistogramMode.Red -> Res.string.editor_histogram_mode_red
    PhotoHistogramMode.Green -> Res.string.editor_histogram_mode_green
    PhotoHistogramMode.Blue -> Res.string.editor_histogram_mode_blue
    PhotoHistogramMode.Luma -> Res.string.editor_histogram_mode_luma
}

/**
 * The expanded sheet's settings rows — the same title / summary /
 * trailing-control anatomy the app's settings DSL uses, scoped to the
 * histogram the rows configure. "Show clipping" is a switch row;
 * "Histogram mode" is a value row whose click cycles the same channels
 * a graph tap does.
 */
@Composable
internal fun PhotoHistogramSettingsRows(
    prefs: PhotoHistogramPrefs,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_PHOTO_SETTINGS_ROWS),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorSettingsRow(
            title = stringResource(Res.string.editor_show_clipping),
            summary = stringResource(Res.string.editor_show_clipping_summary),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag(TAG_PHOTO_SHOW_CLIPPING),
            trailing = {
                Switch(
                    checked = prefs.showClipping,
                    onCheckedChange = { prefs.showClipping = it }
                )
            }
        )
        EditorSettingsRow(
            title = stringResource(Res.string.editor_histogram_mode),
            summary = stringResource(Res.string.editor_histogram_mode_summary),
            onClick = prefs::cycleMode,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag(TAG_PHOTO_HISTOGRAM_MODE),
            trailing = {
                Text(
                    text = stringResource(histogramModeLabelRes(prefs.mode)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }
}

/**
 * One settings row — title over an optional summary on the left, the
 * control slot on the right. Omitting [onClick] renders a display row
 * (the trailing control owns the interaction); supplying it makes the
 * whole row the click target.
 */
@Composable
internal fun EditorSettingsRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 16.dp)) { trailing() }
        }
    }
}

internal const val TAG_PHOTO_HISTOGRAM_PILL = "editor.photo.histogramPill"
internal const val TAG_PHOTO_HISTOGRAM_PILL_MODE = "editor.photo.histogramPill.mode"
internal const val TAG_PHOTO_HISTOGRAM_PILL_OPEN = "editor.photo.histogramPill.open"

internal fun photoHistogramPillModeTag(mode: PhotoHistogramMode) =
    "editor.photo.histogramPill.mode.${mode.name}"

/**
 * The V3 floating histogram pill — a compact blurred panel the workspace
 * home and the adjust tool canvases float over the photo. It carries a
 * bar icon, a real mini chart of the current mode's channels and the
 * mode label, with a caret opening the mode menu (every mode is a menu
 * item, not a blind cycle). The pill body opens the panel. The pill owns its histogram computation: the
 * surfaces hosting it are mutually exclusive (the home lane and the tool
 * sheet never compose together), so no shared instance is hoisted.
 */
/**
 * The one histogram surface: the collapsed pill and the opened panel are
 * the same control in two sizes, so the expansion morphs — the pill's
 * corner is the transform origin both ways, and the container animates its
 * size while the content cross-fades. A hard swap made the panel appear
 * from nowhere.
 */
@Composable
internal fun HistogramMorph(
    expanded: Boolean,
    pixels: () -> SrgbPixels?,
    prefs: PhotoHistogramPrefs,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(tween(200)) + scaleIn(
                    tween(240),
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(1f, 0f)
                )
                ) togetherWith (
                fadeOut(tween(120)) + scaleOut(
                    tween(160),
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(1f, 0f)
                )
                ) using SizeTransform(clip = false) { _, _ -> tween(260) }
        },
        label = "histogramMorph"
    ) { open ->
        if (open) {
            PhotoHistogramPanel(
                pixels = pixels(),
                prefs = prefs,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                PhotoHistogramPill(
                    pixels = pixels(),
                    prefs = prefs,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
internal fun PhotoHistogramPill(
    pixels: SrgbPixels?,
    prefs: PhotoHistogramPrefs,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var modeMenuOpen by remember { mutableStateOf(false) }
    val histogram by produceState<PhotoHistogram?>(null, pixels) {
        value = pixels?.let {
            withContext(Dispatchers.Default) { calculatePhotoHistogram(it) }
        }
    }
    val mode = prefs.mode
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (enabled) 1f else 0.38f
    )
    EditorFloatingPanel(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.testTag(TAG_PHOTO_HISTOGRAM_PILL)
    ) {
        val openLabel = stringResource(Res.string.editor_histogram_open)
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = openLabel,
                        onClick = { prefs.expanded = true }
                    )
                    .semantics { contentDescription = openLabel }
                    .padding(start = 16.dp, end = 4.dp)
                    .testTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = EditorSymbols.GraphicEq,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                // The mini real-bins chart — the same computed channels the
                // full chart draws, at pill scale.
                Canvas(
                    modifier = Modifier
                        .size(width = 48.dp, height = 22.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    if (histogram == null) {
                        drawLine(
                            color = contentColor.copy(alpha = 0.3f),
                            start = Offset(0f, size.height - 1.dp.toPx()),
                            end = Offset(size.width, size.height - 1.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                        return@Canvas
                    }
                    val allChannels = listOf(
                        Triple(PhotoHistogramMode.Red, histogram!!.red, Color(0xFFFF6B6B)),
                        Triple(PhotoHistogramMode.Green, histogram!!.green, Color(0xFF53D98A)),
                        Triple(PhotoHistogramMode.Blue, histogram!!.blue, Color(0xFF6FA8FF)),
                        Triple(PhotoHistogramMode.Luma, histogram!!.luma, Color(0xFFE8EAED))
                    )
                    val channels = allChannels.filter { (channelMode, _, _) ->
                        mode == PhotoHistogramMode.Rgb || channelMode == mode
                    }
                    channels.forEach { (_, values, color) ->
                        if (values.isEmpty()) return@forEach
                        val path = Path()
                        values.forEachIndexed { index, value ->
                            val x = index.toFloat() / values.lastIndex * size.width
                            val y = size.height - 1.dp.toPx() -
                                value.coerceIn(0f, 1f) * (size.height - 2.dp.toPx())
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.8f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                Text(
                    text = stringResource(histogramModeLabelRes(mode)),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // The channel menu keeps its own 48 dp target beside the body.
            val modeLabel = stringResource(Res.string.editor_histogram_mode)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        role = Role.DropdownList,
                        onClickLabel = modeLabel,
                        onClick = { modeMenuOpen = true }
                    )
                    .semantics { contentDescription = modeLabel }
                    .testTag(TAG_PHOTO_HISTOGRAM_PILL_MODE),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        DropdownMenu(
            expanded = modeMenuOpen,
            onDismissRequest = { modeMenuOpen = false }
        ) {
            PhotoHistogramMode.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(stringResource(histogramModeLabelRes(candidate))) },
                    onClick = {
                        prefs.mode = candidate
                        modeMenuOpen = false
                    },
                    modifier = Modifier.testTag(photoHistogramPillModeTag(candidate))
                )
            }
        }
    }
}
