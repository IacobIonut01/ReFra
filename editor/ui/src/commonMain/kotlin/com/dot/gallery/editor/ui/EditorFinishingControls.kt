package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_param_summary
import com.dot.gallery.editor.ui.generated.resources.editor_precision_ruler
import com.dot.gallery.editor.ui.generated.resources.editor_reset_parameter
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.RestartAlt
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * One finishing parameter in display units — the model stores
 * normalized fractions/kelvin, the UI speaks the -100..100 / kelvin
 * domains. [read]/[write] map display values onto the recipe copy so
 * the ruler, numeric entry and tick labels share one coordinate space.
 */
internal class FinishingParam(
    val key: String,
    val labelRes: StringResource,
    val displayRange: ClosedFloatingPointRange<Float>,
    val step: Float,
    /** The display-domain neutral — rows reset to it, not to 0. */
    val neutral: Float,
    val signed: Boolean = true,
    val unitSuffix: String = "",
    /** A hint of what the ruler does, painted along its slab. */
    val trackGradient: List<Color>? = null,
    val read: (PhotoEdits) -> Float,
    val write: (PhotoEdits, Float) -> PhotoEdits
) {
    fun displayOf(edits: PhotoEdits): String = format(read(edits))

    fun format(value: Float): String =
        (if (signed) formatSignedCompact(value)
        else formatDecimal(value).trimEnd('0').trimEnd('.')) + unitSuffix

    /** The ruler's range, "start / end", in the parameter's display units. */
    fun rangeCaption(): String = "${format(displayRange.start)} / ${format(displayRange.endInclusive)}"
}

/**
 * The shared finishing-tool control stack — the parameter's name and
 * range over the shared ruler slab (a readout pill that opens exact entry,
 * the −/+ steps and the precision ruler), with the per-parameter reset
 * published to the context header's More menu, plus the numeric-entry
 * dialog. Used by the
 * tabbed finishing tools (White balance, Vignette, Grain): the active
 * parameter owns the ruler, every edit is one session interaction.
 */
@Composable
internal fun FinishingParamControls(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    param: FinishingParam,
    label: String,
    enabled: Boolean,
    identity: Any,
    modifier: Modifier = Modifier,
    /** Multi-control surfaces (White balance) keep visible reset icons —
     * the menu can carry only one parameter's reset. */
    publishResetToMenu: Boolean = true,
    /** Off for a bare scrubber: no name/range row, the window's endpoints
     * ride the track instead. */
    showMetaRow: Boolean = true,
    limitLabels: Boolean = false,
    compact: Boolean = false,
    /** Off when the host card already insets its content. */
    insetContent: Boolean = true
) {
    // A missing document reads neutral — the controls are disabled then.
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val owner = remember { NumericEntryOwner() }
    var dialogOpen by remember { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)

    NumericEntryOwnerEffects(owner, state, actions)

    fun editsFor(displayValue: Float): PhotoEdits? {
        val current = latestState.document?.toEdits() ?: return null
        return if (displayValue == param.read(current)) {
            // An identical entry commits as a no-op, never an empty undo.
            null
        } else {
            param.write(current, displayValue)
        }
    }

    // The compact tool surface publishes the per-parameter reset into the
    // context header's More menu; layouts without the menu — or surfaces
    // showing several parameters at once — keep the visible icon.
    val toolMenu = LocalPhotoToolMenu.current
    val resetViaMenu = toolMenu != null && publishResetToMenu
    if (resetViaMenu) toolMenu?.apply {
        resetParameterLabel = stringResource(
            Res.string.editor_reset_parameter, label
        )
        resetParameterTag = "editor.reset.${param.key}"
        resetParameterFooterTag = "editor.reset.${param.key}.foot"
        resetParameterEnabled = enabled && !state.interactionActive &&
            param.read(edits) != param.neutral
        onResetParameter = {
            latestState.document?.toEdits()?.let { current ->
                latestActions.applyEdits(
                    "Reset $label", param.write(current, param.neutral)
                )
            }
        }
    }
    // A control that never published must not clear what its host published.
    DisposableEffect(toolMenu, resetViaMenu) {
        onDispose {
            if (resetViaMenu) {
                toolMenu?.resetParameterLabel = null
                toolMenu?.onResetParameter = null
            }
        }
    }

    fun resetToNeutral() {
        latestState.document?.toEdits()?.let { current ->
            latestActions.applyEdits("Reset $label", param.write(current, param.neutral))
        }
    }
    val resetLabel = stringResource(Res.string.editor_reset_parameter, label)
    val canReset = enabled && !state.interactionActive && param.read(edits) != param.neutral
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = if (insetContent) 16.dp else 0.dp)
    ) {
        // The meta row: the parameter's name, its range caption (dropped
        // rather than clipped at large text) and, where the header menu
        // cannot carry it, the visible reset.
        if (showMetaRow) Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 8.dp)
            )
            FitOrDrop(Modifier.weight(1f)) {
                Text(
                    text = param.rangeCaption(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (!resetViaMenu) {
                WorkbenchIconButton(
                    icon = EditorSymbols.RestartAlt,
                    label = resetLabel,
                    onClick = { resetToNeutral() },
                    enabled = canReset,
                    modifier = Modifier.testTag("editor.reset.${param.key}")
                )
            }
        }
        EditorRulerSlab(
            label = label,
            value = param.read(edits),
            display = param.displayOf(edits),
            accent = param.read(edits) != param.neutral || state.interactionActive,
            range = param.displayRange,
            step = param.step,
            summary = stringResource(
                Res.string.editor_param_summary,
                label,
                param.displayOf(edits)
            ),
            description = stringResource(
                Res.string.editor_precision_ruler,
                label,
                param.displayOf(edits)
            ),
            enabled = enabled,
            interactionActive = state.interactionActive,
            identity = identity,
            sliderTag = "editor.slider.${param.key}",
            valueTag = "editor.value.${param.key}",
            stepKey = param.key,
            limitStartLabel = if (limitLabels) {
                boundaryLabel(param.displayRange.start, param.unitSuffix)
            } else null,
            limitEndLabel = if (limitLabels) {
                boundaryLabel(param.displayRange.endInclusive, param.unitSuffix)
            } else null,
            compact = compact,
            trackGradient = param.trackGradient,
            onOpenEntry = {
                if (latestState.document != null) {
                    try {
                        latestActions.beginEdit("Adjust $label")
                        dialogOpen = true
                        owner.open(latestState.info?.reference)
                    } catch (rejected: IllegalStateException) {
                        // Another interaction owns the draft; nothing opens.
                    }
                }
            },
            onBeginInteraction = {
                latestActions.beginEdit("Adjust $label")
            },
            onUpdateInteraction = { display ->
                editsFor(display)?.let { latestActions.updateEdits(it) }
            },
            onCommitInteraction = latestActions.commitEdit,
            onCancelInteraction = latestActions.cancelEdit,
            onApplyStep = { display ->
                editsFor(display.coerceIn(param.displayRange.start, param.displayRange.endInclusive))
                    ?.let { latestActions.applyEdits("Adjust $label", it) }
            },
            hapticAt = { it == param.neutral },
            onResetValue = { if (canReset) resetToNeutral() },
            resetLabel = resetLabel
        )
    }

    if (owner.showDialog && dialogOpen) {
        NumericEntryDialog(
            label = label,
            initialValue = param.read(edits),
            range = param.displayRange,
            onConfirm = { entered ->
                val current = latestState
                if (current.document != null && owner.ownsDraft &&
                    owner.sourceRef == current.info?.reference
                ) {
                    editsFor(entered)?.let { latestActions.updateEdits(it) }
                    latestActions.commitEdit()
                }
                dialogOpen = false
                owner.hide()
            },
            onDismiss = {
                dialogOpen = false
                owner.dismiss(
                    latestState.info?.reference,
                    latestActions.cancelEdit
                )
            }
        )
    }
}

/**
 * One finishing parameter chip — the same 48 dp selector cell the Light
 * tool's strip paints, used for the tab rows of the finishing tools.
 */
@Composable
internal fun FinishingChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional leading 18 dp glyph — decorative, the label names it. */
    icon: ImageVector? = null,
    /** The reference's strong selection: primary fill, on-primary label. */
    accentSelection: Boolean = false,
    /** A tighter 40 dp pill with a smaller label — the crop aspect row. */
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
            .background(
                when {
                    selected && accentSelection ->
                        MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    LocalPhotoToolSheetExpanded.current != null -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label
            ) { onClick() }
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val content = when {
            selected && accentSelection -> MaterialTheme.colorScheme.onPrimary
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp)
                )
            }
            Text(
                text = label,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = content,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * One settings row — the finishing tools list every field of their
 * recipe under the active control; tapping a row selects its tab.
 */
@Composable
internal fun FinishingSettingsRow(
    label: String,
    value: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalPhotoToolSheetExpanded.current != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                else Modifier
            )
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label
            ) { onClick() }
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** A painted-window endpoint, e.g. "0%" / "100%". */
private fun boundaryLabel(value: Float, unitSuffix: String): String =
    "${value.roundToInt()}$unitSuffix"
