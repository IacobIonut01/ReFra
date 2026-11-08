package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.offset
import com.dot.gallery.editor.ui.icons.Crop
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_crop_four_five
import com.dot.gallery.editor.ui.generated.resources.editor_crop_free
import com.dot.gallery.editor.ui.generated.resources.editor_crop_guided_count
import com.dot.gallery.editor.ui.generated.resources.editor_crop_guided_hint
import com.dot.gallery.editor.ui.generated.resources.editor_crop_guided_upright
import com.dot.gallery.editor.ui.generated.resources.editor_crop_guides_clear
import com.dot.gallery.editor.ui.generated.resources.editor_crop_original
import com.dot.gallery.editor.ui.generated.resources.editor_crop_ratio_desc
import com.dot.gallery.editor.ui.generated.resources.editor_crop_reset
import com.dot.gallery.editor.ui.generated.resources.editor_crop_reset_value
import com.dot.gallery.editor.ui.generated.resources.editor_crop_output
import com.dot.gallery.editor.ui.generated.resources.editor_crop_square
import com.dot.gallery.editor.ui.generated.resources.editor_crop_swap_to_landscape
import com.dot.gallery.editor.ui.generated.resources.editor_crop_swap_to_portrait
import com.dot.gallery.editor.ui.generated.resources.editor_crop_value_hint
import com.dot.gallery.editor.ui.generated.resources.editor_flip_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_flip_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_perspective_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_precision_ruler
import com.dot.gallery.editor.ui.generated.resources.editor_reset_crop
import com.dot.gallery.editor.ui.generated.resources.editor_rotate_left
import com.dot.gallery.editor.ui.generated.resources.editor_straighten
import com.dot.gallery.editor.ui.icons.AspectSwap
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Flip
import com.dot.gallery.editor.ui.icons.RestartAlt
import com.dot.gallery.editor.ui.icons.RotateLeft
import com.dot.gallery.editor.ui.icons.StraightGuide
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CROP_FREE = "editor.crop.free"
internal const val TAG_CROP_ORIGINAL = "editor.crop.original"
internal const val TAG_CROP_1_1 = "editor.crop.1x1"
internal const val TAG_CROP_4_5 = "editor.crop.4x5"
internal const val TAG_CROP_3_2 = "editor.crop.3x2"
internal const val TAG_CROP_4_3 = "editor.crop.4x3"
internal const val TAG_CROP_16_9 = "editor.crop.16x9"
internal const val TAG_CROP_ORIENTATION = "editor.crop.orientation"
internal const val TAG_CROP_ASPECT = "editor.crop.aspect"
internal const val TAG_CROP_AXIS = "editor.crop.axis"
internal const val TAG_STRAIGHTEN = "editor.straighten"
internal const val TAG_STRAIGHTEN_VALUE = "editor.straighten.value"
internal const val TAG_CROP_TAB_STRAIGHTEN = "editor.crop.tab.straighten"
internal const val TAG_CROP_TAB_VERTICAL = "editor.crop.tab.vertical"
internal const val TAG_CROP_TAB_HORIZONTAL = "editor.crop.tab.horizontal"
internal const val TAG_ROTATE_LEFT = "editor.rotateLeft"
internal const val TAG_FLIP_H = "editor.flipH"
internal const val TAG_FLIP_V = "editor.flipV"
internal const val TAG_RESET_CROP = "editor.resetCrop"

/** The floating card's foot reset — distinct from the inline row target. */
internal const val TAG_RESET_CROP_FOOTER = "editor.reset.crop"
internal const val TAG_CROP_OUTPUT = "editor.crop.output"
internal const val TAG_CROP_GUIDES_TOGGLE = "editor.crop.guides"
internal const val TAG_CROP_GUIDES_CLEAR = "editor.crop.guides.clear"

/** Rulers: straighten ±45° (0.1° steps), keystones ±50 (whole steps). */
private val StraightenScale = RulerScale(minor = 2.5f, mid = 5f, major = 5f, labelEvery = 15f)
private val KeystoneScale = RulerScale(minor = 2.5f, mid = 5f, major = 10f, labelEvery = 25f)

internal fun cropRulerRange(tab: CropTab) =
    if (tab == CropTab.Straighten) -45f..45f else -50f..50f

internal fun cropRulerStep(tab: CropTab) = if (tab == CropTab.Straighten) 0.1f else 1f

internal fun formatCropValue(tab: CropTab, v: Float) =
    if (tab == CropTab.Straighten) formatDegrees(v) else formatSignedCompact(v.roundToInt().toFloat())

/**
 * Crop & straighten — crop, straighten and keystone in one tool. Collapsed:
 * the aspect strip with its orientation swap, Rotate · Flip · the real
 * output size · Reset, the shared ruler slab and the Straighten ·
 * Vertical · Horizontal tabs. Expanded adds Flip vertical and guided
 * upright. Every edit runs through [CropToolOps], so
 * the crop never shows blank edges and each gesture is one undo step.
 *
 * [initialTab] opens a specific ruler — the legacy Perspective tool id
 * routes here on the Vertical tab.
 */
@Composable
internal fun CropToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier,
    initialTab: CropTab? = null
) {
    val uiState = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val ops = rememberCropToolOps(state, actions, uiState)
    val document = state.document
    val upright = state.info?.uprightSize
    val enabled = document != null && !state.opening && upright != null
    val canEdit = enabled && !state.interactionActive
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)

    LaunchedEffect(state.info?.reference, document != null) {
        if (document != null && upright != null) uiState.seedPreset(document.geometry, upright)
    }
    LaunchedEffect(initialTab) {
        if (initialTab != null) uiState.cropTab = initialTab
    }
    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) ops.interactionEnded()
    }

    // The header's More menu carries the whole-geometry reset.
    val toolMenu = LocalPhotoToolMenu.current
    val resetCropLabel = stringResource(Res.string.editor_reset_crop)
    toolMenu?.apply {
        resetParameterLabel = resetCropLabel
        resetParameterTag = "$TAG_RESET_CROP.menu"
        resetParameterFooterTag = TAG_RESET_CROP_FOOTER
        resetParameterEnabled = canEdit && ops.canReset
        onResetParameter = { ops.reset() }
    }
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }

    val tab = uiState.cropTab
    val tabLabels = mapOf(
        CropTab.Straighten to stringResource(Res.string.editor_straighten),
        CropTab.Vertical to stringResource(Res.string.editor_perspective_vertical),
        CropTab.Horizontal to stringResource(Res.string.editor_perspective_horizontal)
    )
    val label = tabLabels.getValue(tab)
    val value = ops.valueOf(tab)
    val range = cropRulerRange(tab)
    val step = cropRulerStep(tab)
    val display = formatCropValue(tab, value)

    val owner = remember { NumericEntryOwner() }
    NumericEntryOwnerEffects(owner, state, actions)
    var dialogTab by remember { mutableStateOf<CropTab?>(null) }

    fun openDialog() {
        if (latestState.document == null) return
        try {
            ops.beginValue("Adjust $label")
        } catch (rejected: IllegalStateException) {
            return
        }
        dialogTab = tab
        owner.open(latestState.info?.reference)
    }

    PhotoParameterControls(
        modifier = modifier,
        controls = {
            Column(Modifier.fillMaxWidth()) {
                // The two choices (aspect and axis) are dropdown pills on
                // one row, leaving the row below to the scrubber.
                CropPillsRow(
                    ops = ops,
                    tab = tab,
                    tabs = tabLabels,
                    enabled = canEdit,
                    canEdit = canEdit,
                    onSelectTab = { next ->
                        if (next != uiState.cropTab) {
                            if (latestState.interactionActive) latestActions.cancelEdit()
                            uiState.cropTab = next
                        }
                    }
                )
                CropControlRow(
                    ops = ops,
                    tab = tab,
                    label = label,
                    value = value,
                    display = display,
                    range = range,
                    step = step,
                    enabled = enabled,
                    interactionActive = state.interactionActive,
                    identity = state.info?.reference to tab,
                    onOpenEntry = { openDialog() },
                    onBegin = { ops.beginValue("Adjust $label") },
                    onUpdate = { ops.updateValue(tab, it) },
                    onCommit = { ops.endValue(commit = true) },
                    onCancel = { ops.endValue(commit = false) },
                    onStep = { ops.stepValue("Adjust $label", tab, it) }
                )
            }
        },
        tabs = {
            // The phone card's axis chip owns this switch; the wide
            // inspector keeps the three labelled tabs.
            if (LocalPhotoToolSheetExpanded.current != null) return@PhotoParameterControls
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CropTab.entries.forEach { t ->
                        FinishingChip(
                            label = tabLabels.getValue(t),
                            selected = t == tab,
                            enabled = enabled,
                            tag = when (t) {
                                CropTab.Straighten -> TAG_CROP_TAB_STRAIGHTEN
                                CropTab.Vertical -> TAG_CROP_TAB_VERTICAL
                                CropTab.Horizontal -> TAG_CROP_TAB_HORIZONTAL
                            },
                            onClick = {
                                if (t != uiState.cropTab) {
                                    if (latestState.interactionActive) latestActions.cancelEdit()
                                    uiState.cropTab = t
                                }
                            }
                        )
                    }
                }
            }
        },
        details = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropAction(
                    EditorSymbols.Flip, stringResource(Res.string.editor_flip_vertical),
                    canEdit, TAG_FLIP_V, Modifier.fillMaxWidth(), rotateIcon = true
                ) { ops.flipVertical() }
                GuidedUprightRow(ops = ops, enabled = canEdit)
            }
        }
    )

    val openTab = dialogTab
    if (openTab != null && owner.showDialog) {
        NumericEntryDialog(
            label = tabLabels.getValue(openTab),
            initialValue = ops.valueOf(openTab),
            range = cropRulerRange(openTab),
            onConfirm = { entered ->
                if (latestState.document != null && owner.ownsDraft &&
                    owner.sourceRef == latestState.info?.reference
                ) {
                    ops.updateValue(openTab, entered)
                    ops.endValue(commit = true)
                }
                owner.hide()
                dialogTab = null
            },
            onDismiss = {
                dialogTab = null
                ops.interactionEnded()
                owner.dismiss(latestState.info?.reference, latestActions.cancelEdit)
            }
        )
    }
}

/**
 * Mouse-wheel scrubbing over the ruler: one notch is one step; a burst of
 * notches is one interaction (one undo step) that commits 400 ms after the
 * last notch.
 */
@Composable
private fun CropWheelScrub(
    ops: CropToolOps,
    tab: CropTab,
    label: String,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    var open by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(0f) }
    var commitJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(tab) {
        onDispose {
            if (open) {
                commitJob?.cancel()
                open = false
                ops.endValue(commit = true)
            }
        }
    }
    Box(
        Modifier.pointerInput(tab) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val notches = change.scrollDelta.y + change.scrollDelta.x
                    if (notches == 0f) continue
                    if (!open) {
                        if (!latestEnabled) continue
                        try {
                            ops.beginValue("Adjust $label")
                        } catch (rejected: IllegalStateException) {
                            continue
                        }
                        open = true
                        value = ops.valueOf(tab)
                    }
                    change.consume()
                    val range = cropRulerRange(tab)
                    val step = cropRulerStep(tab)
                    value = (((value - notches * step) / step).roundToInt() * step)
                        .coerceIn(range.start, range.endInclusive)
                    ops.updateValue(tab, value)
                    commitJob?.cancel()
                    commitJob = scope.launch {
                        delay(400)
                        if (open) {
                            open = false
                            ops.endValue(commit = true)
                        }
                    }
                }
            }
        }
    ) { content() }
}

/**
 * The aspect choice as a split dropdown pill: the current ratio opens the
 * preset menu, and the orientation swap is the pill's attached trailing
 * segment. The presets no longer own a whole card row, so the scrubber
 * below keeps the card's full width.
 */
@Composable
private fun CropAspectPill(ops: CropToolOps, enabled: Boolean) {
    val ui = ops.ui
    val portrait = ui.cropPortrait
    val presets = listOf(
        CropAspectPreset.Free to TAG_CROP_FREE,
        CropAspectPreset.Original to TAG_CROP_ORIGINAL,
        CropAspectPreset.Square to TAG_CROP_1_1,
        CropAspectPreset.FourFive to TAG_CROP_4_5,
        // The reference's fifth chip is 16:9; 3:2 / 4:3 follow it.
        CropAspectPreset.Wide to TAG_CROP_16_9,
        CropAspectPreset.ThreeTwo to TAG_CROP_3_2,
        CropAspectPreset.FourThree to TAG_CROP_4_3
    )
    val free = stringResource(Res.string.editor_crop_free)
    val original = stringResource(Res.string.editor_crop_original)
    val square = stringResource(Res.string.editor_crop_square)
    // Keep the shipped "4:5" resource for the default orientation label.
    val fourFive = stringResource(Res.string.editor_crop_four_five)
    fun labelOf(preset: CropAspectPreset): String = when (preset) {
        CropAspectPreset.Free -> free
        CropAspectPreset.Original -> original
        CropAspectPreset.Square -> square
        CropAspectPreset.FourFive -> if (portrait) fourFive else preset.ratioLabel(false)
        else -> preset.ratioLabel(portrait)
    }
    val current = labelOf(ui.cropPreset)
    val currentDesc = stringResource(Res.string.editor_crop_ratio_desc, current)
    val swapLabel = stringResource(
        if (portrait) Res.string.editor_crop_swap_to_landscape
        else Res.string.editor_crop_swap_to_portrait
    )
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .testTag(TAG_CROP_ASPECT)
                    .semantics { contentDescription = currentDesc }
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = current
                    ) { open = true }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = EditorSymbols.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            // The attached orientation segment, divided like the media
            // viewer's paired header actions.
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        enabled = enabled && !ui.cropPreset.isSquare,
                        role = Role.Button,
                        onClickLabel = swapLabel
                    ) { ops.swapOrientation() }
                    .testTag(TAG_CROP_ORIENTATION),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.AspectSwap,
                    contentDescription = swapLabel,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { (preset, tag) ->
                DropdownMenuItem(
                    text = { Text(labelOf(preset)) },
                    onClick = {
                        open = false
                        ops.selectPreset(preset)
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag(tag)
                )
            }
        }
    }
}

/** The aspect pill and the axis pill share the card's first row. */
@Composable
private fun CropPillsRow(
    ops: CropToolOps,
    tab: CropTab,
    tabs: Map<CropTab, String>,
    enabled: Boolean,
    canEdit: Boolean,
    onSelectTab: (CropTab) -> Unit
) {
    // At large text the two pills would clip in one row with the circles,
    // so they take a second row instead of squeezing.
    val stacked = LocalDensity.current.fontScale >= 1.5f
    val circles: @Composable () -> Unit = {
        CropCircleButton(
            icon = EditorSymbols.RotateLeft,
            label = stringResource(Res.string.editor_rotate_left),
            enabled = canEdit,
            tag = TAG_ROTATE_LEFT,
            onClick = { ops.rotateLeft() }
        )
        CropCircleButton(
            icon = EditorSymbols.Flip,
            label = stringResource(Res.string.editor_flip_horizontal),
            enabled = canEdit,
            tag = TAG_FLIP_H,
            onClick = { ops.flipHorizontal() }
        )
    }
    val pills: @Composable () -> Unit = {
        CropAspectPill(ops = ops, enabled = enabled)
        CropAxisChip(
            tab = tab,
            tabs = tabs,
            enabled = enabled,
            onSelect = onSelectTab
        )
    }
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                circles()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pills()
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            circles()
            Spacer(Modifier.weight(1f))
            pills()
        }
    }
}

/**
 * Rotate left and Flip horizontal on the left, the crop's real output
 * ("4:5 · 3200 × 4000 px", dropped rather than clipped at large text) in
 * the middle and the whole-geometry Reset on the right — 48 dp labelled
 * icon buttons.
 */
/**
 * The card's control row, matching the approved crop frame: rotate and flip
 * as 44 dp tonal circles, the active axis' ruler drawn bare (no slab) with
 * its angle pill riding above the needle, and the axis chip that switches
 * straighten / vertical / horizontal where the mock carries Auto.
 */
@Composable
private fun CropControlRow(
    ops: CropToolOps,
    tab: CropTab,
    label: String,
    value: Float,
    display: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    onOpenEntry: () -> Unit,
    onBegin: () -> Unit,
    onUpdate: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onStep: (Float) -> Unit
) {
    val ruler: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            // Desktop wheel scrubbing still works over the bare ruler.
            CropWheelScrub(
                ops = ops,
                tab = tab,
                label = label,
                enabled = enabled
            ) {
                CropAngleRuler(
                    label = label,
                    value = value,
                    display = display,
                    range = range,
                    step = step,
                    enabled = enabled,
                    interactionActive = interactionActive,
                    identity = identity,
                    tab = tab,
                    onOpenEntry = onOpenEntry,
                    onBegin = onBegin,
                    onUpdate = onUpdate,
                    onCommit = onCommit,
                    onCancel = onCancel,
                    onStep = onStep
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp)
            .heightIn(min = 58.dp)
    ) {
        ruler(Modifier.fillMaxWidth())
    }
}

/** One 44 dp tonal circle — the reference row's rotate/flip controls. */
@Composable
private fun CropCircleButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickableAction(enabled, label, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The bare precision ruler: only the tick ladder, with the angle pill above
 * its needle. The slab background and the −/+ pair are gone — the card's
 * rotate/flip circles flank it instead, like the reference.
 */
@Composable
private fun CropAngleRuler(
    label: String,
    value: Float,
    display: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    tab: CropTab,
    onOpenEntry: () -> Unit,
    onBegin: () -> Unit,
    onUpdate: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onStep: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = if (tab == CropTab.Straighten) StraightenScale else KeystoneScale
    // The box grows with the text so the pill never clips its value.
    val fontScale = LocalDensity.current.fontScale
    val boxHeight = 58.dp + ((fontScale - 1f).coerceAtLeast(0f) * 36f).dp
    BoxWithConstraints(modifier.fillMaxWidth().height(boxHeight)) {
        val density = LocalDensity.current
        val fraction = ((value - range.start) / (range.endInclusive - range.start))
            .coerceIn(0f, 1f)
        var pillWidthPx by remember { mutableIntStateOf(0) }
        var pillHeightPx by remember { mutableIntStateOf(0) }
        val pillHalf = with(density) { pillWidthPx.toDp() / 2f }
        val pillHeight = with(density) {
            if (pillHeightPx > 0) pillHeightPx.toDp() else 24.dp
        }
        // The pill rides the ruler's top edge (a 6 dp overlap), wherever
        // the taller box put the ladder.
        val rulerTop = maxHeight - 40.dp
        val pillY = (rulerTop - pillHeight + 6.dp).coerceAtLeast(0.dp)
        val needleX = 4.dp + (maxWidth - 8.dp) * fraction
        val pillX = (needleX - pillHalf)
            .coerceIn(0.dp, (maxWidth - pillHalf * 2f).coerceAtLeast(0.dp))
        CropAnglePill(
            text = display,
            enabled = enabled && !interactionActive,
            onClick = onOpenEntry,
            modifier = Modifier
                .offset(x = pillX, y = pillY)
                .onSizeChanged {
                    pillWidthPx = it.width
                    pillHeightPx = it.height
                }
                .testTag(
                    if (tab == CropTab.Straighten) TAG_STRAIGHTEN_VALUE
                    else "editor.crop.value.${tab.name.lowercase()}"
                )
        )
        EditorPrecisionRuler(
            value = value,
            range = range,
            step = step,
            summary = "$label $display",
            description = stringResource(Res.string.editor_precision_ruler, label, display),
            enabled = enabled,
            interactionActive = interactionActive,
            identity = identity,
            sliderTag = if (tab == CropTab.Straighten) TAG_STRAIGHTEN
            else "editor.crop.ruler.${tab.name.lowercase()}",
            onBeginInteraction = onBegin,
            onUpdateInteraction = onUpdate,
            onCommitInteraction = onCommit,
            onCancelInteraction = onCancel,
            onApplyStep = onStep,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp),
            showSummary = false,
            showLabels = false,
            containerColor = Color.Transparent,
            scale = scale,
            hapticAt = scale::isDetent,
            needleGlow = true
        )
    }
}

/**
 * The small angle readout that rides the ruler's needle — the reference's
 * white pill with dark text, whatever the value.
 */
@Composable
private fun CropAnglePill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = Color(0xFF1B1B1F)
    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.6f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = text, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = EditorSymbols.Crop,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * The axis control that takes the reference's Auto slot: the active axis on a
 * tonal chip, opening the three real rulers. Auto crop/straighten is not
 * shipped (no such engine operation).
 */
@Composable
private fun CropAxisChip(
    tab: CropTab,
    tabs: Map<CropTab, String>,
    enabled: Boolean,
    onSelect: (CropTab) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag(TAG_CROP_AXIS)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = tabs.getValue(tab),
                    onClick = { open = true }
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tabs.getValue(tab),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = EditorSymbols.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CropTab.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(tabs.getValue(t)) },
                    onClick = {
                        open = false
                        onSelect(t)
                    },
                    modifier = Modifier.testTag(
                        when (t) {
                            CropTab.Straighten -> TAG_CROP_TAB_STRAIGHTEN
                            CropTab.Vertical -> TAG_CROP_TAB_VERTICAL
                            CropTab.Horizontal -> TAG_CROP_TAB_HORIZONTAL
                        }
                    )
                )
            }
        }
    }
}

/** One 64 dp labelled action: icon above a single-line label, 16 dp V4 corners. */
@Composable
private fun CropAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    rotateIcon: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        modifier = modifier
            .sizeIn(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickableAction(enabled, label, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).then(if (rotateIcon) Modifier.rotate(90f) else Modifier)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * Guided upright: a switch that turns canvas drags into guide drawing,
 * the guide count, and Clear. Guides solve on every change.
 */
@Composable
private fun GuidedUprightRow(ops: CropToolOps, enabled: Boolean) {
    val ui = ops.ui
    val title = stringResource(Res.string.editor_crop_guided_upright)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickableAction(enabled, title, role = Role.Switch) { ui.guidesMode = !ui.guidesMode }
                .padding(horizontal = 16.dp)
                .testTag(TAG_CROP_GUIDES_TOGGLE),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                EditorSymbols.StraightGuide, contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (ui.guides.isEmpty()) stringResource(Res.string.editor_crop_guided_hint)
                    else stringResource(Res.string.editor_crop_guided_count, ui.guides.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = ui.guidesMode, onCheckedChange = null, enabled = enabled)
        }
        if (ui.guides.isNotEmpty()) {
            val clear = stringResource(Res.string.editor_crop_guides_clear)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickableAction(enabled, clear) { ui.guides.clear() }
                    .padding(horizontal = 16.dp)
                    .testTag(TAG_CROP_GUIDES_CLEAR),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    EditorSymbols.Close, contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(clear, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun Modifier.clickableAction(
    enabled: Boolean,
    label: String,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier = clickable(enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
