package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The Geometry tools' 48 dp label/value row: a label on the left, an
 * 88×40 8dp-corner field on the right showing the formatted value. The
 * whole row is the analog input — a horizontal drag scrubs [value] by
 * [step] per pixel, arrow keys step (Shift = ×10), Home/End hit the
 * [range] bounds — while the field's click opens the shared
 * [NumericEntryDialog] for exact entry.
 *
 * Semantics mirror the precision ruler: `progressBarRangeInfo` on the
 * row and a `setProgress` that commits one local step at the clamped
 * raw value. Pointer scrubs run one begin/update/commit interaction;
 * keys and SetProgress each land one apply step. The draft interlock is
 * identical to the ruler's: another control's interaction locks the row,
 * a disposed mid-gesture row cancels.
 */
@Composable
internal fun GeometryValueRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    display: String,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    rowTag: String,
    valueTag: String,
    onBeginInteraction: () -> Unit,
    onUpdateInteraction: (Float) -> Unit,
    onCommitInteraction: () -> Unit,
    onCancelInteraction: () -> Unit,
    onApplyStep: (Float) -> Unit,
    onRequestDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var gestureOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val currentValue by rememberUpdatedState(value)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestInteractionActive by rememberUpdatedState(interactionActive)
    val latestBegin by rememberUpdatedState(onBeginInteraction)
    val latestUpdate by rememberUpdatedState(onUpdateInteraction)
    val latestCommit by rememberUpdatedState(onCommitInteraction)
    val latestCancel by rememberUpdatedState(onCancelInteraction)
    val latestApply by rememberUpdatedState(onApplyStep)
    val latestDialog by rememberUpdatedState(onRequestDialog)

    fun canStart(): Boolean =
        latestEnabled && !latestInteractionActive && !gestureOpen

    LaunchedEffect(interactionActive) {
        if (!interactionActive) gestureOpen = false
    }
    DisposableEffect(identity) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                latestCancel()
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = "$label, $display"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(range.start, range.endInclusive),
                    range = range,
                    steps = 0
                )
                setProgress { target ->
                    if (!canStart() || !target.isFinite()) {
                        return@setProgress false
                    }
                    latestApply(target.coerceIn(range.start, range.endInclusive))
                    true
                }
            }
            .then(
                if (focused) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .focusRequester(focusRequester)
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
                true
            }
            .focusable(interactionSource = interactionSource)
            .pointerInput(identity) {
                var began = false
                var emitted = Float.NaN
                var startValue = 0f
                var totalDx = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        focusRequester.requestFocus()
                        if (canStart()) {
                            // `canStart` read a published snapshot —
                            // the session's nested-draft check is the
                            // live verdict; on a rejection this drag
                            // owns nothing and never reaches commit.
                            try {
                                latestBegin()
                            } catch (rejected: IllegalStateException) {
                                return@detectHorizontalDragGestures
                            }
                            began = true
                            gestureOpen = true
                            startValue = currentValue
                            totalDx = 0f
                            emitted = startValue
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
                    onHorizontalDrag = { change, dragAmount ->
                        if (!began) return@detectHorizontalDragGestures
                        change.consume()
                        totalDx += dragAmount
                        // Scrub: one step per dragged pixel from the value
                        // at gesture start — relative, never a position
                        // jump like the fixed-scale ruler.
                        val next = ((startValue + totalDx * step) / step)
                            .roundToInt() * step
                        val clamped =
                            next.coerceIn(range.start, range.endInclusive)
                        if (clamped != emitted) {
                            emitted = clamped
                            latestUpdate(clamped)
                        }
                    }
                )
            }
            .testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        // The 88×40 exact-entry field — tapping it owns a numeric-entry
        // draft; the row's drag does not reach through a click.
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp)
                )
                .clickable(
                    enabled = enabled && !interactionActive,
                    role = Role.Button,
                    onClickLabel = label
                ) { latestDialog() }
                .testTag(valueTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = 1
            )
        }
    }
}
