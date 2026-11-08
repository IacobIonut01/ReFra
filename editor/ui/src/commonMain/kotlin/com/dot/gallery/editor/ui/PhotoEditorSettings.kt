package com.dot.gallery.editor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RawOn
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_blue
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_green
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_channel_red
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_mode_rgb
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_jpeg
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_png
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff16
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff8
import com.dot.gallery.editor.ui.generated.resources.editor_settings_clipping
import com.dot.gallery.editor.ui.generated.resources.editor_settings_clipping_summary
import com.dot.gallery.editor.ui.generated.resources.editor_settings_copy_format
import com.dot.gallery.editor.ui.generated.resources.editor_settings_copy_format_summary
import com.dot.gallery.editor.ui.generated.resources.editor_settings_format_heif
import com.dot.gallery.editor.ui.generated.resources.editor_settings_format_jxl
import com.dot.gallery.editor.ui.generated.resources.editor_settings_format_original
import com.dot.gallery.editor.ui.generated.resources.editor_settings_format_webp
import com.dot.gallery.editor.ui.generated.resources.editor_settings_grid
import com.dot.gallery.editor.ui.generated.resources.editor_settings_grid_summary
import com.dot.gallery.editor.ui.generated.resources.editor_settings_histogram
import com.dot.gallery.editor.ui.generated.resources.editor_settings_histogram_luma
import com.dot.gallery.editor.ui.generated.resources.editor_settings_photo_title
import com.dot.gallery.editor.ui.generated.resources.editor_settings_pinned
import com.dot.gallery.editor.ui.generated.resources.editor_settings_raw_ask
import com.dot.gallery.editor.ui.generated.resources.editor_settings_raw_export
import com.dot.gallery.editor.ui.generated.resources.editor_settings_saving
import com.dot.gallery.editor.ui.generated.resources.editor_settings_workspace
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_SETTINGS_HISTOGRAM = "editor.settings.histogram"
internal const val TAG_SETTINGS_CLIPPING = "editor.settings.clipping"
internal const val TAG_SETTINGS_GRID = "editor.settings.grid"
internal const val TAG_SETTINGS_PINNED = "editor.settings.pinned"
internal const val TAG_SETTINGS_COPY_FORMAT = "editor.settings.copyFormat"
internal const val TAG_SETTINGS_RAW_FORMAT = "editor.settings.rawFormat"

/** The RAW save containers, in the RAW export sheet's order; null asks. */
internal val RAW_SETTING_FORMATS = listOf(
    null, PhotoExportFormat.JPEG, PhotoExportFormat.PNG,
    PhotoExportFormat.TIFF_8, PhotoExportFormat.TIFF_16
)

/** The copy containers offered: Original, then what the host can write (TIFF is RAW-only). */
internal fun photoCopyFormats(supported: Set<PhotoExportFormat>): List<PhotoExportFormat?> =
    listOf(null) + PhotoExportFormat.entries.filter { it in supported && !it.isTiff }

private fun photoFormatLabel(format: PhotoExportFormat): StringResource = when (format) {
    PhotoExportFormat.JPEG -> Res.string.editor_raw_export_jpeg
    PhotoExportFormat.PNG -> Res.string.editor_raw_export_png
    PhotoExportFormat.WEBP -> Res.string.editor_settings_format_webp
    PhotoExportFormat.HEIF -> Res.string.editor_settings_format_heif
    PhotoExportFormat.JXL -> Res.string.editor_settings_format_jxl
    PhotoExportFormat.TIFF_8 -> Res.string.editor_raw_export_tiff8
    PhotoExportFormat.TIFF_16 -> Res.string.editor_raw_export_tiff16
}

private fun histogramLabel(mode: PhotoHistogramMode): StringResource = when (mode) {
    PhotoHistogramMode.Rgb -> Res.string.editor_histogram_mode_rgb
    PhotoHistogramMode.Red -> Res.string.editor_histogram_channel_red
    PhotoHistogramMode.Green -> Res.string.editor_histogram_channel_green
    PhotoHistogramMode.Blue -> Res.string.editor_histogram_channel_blue
    PhotoHistogramMode.Luma -> Res.string.editor_settings_histogram_luma
}

/**
 * The Photo editor settings page: the histogram and straighten-grid
 * defaults, the pinned dock tools (the pin manager) and the save
 * containers. Opened from the Tools catalogue.
 */
@Composable
internal fun PhotoEditorSettingsPage(
    store: EditorSettingsStore,
    workspace: EditorWorkspaceState,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    exportFormats: Set<PhotoExportFormat>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = store.settings
    val layers = stringResource(Res.string.editor_layers)
    val pinnedNames = workspace.pinnedPhotoDestinations.mapNotNull { destination ->
        if (destination == PHOTO_PIN_LAYERS) layers
        else entries.firstOrNull { it.descriptor.id.value == destination }
            ?.let { stringResource(it.descriptor.title) }
    }
    val modes = PhotoHistogramMode.entries
    val copyFormats = photoCopyFormats(exportFormats)
    val original = stringResource(Res.string.editor_settings_format_original)
    val ask = stringResource(Res.string.editor_settings_raw_ask)
    val modeLabels = modes.map { stringResource(histogramLabel(it)) }
    val copyLabels = copyFormats.map { it?.let { f -> stringResource(photoFormatLabel(f)) } ?: original }
    val rawLabels = RAW_SETTING_FORMATS.map { it?.let { f -> stringResource(photoFormatLabel(f)) } ?: ask }
    EditorSettingsPage(
        title = stringResource(Res.string.editor_settings_photo_title),
        onBack = onBack,
        modifier = modifier,
        sections = listOf(
            EditorSettingsSection(
                stringResource(Res.string.editor_settings_workspace),
                listOf(
                    EditorSettingRow.Choice(
                        Icons.Outlined.ShowChart,
                        stringResource(Res.string.editor_settings_histogram), null,
                        TAG_SETTINGS_HISTOGRAM,
                        options = modeLabels,
                        selected = modes.indexOf(settings.histogramMode),
                        onSelect = { i -> store.update { it.copy(histogramMode = modes[i]) } }
                    ),
                    EditorSettingRow.Toggle(
                        Icons.Outlined.Contrast,
                        stringResource(Res.string.editor_settings_clipping),
                        stringResource(Res.string.editor_settings_clipping_summary),
                        TAG_SETTINGS_CLIPPING,
                        checked = settings.histogramClipping,
                        onCheckedChange = { on -> store.update { it.copy(histogramClipping = on) } }
                    ),
                    EditorSettingRow.Toggle(
                        Icons.Outlined.GridOn,
                        stringResource(Res.string.editor_settings_grid),
                        stringResource(Res.string.editor_settings_grid_summary),
                        TAG_SETTINGS_GRID,
                        checked = settings.straightenGrid,
                        onCheckedChange = { on -> store.update { it.copy(straightenGrid = on) } }
                    ),
                    EditorSettingRow.Action(
                        Icons.Outlined.PushPin,
                        stringResource(Res.string.editor_settings_pinned),
                        pinnedNames.joinToString(", ").ifEmpty { null },
                        TAG_SETTINGS_PINNED,
                        onClick = { workspace.pinEditorOpen = true }
                    )
                )
            ),
            EditorSettingsSection(
                stringResource(Res.string.editor_settings_saving),
                listOf(
                    EditorSettingRow.Choice(
                        Icons.Outlined.Image,
                        stringResource(Res.string.editor_settings_copy_format),
                        stringResource(Res.string.editor_settings_copy_format_summary),
                        TAG_SETTINGS_COPY_FORMAT,
                        options = copyLabels,
                        selected = copyFormats.indexOf(settings.copyFormat).coerceAtLeast(0),
                        onSelect = { i -> store.update { it.copy(copyFormat = copyFormats[i]) } }
                    ),
                    EditorSettingRow.Choice(
                        Icons.Outlined.RawOn,
                        stringResource(Res.string.editor_settings_raw_export), null,
                        TAG_SETTINGS_RAW_FORMAT,
                        options = rawLabels,
                        selected = RAW_SETTING_FORMATS.indexOf(settings.rawFormat).coerceAtLeast(0),
                        onSelect = { i -> store.update { it.copy(rawFormat = RAW_SETTING_FORMATS[i]) } }
                    )
                )
            )
        )
    )
}

/**
 * The container Save copy writes, or null to ask (a RAW without a stored
 * choice) — [settings] resolved against what the host can write. A stored
 * TIFF only applies while the RAW edit is develop-only; otherwise a RAW asks.
 */
internal fun resolveSaveFormat(
    settings: EditorSettings,
    isRaw: Boolean,
    tiffAllowed: Boolean,
    supported: Set<PhotoExportFormat>
): SaveFormatDecision = if (isRaw) {
    val raw = settings.rawFormat
    when {
        raw == null -> SaveFormatDecision.Ask
        raw.isTiff && !tiffAllowed -> SaveFormatDecision.Ask
        else -> SaveFormatDecision.Write(raw)
    }
} else {
    SaveFormatDecision.Write(settings.copyFormat?.takeIf { it in supported })
}

/** What Save copy does: ask (the RAW sheet) or write [format] (null = the source's container). */
internal sealed interface SaveFormatDecision {
    data object Ask : SaveFormatDecision
    data class Write(val format: PhotoExportFormat?) : SaveFormatDecision
}
