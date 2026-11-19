package com.dot.gallery.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_ruler_hint
import com.dot.gallery.editor.ui.generated.resources.editor_step_decrease
import com.dot.gallery.editor.ui.generated.resources.editor_step_increase
import org.jetbrains.compose.resources.stringResource

/** The floating readout pill: its bottom half rides the slab's top edge. */
private val PhotoRulerPillHeight = 34.dp
private val PhotoRulerPillOverlap = 17.dp

/**
 * The slab height a painted scale needs: the 56 dp control plus the label
 * line under its tick ladder. `EditorPrecisionRuler` paints the labels and
 * the slab's Row sizes itself with this same rule, so the two never drift
 * and the `fillMaxHeight` step buttons always have a bounded parent.
 */
internal fun rulerSlabHeight(labelLine: Dp): Dp =
    maxOf(56.dp, (56.dp - 18.dp) / 2 + 22.dp + labelLine + 2.dp)

/**
 * The single scrubber anatomy every ruler tool shares: a 48 dp lane the
 * readout pill rides in above the needle, then the capsule holding a
 * minus step, the transparent [EditorPrecisionRuler] and a plus step.
 * The capsule is 56 dp, or taller when a painted scale needs its own
 * label line ([rulerSlabHeight]).
 *
 * The pill shows [display] and is accented while [accent] holds; a tap
 * runs [onOpenEntry] (exact numeric entry). When [onResetValue] is given
 * a double tap, and an accessibility action named [resetLabel], reset the
 * value. The steps apply `value ∓ step` through [onApplyStep] and are
 * announced as "Decrease/Increase [label]".
 *
 * [track] paints the parameter's rendered band along the slab's top,
 * between the step buttons; [trackGradient] is the colour-ramp shorthand
 * for it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorRulerSlab(
    label: String,
    value: Float,
    display: String,
    accent: Boolean,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    summary: String,
    description: String,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    sliderTag: String,
    valueTag: String,
    stepKey: String,
    onOpenEntry: () -> Unit,
    onBeginInteraction: () -> Unit,
    onUpdateInteraction: (Float) -> Unit,
    onCommitInteraction: () -> Unit,
    onCancelInteraction: () -> Unit,
    onApplyStep: (Float) -> Unit,
    modifier: Modifier = Modifier,
    scale: RulerScale? = null,
    hapticAt: ((Float) -> Boolean)? = { it == 0f },
    onResetValue: (() -> Unit)? = null,
    resetLabel: String? = null,
    limitStartLabel: String? = null,
    limitEndLabel: String? = null,
    /** A lower-profile rail: 26 dp pill over a 44 dp capsule. */
    compact: Boolean = false,
    /** Painted along the slab's top, between the step buttons. */
    trackGradient: List<Color>? = null,
    /** A rendered track band — wins over [trackGradient]. */
    track: EditorSliderTrack? = null
) {
    val canAct = enabled && !interactionActive
    // A disabled rail's readout recedes with it rather than staying a
    // bright "live" value.
    val pillBackground = when {
        !enabled -> if (accent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val pillTextColor = when {
        !enabled -> (
            if (accent) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
            ).copy(alpha = 0.38f)
        accent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val latestOpen by rememberUpdatedState(onOpenEntry)
    val latestReset by rememberUpdatedState(onResetValue)
    val latestCanAct by rememberUpdatedState(canAct)
    val hintStore = LocalEditorSettingsStore.current
    val showRulerHint = hintStore != null && !hintStore.settings.rulerHintSeen
    Column(modifier = modifier.fillMaxWidth()) {
        var pillWidthPx by remember { mutableIntStateOf(0) }
        val targetFraction =
            ((value - range.start) / (range.endInclusive - range.start))
                .coerceIn(0f, 1f)
        // A parameter switch moves the pill and the needle to the new
        // value instead of snapping them; a live drag still tracks the
        // finger exactly.
        val needleAnim = remember { Animatable(targetFraction) }
        LaunchedEffect(identity, targetFraction, interactionActive) {
            if (interactionActive) {
                needleAnim.snapTo(targetFraction)
            } else {
                needleAnim.animateTo(targetFraction, tween(260))
            }
        }
        val fraction = needleAnim.value
        // A committed change gives the readout pill a quick bouncy pop;
        // a live drag keeps it still.
        val pillPulse = remember { Animatable(1f) }
        LaunchedEffect(value, interactionActive) {
            if (interactionActive) {
                pillPulse.snapTo(1f)
            } else {
                pillPulse.snapTo(1.12f)
                pillPulse.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
        // The readout pill rides the slab's top edge instead of owning a
        // whole lane above it: only half of it sits above the control.
        val pillHeight = if (compact) 26.dp else PhotoRulerPillHeight
        val pillOverlap = if (compact) 8.dp else PhotoRulerPillOverlap
        val stepWidth = if (compact) 40.dp else 48.dp
        val slabHeight = when {
            compact -> 44.dp
            scale != null -> rulerSlabHeight(
                with(LocalDensity.current) {
                    MaterialTheme.typography.labelSmall.lineHeight.toDp()
                }
            )
            else -> 56.dp
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pillHalf = with(LocalDensity.current) { pillWidthPx.toDp() / 2f }
            // The needle sits on the ruler's 12 dp-inset track between the
            // two 48 dp step buttons.
            val needleX = stepWidth + 12.dp +
                (maxWidth - stepWidth * 2f - 24.dp) * fraction
            val pillX = (needleX - pillHalf).coerceIn(0.dp, maxWidth - pillHalf * 2f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = pillOverlap)
                    // The slab grows with the painted scale's label line at
                    // large text; it is never shorter than the 56 dp control.
                    // The height is fixed (not `heightIn`) because the step
                    // buttons fill it — an unbounded parent would let them
                    // expand into the whole panel.
                    .height(slabHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .drawBehind {
                        val band = track ?: trackGradient?.let {
                            EditorSliderTrack.Gradient(it)
                        } ?: return@drawBehind
                        val inset = stepWidth.toPx() + 12.dp.toPx()
                        // The band rides directly under the tick ladder —
                        // the ruler's ticks hang from y19 (y14 on the
                        // compact rail) and stand 16 dp (10 dp) tall, so
                        // the strip starts one dp below their foot and the
                        // limit labels keep the slab's bottom line.
                        val bandTop = if (compact) 25.dp.toPx() else 36.dp.toPx()
                        val bandHeight = 4.dp.toPx()
                        val bandWidth = (size.width - inset * 2f).coerceAtLeast(0f)
                        val bandRight = inset + bandWidth
                        val corner = CornerRadius(bandHeight / 2f)
                        when (band) {
                            is EditorSliderTrack.Gradient -> drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    band.colors,
                                    startX = inset,
                                    endX = bandRight
                                ),
                                topLeft = Offset(inset, bandTop),
                                size = Size(bandWidth, bandHeight),
                                cornerRadius = corner
                            )
                            is EditorSliderTrack.Tonal -> {
                                val clip = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            inset, bandTop, bandRight,
                                            bandTop + bandHeight, corner
                                        )
                                    )
                                }
                                clipPath(clip) {
                                    drawTonalTrack(
                                        band, inset, bandTop, bandWidth, bandHeight
                                    )
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlabStepButton(
                    text = "-",
                    label = stringResource(Res.string.editor_step_decrease, label),
                    enabled = canAct,
                    width = stepWidth,
                    onClick = { onApplyStep(value - step) },
                    tag = "editor.step.minus.$stepKey"
                )
                EditorPrecisionRuler(
                    value = value,
                    range = range,
                    step = step,
                    summary = summary,
                    description = description,
                    enabled = enabled,
                    interactionActive = interactionActive,
                    identity = identity,
                    sliderTag = sliderTag,
                    onBeginInteraction = onBeginInteraction,
                    onUpdateInteraction = onUpdateInteraction,
                    onCommitInteraction = {
                        onCommitInteraction()
                        hintStore?.update { it.copy(rulerHintSeen = true) }
                    },
                    onCancelInteraction = onCancelInteraction,
                    onApplyStep = onApplyStep,
                    modifier = Modifier.weight(1f),
                    showSummary = false,
                    showLabels = false,
                    containerColor = Color.Transparent,
                    scale = scale,
                    hapticAt = hapticAt,
                    onDoubleTapReset = onResetValue,
                    needleFraction = fraction,
                    limitStartLabel = limitStartLabel,
                    limitEndLabel = limitEndLabel,
                    compact = compact
                )
                SlabStepButton(
                    text = "+",
                    label = stringResource(Res.string.editor_step_increase, label),
                    enabled = canAct,
                    width = stepWidth,
                    onClick = { onApplyStep(value + step) },
                    tag = "editor.step.plus.$stepKey"
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = pillX)
                    .height(pillHeight)
                    .graphicsLayer {
                        scaleX = pillPulse.value
                        scaleY = pillPulse.value
                    }
                    .onSizeChanged { pillWidthPx = it.width }
                    .combinedClickable(
                        enabled = canAct,
                        role = Role.Button,
                        onClickLabel = label,
                        onDoubleClick = if (onResetValue != null) {
                            { if (latestCanAct) latestReset?.invoke() }
                        } else null,
                        onClick = { if (latestCanAct) latestOpen() }
                    )
                    .then(
                        if (onResetValue != null && resetLabel != null) {
                            Modifier.semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(resetLabel) {
                                        if (latestCanAct) latestReset?.invoke()
                                        latestCanAct
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .testTag(valueTag),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        // Wrap the value, never the lane: the pill must stay
                        // a pill, not a full-width bar.
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(pillHeight / 2))
                        .background(pillBackground)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.labelMedium,
                        color = pillTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        if (showRulerHint) {
            Text(
                text = stringResource(Res.string.editor_ruler_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SlabStepButton(
    text: String,
    label: String,
    enabled: Boolean,
    width: Dp,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
