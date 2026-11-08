package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoMarkupMetrics
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupText
import com.dot.gallery.editor.core.model.PhotoMarkupTextLayout
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.measureMarkupTextEm
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_add_text
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_blur
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_blur_faces
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_clear_markup
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_done
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_draw
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_erase
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_finding_faces
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_highlighter
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_mosaic
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_mosaic_faces
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_move
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_no_faces
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_pen
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_remove
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_size
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_strength
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_text
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_text_hint
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_text_placeholder
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_text_selected_hint
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.LensBlur
import com.dot.gallery.editor.ui.icons.RotateRight
import com.dot.gallery.editor.ui.icons.Tune
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_MARKUP = "editor.category.markup"
internal const val TAG_MARKUP_SURFACE = "editor.markup.surface"
internal const val TAG_MARKUP_TEXT_FIELD = "editor.markup.text.field"

/** Longest label one text entry accepts. */
private const val MAX_TEXT_LENGTH = 2000

/** Point budget for one stroke; denser input is decimated by distance first. */
private const val MAX_STROKE_POINTS = 1500

/** Skips pointer samples closer than this, as a fraction of the frame. */
private const val MIN_POINT_STEP = 0.0015f

private const val ERASE_RADIUS = 0.04f

internal enum class MarkupMode(val labelRes: StringResource) {
    Pen(Res.string.editor_retouch_pen),
    Highlighter(Res.string.editor_retouch_highlighter),
    Blur(Res.string.editor_retouch_blur),
    Mosaic(Res.string.editor_retouch_mosaic),
    Text(Res.string.editor_retouch_text);

    val strokeType: PhotoMarkupType?
        get() = when (this) {
            Pen -> PhotoMarkupType.Pen
            Highlighter -> PhotoMarkupType.Highlighter
            Blur -> PhotoMarkupType.Blur
            Mosaic -> PhotoMarkupType.Mosaic
            Text -> null
        }

    val icon: ImageVector
        get() = when (this) {
            Pen -> RetouchSymbols.Pen
            Highlighter -> RetouchSymbols.Highlighter
            Blur -> EditorSymbols.LensBlur
            Mosaic -> RetouchSymbols.Mosaic
            Text -> RetouchSymbols.Text
        }
}

internal enum class MarkupInkMode { Draw, Move, Erase }

/** The blur/mosaic stroke a face rectangle becomes, so faces undo and erase like ink. */
internal fun faceObscureStroke(
    face: EditorFaceBox,
    output: PixelSize,
    type: PhotoMarkupType,
    strength: Float
): PhotoMarkupStroke {
    val longEdge = max(output.width, output.height).toFloat()
    val boxWidth = (face.right - face.left) * output.width
    val boxHeight = (face.bottom - face.top) * output.height
    val diameter = max(boxWidth, boxHeight) * 1.15f
    val size = ((diameter / longEdge - 0.002f) / 0.0007f).coerceIn(1f, 100f)
    val cx = ((face.left + face.right) / 2f).coerceIn(0f, 1f)
    val cy = ((face.top + face.bottom) / 2f).coerceIn(0f, 1f)
    return PhotoMarkupStroke(
        type = type,
        points = persistentListOf(NormalizedPoint(cx, cy)),
        size = size,
        strength = strength
    )
}

/** Strokes kept when an eraser passes [point]; a stroke goes as a whole. */
internal fun eraseStrokes(
    strokes: List<PhotoMarkupStroke>,
    point: NormalizedPoint,
    aspect: Float
): List<PhotoMarkupStroke> = strokes.filter { stroke ->
    stroke.points.none {
        hypot((it.x - point.x) * aspect, it.y - point.y) < ERASE_RADIUS
    }
}

private fun PhotoEdits.withMarkup(markup: PhotoMarkup): PhotoEdits =
    copy(retouch = retouch.copy(markup = markup))

/**
 * Markup: the five connected tabs (the selected tab keeps its label, the rest
 * shrink to icons), then each tab's own settings. Blur and Mosaic carry Size and
 * Strength chips, plus a faces action — Blur faces or Mosaic faces — when the
 * host can detect faces. Size, strength and colour are tool settings, stamped on
 * each stroke.
 */
@Composable
internal fun MarkupToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val toolUi = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val host = LocalEditorHostTools.current
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()
    val enabled = state.document != null && !state.opening
    val modes = MarkupMode.entries
    val mode = toolUi.markupMode
    val markup = state.document?.retouch?.markup ?: PhotoMarkup()
    val toolTitle = photoToolTitle(EditorToolIds.PhotoMarkup)
    val clearLabel = stringResource(Res.string.editor_retouch_clear_markup)
    val noFaces = stringResource(Res.string.editor_retouch_no_faces)

    val toolMenu = LocalPhotoToolMenu.current
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }
    toolMenu?.apply {
        resetParameterLabel = clearLabel
        resetParameterTag = "editor.markup.clear"
        resetParameterEnabled = enabled && !markup.isEmpty
        onResetParameter = {
            latestState.document?.toEdits()?.let { edits ->
                latestActions.beginToolEdit(toolTitle)
                latestActions.applyEdits(clearLabel, edits.withMarkup(PhotoMarkup()))
                toolUi.markupSelectedText = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RetouchCardPadding)
            .testTag("editor.markup"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectedButtonGroup(
            itemCount = modes.size,
            selectedIndex = modes.indexOf(mode),
            enabled = enabled,
            scrolling = false,
            segmentWeight = { active -> if (active) 2.4f else 1f },
            label = { if (modes[it] == mode) stringResource(modes[it].labelRes) else "" },
            tag = { "editor.markup.tab.${modes[it].name}" },
            onSelect = { index ->
                val next = modes[index]
                toolUi.markupMode = next
                toolUi.markupSelectedText = null
                toolUi.markupNotice = null
                if (next == MarkupMode.Text && markup.texts.isEmpty()) {
                    toolUi.markupTextEditing = MarkupTextDraft(null, "", toolUi.markupColor)
                }
            },
            leading = { index, active ->
                Icon(
                    imageVector = modes[index].icon,
                    contentDescription = if (active) null else stringResource(modes[index].labelRes),
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        when (mode) {
            MarkupMode.Pen, MarkupMode.Highlighter -> {
                RetouchColourRow(
                    selected = toolUi.markupColor,
                    enabled = enabled,
                    tag = "editor.markup.colour",
                    onSelect = { toolUi.markupColor = it }
                )
                MarkupSizeRuler(toolUi, enabled)
            }
            MarkupMode.Blur, MarkupMode.Mosaic -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColourParamChip(
                        label = stringResource(Res.string.editor_retouch_size),
                        icon = RetouchSymbols.LineWeight,
                        value = toolUi.markupSize.toInt().toString(),
                        modified = false,
                        selected = !toolUi.markupEditingStrength,
                        enabled = enabled,
                        tag = "editor.markup.param.size",
                        onClick = { toolUi.markupEditingStrength = false }
                    )
                    ColourParamChip(
                        label = stringResource(Res.string.editor_retouch_strength),
                        icon = EditorSymbols.Tune,
                        value = toolUi.markupStrength.toInt().toString(),
                        modified = false,
                        selected = toolUi.markupEditingStrength,
                        enabled = enabled,
                        tag = "editor.markup.param.strength",
                        onClick = { toolUi.markupEditingStrength = true }
                    )
                    val detect = host.detectFaces
                    if (detect != null) {
                        val busy = toolUi.markupFindingFaces
                        ColourParamChip(
                            label = stringResource(
                                when {
                                    busy -> Res.string.editor_retouch_finding_faces
                                    mode == MarkupMode.Mosaic -> Res.string.editor_retouch_mosaic_faces
                                    else -> Res.string.editor_retouch_blur_faces
                                }
                            ),
                            icon = RetouchSymbols.Face,
                            value = "",
                            modified = false,
                            selected = false,
                            enabled = enabled && !busy && state.preview != null,
                            tag = "editor.markup.blurFaces",
                            onClick = {
                                val pixels = latestState.preview ?: return@ColourParamChip
                                toolUi.markupFindingFaces = true
                                toolUi.markupNotice = null
                                scope.launch {
                                    val faces = try {
                                        detect(pixels)
                                    } finally {
                                        toolUi.markupFindingFaces = false
                                    }
                                    val edits = latestState.document?.toEdits()
                                        ?: return@launch
                                    if (faces.isEmpty()) {
                                        toolUi.markupNotice = noFaces
                                        return@launch
                                    }
                                    val added = faces.map {
                                        faceObscureStroke(
                                            it,
                                            pixels.size,
                                            mode.strokeType ?: PhotoMarkupType.Blur,
                                            toolUi.markupStrength
                                        )
                                    }
                                    latestActions.beginToolEdit(toolTitle)
                                    latestActions.applyEdits(
                                        toolTitle,
                                        edits.withMarkup(
                                            edits.retouch.markup.copy(
                                                strokes = (edits.retouch.markup.strokes + added)
                                                    .toPersistentList()
                                            )
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                if (toolUi.markupEditingStrength) {
                    MarkupStrengthRuler(toolUi, enabled)
                } else {
                    MarkupSizeRuler(toolUi, enabled)
                }
                toolUi.markupNotice?.let { RetouchHint(it) }
            }
            MarkupMode.Text -> {
                val selected = markup.texts.firstOrNull { it.id == toolUi.markupSelectedText }
                if (selected != null) {
                    RetouchColourRow(
                        selected = selected.color,
                        enabled = enabled && !state.interactionActive,
                        tag = "editor.markup.colour",
                        onSelect = { colour ->
                            toolUi.markupColor = colour
                            val edits = latestState.document?.toEdits()
                                ?: return@RetouchColourRow
                            latestActions.beginToolEdit(toolTitle)
                            latestActions.applyEdits(
                                toolTitle,
                                edits.withMarkup(
                                    edits.retouch.markup.copy(
                                        texts = edits.retouch.markup.texts.map {
                                            if (it.id == selected.id) it.copy(color = colour) else it
                                        }.toPersistentList()
                                    )
                                )
                            )
                        }
                    )
                }
                RetouchHint(
                    stringResource(
                        if (selected != null) Res.string.editor_retouch_text_selected_hint
                        else Res.string.editor_retouch_text_hint
                    )
                )
                RetouchActionButton(
                    icon = RetouchSymbols.Add,
                    label = stringResource(Res.string.editor_retouch_add_text),
                    enabled = enabled,
                    tag = "editor.markup.addText",
                    onClick = {
                        toolUi.markupTextEditing =
                            MarkupTextDraft(null, "", toolUi.markupColor)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    toolUi.markupTextEditing?.let { draft ->
        MarkupTextDialog(
            draft = draft,
            onDismiss = { toolUi.markupTextEditing = null },
            onCommit = { text, colour ->
                toolUi.markupTextEditing = null
                toolUi.markupColor = colour
                val edits = latestState.document?.toEdits() ?: return@MarkupTextDialog
                val texts = edits.retouch.markup.texts
                val value = PhotoMarkupTextLayout.normalizeText(text)
                val frame = latestState.previewFrame()
                val updated = when {
                    value.isBlank() -> texts.filter { it.id != draft.id }
                    draft.id == null -> texts + PhotoMarkupText(
                        id = (texts.maxOfOrNull { it.id } ?: 0L) + 1L,
                        text = value,
                        x = 0.5f,
                        y = 0.4f,
                        color = colour
                    ).fitted(frame.first, frame.second)
                    else -> texts.map {
                        if (it.id == draft.id) {
                            it.copy(text = value, color = colour).fitted(frame.first, frame.second)
                        } else {
                            it
                        }
                    }
                }
                toolUi.markupSelectedText = when {
                    value.isBlank() -> null
                    draft.id == null -> updated.last().id
                    else -> draft.id
                }
                latestActions.beginToolEdit(toolTitle)
                latestActions.applyEdits(
                    toolTitle,
                    edits.withMarkup(
                        edits.retouch.markup.copy(texts = updated.toPersistentList())
                    )
                )
            }
        )
    }
}

@Composable
private fun MarkupSizeRuler(toolUi: PhotoToolUiState, enabled: Boolean) {
    MarkupSettingRuler(
        label = stringResource(Res.string.editor_retouch_size),
        value = toolUi.markupSize,
        range = 1f..100f,
        enabled = enabled,
        keyName = "size",
        onChange = { toolUi.markupSize = it }
    )
}

@Composable
private fun MarkupStrengthRuler(toolUi: PhotoToolUiState, enabled: Boolean) {
    MarkupSettingRuler(
        label = stringResource(Res.string.editor_retouch_strength),
        value = toolUi.markupStrength,
        range = 0f..100f,
        enabled = enabled,
        keyName = "strength",
        onChange = { toolUi.markupStrength = it }
    )
}

/** The shared ruler over a tool setting rather than a recipe field. */
@Composable
private fun MarkupSettingRuler(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    keyName: String,
    onChange: (Float) -> Unit
) {
    val display = formatDecimal(value).trimEnd('0').trimEnd('.')
    EditorRulerSlab(
        label = label,
        value = value,
        display = display,
        accent = false,
        range = range,
        step = 1f,
        summary = "$label $display",
        description = label,
        enabled = enabled,
        interactionActive = false,
        identity = keyName,
        sliderTag = "editor.markup.slider.$keyName",
        valueTag = "editor.markup.value.$keyName",
        stepKey = keyName,
        onOpenEntry = {},
        onBeginInteraction = {},
        onUpdateInteraction = { onChange(it.coerceIn(range)) },
        onCommitInteraction = {},
        onCancelInteraction = {},
        onApplyStep = { onChange(it.coerceIn(range)) },
        hapticAt = null,
        limitStartLabel = formatDecimal(range.start).trimEnd('0').trimEnd('.'),
        limitEndLabel = formatDecimal(range.endInclusive).trimEnd('0').trimEnd('.'),
        modifier = Modifier.padding(top = 14.dp)
    )
}

/** A text being added ([id] null) or changed, as the entry dialog edits it. */
internal class MarkupTextDraft(val id: Long?, val text: String, val color: Int)

/**
 * Text entry over a dimmed photo: the field takes the label's colour, the
 * swatches change it, Done commits, and Remove or an empty Done deletes.
 */
@Composable
private fun MarkupTextDialog(
    draft: MarkupTextDraft,
    onDismiss: () -> Unit,
    onCommit: (String, Int) -> Unit
) {
    var text by remember(draft) { mutableStateOf(draft.text) }
    var colour by remember(draft) { mutableStateOf(draft.color) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.86f))
                .imePadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_TEXT_LENGTH) text = it },
                textStyle = TextStyle(
                    color = Color(colour),
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 240.dp)
                    .focusRequester(focus)
                    .testTag(TAG_MARKUP_TEXT_FIELD),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.editor_retouch_text_placeholder),
                                style = TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                }
            )
            RetouchColourRow(
                selected = colour,
                enabled = true,
                tag = "editor.markup.text.colour",
                onSelect = { colour = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkupDialogButton(
                    label = stringResource(Res.string.editor_retouch_remove),
                    filled = false,
                    tag = "editor.markup.text.remove",
                    onClick = { onCommit("", colour) }
                )
                MarkupDialogButton(
                    label = stringResource(Res.string.editor_retouch_done),
                    filled = true,
                    tag = "editor.markup.text.done",
                    onClick = { onCommit(text, colour) }
                )
            }
        }
    }
}

@Composable
private fun MarkupDialogButton(
    label: String,
    filled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .background(
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * The Markup canvas layer. Brush tabs draw (or erase) on the image box and keep
 * ink visible until the CPU preview catches up; Move leaves the gestures to the
 * canvas; the Text tab selects, drags and re-opens labels. The floating pill
 * chooses Draw, Move or Erase on every brush tab.
 */
@Composable
internal fun MarkupCanvasOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    toolUi: PhotoToolUiState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val boxW = with(density) { layout.zoomedWidth.toPx() }
    val boxH = with(density) { layout.zoomedHeight.toPx() }
    val mode = toolUi.markupMode
    val ink = toolUi.markupInk
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val toolTitle = photoToolTitle(EditorToolIds.PhotoMarkup)
    var live by remember { mutableStateOf<PhotoMarkupStroke?>(null) }
    val markup = state.document?.retouch?.markup ?: PhotoMarkup()
    val rendered = state.previewDocument?.retouch?.markup?.strokes?.size ?: 0
    // Strokes the preview has not painted yet, plus the one under the finger.
    val unrendered = markup.strokes.drop(rendered)
    val output = PixelSize(max(1, boxW.toInt()), max(1, boxH.toInt()))

    fun normalized(position: Offset) = NormalizedPoint(
        (position.x / boxW).coerceIn(0f, 1f),
        (position.y / boxH).coerceIn(0f, 1f)
    )

    val surfaceLeft = with(density) { layout.zoomedLeft.toPx() }
    val surfaceTop = with(density) { layout.zoomedTop.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .then(
                if (mode == MarkupMode.Text) {
                    Modifier.pointerInput(boxW, boxH, surfaceLeft, surfaceTop) {
                        markupTextGestures(
                            frameWidth = boxW,
                            frameHeight = boxH,
                            origin = Offset(surfaceLeft, surfaceTop),
                            documentOf = { latestState.document?.toEdits() },
                            toolUi = toolUi,
                            actions = { latestActions },
                            toolTitle = toolTitle
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = layout.zoomedLeft, y = layout.zoomedTop)
                .size(width = layout.zoomedWidth, height = layout.zoomedHeight)
                .testTag(TAG_MARKUP_SURFACE)
                .then(
                    when {
                        mode == MarkupMode.Text -> Modifier
                        ink == MarkupInkMode.Erase -> Modifier.pointerInput(boxW, boxH) {
                            val aspect = boxW / boxH
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val edits = latestState.document?.toEdits()
                                    ?: return@awaitEachGesture
                                down.consume()
                                try {
                                    latestActions.beginToolEdit(toolTitle)
                                    latestActions.beginEdit(toolTitle)
                                } catch (rejected: IllegalStateException) {
                                    return@awaitEachGesture
                                }
                                var strokes: List<PhotoMarkupStroke> = edits.retouch.markup.strokes
                                fun eraseAt(position: Offset) {
                                    val kept = eraseStrokes(strokes, normalized(position), aspect)
                                    if (kept.size != strokes.size) {
                                        strokes = kept
                                        latestActions.updateEdits(
                                            edits.withMarkup(
                                                edits.retouch.markup.copy(
                                                    strokes = kept.toPersistentList()
                                                )
                                            )
                                        )
                                    }
                                }
                                eraseAt(down.position)
                                try {
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach {
                                            eraseAt(it.position)
                                            it.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    latestActions.commitEdit()
                                }
                            }
                        }
                        ink == MarkupInkMode.Draw -> Modifier.pointerInput(boxW, boxH, mode) {
                            val type = mode.strokeType ?: return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (latestState.interactionActive) return@awaitEachGesture
                                down.consume()
                                val points = ArrayList<NormalizedPoint>()
                                points += normalized(down.position)
                                val brush = PhotoMarkupStroke(
                                    type = type,
                                    points = persistentListOf(points[0]),
                                    size = toolUi.markupSize,
                                    strength = toolUi.markupStrength,
                                    color = toolUi.markupColor
                                )
                                live = brush
                                var cancelled = false
                                try {
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size > 1) {
                                            // A second finger means the gesture was not a stroke.
                                            cancelled = true
                                            break
                                        }
                                        event.changes.forEach { change ->
                                            val next = normalized(change.position)
                                            val last = points.last()
                                            val moved = abs(next.x - last.x) + abs(next.y - last.y)
                                            if (moved >= MIN_POINT_STEP &&
                                                points.size < MAX_STROKE_POINTS
                                            ) {
                                                points += next
                                                live = brush.copy(points = points.toPersistentList())
                                            }
                                            change.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    live = null
                                }
                                if (cancelled) return@awaitEachGesture
                                val edits = latestState.document?.toEdits()
                                    ?: return@awaitEachGesture
                                val stroke = brush.copy(points = points.toPersistentList())
                                try {
                                    latestActions.beginToolEdit(toolTitle)
                                    latestActions.applyEdits(
                                        toolTitle,
                                        edits.withMarkup(
                                            edits.retouch.markup.copy(
                                                strokes = edits.retouch.markup.strokes.add(stroke)
                                            )
                                        )
                                    )
                                } catch (rejected: IllegalStateException) {
                                    // Another draft owns the session: drop this ink.
                                    return@awaitEachGesture
                                }
                            }
                        }
                        else -> Modifier
                    }
                )
                .semantics { contentDescription = toolTitle }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                (unrendered + listOfNotNull(live)).forEach { stroke ->
                    drawLiveStroke(stroke, output)
                }
            }
        }
        if (mode == MarkupMode.Text) {
            MarkupTextSelection(markup.texts, toolUi.markupSelectedText, boxW, boxH, layout)
        }
        if (mode != MarkupMode.Text) {
            EditorFloatingPanel(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = RETOUCH_HEADER_CLEARANCE, end = 12.dp)
                    .testTag("editor.markup.ink")
            ) {
                Row(
                    Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RetouchPillItem(
                        icon = RetouchSymbols.Draw,
                        label = stringResource(Res.string.editor_retouch_draw),
                        selected = ink == MarkupInkMode.Draw,
                        tag = "editor.markup.ink.draw",
                        onClick = { toolUi.markupInk = MarkupInkMode.Draw }
                    )
                    RetouchPillItem(
                        icon = RetouchSymbols.Pan,
                        label = stringResource(Res.string.editor_retouch_move),
                        selected = ink == MarkupInkMode.Move,
                        tag = "editor.markup.ink.move",
                        onClick = { toolUi.markupInk = MarkupInkMode.Move }
                    )
                    RetouchPillItem(
                        icon = RetouchSymbols.Eraser,
                        label = stringResource(Res.string.editor_retouch_erase),
                        selected = ink == MarkupInkMode.Erase,
                        tag = "editor.markup.ink.erase",
                        onClick = { toolUi.markupInk = MarkupInkMode.Erase }
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLiveStroke(stroke: PhotoMarkupStroke, output: PixelSize) {
    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (stroke.points.size == 1) {
        path.lineTo(stroke.points[0].x * size.width + 0.1f, stroke.points[0].y * size.height)
    }
    val colour = when (stroke.type) {
        PhotoMarkupType.Pen -> Color(stroke.color)
        PhotoMarkupType.Highlighter -> Color(stroke.color).copy(alpha = 0.42f)
        // Blur and Mosaic show their footprint until the render lands.
        PhotoMarkupType.Blur, PhotoMarkupType.Mosaic -> Color.White.copy(alpha = 0.35f)
    }
    drawPath(
        path = path,
        color = colour,
        style = Stroke(
            width = PhotoMarkupMetrics.strokeWidth(stroke.size, output),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/** Handle indices, matching the old editor's corners. */
private const val HANDLE_DELETE = 0
private const val HANDLE_RESIZE_START = 1
private const val HANDLE_ROTATE = 2
private const val HANDLE_RESIZE_END = 3

/** Extra grab room around a label's box, and the corners' distance from it. */
private val TEXT_HIT_PADDING = 8.dp
private val TEXT_BOX_PADDING = 8.dp
private val TEXT_HANDLE_SIZE = 24.dp
private val TEXT_HANDLE_HIT_RADIUS = 24.dp

/** A label's box in font sizes, falling back to a rough estimate. */
private fun PhotoMarkupText.em(): Pair<Float, Float> =
    measureMarkupTextEm(text) ?: run {
        val lines = text.split('\n')
        (lines.maxOf { it.length } * 0.55f) to (lines.size * PhotoMarkupTextLayout.LINE_HEIGHT)
    }

/** [this] sized and placed inside a frame by the shared bounds rules. */
private fun PhotoMarkupText.fitted(frameWidth: Float, frameHeight: Float): PhotoMarkupText {
    val (emW, emH) = em()
    return PhotoMarkupTextLayout.fit(this, emW, emH, frameWidth, frameHeight)
}

/** The rendered preview's size, the frame used before any canvas is measured. */
private fun PhotoEditorState.previewFrame(): Pair<Float, Float> {
    val size = preview?.size ?: return 1f to 1f
    return size.width.toFloat() to size.height.toFloat()
}

/** A label's box in frame pixels. */
private class TextBox(val centre: Offset, val width: Float, val height: Float, val rotation: Float) {
    /** [point] in the box's own upright space, relative to its centre. */
    fun toLocal(point: Offset): Offset {
        val radians = -rotation * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        val d = point - centre
        return Offset(d.x * c - d.y * s, d.x * s + d.y * c)
    }

    /** A point given in the box's own space, back in frame pixels. */
    fun toFrame(local: Offset): Offset {
        val radians = rotation * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        return centre + Offset(local.x * c - local.y * s, local.x * s + local.y * c)
    }

    fun contains(point: Offset, padding: Float): Boolean {
        val local = toLocal(point)
        return abs(local.x) <= width / 2f + padding && abs(local.y) <= height / 2f + padding
    }

    /** The four padded corners, in handle order. */
    fun corners(padding: Float): List<Offset> {
        val x = width / 2f + padding
        val y = height / 2f + padding
        return listOf(Offset(-x, -y), Offset(-x, y), Offset(x, -y), Offset(x, y))
    }
}

private fun PhotoMarkupText.box(frameWidth: Float, frameHeight: Float): TextBox {
    val (emW, emH) = em()
    val fontPixels = size * frameHeight
    return TextBox(
        Offset(x * frameWidth, y * frameHeight),
        emW * fontPixels,
        emH * fontPixels,
        rotation
    )
}

private enum class TextGesture { None, Drag, Resize, Rotate, Pinch }

/**
 * The Text tab's gestures, with the old editor's rules: on the selected label the
 * top-left corner deletes, the top-right rotates and the bottom corners resize.
 * Elsewhere a touch selects the topmost label under it and drags it, a second tap
 * on a selected label edits it, and two fingers on it rotate and pinch-resize. A
 * touch that misses every label clears the selection.
 */
private suspend fun PointerInputScope.markupTextGestures(
    frameWidth: Float,
    frameHeight: Float,
    origin: Offset,
    documentOf: () -> PhotoEdits?,
    toolUi: PhotoToolUiState,
    actions: () -> PhotoEditorActions,
    toolTitle: String
) {
    val handleRadius = TEXT_HANDLE_HIT_RADIUS.toPx()
    val boxPadding = TEXT_BOX_PADDING.toPx()
    val hitPadding = TEXT_HIT_PADDING.toPx()
    val slop = viewConfiguration.touchSlop

    fun PhotoEdits.withText(updated: PhotoMarkupText): PhotoEdits = withMarkup(
        retouch.markup.copy(
            texts = retouch.markup.texts.map {
                if (it.id == updated.id) updated else it
            }.toPersistentList()
        )
    )

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var edits = documentOf() ?: return@awaitEachGesture
        val first = down.position - origin
        val texts = edits.retouch.markup.texts
        val selected = texts.firstOrNull { it.id == toolUi.markupSelectedText }

        val handle = selected?.let { label ->
            val box = label.box(frameWidth, frameHeight)
            val local = box.toLocal(first)
            box.corners(boxPadding).indexOfFirst { (local - it).getDistance() <= handleRadius }
        } ?: -1
        if (selected != null && handle == HANDLE_DELETE) {
            down.consume()
            toolUi.markupSelectedText = null
            try {
                actions().beginToolEdit(toolTitle)
                actions().applyEdits(
                    toolTitle,
                    edits.withMarkup(
                        edits.retouch.markup.copy(
                            texts = texts.filter { it.id != selected.id }.toPersistentList()
                        )
                    )
                )
            } catch (rejected: IllegalStateException) {
                // Another draft owns the session: the label stays.
                toolUi.markupSelectedText = selected.id
            }
            return@awaitEachGesture
        }

        var target: PhotoMarkupText? = selected
        var gesture = TextGesture.None
        var pendingDrag = false
        var wasSelected = false
        when (handle) {
            HANDLE_ROTATE -> gesture = TextGesture.Rotate
            HANDLE_RESIZE_START, HANDLE_RESIZE_END -> gesture = TextGesture.Resize
            else -> {
                val hit = texts.lastOrNull {
                    it.box(frameWidth, frameHeight).contains(first, hitPadding)
                }
                wasSelected = hit != null && hit.id == toolUi.markupSelectedText
                if (hit != null) {
                    toolUi.markupSelectedText = hit.id
                    target = hit
                    pendingDrag = true
                } else {
                    toolUi.markupSelectedText = null
                    target = null
                }
            }
        }
        if (target != null) down.consume()

        var draftOpen = false
        fun openDraft(): Boolean {
            if (draftOpen) return true
            return try {
                actions().beginToolEdit(toolTitle)
                actions().beginEdit(toolTitle)
                draftOpen = true
                true
            } catch (rejected: IllegalStateException) {
                false
            }
        }
        fun publish(updated: PhotoMarkupText) {
            if (!openDraft()) return
            target = updated
            edits = edits.withText(updated)
            actions().updateEdits(edits)
        }

        val rotateStart = target?.let { atan2(first.y - it.y * frameHeight, first.x - it.x * frameWidth) } ?: 0f
        val rotationStart = target?.rotation ?: 0f
        var moved = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val label = target ?: toolUi.markupSelectedText?.let { id ->
                    edits.retouch.markup.texts.firstOrNull { it.id == id }
                }

                // Two fingers on the selected label rotate and pinch it together.
                if (pressed.size >= 2 && label != null && gesture != TextGesture.Rotate &&
                    gesture != TextGesture.Resize
                ) {
                    val centroid = event.calculateCentroid(useCurrent = true) - origin
                    if (gesture == TextGesture.Pinch ||
                        label.box(frameWidth, frameHeight).contains(centroid, hitPadding)
                    ) {
                        gesture = TextGesture.Pinch
                        pendingDrag = false
                        moved = true
                        target = label
                        val turned = label.copy(
                            rotation = PhotoMarkupTextLayout.normalizeRotation(
                                label.rotation + event.calculateRotation()
                            )
                        )
                        val (emW, emH) = turned.em()
                        val cap = PhotoMarkupTextLayout.maxFittingSize(
                            turned, emW, emH, frameWidth, frameHeight
                        )
                        val scaled = (turned.size * event.calculateZoom()).coerceIn(
                            PhotoMarkupTextLayout.MIN_SIZE, cap
                        )
                        publish(
                            PhotoMarkupTextLayout.clampCentre(
                                turned.copy(size = scaled), emW, emH, frameWidth, frameHeight
                            )
                        )
                        event.changes.forEach { it.consume() }
                        continue
                    }
                }

                val change = pressed.first()
                val position = change.position - origin
                if (pendingDrag && (position - first).getDistance() > slop) {
                    gesture = TextGesture.Drag
                    pendingDrag = false
                }
                val current = target
                if (current != null) {
                    when (gesture) {
                        TextGesture.Drag -> {
                            moved = true
                            val delta = change.position - change.previousPosition
                            val (emW, emH) = current.em()
                            publish(
                                PhotoMarkupTextLayout.clampCentre(
                                    current.copy(
                                        x = (current.x + delta.x / frameWidth).coerceIn(0f, 1f),
                                        y = (current.y + delta.y / frameHeight).coerceIn(0f, 1f)
                                    ),
                                    emW, emH, frameWidth, frameHeight
                                )
                            )
                        }
                        TextGesture.Resize -> {
                            moved = true
                            val dy = (change.position.y - change.previousPosition.y) / frameHeight
                            val (emW, emH) = current.em()
                            val cap = PhotoMarkupTextLayout.maxFittingSize(
                                current, emW, emH, frameWidth, frameHeight
                            )
                            publish(
                                PhotoMarkupTextLayout.clampCentre(
                                    current.copy(
                                        size = (current.size + dy)
                                            .coerceIn(PhotoMarkupTextLayout.MIN_SIZE, cap)
                                    ),
                                    emW, emH, frameWidth, frameHeight
                                )
                            )
                        }
                        TextGesture.Rotate -> {
                            moved = true
                            val angle = atan2(
                                position.y - current.y * frameHeight,
                                position.x - current.x * frameWidth
                            )
                            val degrees = (angle - rotateStart) * 180f / PI.toFloat()
                            val (emW, emH) = current.em()
                            val turned = current.copy(
                                rotation = PhotoMarkupTextLayout.normalizeRotation(
                                    rotationStart + degrees
                                )
                            )
                            // A turn can widen the box past the frame, so re-fit it.
                            publish(
                                PhotoMarkupTextLayout.fit(turned, emW, emH, frameWidth, frameHeight)
                            )
                        }
                        else -> Unit
                    }
                    change.consume()
                }
            }
        } finally {
            if (draftOpen) actions().commitEdit()
        }
        if (!moved && wasSelected) {
            val label = target
            if (label != null) {
                toolUi.markupTextEditing = MarkupTextDraft(label.id, label.text, label.color)
            }
        }
    }
}

/** The selected label's rotated outline and its four corner handles. */
@Composable
private fun MarkupTextSelection(
    texts: List<PhotoMarkupText>,
    selectedId: Long?,
    frameWidth: Float,
    frameHeight: Float,
    layout: EditorImageLayout
) {
    val selected = texts.firstOrNull { it.id == selectedId } ?: return
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val box = selected.box(frameWidth, frameHeight)
    val padding = with(density) { TEXT_BOX_PADDING.toPx() }
    val left = with(density) { layout.zoomedLeft.toPx() }
    val top = with(density) { layout.zoomedTop.toPx() }
    val corners = box.corners(padding).map { box.toFrame(it) + Offset(left, top) }
    val outline = with(density) { 2.dp.toPx() }
    Canvas(Modifier.fillMaxSize().testTag("editor.markup.text.selection")) {
        val path = Path().apply {
            moveTo(corners[HANDLE_DELETE].x, corners[HANDLE_DELETE].y)
            lineTo(corners[HANDLE_ROTATE].x, corners[HANDLE_ROTATE].y)
            lineTo(corners[HANDLE_RESIZE_END].x, corners[HANDLE_RESIZE_END].y)
            lineTo(corners[HANDLE_RESIZE_START].x, corners[HANDLE_RESIZE_START].y)
            close()
        }
        drawPath(path, primary, style = Stroke(width = outline))
    }
    val half = with(density) { (TEXT_HANDLE_SIZE / 2).toPx() }
    listOf(
        Triple(HANDLE_DELETE, EditorSymbols.Close, "delete"),
        Triple(HANDLE_RESIZE_START, RetouchSymbols.OpenInFull, "resizeStart"),
        Triple(HANDLE_ROTATE, EditorSymbols.RotateRight, "rotate"),
        Triple(HANDLE_RESIZE_END, RetouchSymbols.OpenInFull, "resizeEnd")
    ).forEach { (index, icon, name) ->
        val corner = corners[index]
        with(density) {
            Box(
                modifier = Modifier
                    .offset(x = (corner.x - half).toDp(), y = (corner.y - half).toDp())
                    .size(TEXT_HANDLE_SIZE)
                    .clip(CircleShape)
                    .background(primary)
                    .testTag("editor.markup.text.handle.$name"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        // The bottom-right glyph mirrors the bottom-left's diagonal.
                        .graphicsLayer {
                            rotationZ = box.rotation +
                                if (index == HANDLE_RESIZE_START) 90f else 0f
                        }
                )
            }
        }
    }
}
