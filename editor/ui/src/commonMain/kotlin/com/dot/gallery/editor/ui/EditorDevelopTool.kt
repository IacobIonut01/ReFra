package com.dot.gallery.editor.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoRawColourSpace
import com.dot.gallery.editor.core.model.PhotoRawDemosaic
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoRawHighlights
import com.dot.gallery.editor.core.model.PhotoRawNoiseReduction
import com.dot.gallery.editor.core.model.PhotoRawWhiteBalance
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_develop_auto
import com.dot.gallery.editor.ui.generated.resources.editor_develop_blend
import com.dot.gallery.editor.ui.generated.resources.editor_develop_brightness
import com.dot.gallery.editor.ui.generated.resources.editor_develop_camera
import com.dot.gallery.editor.ui.generated.resources.editor_develop_clip
import com.dot.gallery.editor.ui.generated.resources.editor_develop_colour_space
import com.dot.gallery.editor.ui.generated.resources.editor_develop_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_develop_custom
import com.dot.gallery.editor.ui.generated.resources.editor_develop_daylight
import com.dot.gallery.editor.ui.generated.resources.editor_develop_demosaic
import com.dot.gallery.editor.ui.generated.resources.editor_develop_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_develop_half
import com.dot.gallery.editor.ui.generated.resources.editor_develop_high
import com.dot.gallery.editor.ui.generated.resources.editor_develop_highlight_recovery
import com.dot.gallery.editor.ui.generated.resources.editor_develop_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_develop_low
import com.dot.gallery.editor.ui.generated.resources.editor_develop_medium
import com.dot.gallery.editor.ui.generated.resources.editor_develop_noise
import com.dot.gallery.editor.ui.generated.resources.editor_develop_off
import com.dot.gallery.editor.ui.generated.resources.editor_develop_rebuild
import com.dot.gallery.editor.ui.generated.resources.editor_develop_reset
import com.dot.gallery.editor.ui.generated.resources.editor_develop_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_develop_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_develop_sharpen
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tab_colour
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tab_detail
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tab_output
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tab_tone
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tab_wb
import com.dot.gallery.editor.ui.generated.resources.editor_develop_temperature
import com.dot.gallery.editor.ui.generated.resources.editor_develop_tint
import com.dot.gallery.editor.ui.generated.resources.editor_develop_vibrance
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_jpeg
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_png
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_subtitle
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff16
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff8
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff_note
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_title
import com.dot.gallery.editor.ui.icons.ColorLens
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.LightMode
import com.dot.gallery.editor.ui.icons.Tune
import com.dot.gallery.editor.ui.icons.WbSunny
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_DEVELOP = "editor.category.develop"
internal const val TAG_RAW_EXPORT_SHEET = "editor.rawExport"

internal enum class DevelopTab(val labelRes: StringResource) {
    WhiteBalance(Res.string.editor_develop_tab_wb),
    Tone(Res.string.editor_develop_tab_tone),
    Detail(Res.string.editor_develop_tab_detail),
    Colour(Res.string.editor_develop_tab_colour),
    Output(Res.string.editor_develop_tab_output)
}

private inline fun PhotoEdits.developing(
    update: (PhotoRawDevelop) -> PhotoRawDevelop
): PhotoEdits = copy(develop = update(develop))

private fun unitParam(
    key: String,
    label: StringResource,
    read: (PhotoRawDevelop) -> Float,
    write: (PhotoRawDevelop, Float) -> PhotoRawDevelop
) = FinishingParam(
    key = key,
    labelRes = label,
    displayRange = -100f..100f,
    step = 1f,
    neutral = 0f,
    read = { read(it.develop) * 100f },
    write = { e, v -> e.developing { write(it, v / 100f) } }
)

private val TemperatureParam = FinishingParam(
    key = "developTemperature",
    labelRes = Res.string.editor_develop_temperature,
    displayRange = 2000f..12000f,
    step = 50f,
    neutral = 5500f,
    signed = false,
    unitSuffix = " K",
    trackGradient = listOf(Color(0xFF5B9BFF), Color(0xFFE6E2D6), Color(0xFFFFB74D)),
    read = { it.develop.temperatureKelvin },
    write = { e, v -> e.developing { it.copy(temperatureKelvin = v) } }
)

private val TintParam = unitParam(
    "developTint", Res.string.editor_develop_tint,
    { it.tint }, { d, v -> d.copy(tint = v) }
).let {
    FinishingParam(
        key = it.key,
        labelRes = it.labelRes,
        displayRange = it.displayRange,
        step = it.step,
        neutral = it.neutral,
        trackGradient = listOf(Color(0xFF66BB6A), Color(0xFFE6E2D6), Color(0xFFD16BD1)),
        read = it.read,
        write = it.write
    )
}

private val ToneParams = listOf(
    FinishingParam(
        key = "developExposure",
        labelRes = Res.string.editor_develop_exposure,
        displayRange = -3f..3f,
        step = 0.1f,
        neutral = 0f,
        read = { it.develop.exposure },
        write = { e, v -> e.developing { it.copy(exposure = v) } }
    ),
    FinishingParam(
        key = "developBrightness",
        labelRes = Res.string.editor_develop_brightness,
        displayRange = -2f..2f,
        step = 0.1f,
        neutral = 0f,
        read = { it.develop.brightness },
        write = { e, v -> e.developing { it.copy(brightness = v) } }
    ),
    unitParam("developContrast", Res.string.editor_develop_contrast, { it.contrast }, { d, v -> d.copy(contrast = v) }),
    unitParam("developShadows", Res.string.editor_develop_shadows, { it.shadows }, { d, v -> d.copy(shadows = v) }),
    unitParam("developHighlights", Res.string.editor_develop_highlights, { it.highlights }, { d, v -> d.copy(highlights = v) })
)

private val SharpenParam = FinishingParam(
    key = "developSharpen",
    labelRes = Res.string.editor_develop_sharpen,
    displayRange = 0f..100f,
    step = 1f,
    neutral = 0f,
    signed = false,
    read = { it.develop.sharpen * 50f },
    write = { e, v -> e.developing { it.copy(sharpen = v / 50f) } }
)

private val ColourParams = listOf(
    unitParam("developSaturation", Res.string.editor_develop_saturation, { it.saturation }, { d, v -> d.copy(saturation = v) }),
    unitParam("developVibrance", Res.string.editor_develop_vibrance, { it.vibrance }, { d, v -> d.copy(vibrance = v) })
)

/** Labels for the Colour tab's spaces; product names, so they stay untranslated. */
private fun colourSpaceLabel(space: PhotoRawColourSpace) = when (space) {
    PhotoRawColourSpace.Srgb -> "sRGB"
    PhotoRawColourSpace.AdobeRgb -> "Adobe RGB"
    PhotoRawColourSpace.Wide -> "Wide"
    PhotoRawColourSpace.ProPhoto -> "ProPhoto"
}

/** Labels for the demosaic algorithms; acronyms, so they stay untranslated. */
private fun demosaicLabel(demosaic: PhotoRawDemosaic) = when (demosaic) {
    PhotoRawDemosaic.Linear -> "Linear"
    PhotoRawDemosaic.Vng -> "VNG"
    PhotoRawDemosaic.Ppg -> "PPG"
    PhotoRawDemosaic.Ahd -> "AHD"
    PhotoRawDemosaic.Dcb -> "DCB"
}

/**
 * Develop: five connected tabs over the RAW recipe. Tile options show a real
 * develop of the photo. Every change lands in the recipe, and the session
 * re-decodes behind it, so undo and the saved copy both follow.
 */
@Composable
internal fun DevelopToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val sourceRef = state.info?.reference
    var tab by rememberSaveable(sourceRef) { mutableStateOf(DevelopTab.WhiteBalance) }
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val develop = state.document?.develop ?: PhotoRawDevelop()
    val resetLabel = stringResource(Res.string.editor_develop_reset)

    val toolMenu = LocalPhotoToolMenu.current
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }
    toolMenu?.apply {
        resetParameterLabel = resetLabel
        resetParameterTag = "editor.develop.reset"
        resetParameterEnabled = enabled && !develop.isNeutral
        onResetParameter = {
            latestState.document?.toEdits()?.let {
                latestActions.applyEdits(resetLabel, it.copy(develop = PhotoRawDevelop()))
            }
        }
    }

    fun apply(update: (PhotoRawDevelop) -> PhotoRawDevelop) {
        val edits = latestState.document?.toEdits() ?: return
        latestActions.applyEdits(resetLabel, edits.developing(update))
    }

    val tabs = DevelopTab.entries
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RetouchCardPadding)
            .testTag("editor.develop"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectedButtonGroup(
            itemCount = tabs.size,
            selectedIndex = tabs.indexOf(tab),
            enabled = enabled,
            // The five labels fit a phone at normal text; large text scrolls.
            scrolling = LocalDensity.current.fontScale > 1.15f,
            label = { stringResource(tabs[it].labelRes) },
            tag = { "editor.develop.tab.${tabs[it].name}" },
            onSelect = {
                if (tabs[it] != tab) {
                    latestActions.cancelEdit()
                    tab = tabs[it]
                }
            }
        )
        when (tab) {
            DevelopTab.WhiteBalance -> {
                val options = listOf(
                    PhotoRawWhiteBalance.Camera to Res.string.editor_develop_camera,
                    PhotoRawWhiteBalance.Auto to Res.string.editor_develop_auto,
                    PhotoRawWhiteBalance.Daylight to Res.string.editor_develop_daylight,
                    PhotoRawWhiteBalance.Custom to Res.string.editor_develop_custom
                )
                DevelopTiles(
                    options = options.map { (mode, label) ->
                        DevelopTile(
                            name = mode.name,
                            label = stringResource(label),
                            selected = develop.whiteBalance == mode,
                            preview = PhotoRawDevelop(whiteBalance = mode),
                            onClick = { apply { it.copy(whiteBalance = mode) } }
                        )
                    },
                    enabled = enabled,
                    actions = actions
                )
                if (develop.whiteBalance == PhotoRawWhiteBalance.Custom) {
                    DevelopParamGroup(state, actions, enabled, listOf(TemperatureParam, TintParam))
                }
            }
            DevelopTab.Tone -> DevelopParamGroup(state, actions, enabled, ToneParams)
            DevelopTab.Detail -> {
                DevelopParamGroup(state, actions, enabled, listOf(SharpenParam))
                DevelopOptions(
                    label = stringResource(Res.string.editor_develop_demosaic),
                    options = PhotoRawDemosaic.entries.map {
                        DevelopOption(it.name, demosaicLabel(it), develop.demosaic == it) {
                            apply { d -> d.copy(demosaic = it) }
                        }
                    },
                    enabled = enabled
                )
                DevelopOptions(
                    label = stringResource(Res.string.editor_develop_noise),
                    options = listOf(
                        PhotoRawNoiseReduction.Off to Res.string.editor_develop_off,
                        PhotoRawNoiseReduction.Low to Res.string.editor_develop_low,
                        PhotoRawNoiseReduction.Medium to Res.string.editor_develop_medium,
                        PhotoRawNoiseReduction.High to Res.string.editor_develop_high
                    ).map { (mode, label) ->
                        DevelopOption(mode.name, stringResource(label), develop.noiseReduction == mode) {
                            apply { d -> d.copy(noiseReduction = mode) }
                        }
                    },
                    enabled = enabled
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.editor_develop_half),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = develop.halfSize,
                        enabled = enabled,
                        onCheckedChange = { checked -> apply { it.copy(halfSize = checked) } },
                        modifier = Modifier.testTag("editor.develop.half")
                    )
                }
            }
            DevelopTab.Colour -> {
                DevelopParamGroup(state, actions, enabled, ColourParams)
                DevelopOptions(
                    label = stringResource(Res.string.editor_develop_colour_space),
                    options = PhotoRawColourSpace.entries.map {
                        DevelopOption(it.name, colourSpaceLabel(it), develop.colourSpace == it) {
                            apply { d -> d.copy(colourSpace = it) }
                        }
                    },
                    enabled = enabled
                )
            }
            DevelopTab.Output -> {
                Text(
                    text = stringResource(Res.string.editor_develop_highlight_recovery),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                DevelopTiles(
                    options = listOf(
                        PhotoRawHighlights.Clip to Res.string.editor_develop_clip,
                        PhotoRawHighlights.Blend to Res.string.editor_develop_blend,
                        PhotoRawHighlights.Rebuild to Res.string.editor_develop_rebuild
                    ).map { (mode, label) ->
                        DevelopTile(
                            name = mode.name,
                            label = stringResource(label),
                            selected = develop.highlightRecovery == mode,
                            preview = PhotoRawDevelop(highlightRecovery = mode),
                            onClick = { apply { it.copy(highlightRecovery = mode) } }
                        )
                    },
                    enabled = enabled,
                    actions = actions
                )
            }
        }
    }
}

private fun paramIcon(key: String): ImageVector = when (key) {
    "developTemperature" -> EditorSymbols.WbSunny
    "developTint" -> EditorSymbols.ColorLens
    "developExposure", "developBrightness" -> EditorSymbols.LightMode
    "developContrast" -> RetouchSymbols.Contrast
    "developSaturation", "developVibrance" -> EditorSymbols.ColorLens
    else -> EditorSymbols.Tune
}

/** Chips pick a parameter when there are several, over the shared ruler. */
@Composable
private fun DevelopParamGroup(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    enabled: Boolean,
    params: List<FinishingParam>
) {
    val sourceRef = state.info?.reference
    var activeKey by rememberSaveable(sourceRef, params.first().key) {
        mutableStateOf(params.first().key)
    }
    val active = params.firstOrNull { it.key == activeKey } ?: params.first()
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val latestActions by rememberUpdatedState(actions)
    if (params.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            params.forEach { param ->
                ColourParamChip(
                    label = stringResource(param.labelRes),
                    icon = paramIcon(param.key),
                    value = param.displayOf(edits),
                    modified = param.read(edits) != param.neutral,
                    selected = param.key == active.key,
                    enabled = enabled,
                    tag = "editor.develop.param.${param.key}",
                    onClick = {
                        if (param.key != activeKey) {
                            latestActions.cancelEdit()
                            activeKey = param.key
                        }
                    }
                )
            }
        }
    }
    FinishingParamControls(
        state = state,
        actions = actions,
        param = active,
        label = stringResource(active.labelRes),
        enabled = enabled,
        identity = sourceRef to active.key,
        publishResetToMenu = false,
        showMetaRow = false,
        limitLabels = true,
        insetContent = false,
        modifier = Modifier.padding(top = 14.dp)
    )
}

private class DevelopOption(
    val name: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun DevelopOptions(label: String, options: List<DevelopOption>, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                FinishingChip(
                    label = option.label,
                    selected = option.selected,
                    enabled = enabled,
                    tag = "editor.develop.option.${option.name}",
                    onClick = option.onClick
                )
            }
        }
    }
}

private class DevelopTile(
    val name: String,
    val label: String,
    val selected: Boolean,
    val preview: PhotoRawDevelop,
    val onClick: () -> Unit
)

/** Option tiles; each decode waits its turn so a tab never floods the worker. */
@Composable
private fun DevelopTiles(
    options: List<DevelopTile>,
    enabled: Boolean,
    actions: PhotoEditorActions
) {
    val latestActions by rememberUpdatedState(actions)
    val turn = remember { Mutex() }
    val thumbnails = remember { mutableStateMapOf<PhotoRawDevelop, ImageBitmap>() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            LaunchedEffect(option.preview) {
                if (option.preview !in thumbnails) {
                    turn.withLock {
                        latestActions.renderDevelopThumbnail(option.preview)
                            ?.let { thumbnails[option.preview] = it.toEditorImageBitmap() }
                    }
                }
            }
            val corner by animateDpAsState(if (option.selected) 28.dp else 16.dp, label = "tile")
            val shape = RoundedCornerShape(corner)
            val thumbnail = thumbnails[option.preview]
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = option.label,
                        onClick = option.onClick
                    )
                    .semantics { selected = option.selected }
                    .testTag("editor.develop.tile.${option.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (option.selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (option.selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The developed RAW's format sheet. A TIFF comes straight from the RAW decoder at
 * full bit depth, so it can only carry the develop; once anything else is edited
 * the TIFF rows go and a note says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoRawExportSheet(
    allowTiff: Boolean,
    onSelect: (PhotoExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .testTag(TAG_RAW_EXPORT_SHEET),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.editor_raw_export_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.editor_raw_export_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val rows = buildList {
                add(PhotoExportFormat.JPEG to Res.string.editor_raw_export_jpeg)
                add(PhotoExportFormat.PNG to Res.string.editor_raw_export_png)
                if (allowTiff) {
                    add(PhotoExportFormat.TIFF_8 to Res.string.editor_raw_export_tiff8)
                    add(PhotoExportFormat.TIFF_16 to Res.string.editor_raw_export_tiff16)
                }
            }
            rows.forEach { (format, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onSelect(format) }
                        .testTag("$TAG_RAW_EXPORT_SHEET.${format.name}")
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RetouchSymbols.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (!allowTiff) {
                Text(
                    text = stringResource(Res.string.editor_raw_export_tiff_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp)
                        .testTag("$TAG_RAW_EXPORT_SHEET.note")
                )
            }
        }
    }
}
