package com.dot.gallery.editor.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_channel_blue
import com.dot.gallery.editor.ui.generated.resources.editor_channel_green
import com.dot.gallery.editor.ui.generated.resources.editor_channel_red
import com.dot.gallery.editor.ui.generated.resources.editor_channel_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_curve_add_point
import com.dot.gallery.editor.ui.generated.resources.editor_curve_black_point
import com.dot.gallery.editor.ui.generated.resources.editor_curve_decrease
import com.dot.gallery.editor.ui.generated.resources.editor_curve_editor
import com.dot.gallery.editor.ui.generated.resources.editor_curve_in_out
import com.dot.gallery.editor.ui.generated.resources.editor_curve_increase
import com.dot.gallery.editor.ui.generated.resources.editor_curve_input
import com.dot.gallery.editor.ui.generated.resources.editor_curve_output
import com.dot.gallery.editor.ui.generated.resources.editor_curve_point
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_auto
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_brighten
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_fade
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_linear
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_soft
import com.dot.gallery.editor.ui.generated.resources.editor_curve_preset_strong
import com.dot.gallery.editor.ui.generated.resources.editor_curve_release_to_remove
import com.dot.gallery.editor.ui.generated.resources.editor_curve_remove_point
import com.dot.gallery.editor.ui.generated.resources.editor_curve_tap_hint
import com.dot.gallery.editor.ui.generated.resources.editor_curve_white_point
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_midtones
import com.dot.gallery.editor.ui.generated.resources.editor_reset_channel
import com.dot.gallery.editor.ui.generated.resources.editor_reset_curves
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.icons.AutoFixHigh
import com.dot.gallery.editor.ui.icons.BlurOn
import com.dot.gallery.editor.ui.icons.Contrast
import com.dot.gallery.editor.ui.icons.Delete
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.LightMode
import com.dot.gallery.editor.ui.icons.NorthEast
import com.dot.gallery.editor.ui.icons.RestartAlt
import com.dot.gallery.editor.ui.icons.Tonality
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CURVES_READOUT = "editor.curves.readout"
internal const val TAG_CATEGORY_CURVES = "editor.category.curves"
internal const val TAG_CURVES_REMOVE = "editor.curves.remove"
internal const val TAG_CURVES_RESET_ALL = "editor.reset.curves"

/** How far past the graph, above or below, a dragged point is pulled off. */
private val RemoveMargin = 40.dp
private val PointTarget = 44.dp
private val PointHitRadius = 24.dp

/** The input's distribution on the curve axis, one count per 0..255 level. */
internal class CurveInputHistogram(
    val luma: LongArray,
    val red: LongArray,
    val green: LongArray,
    val blue: LongArray
)

/** Counts the pixels reaching the curves; partial alpha counts by its alpha. */
internal fun curveInputHistogram(pixels: SrgbPixels): CurveInputHistogram {
    val luma = LongArray(256)
    val red = LongArray(256)
    val green = LongArray(256)
    val blue = LongArray(256)
    for (argb in pixels.copyPixels()) {
        val alpha = (argb ushr 24 and 0xFF).toLong()
        if (alpha == 0L) continue
        val r = argb ushr 16 and 0xFF
        val g = argb ushr 8 and 0xFF
        val b = argb and 0xFF
        red[r] += alpha
        green[g] += alpha
        blue[b] += alpha
        luma[(13_933 * r + 46_871 * g + 4_732 * b + 32_768) ushr 16] += alpha
    }
    return CurveInputHistogram(luma, red, green, blue)
}

/** [counts] after [curve]: what the channel curves read once master has run. */
internal fun curveRemapped(counts: LongArray, curve: PhotoToneCurve): LongArray {
    if (curve.isNeutral) return counts
    val out = LongArray(256)
    for (level in 0..255) {
        val mapped = (curve.spline.evaluate(level / 255f) * 255f).roundToInt().coerceIn(0, 255)
        out[mapped] += counts[level]
    }
    return out
}

/**
 * The drawn histogram: 128 heights in 0..1, softened by a square root and
 * scaled against the peak away from the rails, where clipped pixels pile up.
 */
internal fun curveHistogramHeights(counts: LongArray): FloatArray {
    var peak = 1L
    for (level in 4 until 252) peak = max(peak, counts[level])
    return FloatArray(128) { bin ->
        val mean = (counts[bin * 2] + counts[bin * 2 + 1]) / 2f
        sqrt(mean / peak).coerceAtMost(1f) * (232f / 255f)
    }
}

/**
 * The Auto preset: black and white points at the levels where 0.3% of the
 * pixels fall outside on either side, at least 16 levels apart. An empty
 * distribution has nothing to stretch and stays the identity.
 */
internal fun curveAuto(counts: LongArray): PhotoToneCurve {
    val total = counts.sum()
    if (total == 0L) return PhotoToneCurve()
    val clip = total * 0.003
    var low = 0
    var seen = 0L
    for (level in 0..255) {
        seen += counts[level]
        if (seen >= clip) {
            low = level
            break
        }
    }
    var high = 255
    seen = 0L
    for (level in 255 downTo 0) {
        seen += counts[level]
        if (seen >= clip) {
            high = level
            break
        }
    }
    low = low.coerceAtMost(239)
    return PhotoToneCurve.ofSteps(low to 0, max(high, low + 16) to 255)
}

private class CurveChannel(
    val key: String,
    val labelRes: StringResource,
    val color: Color,
    val fill: Color,
    val read: (PhotoCurves) -> PhotoToneCurve,
    val write: (PhotoCurves, PhotoToneCurve) -> PhotoCurves
)

// RGB means master — the channels apply master first, then their own
// curve, exactly like the processor.
private val CurveChannels = listOf(
    CurveChannel(
        "rgb", Res.string.editor_channel_rgb,
        color = Color(0xFFF1F0F4),
        fill = Color(0x33C4C6CF),
        read = { it.master },
        write = { c, v -> c.copy(master = v) }
    ),
    CurveChannel(
        "red", Res.string.editor_channel_red,
        color = Color(0xFFFF8A80),
        fill = Color(0x38FF8A80),
        read = { it.red },
        write = { c, v -> c.copy(red = v) }
    ),
    CurveChannel(
        "green", Res.string.editor_channel_green,
        color = Color(0xFFB9F6CA),
        fill = Color(0x2EB9F6CA),
        read = { it.green },
        write = { c, v -> c.copy(green = v) }
    ),
    CurveChannel(
        "blue", Res.string.editor_channel_blue,
        color = Color(0xFF82B1FF),
        fill = Color(0x3882B1FF),
        read = { it.blue },
        write = { c, v -> c.copy(blue = v) }
    )
)

/** A preset's fixed curve; Auto's is null, read from the photo instead. */
private class CurvePreset(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val curve: PhotoToneCurve?
)

private val CurvePresets = listOf(
    CurvePreset("auto", Res.string.editor_curve_preset_auto, EditorSymbols.AutoFixHigh, null),
    CurvePreset(
        "linear", Res.string.editor_curve_preset_linear, EditorSymbols.NorthEast,
        PhotoToneCurve()
    ),
    CurvePreset(
        "soft", Res.string.editor_curve_preset_soft, EditorSymbols.Contrast,
        PhotoToneCurve.ofSteps(0 to 0, 64 to 54, 192 to 202, 255 to 255)
    ),
    CurvePreset(
        "strong", Res.string.editor_curve_preset_strong, EditorSymbols.Tonality,
        PhotoToneCurve.ofSteps(0 to 0, 64 to 40, 192 to 216, 255 to 255)
    ),
    CurvePreset(
        "fade", Res.string.editor_curve_preset_fade, EditorSymbols.BlurOn,
        PhotoToneCurve.ofSteps(0 to 34, 128 to 132, 255 to 236)
    ),
    CurvePreset(
        "brighten", Res.string.editor_curve_preset_brighten, EditorSymbols.LightMode,
        PhotoToneCurve.ofSteps(0 to 0, 112 to 146, 255 to 255)
    )
)

/**
 * How the tool spends its space. A phone's sheet stacks a small graph over a
 * preset strip; a wide sheet sets a tall graph beside steppers and a preset
 * grid; the inspector stacks those; and a short window's inspector keeps only
 * the graph, because a phone in landscape has no height for the rest.
 */
private enum class CurvesLayout(
    val graphHeight: Dp,
    val steppers: Boolean,
    val presets: Boolean,
    val presetGrid: Boolean
) {
    Sheet(170.dp, steppers = false, presets = true, presetGrid = false),
    WideSheet(320.dp, steppers = true, presets = true, presetGrid = true),
    Inspector(280.dp, steppers = true, presets = true, presetGrid = true),
    ShortInspector(140.dp, steppers = false, presets = false, presetGrid = false)
}

private val PhotoToneCurve.edited: Boolean get() = points != PhotoToneCurve.DEFAULT

/**
 * The Curves tool: a connected channel group, the In/Out readout with point
 * removal and the channel reset, the graph, then steppers and presets as the
 * layout has room. Tapping the curve adds a point and dragging it past the
 * graph removes it; each gesture is one undo step through the session draft.
 * The histogram behind the curve shows what reaches the curves, so it holds
 * still while they change and Auto always reads the same input.
 */
@Composable
internal fun CurvesToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val toolUi = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val sourceRef = state.info?.reference
    val channel = CurveChannels.firstOrNull { it.key == toolUi.curvesChannel }
        ?: CurveChannels.first()
    val channelLabel = stringResource(channel.labelRes)
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val curves = edits.finishing.curves
    val curve = channel.read(curves)
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val selected = toolUi.curvesSelection[channel.key]
        ?.takeIf { it in curve.points.indices }
    val point = selected?.let { curve.points[it] }
    var removing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    val inputKey = state.document?.let { document ->
        document.copy(
            finishing = PhotoFinishing(
                whiteBalance = document.finishing.whiteBalance,
                grade = document.finishing.grade
            )
        )
    }
    val histogram by produceState<CurveInputHistogram?>(null, inputKey) {
        val pixels = latestActions.renderCurvesInput() ?: latestState.originalPreview
        if (pixels != null) {
            value = withContext(Dispatchers.Default) { curveInputHistogram(pixels) }
        }
    }
    val channelCounts = remember(histogram, channel.key, curves.master) {
        histogram?.let {
            when (channel.key) {
                "red" -> curveRemapped(it.red, curves.master)
                "green" -> curveRemapped(it.green, curves.master)
                "blue" -> curveRemapped(it.blue, curves.master)
                else -> it.luma
            }
        }
    }

    fun select(index: Int?) {
        if (index == null) toolUi.curvesSelection.remove(channel.key)
        else toolUi.curvesSelection[channel.key] = index
    }

    fun editsForCurve(next: PhotoToneCurve): PhotoEdits? {
        val current = latestState.document?.toEdits() ?: return null
        if (channel.read(current.finishing.curves) == next) return null
        return current.copy(
            finishing = current.finishing.copy(
                curves = channel.write(current.finishing.curves, next)
            )
        )
    }

    fun applyCurve(label: String, next: PhotoToneCurve) {
        editsForCurve(next)?.let { latestActions.applyEdits(label, it) }
    }

    val adjustLabel = "Adjust $channelLabel"
    val resetChannelLabel = stringResource(Res.string.editor_reset_channel)
    val resetAllLabel = stringResource(Res.string.editor_reset_curves)
    val canResetAll = enabled && !state.interactionActive &&
        CurveChannels.any { it.read(curves).edited }
    val onResetAll = {
        latestState.document?.toEdits()?.let { current ->
            toolUi.curvesSelection.clear()
            latestActions.applyEdits(
                resetAllLabel,
                current.copy(finishing = current.finishing.copy(curves = PhotoCurves()))
            )
        }
        Unit
    }

    val inSheet = LocalPhotoToolSheetExpanded.current != null
    val toolMenu = LocalPhotoToolMenu.current.takeIf { inSheet }
    toolMenu?.apply {
        resetParameterLabel = resetAllLabel
        resetParameterTag = "editor.reset.curves.menu"
        resetParameterFooterTag = "editor.reset.curves.foot"
        resetParameterEnabled = canResetAll
        onResetParameter = onResetAll
    }
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeight = with(density) { windowSize.height.toDp() }

    @Composable
    fun body(maxWidth: Dp) {
        val layout = when {
            inSheet && maxWidth >= 600.dp -> CurvesLayout.WideSheet
            inSheet -> CurvesLayout.Sheet
            windowHeight < 480.dp -> CurvesLayout.ShortInspector
            else -> CurvesLayout.Inspector
        }
        val canEdit = enabled && !state.interactionActive
        // A side panel can be far narrower than a sheet: it stacks the
        // steppers, reduces the channel reset to its icon and drops swatches.
        val narrow = maxWidth < 320.dp
        // The inspector's ground matches the sheet's chip colour, one step up.
        val raised = if (inSheet) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerHighest
        val slab = if (inSheet) MaterialTheme.colorScheme.surfaceContainer else raised

        @Composable
        fun graphColumn(columnModifier: Modifier) {
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurveReadoutRow(
                    text = when {
                        removing -> stringResource(Res.string.editor_curve_release_to_remove)
                        point != null -> stringResource(
                            Res.string.editor_curve_in_out,
                            (point.x * 255f).roundToInt(),
                            (point.y * 255f).roundToInt()
                        )
                        else -> stringResource(Res.string.editor_curve_tap_hint)
                    },
                    warning = removing,
                    canRemove = canEdit && !dragging && selected != null &&
                        selected > 0 && selected < curve.points.lastIndex,
                    onRemove = {
                        selected?.let { index ->
                            curve.withoutPoint(index)?.let {
                                select(index - 1)
                                applyCurve(adjustLabel, it)
                            }
                        }
                    },
                    resetLabel = resetChannelLabel,
                    resetIconOnly = narrow || LocalDensity.current.fontScale > 1.3f,
                    canReset = canEdit && curve.edited,
                    onReset = {
                        select(null)
                        applyCurve(resetChannelLabel, PhotoToneCurve())
                    }
                )
                CurveGraph(
                    channel = channel,
                    channelLabel = channelLabel,
                    curve = curve,
                    curves = curves,
                    selected = selected,
                    removing = removing,
                    counts = channelCounts,
                    height = layout.graphHeight,
                    enabled = enabled,
                    interactionActive = state.interactionActive,
                    identity = sourceRef to channel.key,
                    onSelect = ::select,
                    onRemovingChange = { removing = it },
                    onDraggingChange = { dragging = it },
                    onBegin = { latestActions.beginEdit(adjustLabel) },
                    onUpdate = { next -> editsForCurve(next)?.let { latestActions.updateEdits(it) } },
                    onCommit = latestActions.commitEdit,
                    onCancel = latestActions.cancelEdit,
                    onApply = { next -> applyCurve(adjustLabel, next) }
                )
                CurveZoneLabels()
            }
        }

        @Composable
        fun sideContent(stackSteppers: Boolean) {
            if (layout.steppers) {
                CurveSteppers(
                    x = point?.x,
                    y = point?.y,
                    stacked = stackSteppers || narrow,
                    background = slab,
                    enabled = canEdit,
                    onStep = { inStep, outStep ->
                        selected?.let { index ->
                            val moved = curve.points[index]
                            applyCurve(
                                adjustLabel,
                                curve.withPointMoved(
                                    index,
                                    moved.x + inStep / 255f,
                                    moved.y + outStep / 255f
                                )
                            )
                        }
                    }
                )
            }
            if (layout.presets) {
                CurvePresetChoices(
                    curve = curve,
                    grid = layout.presetGrid,
                    background = raised,
                    enabled = canEdit,
                    onPick = { preset ->
                        val next = preset.curve
                            ?: channelCounts?.let(::curveAuto)
                            ?: PhotoToneCurve()
                        select(if (next.points.size > 2) 1 else null)
                        applyCurve(adjustLabel, next)
                    }
                )
            }
        }

        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectedButtonGroup(
                itemCount = CurveChannels.size,
                selectedIndex = CurveChannels.indexOf(channel),
                enabled = enabled,
                scrolling = LocalDensity.current.fontScale > 1.15f,
                inactiveColor = raised,
                label = { stringResource(CurveChannels[it].labelRes) },
                tag = { "editor.curves.tab.${CurveChannels[it].key}" },
                onSelect = { index ->
                    // A channel switch cancels an unfinished interaction —
                    // completed edits stay on the tool's history.
                    latestActions.cancelEdit()
                    removing = false
                    toolUi.curvesChannel = CurveChannels[index].key
                },
                leading = if (narrow) null else { index, active ->
                    CurveChannelSwatch(CurveChannels[index], active)
                },
                trailing = { index, active ->
                    CurveEditedDot(CurveChannels[index].read(curves).edited, active)
                }
            )
            if (layout == CurvesLayout.WideSheet) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    graphColumn(Modifier.weight(1f))
                    Column(
                        Modifier.width(248.dp).padding(top = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { sideContent(stackSteppers = true) }
                }
            } else {
                graphColumn(Modifier.fillMaxWidth())
                sideContent(stackSteppers = false)
            }
            if (!inSheet) {
                TextButton(
                    onClick = onResetAll,
                    enabled = canResetAll,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_CURVES_RESET_ALL)
                ) {
                    Icon(
                        imageVector = EditorSymbols.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(resetAllLabel, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    // The sheet sizes itself to this content, and a subcomposed root here
    // leaves the scaffold reading its offset before layout when tools switch.
    // The sheet spans the window less its insets, so the window answers there.
    if (inSheet) {
        body(with(density) { windowSize.width.toDp() } - 24.dp)
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth()) { body(maxWidth) }
    }
}

@Composable
private fun CurveChannelSwatch(channel: CurveChannel, active: Boolean) {
    val ring = if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
    else Color.Black.copy(alpha = 0.4f)
    val fill = if (channel.key == "rgb") {
        Brush.sweepGradient(
            listOf(Color(0xFFFF8A80), Color(0xFFB9F6CA), Color(0xFF82B1FF), Color(0xFFFF8A80))
        )
    } else {
        Brush.linearGradient(listOf(channel.color, channel.color))
    }
    Box(
        Modifier
            .size(10.dp)
            .border(1.dp, ring, CircleShape)
            .clip(CircleShape)
            .background(fill)
    )
}

@Composable
private fun CurveEditedDot(edited: Boolean, active: Boolean) {
    val color = when {
        !edited -> Color.Transparent
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun CurveReadoutRow(
    text: String,
    warning: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    resetLabel: String,
    resetIconOnly: Boolean,
    canReset: Boolean,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (warning) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(TAG_CURVES_READOUT)
        )
        IconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier.size(44.dp).testTag(TAG_CURVES_REMOVE)
        ) {
            Icon(
                imageVector = EditorSymbols.Delete,
                contentDescription = stringResource(Res.string.editor_curve_remove_point),
                modifier = Modifier.size(20.dp)
            )
        }
        if (resetIconOnly) {
            IconButton(
                onClick = onReset,
                enabled = canReset,
                modifier = Modifier.size(44.dp).testTag("editor.curves.reset")
            ) {
                Icon(
                    imageVector = EditorSymbols.RestartAlt,
                    contentDescription = resetLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            TextButton(
                onClick = onReset,
                enabled = canReset,
                modifier = Modifier.heightIn(min = 44.dp).testTag("editor.curves.reset")
            ) {
                Icon(
                    imageVector = EditorSymbols.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(resetLabel, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun CurveZoneLabels() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(
            Res.string.editor_shadows,
            Res.string.editor_midtones,
            Res.string.editor_highlights
        ).forEach { label ->
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * The graph: histogram, quarter grid, identity diagonal, the other edited
 * channels as ghosts, the selected point's guides and the curve. The field
 * owns the touch gesture and each point adds an invisible 44 dp target for
 * focus, keys and accessibility, which leaves touches to the field.
 */
@Composable
private fun CurveGraph(
    channel: CurveChannel,
    channelLabel: String,
    curve: PhotoToneCurve,
    curves: PhotoCurves,
    selected: Int?,
    removing: Boolean,
    counts: LongArray?,
    height: Dp,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    onSelect: (Int?) -> Unit,
    onRemovingChange: (Boolean) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onBegin: () -> Unit,
    onUpdate: (PhotoToneCurve) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onApply: (PhotoToneCurve) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val latestCurve by rememberUpdatedState(curve)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestInteractionActive by rememberUpdatedState(interactionActive)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestRemoving by rememberUpdatedState(onRemovingChange)
    val latestDragging by rememberUpdatedState(onDraggingChange)
    val latestBegin by rememberUpdatedState(onBegin)
    val latestUpdate by rememberUpdatedState(onUpdate)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestCancel by rememberUpdatedState(onCancel)
    val latestApply by rememberUpdatedState(onApply)
    var gestureOpen by remember { mutableStateOf(false) }
    val editorDescription = stringResource(Res.string.editor_curve_editor, channelLabel)
    val addPointLabel = stringResource(Res.string.editor_curve_add_point)
    val removePointLabel = stringResource(Res.string.editor_curve_remove_point)
    val heights = remember(counts) { counts?.let(::curveHistogramHeights) }
    val ghosts = (if (channel.key == "rgb") CurveChannels.drop(1) else CurveChannels.take(1))
        .filter { it.read(curves).edited }
        .map { it.color to it.read(curves) }

    fun canStart(): Boolean =
        latestEnabled && !latestInteractionActive && !gestureOpen

    // If the interaction ended elsewhere (param/tool switch, Apply), drop
    // the flag; leaving composition mid-gesture cancels it.
    LaunchedEffect(interactionActive) {
        if (!interactionActive) gestureOpen = false
    }
    DisposableEffect(identity) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                latestDragging(false)
                latestRemoving(false)
                latestCancel()
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            // Painted, not clipped: the corner points' targets overhang the slab.
            .background(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                RoundedCornerShape(16.dp)
            )
            .padding(10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = editorDescription
                    customActions = listOf(
                        CustomAccessibilityAction(addPointLabel) {
                            val points = latestCurve.points
                            val gap = (1 until points.size)
                                .maxByOrNull { points[it].x - points[it - 1].x }
                                ?: return@CustomAccessibilityAction false
                            val next = latestCurve.withPointInserted(
                                (points[gap].x + points[gap - 1].x) / 2f
                            ) ?: return@CustomAccessibilityAction false
                            latestSelect(gap)
                            latestApply(next)
                            true
                        }
                    )
                }
                .pointerInput(identity) {
                    val hitRadius = PointHitRadius.toPx()
                    val removeMargin = RemoveMargin.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!canStart() || size.width == 0 || size.height == 0) {
                            return@awaitEachGesture
                        }
                        down.consume()
                        val width = size.width.toFloat()
                        val heightPx = size.height.toFloat()
                        val startCurve = latestCurve
                        val downX = down.position.x / width
                        val downY = 1f - down.position.y / heightPx
                        var bestDistance = hitRadius
                        var index = -1
                        startCurve.points.forEachIndexed { i, p ->
                            val distance = hypot(
                                (p.x - downX) * width,
                                (p.y - downY) * heightPx
                            )
                            if (distance < bestDistance) {
                                bestDistance = distance
                                index = i
                            }
                        }
                        var working = startCurve
                        var began = false
                        fun begin(): Boolean {
                            if (began) return true
                            try {
                                latestBegin()
                            } catch (rejected: IllegalStateException) {
                                return false
                            }
                            began = true
                            gestureOpen = true
                            latestDragging(true)
                            return true
                        }
                        if (index < 0) {
                            val inserted = startCurve.withPointInserted(downX)
                                ?: return@awaitEachGesture
                            index = inserted.points.indices.first {
                                it >= startCurve.points.size ||
                                    inserted.points[it] != startCurve.points[it]
                            }
                            if (!begin()) return@awaitEachGesture
                            working = inserted
                            latestUpdate(inserted)
                        }
                        latestSelect(index)
                        val grabbed = working.points[index]
                        val offsetX = grabbed.x - downX
                        val offsetY = grabbed.y - downY
                        val interior = index > 0 && index < working.points.lastIndex
                        var moving = false
                        var pullingOff = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            // A tap only selects: nothing moves, and no undo
                            // step lands, until the finger leaves the slop.
                            if (!moving) {
                                val travel = (change.position - down.position).getDistance()
                                if (travel < viewConfiguration.touchSlop) continue
                                if (!begin()) break
                                moving = true
                            }
                            val pulling = interior && (
                                change.position.y < -removeMargin ||
                                    change.position.y > heightPx + removeMargin
                                )
                            if (pulling != pullingOff) {
                                pullingOff = pulling
                                latestRemoving(pulling)
                            }
                            working = working.withPointMoved(
                                index,
                                change.position.x / width + offsetX,
                                1f - change.position.y / heightPx + offsetY
                            )
                            latestUpdate(working)
                        }
                        if (pullingOff) {
                            latestRemoving(false)
                            working.withoutPoint(index)?.let {
                                working = it
                                latestSelect(index - 1)
                            }
                        }
                        if (began) {
                            gestureOpen = false
                            latestDragging(false)
                            if (working == startCurve) {
                                latestCancel()
                            } else {
                                latestUpdate(working)
                                latestCommit()
                            }
                        }
                    }
                }
                .testTag("editor.curves.editor")
        ) {
            val plotWidth = constraints.maxWidth.toFloat()
            val plotHeight = constraints.maxHeight.toFloat()
            val point = selected?.let { curve.points.getOrNull(it) }
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCurveBackdrop(heights, channel.fill, lineColor)
                        ghosts.forEach { (color, ghost) ->
                            drawCurveLine(ghost, color.copy(alpha = 0.5f), 1.5.dp.toPx())
                        }
                        if (point != null) {
                            val path = Path().apply {
                                moveTo(point.x * size.width, size.height)
                                lineTo(point.x * size.width, (1f - point.y) * size.height)
                                lineTo(0f, (1f - point.y) * size.height)
                            }
                            drawPath(
                                path,
                                channel.color.copy(alpha = 0.45f),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(2.dp.toPx(), 3.dp.toPx())
                                    )
                                )
                            )
                        }
                        drawCurveLine(curve, channel.color, 2.5.dp.toPx())
                    }
            )
            curve.points.forEachIndexed { i, p ->
                CurvePointTarget(
                    curve = curve,
                    index = i,
                    channel = channel,
                    channelLabel = channelLabel,
                    selected = i == selected,
                    fading = i == selected && removing,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight,
                    removeLabel = removePointLabel,
                    canAdjust = ::canStart,
                    onSelect = { latestSelect(it) },
                    onApply = { latestApply(it) }
                )
            }
        }
    }
}

private fun DrawScope.drawCurveBackdrop(
    heights: FloatArray?,
    fill: Color,
    lineColor: Color
) {
    val w = size.width
    val h = size.height
    if (heights != null) {
        val area = Path().apply {
            moveTo(0f, h)
            heights.forEachIndexed { bin, value ->
                lineTo((bin * 2 + 1) / 255f * w, h - value * h)
            }
            lineTo(w, h)
            close()
        }
        drawPath(area, fill)
    }
    val stroke = 1.dp.toPx()
    val grid = lineColor.copy(alpha = 0.14f)
    for (i in 1..3) {
        val f = i / 4f
        drawLine(grid, Offset(w * f, 0f), Offset(w * f, h), stroke)
        drawLine(grid, Offset(0f, h * f), Offset(w, h * f), stroke)
    }
    drawRect(lineColor.copy(alpha = 0.22f), style = Stroke(stroke))
    drawLine(
        lineColor.copy(alpha = 0.35f),
        Offset(0f, h),
        Offset(w, 0f),
        stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    )
}

private fun DrawScope.drawCurveLine(curve: PhotoToneCurve, color: Color, width: Float) {
    val spline = curve.spline
    val steps = max(2, (size.width / 3f).toInt())
    val path = Path()
    for (step in 0..steps) {
        val x = step / steps.toFloat()
        val y = (1f - spline.evaluate(x).coerceIn(0f, 1f)) * size.height
        if (step == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun CurvePointTarget(
    curve: PhotoToneCurve,
    index: Int,
    channel: CurveChannel,
    channelLabel: String,
    selected: Boolean,
    fading: Boolean,
    plotWidth: Float,
    plotHeight: Float,
    removeLabel: String,
    canAdjust: () -> Boolean,
    onSelect: (Int) -> Unit,
    onApply: (PhotoToneCurve) -> Unit
) {
    val point = curve.points[index]
    val input = (point.x * 255f).roundToInt()
    val output = (point.y * 255f).roundToInt()
    val description = when (index) {
        0 -> stringResource(Res.string.editor_curve_black_point, channelLabel, input, output)
        curve.points.lastIndex ->
            stringResource(Res.string.editor_curve_white_point, channelLabel, input, output)
        else -> stringResource(Res.string.editor_curve_point, channelLabel, index, input, output)
    }
    val targetPx = with(LocalDensity.current) { PointTarget.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val dotSize by animateDpAsState(
        if (selected) 20.dp else 12.dp,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
        label = "curvePoint"
    )
    val focusColor = MaterialTheme.colorScheme.primary
    val hollow = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x * plotWidth - targetPx / 2f).roundToInt(),
                    ((1f - point.y) * plotHeight - targetPx / 2f).roundToInt()
                )
            }
            .size(PointTarget)
            .semantics {
                contentDescription = description
                this.selected = selected
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = point.y,
                    range = 0f..1f,
                    steps = 0
                )
                setProgress { target ->
                    if (!canAdjust() || !target.isFinite()) {
                        return@setProgress false
                    }
                    onSelect(index)
                    onApply(curve.withPointMoved(index, point.x, target.coerceIn(0f, 1f)))
                    true
                }
                if (index > 0 && index < curve.points.lastIndex) {
                    customActions = listOf(
                        CustomAccessibilityAction(removeLabel) {
                            val next = curve.withoutPoint(index)
                            if (!canAdjust() || next == null) {
                                return@CustomAccessibilityAction false
                            }
                            onSelect(index - 1)
                            onApply(next)
                            true
                        }
                    )
                }
            }
            .then(
                if (focused) Modifier.border(2.dp, focusColor, CircleShape) else Modifier
            )
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !canAdjust()) {
                    return@onKeyEvent false
                }
                val step = (if (event.isShiftPressed) 10f else 1f) / 255f
                val next = when (event.key) {
                    Key.DirectionUp -> curve.withPointMoved(index, point.x, point.y + step)
                    Key.DirectionDown -> curve.withPointMoved(index, point.x, point.y - step)
                    Key.DirectionRight -> curve.withPointMoved(index, point.x + step, point.y)
                    Key.DirectionLeft -> curve.withPointMoved(index, point.x - step, point.y)
                    Key.Delete, Key.Backspace -> curve.withoutPoint(index)?.also {
                        onSelect(index - 1)
                    } ?: return@onKeyEvent false
                    else -> return@onKeyEvent false
                }
                if (event.key != Key.Delete && event.key != Key.Backspace) onSelect(index)
                onApply(next)
                true
            }
            .focusable(interactionSource = interactionSource)
            .testTag("editor.curves.point.$index"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .alpha(if (fading) 0.35f else 1f)
                .then(
                    if (selected) Modifier.shadow(4.dp, CircleShape) else Modifier
                )
                .size(dotSize)
                .clip(CircleShape)
                .background(if (selected) channel.color else hollow)
                .border(
                    width = if (selected) 3.dp else 2.dp,
                    color = if (selected) Color.White else channel.color,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun CurveSteppers(
    x: Float?,
    y: Float?,
    stacked: Boolean,
    background: Color,
    enabled: Boolean,
    onStep: (Int, Int) -> Unit
) {
    @Composable
    fun input(rowModifier: Modifier) = CurveStepper(
        name = stringResource(Res.string.editor_curve_input),
        tag = "editor.curves.stepper.input",
        value = x,
        background = background,
        enabled = enabled,
        onStep = { onStep(it, 0) },
        modifier = rowModifier
    )

    @Composable
    fun output(rowModifier: Modifier) = CurveStepper(
        name = stringResource(Res.string.editor_curve_output),
        tag = "editor.curves.stepper.output",
        value = y,
        background = background,
        enabled = enabled,
        onStep = { onStep(0, it) },
        modifier = rowModifier
    )
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            input(Modifier.fillMaxWidth())
            output(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            input(Modifier.weight(1f))
            output(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CurveStepper(
    name: String,
    tag: String,
    value: Float?,
    background: Color,
    enabled: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier
) {
    val active = enabled && value != null
    val color = if (value != null) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    @Composable
    fun step(delta: Int, symbol: String, descriptionRes: StringResource, stepTag: String) {
        val description = stringResource(descriptionRes, name)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = active, onClickLabel = description) { onStep(delta) }
                .semantics { contentDescription = description }
                .testTag(stepTag),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        step(-1, "−", Res.string.editor_curve_decrease, "$tag.minus")
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value?.let { (it * 255f).roundToInt().toString() } ?: "–",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                modifier = Modifier.testTag("$tag.value")
            )
        }
        step(1, "+", Res.string.editor_curve_increase, "$tag.plus")
    }
}

@Composable
private fun CurvePresetChoices(
    curve: PhotoToneCurve,
    grid: Boolean,
    background: Color,
    enabled: Boolean,
    onPick: (CurvePreset) -> Unit
) {
    if (grid) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CurvePresets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { preset ->
                        CurvePresetChip(preset, curve, background, enabled, onPick, Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CurvePresets.forEach { preset ->
                CurvePresetChip(preset, curve, background, enabled, onPick, Modifier)
            }
        }
    }
}

@Composable
private fun CurvePresetChip(
    preset: CurvePreset,
    curve: PhotoToneCurve,
    background: Color,
    enabled: Boolean,
    onPick: (CurvePreset) -> Unit,
    modifier: Modifier
) {
    val active = preset.curve?.points == curve.points
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (active) primary.copy(alpha = 0.08f) else background)
            .border(2.dp, if (active) primary else Color.Transparent, CircleShape)
            .clickable(enabled = enabled) { onPick(preset) }
            .semantics { selected = active }
            .padding(horizontal = 12.dp)
            .testTag("editor.curves.preset.${preset.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = preset.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(preset.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
