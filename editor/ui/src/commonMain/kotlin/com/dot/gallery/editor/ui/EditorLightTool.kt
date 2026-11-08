package com.dot.gallery.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness5
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.contrastAmount
import com.dot.gallery.editor.core.model.contrastFactor
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_focus
import com.dot.gallery.editor.ui.generated.resources.editor_blacks
import com.dot.gallery.editor.ui.generated.resources.editor_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_next_parameter
import com.dot.gallery.editor.ui.generated.resources.editor_param_summary
import com.dot.gallery.editor.ui.generated.resources.editor_previous_parameter
import com.dot.gallery.editor.ui.generated.resources.editor_precision_ruler
import com.dot.gallery.editor.ui.generated.resources.editor_reset_parameter
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_whites
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.RestartAlt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Parameter chips and the active parameter's controls — `editor.param.*`
// names the six selector chips; the precision ruler reuses the established
// `editor.slider.<param>` / `editor.value.<param>` / `editor.reset.<param>`
// tags for whichever parameter is active, so a test asking for
// editor.slider.exposure gets the exposure ruler.
internal const val TAG_SLIDER_EXPOSURE = "editor.slider.exposure"
internal const val TAG_SLIDER_SATURATION = "editor.slider.saturation"
internal const val TAG_VALUE_EXPOSURE = "editor.value.exposure"
internal const val TAG_VALUE_SATURATION = "editor.value.saturation"
internal const val TAG_PARAM_EXPOSURE = "editor.param.exposure"
internal const val TAG_PARAM_CONTRAST = "editor.param.contrast"
internal const val TAG_PARAM_HIGHLIGHTS = "editor.param.highlights"
internal const val TAG_PARAM_SHADOWS = "editor.param.shadows"
internal const val TAG_PARAM_WHITES = "editor.param.whites"
internal const val TAG_PARAM_BLACKS = "editor.param.blacks"
internal const val TAG_SLIDER_CONTRAST = "editor.slider.contrast"
internal const val TAG_SLIDER_HIGHLIGHTS = "editor.slider.highlights"
internal const val TAG_SLIDER_SHADOWS = "editor.slider.shadows"
internal const val TAG_SLIDER_WHITES = "editor.slider.whites"
internal const val TAG_SLIDER_BLACKS = "editor.slider.blacks"
internal const val TAG_VALUE_CONTRAST = "editor.value.contrast"
internal const val TAG_VALUE_HIGHLIGHTS = "editor.value.highlights"
internal const val TAG_VALUE_SHADOWS = "editor.value.shadows"
internal const val TAG_VALUE_WHITES = "editor.value.whites"
internal const val TAG_VALUE_BLACKS = "editor.value.blacks"

/**
 * One Light parameter in display units — the model keeps EV stops for
 * exposure, a multiplicative factor for contrast and normalized [-1, 1]
 * tonals; the UI speaks EV and [-100, +100] everywhere. [read]/[write]
 * convert through that display domain so the ruler, numeric entry and
 * tick labels all share one coordinate space. [displayRange] is the
 * authored full domain the numeric dialog and model validation accept;
 * [rulerRange] is the narrower precision window the painted scale shows
 * (Exposure drags across ±2 EV while exact entry still reaches ±10).
 * [chipWidth] is the chip's minimum width — chips measure their content
 * inside that floor.
 */
internal class LightParam(
    val key: String,
    val labelRes: StringResource,
    // The legacy editor's lighting icons, one per sub-tool.
    val icon: ImageVector,
    val displayRange: ClosedFloatingPointRange<Float>,
    val rulerRange: ClosedFloatingPointRange<Float> = displayRange,
    val chipWidth: Dp,
    val step: Float,
    val unitSuffix: String = "",
    val read: (ColorAdjustments) -> Float,
    val write: (ColorAdjustments, Float) -> ColorAdjustments
) {
    fun displayOf(adjustments: ColorAdjustments): String =
        formatSignedCompact(read(adjustments)) + unitSuffix
}

// One stable descriptor list — the entries hold only resource references
// and pure lambdas, so they are created once, never per composition.
internal val LightParameters = listOf(
    LightParam(
        key = "exposure",
        labelRes = Res.string.editor_exposure,
        icon = Icons.Outlined.Brightness5,
        displayRange = -10f..10f,
        // The user's chosen precision window: five stops each way.
        rulerRange = -5f..5f,
        chipWidth = 88.dp,
        step = 0.1f,
        unitSuffix = " EV",
        read = { it.exposureStops },
        write = { adj, v -> adj.copy(exposureStops = v) }
    ),
    LightParam(
        key = "contrast",
        labelRes = Res.string.editor_contrast,
        icon = Icons.Outlined.Contrast,
        displayRange = -100f..100f,
        chipWidth = 85.dp,
        step = 1f,
        read = { contrastAmount(it.contrast) },
        // An unchanged displayed amount maps back to the exact existing
        // factor, so a round trip never dirties the recipe.
        write = { adj, v -> adj.copy(contrast = contrastFactor(v)) }
    ),
    LightParam(
        key = "highlights",
        labelRes = Res.string.editor_highlights,
        icon = Icons.Outlined.Layers,
        displayRange = -100f..100f,
        chipWidth = 93.dp,
        step = 1f,
        read = { it.highlights * 100f },
        write = { adj, v -> adj.copy(highlights = v / 100f) }
    ),
    LightParam(
        key = "shadows",
        labelRes = Res.string.editor_shadows,
        icon = Icons.Outlined.FilterDrama,
        displayRange = -100f..100f,
        chipWidth = 86.dp,
        step = 1f,
        read = { it.shadows * 100f },
        write = { adj, v -> adj.copy(shadows = v / 100f) }
    ),
    LightParam(
        key = "whites",
        labelRes = Res.string.editor_whites,
        icon = Icons.Outlined.Circle,
        displayRange = -100f..100f,
        chipWidth = 74.dp,
        step = 1f,
        read = { it.whites * 100f },
        write = { adj, v -> adj.copy(whites = v / 100f) }
    ),
    LightParam(
        key = "blacks",
        labelRes = Res.string.editor_blacks,
        icon = Icons.Outlined.RadioButtonUnchecked,
        displayRange = -100f..100f,
        chipWidth = 71.dp,
        step = 1f,
        read = { it.blacks * 100f },
        write = { adj, v -> adj.copy(blacks = v / 100f) }
    )
)

/**
 * The Light tool's V3 sheet: one scrollable 48 dp strip of parameter
 * chips (a modified dot, the label and the live value), a meta row with
 * the active parameter's visible Reset and the painted ruler window as
 * the caption, then a 56 dp slab — minus/plus step buttons flanking the
 * transparent precision ruler — with the value pill hovering over it.
 * All edits are interactions on the tool's local history — the
 * session's tool transaction owns the draft for the whole session.
 *
 * [minimal] is the focus-mode form: no chip strip, no histogram, and the
 * meta row becomes the swipeable sub-tool carousel (icon, label and
 * modified dot). Focus is entered and left by dragging the card — the
 * card carries no Focus chip.
 */
@Composable
internal fun LightToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier,
    minimal: Boolean = false
) {
    val params = LightParameters
    val labels = params.map { stringResource(it.labelRes) }
    val sourceRef = state.info?.reference
    // The active parameter is remembered per source in the shared tool
    // UI state, so the sheet and the focus chrome edit the same one; a
    // source switch starts on Exposure again.
    val toolUi = LocalPhotoToolUiState.current
    var localParamKey by rememberSaveable(sourceRef) { mutableStateOf(params.first().key) }
    val activeKey = toolUi?.lightParamKey ?: localParamKey
    val activeIndex = params.indexOfFirst { it.key == activeKey }.coerceAtLeast(0)
    val active = params[activeIndex]
    val activeLabel = labels[params.indexOf(active)]
    val adjustments = state.document?.adjustments ?: ColorAdjustments()
    val enabled = state.document != null && !state.opening

    val owner = remember { NumericEntryOwner() }
    // Which parameter the open dialog edits — captured at open so a chip
    // tap mid-dialog (which cancels the interaction anyway) can never
    // retarget an in-flight entry.
    var dialogParamKey by remember { mutableStateOf<String?>(null) }
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)

    NumericEntryOwnerEffects(owner, state, actions)

    fun selectParam(index: Int) {
        if (index == activeIndex) return
        // A parameter switch cancels an unfinished interaction — completed
        // local tool edits stay on the tool's history. Unconditional:
        // `interactionActive` is a published snapshot that can lag the
        // open draft, and a stray cancel is a no-op.
        latestActions.cancelEdit()
        val key = params[index].key
        if (toolUi != null) toolUi.lightParamKey = key else localParamKey = key
    }

    fun editsFor(param: LightParam, displayValue: Float): PhotoEdits? {
        val document = latestState.document ?: return null
        val current = document.adjustments
        // An unchanged displayed amount must not round-trip through write()
        // into a new factor — compare against the current UI value first so
        // an identical entry preserves the exact stored value.
        val next = if (displayValue == param.read(current)) {
            current
        } else {
            param.write(current, displayValue)
        }
        return document.toEdits().copy(color = next)
    }

    // The compact tool surface publishes the per-parameter reset into the
    // context header's More menu and the floating row under the header.
    // The token keeps the full card's disposal from clearing the focus
    // card's publication when the two forms swap.
    val toolMenu = LocalPhotoToolMenu.current
    val resetToken = remember { Any() }
    toolMenu?.apply {
        resetSource = resetToken
        resetParameterLabel = stringResource(
            Res.string.editor_reset_parameter, activeLabel
        )
        resetParameterTag = "editor.reset.${active.key}"
        resetParameterFooterTag = "editor.reset.${active.key}.foot"
        resetParameterEnabled =
            enabled && !state.interactionActive &&
                active.read(adjustments) != 0f
        onResetParameter = {
            editsFor(active, 0f)?.let {
                latestActions.applyEdits("Reset $activeLabel", it)
            }
        }
    }
    DisposableEffect(toolMenu, resetToken) {
        onDispose {
            if (toolMenu?.resetSource === resetToken) {
                toolMenu.resetSource = null
                toolMenu.resetParameterLabel = null
                toolMenu.resetParameterEnabled = false
                toolMenu.onResetParameter = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // The full card keeps the same 16 dp above its chip strip and
            // below its scrubber; the focus card has its own top lane.
            .then(
                if (minimal) Modifier.padding(bottom = 16.dp)
                else Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
    ) {
        // 1. The chips strip — content-measured pills: a 4 dp dot when the
        // parameter carries a non-zero edit, the label and the live value
        // in the accent color. The selected chip earns a primary outline.
        if (!minimal) Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                // The fade belongs to the scroller's viewport: declared
                // before `horizontalScroll`, it stays pinned to the two
                // edges while the chips slide underneath it. Its width is
                // the strip's own inset, so a resting first chip is not
                // dimmed.
                .softHorizontalEdges(Color(0xFF141416), width = 8.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            params.forEachIndexed { index, param ->
                val value = param.read(adjustments)
                val selected = index == activeIndex
                val chipShape = RoundedCornerShape(16.dp)
                val chipBg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(180),
                    label = "paramChipBg"
                )
                val chipBorder by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "paramChipBorder"
                )
                val chipScale by animateFloatAsState(
                    targetValue = if (selected) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "paramChipScale"
                )
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        }
                        .clip(chipShape)
                        .background(chipBg)
                        .border(1.5.dp, chipBorder, chipShape)
                        .clickable(
                            enabled = enabled,
                            role = Role.Tab,
                            onClickLabel = labels[index]
                        ) { selectParam(index) }
                        .defaultMinSize(
                            minWidth = param.chipWidth,
                            minHeight = 36.dp
                        )
                        .padding(horizontal = 10.dp)
                        .semantics { this.selected = selected }
                        .testTag("editor.param.${param.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // The legacy editor's lighting icon names each row; the
                    // dot at the far edge marks a modified value, like the
                    // reference card.
                    Icon(
                        imageVector = param.icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = param.displayOf(adjustments),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (value != 0f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                    if (value != 0f) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        // 2. Focus swaps the title row for the swipeable sub-tool
        // carousel; the title owns the full width, so the selected
        // sub-tool sits in the card's exact centre. The painted window
        // rides the scrubber's own track ends instead of a caption row.
        // The active parameter's reset lives in the floating row under
        // the header (the crop reset's slot) — the card carries no reset
        // and no focus chip.
        if (minimal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                contentAlignment = Alignment.Center
            ) {
                LightFocusCarousel(
                    params = params,
                    labels = labels,
                    activeIndex = activeIndex,
                    enabled = enabled,
                    valueOf = { it.read(adjustments) },
                    onSelect = ::selectParam
                )
            }
        }
        // 3–4. The shared ruler slab: the readout pill riding above the
        // needle (a tap opens numeric entry) over the minus step, the
        // precision ruler and the plus step.
        val summary = stringResource(
            Res.string.editor_param_summary,
            activeLabel,
            active.displayOf(adjustments)
        )
        fun applyStep(display: Float) {
            editsFor(active, display)?.let {
                latestActions.applyEdits("Adjust $activeLabel", it)
            }
        }
        EditorRulerSlab(
            label = activeLabel,
            value = active.read(adjustments),
            display = active.displayOf(adjustments),
            accent = active.read(adjustments) != 0f || state.interactionActive,
            range = active.rulerRange,
            step = active.step,
            summary = summary,
            description = stringResource(
                Res.string.editor_precision_ruler,
                activeLabel,
                active.displayOf(adjustments)
            ),
            enabled = enabled,
            interactionActive = state.interactionActive,
            identity = sourceRef to active.key,
            sliderTag = "editor.slider.${active.key}",
            valueTag = "editor.value.${active.key}",
            stepKey = active.key,
            limitStartLabel =
                formatSignedCompact(active.rulerRange.start) + active.unitSuffix,
            limitEndLabel =
                formatSignedCompact(active.rulerRange.endInclusive) + active.unitSuffix,
            onOpenEntry = {
                if (latestState.document != null) {
                    try {
                        latestActions.beginEdit("Adjust $activeLabel")
                        dialogParamKey = active.key
                        owner.open(latestState.info?.reference)
                    } catch (rejected: IllegalStateException) {
                        // Another interaction owns the draft; nothing opens.
                    }
                }
            },
            onBeginInteraction = { latestActions.beginEdit("Adjust $activeLabel") },
            onUpdateInteraction = { display ->
                editsFor(active, display)?.let { latestActions.updateEdits(it) }
            },
            onCommitInteraction = latestActions.commitEdit,
            onCancelInteraction = latestActions.cancelEdit,
            onApplyStep = { applyStep(it) }
        )
        // No inline histogram here: the pill's panel is the one histogram
        // surface (D17). Duplicating it in the card was the largest block
        // of dead space on the phone.
    }

    if (owner.showDialog) {
        val dialogParam = params.firstOrNull { it.key == dialogParamKey }
            ?: active
        val dialogLabel = labels[params.indexOf(dialogParam)]
        NumericEntryDialog(
            label = dialogLabel,
            initialValue = dialogParam.read(adjustments),
            range = dialogParam.displayRange,
            onConfirm = { entered ->
                val current = latestState
                // A source switch or an externally-ended draft must not let
                // a stale confirm edit the new document — only close then.
                // `ownsDraft` + the source ref are the ownership proof —
                // `interactionActive` is only a published snapshot and
                // can lag the open draft, which would skip the commit
                // and leave the draft orphaned.
                if (current.document != null && owner.ownsDraft &&
                    owner.sourceRef == current.info?.reference
                ) {
                    // The open draft becomes the typed value — an identical
                    // entry commits as a no-op, never an empty undo step.
                    editsFor(dialogParam, entered)
                        ?.let { latestActions.updateEdits(it) }
                    latestActions.commitEdit()
                }
                owner.hide()
            },
            onDismiss = {
                owner.dismiss(
                    latestState.info?.reference,
                    latestActions.cancelEdit
                )
            }
        )
    }
}

/**
 * The 56 dp fixed-scale precision ruler — the Light tool's actual input,
 * not a decorative graph. The scale paints min→max across a 12 dp-inset
 * track with a 41-tick ladder (major every 10, mid every 5) and five
 * quarter labels; the needle moves to the live fraction. Pointer down and
 * drag map x directly onto the track, quantized to [step]; arrow keys
 * (Shift = 10 steps) and Home/End keep exact-delta semantics and
 * `setProgress` clamps the raw finite value, committing one interaction.
 *
 * Pointer coroutines key on [identity] (source + parameter), never on the
 * live value — a recipe update cannot restart its own drag. Read-through
 * state comes via `rememberUpdatedState` for the same reason.
 */
@Composable
internal fun EditorPrecisionRuler(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    summary: String,
    description: String,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    sliderTag: String,
    onBeginInteraction: () -> Unit,
    onUpdateInteraction: (Float) -> Unit,
    onCommitInteraction: () -> Unit,
    onCancelInteraction: () -> Unit,
    onApplyStep: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showSummary: Boolean = true,
    // Labels under the tick ladder — off when the ruler sits inside a
    // slab whose caption row (the meta row above) owns the window text.
    showLabels: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    // Optional value-anchored scale (the crop tool's degree ladder); null
    // keeps the fixed 41-tick ladder with quarter labels.
    scale: RulerScale? = null,
    // When set, only values it accepts tick the haptic (0 and majors).
    hapticAt: ((Float) -> Boolean)? = null,
    // A soft accent halo behind the needle — every scrubber glows the
    // way the crop reference does.
    needleGlow: Boolean = true,
    // Where the needle is painted, when the caller animates it across a
    // parameter switch; the true value still drives every input.
    needleFraction: Float? = null,
    // The painted window's endpoints, drawn at the track's own ends.
    limitStartLabel: String? = null,
    limitEndLabel: String? = null,
    // A lower-profile rail for secondary surfaces (the Looks intensity):
    // a 44 dp slab, shorter ticks and a 16 dp needle.
    compact: Boolean = false
) {
    var gestureOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()
    // Every path that lands a new quantized step — drag, tap, keys,
    // semantics — ticks the same handle-move haptic.
    val haptics = LocalHapticFeedback.current
    val latestHapticAt by rememberUpdatedState(hapticAt)
    fun stepHaptic(at: Float = Float.NaN) {
        val gate = latestHapticAt
        if (gate == null || (!at.isNaN() && gate(at))) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val currentValue by rememberUpdatedState(value)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestInteractionActive by rememberUpdatedState(interactionActive)
    val latestBegin by rememberUpdatedState(onBeginInteraction)
    val latestUpdate by rememberUpdatedState(onUpdateInteraction)
    val latestCommit by rememberUpdatedState(onCommitInteraction)
    val latestCancel by rememberUpdatedState(onCancelInteraction)
    val latestApply by rememberUpdatedState(onApplyStep)

    // A disabled ruler paints as inactive: ticks, labels, needle and its
    // glow recede to the 38 % disabled ink instead of reading as live.
    val contentAlpha = if (enabled) 1f else 0.38f
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = contentAlpha)
    val majorColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = contentAlpha)
    val needleColor = MaterialTheme.colorScheme.primary
        .copy(alpha = contentAlpha)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = contentAlpha)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
            .copy(alpha = contentAlpha)
    )

    // Another interaction (a dialog, the other param's drag) locks the
    // ruler — but never while it owns the gesture.
    fun canStart(): Boolean =
        latestEnabled && !latestInteractionActive && !gestureOpen

    // Vertical metrics follow the approved p-light frame at ordinary
    // type — a 56 dp slab with the summary at y4, ticks at y20, the
    // 3×20 needle at y18 and labels at y42 — and keep that rhythm as the
    // actual labelSmall line height grows, so 2× text spaces ticks/labels
    // apart instead of clipping them into the summary. The 2 dp reclaimed
    // from the label line is its descender allowance: the numeric-only
    // labels never use it. Without labels the tick ladder centres in the
    // 56 dp slab instead.
    val labelLineHeight =
        with(LocalDensity.current) { labelStyle.lineHeight.toDp() }
    // A painted scale draws its own labels on the ladder's second line,
    // so the slab reserves that line even when the caller hides the
    // five-value label row (the crop rulers). Without it the labels are
    // clipped by the slab at large text. `rulerSlabHeight` is the one
    // place that knows this rule — the slab uses it for its own height.
    val rulerHeight =
        if (compact) {
            44.dp
        } else if (showLabels) {
            maxOf(
                56.dp,
                4.dp + (if (showSummary) labelLineHeight else 0.dp) +
                    22.dp + labelLineHeight - 2.dp
            )
        } else if (scale != null) {
            rulerSlabHeight(labelLineHeight)
        } else {
            56.dp
        }
    val tickTopDp =
        if (compact) {
            // A 16 dp needle centred in the 44 dp rail.
            (44.dp - 16.dp) / 2
        } else if (showLabels) {
            4.dp + if (showSummary) labelLineHeight else 0.dp
        } else if (scale != null) {
            // The ladder keeps its 56 dp position; only the label line
            // below it grows the slab.
            (56.dp - 18.dp) / 2
        } else {
            (rulerHeight - 18.dp) / 2
        }
    val labelTopDp = tickTopDp + 22.dp

    // x → the track's quantized value: the fixed scale maps position to
    // range directly (12 dp insets), snapped to the parameter's step.
    fun quantizedAt(x: Float, widthPx: Float, insetPx: Float): Float {
        val track = (widthPx - 2f * insetPx).coerceAtLeast(1f)
        val fraction = ((x - insetPx) / track).coerceIn(0f, 1f)
        val raw = range.start +
            fraction * (range.endInclusive - range.start)
        val snapped = ((raw / step).roundToInt() * step)
            .coerceIn(range.start, range.endInclusive)
        // The zero detent: at 0.1° steps an exact 0 sits inside a third of
        // a pixel on a phone row, so a drag could leave 0.0 and never get
        // back. A 1 % window of the range returns the neutral position.
        val zeroWindow = max(step, (range.endInclusive - range.start) * 0.01f)
        return if (abs(snapped) <= zeroWindow) 0f else snapped
    }

    // If the interaction ended through a path other than our own
    // commit/cancel (param switch, tool switch, Apply), drop the flag.
    LaunchedEffect(interactionActive) {
        if (!interactionActive) gestureOpen = false
    }
    // Leaving composition mid-gesture (param/source switch, layout flip)
    // cancels the interaction — an open gesture must never outlive the
    // ruler that owns it.
    DisposableEffect(identity) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                latestCancel()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rulerHeight)
            .semantics {
                contentDescription = description
                // The inactive rail also speaks disabled; its SetProgress
                // refuses below so a semantics caller cannot move it.
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(range.start, range.endInclusive),
                    range = range,
                    steps = 0
                )
                // Semantics SetProgress speaks values in [range], matching
                // Slider — clamp the raw value (a typed 0.73 stays 0.73,
                // never a forced 0.7 step), commit one interaction.
                setProgress { target ->
                    if (!canStart() || !target.isFinite()) {
                        return@setProgress false
                    }
                    latestApply(target.coerceIn(range.start, range.endInclusive))
                    stepHaptic(target.coerceIn(range.start, range.endInclusive))
                    true
                }
            }
            // No focus ring: the ruler is focused on every press so arrow
            // keys work, and painting a ring there flashed a blue outline
            // around the scrubber on every drag.
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .focusRequester(focusRequester)
            // Key handling sits above the focus target so arrow keys reach
            // it; every step preserves the exact current value plus a delta
            // — a typed 0.73 steps to 0.83, not 0.8.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !canStart()) {
                    return@onKeyEvent false
                }
                val multiplier = if (event.isShiftPressed) 10f else 1f
                val target = when (event.key) {
                    Key.DirectionLeft -> currentValue - step * multiplier
                    Key.DirectionRight -> currentValue + step * multiplier
                    Key.MoveHome -> range.start
                    Key.MoveEnd -> range.endInclusive
                    else -> return@onKeyEvent false
                }
                latestApply(target.coerceIn(range.start, range.endInclusive))
                stepHaptic(target.coerceIn(range.start, range.endInclusive))
                true
            }
            .focusable(interactionSource = interactionSource)
            .pointerInput(identity) {
                val insetPx = 12.dp.toPx()
                detectTapGestures(
                    onPress = { focusRequester.requestFocus() },
                    onTap = { offset ->
                        if (!canStart()) return@detectTapGestures
                        val tapped = quantizedAt(
                            offset.x, size.width.toFloat(), insetPx
                        )
                        latestApply(tapped)
                        stepHaptic(tapped)
                    }
                )
            }
            .pointerInput(identity) {
                val insetPx = 12.dp.toPx()
                var emitted = Float.NaN
                var began = false
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        focusRequester.requestFocus()
                        if (canStart()) {
                            // `canStart` read a published snapshot —
                            // the session's nested-draft check is the
                            // live verdict; a rejected begin owns
                            // nothing and never reaches commit.
                            try {
                                latestBegin()
                            } catch (rejected: IllegalStateException) {
                                return@detectHorizontalDragGestures
                            }
                            began = true
                            gestureOpen = true
                            emitted = quantizedAt(
                                offset.x, size.width.toFloat(), insetPx
                            )
                            latestUpdate(emitted)
                        }
                    },
                    onDragEnd = {
                        if (began) {
                            began = false
                            gestureOpen = false
                            latestCommit()
                        }
                    },
                    onDragCancel = {
                        if (began) {
                            began = false
                            gestureOpen = false
                            latestCancel()
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (!began) return@detectHorizontalDragGestures
                        change.consume()
                        val next = quantizedAt(
                            change.position.x, size.width.toFloat(), insetPx
                        )
                        if (next != emitted) {
                            val previous = emitted
                            emitted = next
                            // A gated haptic fires when the drag lands on
                            // or crosses an accepted value.
                            if (hapticAt == null) {
                                stepHaptic()
                            } else if (scale != null) {
                                scale.crossedHapticValue(previous, next, hapticAt)
                                    ?.let { stepHaptic(it) }
                            } else {
                                stepHaptic(next)
                            }
                            latestUpdate(next)
                        }
                    }
                )
            }
            .drawBehind {
                val tickTop = tickTopDp.toPx()
                val labelTop = labelTopDp.toPx()
                val trackStart = 12.dp.toPx()
                val trackEnd = size.width.toFloat() - 12.dp.toPx()
                fun xFor(fraction: Float) =
                    trackStart + (trackEnd - trackStart) * fraction
                if (scale != null) {
                    drawRulerScale(
                        scale, range, tickTop, labelTop, trackStart, trackEnd,
                        majorColor, tickColor, textMeasurer, labelStyle,
                        drawLabels = showLabels,
                        accentColor = needleColor
                    )
                }
                // The fixed 41-tick ladder: major every 10, mid every 5,
                // minor otherwise — shorter on the compact rail.
                if (scale == null) for (i in 0..40) {
                    val x = xFor(i / 40f)
                    val heightDp = when {
                        i % 10 == 0 -> if (compact) 10.dp else 16.dp
                        i % 5 == 0 -> if (compact) 7.dp else 11.dp
                        else -> if (compact) 4.dp else 7.dp
                    }
                    drawRoundRect(
                        color = if (i % 10 == 0) majorColor else tickColor,
                        topLeft = Offset(x - 1.dp.toPx(), tickTop),
                        size = Size(2.dp.toPx(), heightDp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
                // Five labels at the quarter fractions — minimum, quarter,
                // midpoint, three-quarter, maximum — endpoint-clamped so
                // the edge labels never leave the control. Slab callers
                // hide them: the meta row above owns the window text.
                if (scale == null && showLabels) for (i in 0..4) {
                    val fraction = i / 4f
                    val labelValue = range.start +
                        fraction * (range.endInclusive - range.start)
                    val layout = textMeasurer.measure(
                        formatSignedCompact(labelValue),
                        style = labelStyle
                    )
                    val labelX = (xFor(fraction) - layout.size.width / 2f)
                        .coerceIn(
                            0f,
                            (size.width - layout.size.width)
                                .coerceAtLeast(0f)
                        )
                    drawText(layout, topLeft = Offset(labelX, labelTop))
                }
                // The limits ride the track's own ends, not a caption row:
                // the painted window stays attached to the scrubber.
                if (limitStartLabel != null && limitEndLabel != null) {
                    val startLayout = textMeasurer.measure(limitStartLabel, style = labelStyle)
                    val endLayout = textMeasurer.measure(limitEndLabel, style = labelStyle)
                    val limitY = size.height - startLayout.size.height
                    drawText(startLayout, topLeft = Offset(trackStart, limitY))
                    drawText(
                        endLayout,
                        topLeft = Offset(trackEnd - endLayout.size.width, limitY)
                    )
                }
                // The needle travels the live fraction, parked just above
                // the tick row and always taller than the scale's
                // checkpoint lines (16 dp on the compact rail). A caller
                // can hand in the fraction to animate it while inputs
                // still read the value.
                val fraction = (needleFraction ?: (
                    (value - range.start) /
                        (range.endInclusive - range.start)
                    )).coerceIn(0f, 1f)
                val needleX = xFor(fraction)
                val needleHeight = if (compact) 16.dp else 22.dp
                val needleTop = if (compact) 2.dp else 3.dp
                if (needleGlow && enabled) {
                    // A radial accent halo, then the solid bar: the
                    // reference's glowing selected value.
                    val glowCenter = Offset(
                        needleX,
                        tickTop + needleHeight.toPx() / 2f
                    )
                    val glowRadius = if (compact) 12.dp.toPx() else 16.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                needleColor.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = glowCenter
                    )
                }
                drawRoundRect(
                    color = needleColor,
                    topLeft = Offset(
                        needleX - 1.5.dp.toPx(),
                        tickTop - needleTop.toPx()
                    ),
                    size = Size(3.dp.toPx(), needleHeight.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
            .testTag(sliderTag)
    ) {
        if (showSummary) Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 4.dp)
        )
    }
}

/** The focus card's carousel title and its edge hints. */
internal const val TAG_FOCUS_CAROUSEL = "editor.focus.carousel"
internal const val TAG_FOCUS_PARAM_LABEL = "editor.focus.param"
internal const val TAG_FOCUS_PREV = "editor.focus.prev"
internal const val TAG_FOCUS_NEXT = "editor.focus.next"

/**
 * The focus card's sub-tool title as a pager: the active parameter (icon,
 * label, modified dot) sits centred, a horizontal swipe slides the
 * neighbouring sub-tools through like a carousel, and the page it settles
 * on selects that sub-tool. The neighbours' icons rest at the edges as
 * the swipe cue and a tap on one steps by a single sub-tool.
 */
@Composable
private fun LightFocusCarousel(
    params: List<LightParam>,
    labels: List<String>,
    activeIndex: Int,
    enabled: Boolean,
    valueOf: (LightParam) -> Float,
    onSelect: (Int) -> Unit
) {
    val pager = rememberPagerState(pageCount = { params.size })
    // Selecting from elsewhere (a hint tap, or the key restored with the
    // sheet) moves the pager; a swipe that settles reports its page back.
    LaunchedEffect(activeIndex) {
        if (!pager.isScrollInProgress && pager.currentPage != activeIndex) {
            pager.animateScrollToPage(activeIndex)
        }
    }
    LaunchedEffect(pager.currentPage, pager.isScrollInProgress) {
        if (!pager.isScrollInProgress && pager.currentPage != activeIndex) {
            onSelect(pager.currentPage)
        }
    }
    val hintAlpha by animateFloatAsState(
        targetValue = if (pager.isScrollInProgress) 0f else 1f,
        label = "carouselHint"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(TAG_FOCUS_CAROUSEL)
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = enabled,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val param = params[index]
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (valueOf(param) != 0f) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = param.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = if (index == activeIndex) {
                        Modifier.testTag(TAG_FOCUS_PARAM_LABEL)
                    } else {
                        Modifier
                    }
                )
            }
        }
        // Edge hints: the neighbouring sub-tools, faded, tappable.
        if (activeIndex > 0) {
            CarouselHint(
                param = params[activeIndex - 1],
                description = stringResource(
                    Res.string.editor_previous_parameter, labels[activeIndex - 1]
                ),
                enabled = enabled,
                alpha = hintAlpha,
                tag = TAG_FOCUS_PREV,
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = { onSelect(activeIndex - 1) }
            )
        }
        if (activeIndex < params.lastIndex) {
            CarouselHint(
                param = params[activeIndex + 1],
                description = stringResource(
                    Res.string.editor_next_parameter, labels[activeIndex + 1]
                ),
                enabled = enabled,
                alpha = hintAlpha,
                tag = TAG_FOCUS_NEXT,
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { onSelect(activeIndex + 1) }
            )
        }
    }
}

@Composable
private fun CarouselHint(
    param: LightParam,
    description: String,
    enabled: Boolean,
    alpha: Float,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .clickable(
                enabled = enabled && alpha > 0.5f,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = param.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Places [content] in the given width at [alignment], or leaves the space
 * empty when it does not fit: secondary captions (the ruler's range, the
 * histogram's zone names) are dropped at large font scales rather than
 * clipped mid-word.
 */
@Composable
internal fun FitOrDrop(
    modifier: Modifier,
    alignment: Alignment.Horizontal = Alignment.End,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        }
        val fits = placeables.all { it.width <= constraints.maxWidth }
        val height = if (fits) placeables.maxOfOrNull { it.height } ?: 0 else 0
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            if (fits) placeables.forEach {
                it.place(
                    alignment.align(it.width, constraints.maxWidth, layoutDirection),
                    (height - it.height) / 2
                )
            }
        }
    }
}
