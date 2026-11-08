package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_band_aqua
import com.dot.gallery.editor.ui.generated.resources.editor_band_blue
import com.dot.gallery.editor.ui.generated.resources.editor_band_green
import com.dot.gallery.editor.ui.generated.resources.editor_band_magenta
import com.dot.gallery.editor.ui.generated.resources.editor_band_orange
import com.dot.gallery.editor.ui.generated.resources.editor_band_purple
import com.dot.gallery.editor.ui.generated.resources.editor_band_red
import com.dot.gallery.editor.ui.generated.resources.editor_band_yellow
import com.dot.gallery.editor.ui.generated.resources.editor_colour_basics
import com.dot.gallery.editor.ui.generated.resources.editor_colour_hsl
import com.dot.gallery.editor.ui.generated.resources.editor_colour_wheels
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_hue
import com.dot.gallery.editor.ui.generated.resources.editor_luminance
import com.dot.gallery.editor.ui.generated.resources.editor_midtones
import com.dot.gallery.editor.ui.generated.resources.editor_protect_skin
import com.dot.gallery.editor.ui.generated.resources.editor_reset_band
import com.dot.gallery.editor.ui.generated.resources.editor_reset_parameter
import com.dot.gallery.editor.ui.generated.resources.editor_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_tool_white_balance
import com.dot.gallery.editor.ui.generated.resources.editor_vibrance
import com.dot.gallery.editor.ui.generated.resources.editor_wheel_description
import com.dot.gallery.editor.ui.generated.resources.editor_wheel_less
import com.dot.gallery.editor.ui.generated.resources.editor_wheel_more
import com.dot.gallery.editor.ui.generated.resources.editor_wheel_turn_back
import com.dot.gallery.editor.ui.generated.resources.editor_wheel_turn_forward
import com.dot.gallery.editor.ui.icons.AutoAwesome
import com.dot.gallery.editor.ui.icons.ColorLens
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.LightMode
import com.dot.gallery.editor.ui.icons.RestartAlt
import com.dot.gallery.editor.ui.icons.Thermostat
import com.dot.gallery.editor.ui.icons.WaterDrop
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_COLOUR_PROTECT_SKIN = "editor.colour.protectSkin"
internal const val TAG_COLOUR_RESET_BAND = "editor.colour.resetBand"

/** The Colour tool's four modes, the top level of its hierarchy. */
internal enum class ColourMode(val key: String, val labelRes: StringResource) {
    Basics("basics", Res.string.editor_colour_basics),
    WhiteBalance("whiteBalance", Res.string.editor_tool_white_balance),
    Hsl("hsl", Res.string.editor_colour_hsl),
    Wheels("wheels", Res.string.editor_colour_wheels)
}

/** The tonal regions of the Wheels mode. */
internal enum class ColourRegion(val key: String, val labelRes: StringResource) {
    Shadows("shadows", Res.string.editor_shadows),
    Midtones("midtones", Res.string.editor_midtones),
    Highlights("highlights", Res.string.editor_highlights)
}

/**
 * How the tool spends its space: a phone's sheet shows one ruler chosen by
 * chips, the inspector stacks every ruler of the mode, and a wide sheet sets
 * two rulers side by side but keeps chips for three, which would be too short.
 */
private enum class ColourLayout { Sheet, WideSheet, Inspector }

private val SpringOvershoot = spring<Dp>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun PhotoEdits.withGrade(grade: PhotoColourGrade): PhotoEdits =
    copy(finishing = finishing.copy(grade = grade))

private fun PhotoColourGrade.wheel(region: ColourRegion): PhotoColourWheel =
    when (region) {
        ColourRegion.Shadows -> shadows
        ColourRegion.Midtones -> midtones
        ColourRegion.Highlights -> highlights
    }

private fun PhotoColourGrade.withWheel(
    region: ColourRegion,
    wheel: PhotoColourWheel
): PhotoColourGrade = when (region) {
    ColourRegion.Shadows -> copy(shadows = wheel)
    ColourRegion.Midtones -> copy(midtones = wheel)
    ColourRegion.Highlights -> copy(highlights = wheel)
}

/** The model's -1..1 fraction in the UI's whole -100..100 steps. */
private fun percentOf(fraction: Float): Float = (fraction * 100f).roundToInt().toFloat()

private val SaturationParameter = FinishingParam(
    key = "saturation",
    labelRes = Res.string.editor_saturation,
    displayRange = 0f..4f,
    step = 0.01f,
    neutral = 1f,
    signed = false,
    unitSuffix = "×",
    read = { it.color.saturation },
    write = { edits, value -> edits.copy(color = edits.color.copy(saturation = value)) }
)

private val VibranceParameter = FinishingParam(
    key = "vibrance",
    labelRes = Res.string.editor_vibrance,
    displayRange = -100f..100f,
    step = 1f,
    neutral = 0f,
    read = { percentOf(it.finishing.grade.vibrance) },
    write = { edits, value ->
        edits.withGrade(edits.finishing.grade.copy(vibrance = value / 100f))
    }
)

private fun bandColor(hue: Float) = Color.hsv(((hue % 360f) + 360f) % 360f, 0.72f, 0.95f)

private fun hslParameters(band: PhotoHslBand): List<FinishingParam> {
    fun parameter(
        name: String,
        labelRes: StringResource,
        gradient: List<Color>,
        get: (PhotoHslAdjustment) -> Float,
        set: (PhotoHslAdjustment, Float) -> PhotoHslAdjustment
    ) = FinishingParam(
        key = "hsl.${band.name.lowercase()}.$name",
        labelRes = labelRes,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        trackGradient = gradient,
        read = { percentOf(get(it.finishing.grade.adjustment(band))) },
        write = { edits, value ->
            val grade = edits.finishing.grade
            edits.withGrade(
                grade.withAdjustment(band, set(grade.adjustment(band), value / 100f))
            )
        }
    )
    val color = bandColor(band.centerHue)
    return listOf(
        parameter(
            "hue", Res.string.editor_hue,
            listOf(bandColor(band.centerHue - 30f), color, bandColor(band.centerHue + 30f)),
            { it.hue }, { a, v -> a.copy(hue = v) }
        ),
        parameter(
            "saturation", Res.string.editor_saturation,
            listOf(Color(0xFF8D9199), color),
            { it.saturation }, { a, v -> a.copy(saturation = v) }
        ),
        parameter(
            "luminance", Res.string.editor_luminance,
            listOf(Color(0xFF1A1C1E), color, Color(0xFFF1F0F4)),
            { it.luminance }, { a, v -> a.copy(luminance = v) }
        )
    )
}

private fun wheelLuminanceParameter(region: ColourRegion) = FinishingParam(
    key = "wheel.${region.key}.luminance",
    labelRes = Res.string.editor_luminance,
    displayRange = -100f..100f,
    step = 1f,
    neutral = 0f,
    read = { percentOf(it.finishing.grade.wheel(region).luminance) },
    write = { edits, value ->
        val grade = edits.finishing.grade
        edits.withGrade(
            grade.withWheel(region, grade.wheel(region).copy(luminance = value / 100f))
        )
    }
)

private fun modeEdited(edits: PhotoEdits, mode: ColourMode): Boolean {
    val grade = edits.finishing.grade
    return when (mode) {
        ColourMode.Basics -> edits.color.saturation != 1f ||
            grade.vibrance != 0f || grade.protectSkin
        ColourMode.WhiteBalance -> !edits.finishing.whiteBalance.isNeutral
        ColourMode.Hsl -> !grade.hslIsNeutral
        ColourMode.Wheels -> !grade.wheelsAreNeutral
    }
}

private fun resetMode(edits: PhotoEdits, mode: ColourMode): PhotoEdits {
    val grade = edits.finishing.grade
    val neutral = PhotoColourGrade()
    return when (mode) {
        ColourMode.Basics -> edits
            .copy(color = edits.color.copy(saturation = 1f))
            .withGrade(grade.copy(vibrance = 0f, protectSkin = false))
        ColourMode.WhiteBalance -> edits.copy(
            finishing = edits.finishing.copy(whiteBalance = PhotoWhiteBalance())
        )
        ColourMode.Hsl -> edits.withGrade(grade.copy(hsl = neutral.hsl))
        ColourMode.Wheels -> edits.withGrade(
            grade.copy(
                shadows = neutral.shadows,
                midtones = neutral.midtones,
                highlights = neutral.highlights
            )
        )
    }
}

/**
 * The Colour tool: a connected mode group over Basics, White balance, HSL and
 * Wheels. The mode's reset goes to the context header's menu, or to a row at
 * the foot of the inspector, which has no such menu.
 */
@Composable
internal fun ColourToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val toolUi = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val mode = toolUi.colourMode
    val resetLabel = stringResource(
        Res.string.editor_reset_parameter,
        stringResource(mode.labelRes)
    )
    val canReset = enabled && !state.interactionActive && modeEdited(edits, mode)
    val onReset = {
        latestState.document?.toEdits()?.let { current ->
            latestActions.applyEdits(resetLabel, resetMode(current, mode))
        }
        Unit
    }

    val inSheet = LocalPhotoToolSheetExpanded.current != null
    val toolMenu = LocalPhotoToolMenu.current.takeIf { inSheet }
    toolMenu?.apply {
        resetParameterLabel = resetLabel
        resetParameterTag = "editor.reset.colour.${mode.key}"
        resetParameterFooterTag = "editor.reset.colour.${mode.key}.foot"
        resetParameterEnabled = canReset
        onResetParameter = onReset
    }
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }

    @Composable
    fun body(maxWidth: Dp) {
        val layout = when {
            !inSheet -> ColourLayout.Inspector
            maxWidth >= 600.dp -> ColourLayout.WideSheet
            else -> ColourLayout.Sheet
        }
        val narrow = maxWidth < 340.dp
        val groupScrolls = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.15f
        Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            ConnectedButtonGroup(
                itemCount = ColourMode.entries.size,
                selectedIndex = mode.ordinal,
                enabled = enabled,
                scrolling = groupScrolls,
                label = { stringResource(ColourMode.entries[it].labelRes) },
                tag = { "editor.colour.mode.${ColourMode.entries[it].key}" },
                onSelect = { toolUi.colourMode = ColourMode.entries[it] },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            when (mode) {
                ColourMode.Basics -> BasicsMode(state, actions, edits, enabled, layout, toolUi)
                ColourMode.WhiteBalance -> {
                    WhiteBalancePresetStrip(state, actions)
                    ColourParams(
                        params = WhiteBalanceParameters,
                        icons = listOf(EditorSymbols.Thermostat, EditorSymbols.ColorLens),
                        activeKey = toolUi.colourWhiteBalanceParam,
                        onSelect = { toolUi.colourWhiteBalanceParam = it },
                        state = state,
                        actions = actions,
                        edits = edits,
                        enabled = enabled,
                        layout = layout
                    )
                }
                ColourMode.Hsl -> HslMode(state, actions, edits, enabled, layout, toolUi)
                ColourMode.Wheels -> WheelsMode(state, actions, edits, enabled, layout, narrow, toolUi)
            }
            if (!inSheet) {
                TextButton(
                    onClick = onReset,
                    enabled = canReset,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag("editor.reset.colour.${mode.key}")
                ) {
                    Icon(
                        imageVector = EditorSymbols.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(resetLabel, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    // The sheet sizes itself to this content, and a subcomposed root here
    // leaves the scaffold reading its offset before layout when tools switch.
    // The sheet spans the window less its insets, so the window answers there.
    if (inSheet) {
        val windowWidth = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width.toDp()
        }
        body(windowWidth - 24.dp)
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth()) { body(maxWidth) }
    }
}

/**
 * The connected button group: square-ish inner joins with round outer ends;
 * the selected segment springs to a full pill in `primary` and takes a little
 * more width. Widths follow the labels, since "White balance" needs twice what
 * "HSL" does; narrow or heavily scaled layouts scroll instead of clipping.
 * [leading] and [trailing] decorate a segment beside its label.
 */
@Composable
internal fun ConnectedButtonGroup(
    itemCount: Int,
    selectedIndex: Int,
    enabled: Boolean,
    scrolling: Boolean,
    label: @Composable (Int) -> String,
    tag: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    leading: (@Composable (index: Int, active: Boolean) -> Unit)? = null,
    trailing: (@Composable (index: Int, active: Boolean) -> Unit)? = null,
    /** Overrides the label-length widths, for groups whose idle segments are icons. */
    segmentWeight: ((active: Boolean) -> Float)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrolling) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (index in 0 until itemCount) {
            val active = index == selectedIndex
            val text = label(index)
            val start by animateDpAsState(
                if (active || index == 0) 26.dp else 8.dp, SpringOvershoot, label = "modeStart"
            )
            val end by animateDpAsState(
                if (active || index == itemCount - 1) 26.dp else 8.dp,
                SpringOvershoot,
                label = "modeEnd"
            )
            val weight by animateFloatAsState(
                segmentWeight?.invoke(active) ?: (text.length + if (active) 5f else 3f),
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "modeWeight"
            )
            val background by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else inactiveColor,
                label = "modeBackground"
            )
            Row(
                modifier = Modifier
                    .then(if (scrolling) Modifier else Modifier.weight(weight))
                    .height(52.dp)
                    .clip(RoundedCornerShape(start, end, end, start))
                    .background(background)
                    .clickable(enabled = enabled, role = Role.Tab, onClickLabel = text) {
                        onSelect(index)
                    }
                    .semantics { this.selected = active }
                    .testTag(tag(index))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading?.invoke(index, active)
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                trailing?.invoke(index, active)
            }
        }
    }
}

/** A mode's parameter rulers, laid out for [layout]. */
@Composable
private fun ColourParams(
    params: List<FinishingParam>,
    icons: List<ImageVector>,
    activeKey: String?,
    onSelect: (String) -> Unit,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    edits: PhotoEdits,
    enabled: Boolean,
    layout: ColourLayout
) {
    @Composable
    fun ruler(param: FinishingParam, showMeta: Boolean, modifier: Modifier = Modifier) {
        FinishingParamControls(
            state = state,
            actions = actions,
            param = param,
            label = stringResource(param.labelRes),
            enabled = enabled,
            identity = state.info?.reference to param.key,
            modifier = modifier,
            publishResetToMenu = false,
            showMetaRow = showMeta,
            limitLabels = !showMeta
        )
    }
    val sideBySide = layout == ColourLayout.WideSheet && params.size <= 2
    when {
        layout == ColourLayout.Inspector -> params.forEach { param -> ruler(param, showMeta = true) }
        sideBySide -> Row(Modifier.fillMaxWidth()) {
            params.forEach { param -> ruler(param, showMeta = true, modifier = Modifier.weight(1f)) }
        }
        else -> {
            val active = params.firstOrNull { it.key == activeKey } ?: params.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                params.forEachIndexed { index, param ->
                    ColourParamChip(
                        label = stringResource(param.labelRes),
                        icon = icons[index],
                        value = param.displayOf(edits),
                        modified = param.read(edits) != param.neutral,
                        selected = param.key == active.key,
                        enabled = enabled,
                        tag = "editor.param.${param.key}",
                        onClick = { onSelect(param.key) }
                    )
                }
            }
            ruler(active, showMeta = false, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}

/**
 * A parameter selector: the third level of the hierarchy, outlined rather than
 * filled so it reads with the ruler below it. Same cell as Light's strip, at
 * the full touch height.
 */
@Composable
internal fun ColourParamChip(
    label: String,
    icon: ImageVector,
    value: String,
    modified: Boolean,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "colourChipBackground"
    )
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "colourChipBorder"
    )
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(1.5.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = label) { onClick() }
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (modified) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun BasicsMode(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    edits: PhotoEdits,
    enabled: Boolean,
    layout: ColourLayout,
    toolUi: PhotoToolUiState
) {
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val grade = edits.finishing.grade
    val protectLabel = stringResource(Res.string.editor_protect_skin)
    ColourParams(
        params = listOf(SaturationParameter, VibranceParameter),
        icons = listOf(EditorSymbols.WaterDrop, EditorSymbols.AutoAwesome),
        activeKey = toolUi.colourBasicsParam,
        onSelect = { toolUi.colourBasicsParam = it },
        state = state,
        actions = actions,
        edits = edits,
        enabled = enabled,
        layout = layout
    )
    // It only damps vibrance, so with no vibrance there is nothing to protect.
    val canToggle = enabled && !state.interactionActive &&
        (grade.vibrance != 0f || grade.protectSkin)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = protectLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = grade.protectSkin,
            enabled = canToggle,
            onCheckedChange = { checked ->
                latestState.document?.toEdits()?.let { current ->
                    latestActions.applyEdits(
                        protectLabel,
                        current.withGrade(current.finishing.grade.copy(protectSkin = checked))
                    )
                }
            },
            modifier = Modifier.testTag(TAG_COLOUR_PROTECT_SKIN)
        )
    }
}

@Composable
private fun bandLabel(band: PhotoHslBand): String = stringResource(
    when (band) {
        PhotoHslBand.Red -> Res.string.editor_band_red
        PhotoHslBand.Orange -> Res.string.editor_band_orange
        PhotoHslBand.Yellow -> Res.string.editor_band_yellow
        PhotoHslBand.Green -> Res.string.editor_band_green
        PhotoHslBand.Aqua -> Res.string.editor_band_aqua
        PhotoHslBand.Blue -> Res.string.editor_band_blue
        PhotoHslBand.Purple -> Res.string.editor_band_purple
        PhotoHslBand.Magenta -> Res.string.editor_band_magenta
    }
)

@Composable
private fun HslMode(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    edits: PhotoEdits,
    enabled: Boolean,
    layout: ColourLayout,
    toolUi: PhotoToolUiState
) {
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val grade = edits.finishing.grade
    val selectedBand = toolUi.colourBand
    val selectedName = bandLabel(selectedBand)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        PhotoHslBand.entries.forEach { band ->
            HueDot(
                band = band,
                label = bandLabel(band),
                selected = band == selectedBand,
                modified = !grade.adjustment(band).isNeutral,
                enabled = enabled,
                onClick = { toolUi.colourBand = band },
                modifier = Modifier.weight(1f)
            )
        }
    }
    val resetBandLabel = stringResource(Res.string.editor_reset_band)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selectedName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                latestState.document?.toEdits()?.let { current ->
                    latestActions.applyEdits(
                        resetBandLabel,
                        current.withGrade(
                            current.finishing.grade.withAdjustment(
                                selectedBand,
                                PhotoHslAdjustment()
                            )
                        )
                    )
                }
            },
            enabled = enabled && !state.interactionActive &&
                !grade.adjustment(selectedBand).isNeutral,
            modifier = Modifier.testTag(TAG_COLOUR_RESET_BAND)
        ) {
            Text(resetBandLabel, maxLines = 1, softWrap = false)
        }
    }
    ColourParams(
        params = hslParameters(selectedBand),
        icons = listOf(
            EditorSymbols.ColorLens,
            EditorSymbols.WaterDrop,
            EditorSymbols.LightMode
        ),
        activeKey = hslParameters(selectedBand)
            .firstOrNull { it.key.endsWith(".${toolUi.colourHslParam}") }?.key,
        onSelect = { toolUi.colourHslParam = it.substringAfterLast('.') },
        state = state,
        actions = actions,
        edits = edits,
        enabled = enabled,
        layout = layout
    )
}

/**
 * One band's selector. Selection changes the shape as well as the ring, so it
 * does not rely on colour; the dot under it marks an edited band.
 */
@Composable
private fun HueDot(
    band: PhotoHslBand,
    label: String,
    selected: Boolean,
    modified: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size by animateDpAsState(if (selected) 34.dp else 26.dp, SpringOvershoot, label = "dotSize")
    val corner by animateDpAsState(if (selected) 12.dp else 13.dp, SpringOvershoot, label = "dotCorner")
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = label) { onClick() }
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .testTag("editor.colour.band.${band.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(bandColor(band.centerHue))
        )
        if (selected) {
            Box(
                Modifier
                    .size(size + 6.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(corner + 3.dp))
            )
        }
        if (modified) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun WheelsMode(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    edits: PhotoEdits,
    enabled: Boolean,
    layout: ColourLayout,
    narrow: Boolean,
    toolUi: PhotoToolUiState
) {
    val grade = edits.finishing.grade
    val selected = toolUi.colourRegion

    @Composable
    fun wheel(region: ColourRegion, size: Dp, ring: Boolean) {
        ColourWheelControl(
            region = region,
            wheel = grade.wheel(region),
            size = size,
            ring = ring,
            enabled = enabled && !state.interactionActive,
            state = state,
            actions = actions,
            onSelect = { toolUi.colourRegion = region }
        )
    }

    @Composable
    fun caption(region: ColourRegion, modifier: Modifier = Modifier) {
        val w = grade.wheel(region)
        Text(
            text = stringResource(region.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = modifier
        )
        Text(
            text = wheelReadout(w),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }

    @Composable
    fun ruler(region: ColourRegion, modifier: Modifier = Modifier) {
        val param = wheelLuminanceParameter(region)
        FinishingParamControls(
            state = state,
            actions = actions,
            param = param,
            label = stringResource(region.labelRes) + " · " +
                stringResource(Res.string.editor_luminance),
            enabled = enabled,
            identity = state.info?.reference to param.key,
            modifier = modifier,
            publishResetToMenu = false,
            showMetaRow = false,
            limitLabels = true
        )
    }

    when {
        layout != ColourLayout.Inspector -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ColourRegion.entries.forEach { region ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        wheel(
                            region,
                            if (layout == ColourLayout.WideSheet) 152.dp else 96.dp,
                            ring = region == selected
                        )
                        caption(region, Modifier.padding(top = 4.dp))
                    }
                }
            }
            ruler(selected, Modifier.padding(bottom = 8.dp))
        }
        narrow -> ColourRegion.entries.forEach { region ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                wheel(region, 132.dp, ring = false)
                caption(region, Modifier.padding(top = 4.dp))
                ruler(region)
            }
        }
        else -> ColourRegion.entries.forEach { region ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                wheel(region, 112.dp, ring = false)
                Column(Modifier.weight(1f)) {
                    Column(Modifier.padding(start = 16.dp, bottom = 4.dp)) { caption(region) }
                    ruler(region)
                }
            }
        }
    }
}

private fun wheelReadout(wheel: PhotoColourWheel): String =
    "${wheel.hueDegrees.roundToInt()}° · ${(wheel.amount * 100f).roundToInt()}"

/**
 * A colour wheel: drag anywhere to set the push's hue (angle, red at the top,
 * clockwise) and amount (distance from the centre). One drag is one undoable
 * edit. The custom actions give assistive tech the same control in steps.
 */
@Composable
private fun ColourWheelControl(
    region: ColourRegion,
    wheel: PhotoColourWheel,
    size: Dp,
    ring: Boolean,
    enabled: Boolean,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    onSelect: () -> Unit
) {
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSelect by rememberUpdatedState(onSelect)
    val name = stringResource(region.labelRes)
    val description = stringResource(Res.string.editor_wheel_description, name)
    val moreLabel = stringResource(Res.string.editor_wheel_more)
    val lessLabel = stringResource(Res.string.editor_wheel_less)
    val forwardLabel = stringResource(Res.string.editor_wheel_turn_forward)
    val backLabel = stringResource(Res.string.editor_wheel_turn_back)
    val ringColor = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    fun editsFor(current: PhotoEdits, next: PhotoColourWheel): PhotoEdits {
        val grade = current.finishing.grade
        return current.withGrade(grade.withWheel(region, next))
    }

    fun step(transform: (PhotoColourWheel) -> PhotoColourWheel): Boolean {
        val current = latestState.document?.toEdits() ?: return false
        if (!latestEnabled) return false
        val next = transform(current.finishing.grade.wheel(region))
        latestActions.applyEdits(name, editsFor(current, next))
        return true
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .testTag("editor.colour.wheel.${region.key}")
            .semantics {
                role = Role.Image
                contentDescription = description
                stateDescription = wheelReadout(wheel)
                customActions = listOf(
                    CustomAccessibilityAction(moreLabel) {
                        step { it.copy(amount = (it.amount + 0.05f).coerceAtMost(1f)) }
                    },
                    CustomAccessibilityAction(lessLabel) {
                        step { it.copy(amount = (it.amount - 0.05f).coerceAtLeast(0f)) }
                    },
                    CustomAccessibilityAction(forwardLabel) {
                        step { it.copy(hueDegrees = (it.hueDegrees + 15f) % 360f) }
                    },
                    CustomAccessibilityAction(backLabel) {
                        step { it.copy(hueDegrees = (it.hueDegrees + 345f) % 360f) }
                    }
                )
            }
            .pointerInput(region) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!latestEnabled || latestState.document == null) return@awaitEachGesture
                    latestSelect()
                    try {
                        latestActions.beginEdit(name)
                    } catch (rejected: IllegalStateException) {
                        // Another interaction owns the draft; this drag is inert.
                        return@awaitEachGesture
                    }
                    fun update(position: Offset) {
                        val current = latestState.document?.toEdits() ?: return
                        val radius = this.size.width / 2f
                        val dx = position.x - radius
                        val dy = position.y - this.size.height / 2f
                        val amount = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                        var degrees = atan2(dx, -dy) * 180f / PI.toFloat()
                        if (degrees < 0f) degrees += 360f
                        latestActions.updateEdits(
                            editsFor(
                                current,
                                current.finishing.grade.wheel(region).copy(
                                    hueDegrees = degrees.roundToInt().toFloat(),
                                    amount = (amount * 100f).roundToInt() / 100f
                                )
                            )
                        )
                    }
                    down.consume()
                    update(down.position)
                    val released = drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                    if (released) latestActions.commitEdit() else latestActions.cancelEdit()
                }
            }
    ) {
        val radius = this.size.minDimension / 2f
        rotate(-90f) {
            drawCircle(
                brush = Brush.sweepGradient(
                    List(7) { Color.hsv(it * 60f % 360f, 0.8f, 1f) },
                    center = center
                ),
                radius = radius
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF0F0D13),
                0.62f to Color(0xE00F0D13),
                1f to Color(0x800F0D13),
                center = center,
                radius = radius - 3.dp.toPx()
            ),
            radius = radius - 3.dp.toPx()
        )
        val inset = radius * 0.24f
        drawLine(guide, Offset(center.x, inset), Offset(center.x, this.size.height - inset), 1.dp.toPx())
        drawLine(guide, Offset(inset, center.y), Offset(this.size.width - inset, center.y), 1.dp.toPx())
        if (ring) {
            drawCircle(ringColor, radius, style = Stroke(2.dp.toPx()))
        }
        val angle = wheel.hueDegrees * PI.toFloat() / 180f
        val puck = Offset(
            center.x + sin(angle) * radius * wheel.amount,
            center.y - cos(angle) * radius * wheel.amount
        )
        if (wheel.amount > 0f) {
            drawCircle(Color.hsv(wheel.hueDegrees % 360f, 0.9f, 0.95f), 8.dp.toPx(), puck)
        }
        drawCircle(Color.White, 9.dp.toPx(), puck, style = Stroke(2.dp.toPx()))
    }
}
