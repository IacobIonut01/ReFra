package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.renderPhotoLookPreview
import com.dot.gallery.editor.render.renderPhotoPresetPreview
import com.dot.gallery.editor.render.sampledTo
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_look_bleach
import com.dot.gallery.editor.ui.generated.resources.editor_look_blue_hour
import com.dot.gallery.editor.ui.generated.resources.editor_look_chrome
import com.dot.gallery.editor.ui.generated.resources.editor_look_cinema_green
import com.dot.gallery.editor.ui.generated.resources.editor_look_cinematic
import com.dot.gallery.editor.ui.generated.resources.editor_look_clean
import com.dot.gallery.editor.ui.generated.resources.editor_look_cool
import com.dot.gallery.editor.ui.generated.resources.editor_look_desert
import com.dot.gallery.editor.ui.generated.resources.editor_look_fade
import com.dot.gallery.editor.ui.generated.resources.editor_look_forest
import com.dot.gallery.editor.ui.generated.resources.editor_look_golden
import com.dot.gallery.editor.ui.generated.resources.editor_look_matte
import com.dot.gallery.editor.ui.generated.resources.editor_look_mono
import com.dot.gallery.editor.ui.generated.resources.editor_look_new_preset
import com.dot.gallery.editor.ui.generated.resources.editor_look_noir
import com.dot.gallery.editor.ui.generated.resources.editor_look_none
import com.dot.gallery.editor.ui.generated.resources.editor_look_pastel
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_default_name
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_delete
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_name
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_nothing
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_options
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_rename
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_rename_title
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_save_title
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_summary
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_update
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_update_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_update_summary
import com.dot.gallery.editor.ui.generated.resources.editor_look_preset_update_title
import com.dot.gallery.editor.ui.generated.resources.editor_look_rose
import com.dot.gallery.editor.ui.generated.resources.editor_look_sepia
import com.dot.gallery.editor.ui.generated.resources.editor_look_teal_orange
import com.dot.gallery.editor.ui.generated.resources.editor_look_vintage
import com.dot.gallery.editor.ui.generated.resources.editor_look_vivid
import com.dot.gallery.editor.ui.generated.resources.editor_look_warm_fade
import com.dot.gallery.editor.ui.generated.resources.editor_looks_all
import com.dot.gallery.editor.ui.generated.resources.editor_looks_cinematic
import com.dot.gallery.editor.ui.generated.resources.editor_looks_colour
import com.dot.gallery.editor.ui.generated.resources.editor_looks_film
import com.dot.gallery.editor.ui.generated.resources.editor_looks_mine
import com.dot.gallery.editor.ui.generated.resources.editor_looks_mono
import com.dot.gallery.editor.ui.generated.resources.editor_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_save
import com.dot.gallery.editor.ui.generated.resources.editor_strength
import com.dot.gallery.editor.ui.icons.Check
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.MoreVert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val TAG_CATEGORY_LOOKS = "editor.category.looks"
internal const val TAG_LOOK_NEW_PRESET = "editor.look.preset.new"
internal const val TAG_LOOK_PRESET_NAME_FIELD = "editor.look.preset.field"
internal const val TAG_LOOK_PRESET_CONFIRM = "editor.look.preset.confirm"
internal const val TAG_LOOK_PRESET_CANCEL = "editor.look.preset.cancel"
internal const val TAG_LOOK_PRESET_RENAME = "editor.look.preset.rename"
internal const val TAG_LOOK_PRESET_UPDATE = "editor.look.preset.update"
internal const val TAG_LOOK_PRESET_DELETE = "editor.look.preset.delete"
internal const val TAG_LOOK_PRESET_DELETE_CONFIRM = "editor.look.preset.delete.confirm"
internal const val TAG_LOOK_PRESET_EMPTY_HINT = "editor.look.preset.empty"
internal const val TAG_LOOK_PRESET_SHEET = "editor.look.preset.sheet"
internal const val TAG_LOOK_PRESET_PREVIEW = "editor.look.preset.preview"

/** A borderless filter tile — smaller than the old cards so more fit. */
private val LookTileWidth = 88.dp
private val LookThumbHeight = 64.dp

/** The localized display name of a look id — shared by the tiles and
 * the edits summary so a name never paints raw enum text. */
internal fun photoLookLabelRes(id: PhotoLookId): StringResource = when (id) {
    PhotoLookId.None -> Res.string.editor_look_none
    PhotoLookId.Clean -> Res.string.editor_look_clean
    PhotoLookId.Vivid -> Res.string.editor_look_vivid
    PhotoLookId.Pastel -> Res.string.editor_look_pastel
    PhotoLookId.Chrome -> Res.string.editor_look_chrome
    PhotoLookId.WarmFade -> Res.string.editor_look_warm_fade
    PhotoLookId.Cool -> Res.string.editor_look_cool
    PhotoLookId.Golden -> Res.string.editor_look_golden
    PhotoLookId.Desert -> Res.string.editor_look_desert
    PhotoLookId.Forest -> Res.string.editor_look_forest
    PhotoLookId.Rose -> Res.string.editor_look_rose
    PhotoLookId.BlueHour -> Res.string.editor_look_blue_hour
    PhotoLookId.Cinematic -> Res.string.editor_look_cinematic
    PhotoLookId.TealOrange -> Res.string.editor_look_teal_orange
    PhotoLookId.CinemaGreen -> Res.string.editor_look_cinema_green
    PhotoLookId.Vintage -> Res.string.editor_look_vintage
    PhotoLookId.Fade -> Res.string.editor_look_fade
    PhotoLookId.Matte -> Res.string.editor_look_matte
    PhotoLookId.Bleach -> Res.string.editor_look_bleach
    PhotoLookId.Sepia -> Res.string.editor_look_sepia
    PhotoLookId.Mono -> Res.string.editor_look_mono
    PhotoLookId.Noir -> Res.string.editor_look_noir
}

private class BuiltInLook(val id: PhotoLookId, val key: String)

/** Every shipped look, in catalogue order (the All tab's order). */
private val BuiltInLooks = listOf(
    BuiltInLook(PhotoLookId.Clean, "clean"),
    BuiltInLook(PhotoLookId.Vivid, "vivid"),
    BuiltInLook(PhotoLookId.Pastel, "pastel"),
    BuiltInLook(PhotoLookId.Chrome, "chrome"),
    BuiltInLook(PhotoLookId.WarmFade, "warmFade"),
    BuiltInLook(PhotoLookId.Cool, "cool"),
    BuiltInLook(PhotoLookId.Golden, "golden"),
    BuiltInLook(PhotoLookId.Desert, "desert"),
    BuiltInLook(PhotoLookId.Forest, "forest"),
    BuiltInLook(PhotoLookId.Rose, "rose"),
    BuiltInLook(PhotoLookId.BlueHour, "blueHour"),
    BuiltInLook(PhotoLookId.Cinematic, "cinematic"),
    BuiltInLook(PhotoLookId.TealOrange, "tealOrange"),
    BuiltInLook(PhotoLookId.CinemaGreen, "cinemaGreen"),
    BuiltInLook(PhotoLookId.Vintage, "vintage"),
    BuiltInLook(PhotoLookId.Fade, "fade"),
    BuiltInLook(PhotoLookId.Matte, "matte"),
    BuiltInLook(PhotoLookId.Bleach, "bleach"),
    BuiltInLook(PhotoLookId.Sepia, "sepia"),
    BuiltInLook(PhotoLookId.Mono, "mono"),
    BuiltInLook(PhotoLookId.Noir, "noir")
)

private val OriginalLook = BuiltInLook(PhotoLookId.None, "none")

/** The catalogue's families; My presets also carries the create tile. */
private enum class LooksCategory(val key: String, val labelRes: StringResource) {
    All("all", Res.string.editor_looks_all),
    Colour("colour", Res.string.editor_looks_colour),
    Cinematic("cinematic", Res.string.editor_looks_cinematic),
    Film("film", Res.string.editor_looks_film),
    Mono("mono", Res.string.editor_looks_mono),
    Mine("mine", Res.string.editor_looks_mine);

    fun includes(id: PhotoLookId): Boolean = when (this) {
        All -> id != PhotoLookId.None
        Colour -> id in ColourLooks
        Cinematic -> id in CinematicLooks
        Film -> id in FilmLooks
        Mono -> id in MonoLooks
        Mine -> false
    }

    companion object {
        private val ColourLooks = setOf(
            PhotoLookId.Clean, PhotoLookId.Vivid, PhotoLookId.Pastel,
            PhotoLookId.Chrome, PhotoLookId.WarmFade, PhotoLookId.Cool,
            PhotoLookId.Golden, PhotoLookId.Desert, PhotoLookId.Forest,
            PhotoLookId.Rose, PhotoLookId.BlueHour
        )
        private val CinematicLooks = setOf(
            PhotoLookId.Cinematic, PhotoLookId.TealOrange,
            PhotoLookId.CinemaGreen
        )
        private val FilmLooks = setOf(
            PhotoLookId.Vintage, PhotoLookId.Fade,
            PhotoLookId.Matte, PhotoLookId.Bleach, PhotoLookId.Sepia
        )
        private val MonoLooks = setOf(PhotoLookId.Mono, PhotoLookId.Noir)
    }
}

private val LookStrengthParam = FinishingParam(
    key = "lookStrength",
    labelRes = Res.string.editor_strength,
    displayRange = 0f..100f,
    step = 1f,
    neutral = 100f,
    signed = false,
    // The model stores a 0–1 blend; the ruler and its readout speak the
    // percentage the design shows.
    unitSuffix = "%",
    read = { it.finishing.look.amount * 100f },
    write = { e, v ->
        e.copy(
            finishing = e.finishing.copy(
                look = e.finishing.look.copy(amount = v / 100f)
            )
        )
    }
)

/**
 * The Looks tool per the reference: a bare intensity scrubber (its
 * endpoints painted on the track, no title), a strip of small borderless
 * look tiles, and the catalogue chips along the card's bottom edge.
 *
 * Ordering: Original first, then the user's presets, then the built-ins
 * for the selected family. Every tile renders from the preview through
 * the shared finishing processor; a user preset renders through the
 * colour stage as well, so a tile is the recipe it writes.
 *
 * Presets capture the colour grade and the finishing look (with its
 * blend); creating one requires a non-neutral grade, the dialog shows
 * exactly what it saves, and the overflow menu can rename, re-capture
 * (update with the current grade) or delete it.
 */
@Composable
internal fun LooksToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val look = edits.finishing.look
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val store = LocalEditorLookPresetStore.current
    val presets = store?.presets.orEmpty()
    var category by remember { mutableStateOf(LooksCategory.All) }
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    var pendingDelete by remember { mutableStateOf<LookPreset?>(null) }

    val labels = (listOf(OriginalLook) + BuiltInLooks)
        .associate { it.id to stringResource(photoLookLabelRes(it.id)) }
    val categoryLabels = LooksCategory.entries.associate {
        it to stringResource(it.labelRes)
    }
    val lightLabels = LightParameters.associateWith { stringResource(it.labelRes) }
    val saturationLabel = stringResource(Res.string.editor_saturation)
    val nothingToSave = stringResource(Res.string.editor_look_preset_nothing)

    // The concrete capture, item by item: what the preset sheet lists and
    // what makes a grade "empty".
    fun adjustmentsOf(
        color: ColorAdjustments,
        lookId: PhotoLookId,
        amount: Float
    ): List<Pair<String, String>> = buildList {
        LightParameters.forEach { param ->
            if (param.read(color) != 0f) {
                add(lightLabels.getValue(param) to param.displayOf(color))
            }
        }
        if (color.saturation != 1f) {
            add(saturationLabel to "${color.saturation}×")
        }
        if (lookId != PhotoLookId.None) {
            add(
                (labels[lookId] ?: lookId.name) to
                    "${(amount * 100f).roundToInt()}%"
            )
        }
    }

    val currentAdjustments = adjustmentsOf(edits.color, look.id, look.amount)
    val gradeIsEmpty = currentAdjustments.isEmpty()

    // Tiles render from the unedited source when the session has one —
    // presets read as what they do, not as "more of the current recipe".
    // The preview is sampled down once: a 96 dp tile never needs a
    // 1024 px grade, and the smaller source keeps a 20-look strip fast.
    val previewSource = state.originalPreview ?: state.preview
    val thumbnails by produceState<Map<PhotoLookId, ImageBitmap>>(
        initialValue = emptyMap(),
        previewSource
    ) {
        val full: SrgbPixels = previewSource ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val source = full.sampledTo(THUMBNAIL_EDGE)
            (listOf(OriginalLook) + BuiltInLooks).associate { preset ->
                preset.id to renderPhotoLookPreview(
                    source, PhotoLook(preset.id)
                ).toEditorImageBitmap()
            }
        }
    }
    // User preset previews run the colour stage too; they are rendered
    // only while that family is on screen.
    val presetThumbnails by produceState<Map<String, ImageBitmap>>(
        initialValue = emptyMap(),
        previewSource,
        presets,
        category
    ) {
        // Presets appear in All (after Original) as well as in Mine.
        if (category != LooksCategory.Mine && category != LooksCategory.All) {
            return@produceState
        }
        val full: SrgbPixels = previewSource ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val source = full.sampledTo(THUMBNAIL_EDGE)
            presets.associate { preset ->
                preset.id to renderPhotoPresetPreview(
                    source,
                    preset.color,
                    PhotoLook(preset.lookId, preset.lookAmount)
                ).toEditorImageBitmap()
            }
        }
    }

    val applyWord = stringResource(Res.string.editor_apply)
    fun applyLook(preset: BuiltInLook) {
        if (preset.id == look.id) return
        val current = latestState.document?.toEdits() ?: return
        latestActions.applyEdits(
            labels.getValue(preset.id),
            current.copy(
                finishing = current.finishing.copy(
                    look = current.finishing.look.copy(id = preset.id)
                )
            )
        )
    }

    fun applyUserPreset(preset: LookPreset) {
        val current = latestState.document?.toEdits() ?: return
        latestActions.applyEdits(
            "$applyWord ${preset.name}",
            current.copy(
                color = preset.color,
                finishing = current.finishing.copy(
                    look = PhotoLook(preset.lookId, preset.lookAmount)
                )
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // 1. The intensity: a bare scrubber whose track paints its own
        // 0 % / 100 % endpoints, above the filters (no title).
        FinishingParamControls(
            state = state,
            actions = actions,
            param = LookStrengthParam,
            label = stringResource(Res.string.editor_strength),
            enabled = enabled && look.id != PhotoLookId.None,
            identity = state.info?.reference to "lookStrength",
            showMetaRow = false,
            limitLabels = true,
            compact = true,
            // The same 16 dp below the rail as at its sides.
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        // 2. The filter strip — small borderless tiles.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LookThumbHeight + 22.dp)
                // Pinned to the viewport, not the content, so the tiles
                // fade at the edges while a cut tile is softened; the
                // width is the strip's inset so a resting tile is not.
                .softHorizontalEdges(Color(0xFF141416), width = 16.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (category == LooksCategory.Mine) {
                NewPresetTile(
                    enabled = enabled && !gradeIsEmpty,
                    onClick = {
                        dialog = PresetDialog(
                            mode = PresetMode.Create,
                            preset = null,
                            initialName = "",
                            nextNumber = presets.size + 1
                        )
                    }
                )
                presets.forEach { preset ->
                    val selectedPreset = edits.color == preset.color &&
                        look.id == preset.lookId &&
                        abs(look.amount - preset.lookAmount) < 1e-4f
                    UserPresetTile(
                        preset = preset,
                        thumbnail = presetThumbnails[preset.id],
                        selected = selectedPreset,
                        enabled = enabled && !state.interactionActive,
                        onApply = { applyUserPreset(preset) },
                        onRename = {
                            dialog = PresetDialog(
                                mode = PresetMode.Rename,
                                preset = preset,
                                initialName = preset.name,
                                nextNumber = presets.size + 1
                            )
                        },
                        onUpdate = {
                            dialog = PresetDialog(
                                mode = PresetMode.Update,
                                preset = preset,
                                initialName = preset.name,
                                nextNumber = presets.size + 1
                            )
                        },
                        canUpdate = !gradeIsEmpty,
                        onDelete = { pendingDelete = preset }
                    )
                }
            } else {
                LookTile(
                    key = OriginalLook.key,
                    label = labels.getValue(PhotoLookId.None),
                    thumbnail = thumbnails[PhotoLookId.None],
                    selected = look.id == PhotoLookId.None,
                    enabled = enabled && !state.interactionActive,
                    onClick = { applyLook(OriginalLook) }
                )
                if (category == LooksCategory.All) {
                    presets.forEach { preset ->
                        val selectedPreset = edits.color == preset.color &&
                            look.id == preset.lookId &&
                            abs(look.amount - preset.lookAmount) < 1e-4f
                        UserPresetTile(
                            preset = preset,
                            thumbnail = presetThumbnails[preset.id],
                            selected = selectedPreset,
                            enabled = enabled && !state.interactionActive,
                            onApply = { applyUserPreset(preset) },
                            onRename = {
                                dialog = PresetDialog(
                                    mode = PresetMode.Rename,
                                    preset = preset,
                                    initialName = preset.name,
                                    nextNumber = presets.size + 1
                                )
                            },
                            onUpdate = {
                                dialog = PresetDialog(
                                    mode = PresetMode.Update,
                                    preset = preset,
                                    initialName = preset.name,
                                    nextNumber = presets.size + 1
                                )
                            },
                            canUpdate = !gradeIsEmpty,
                            onDelete = { pendingDelete = preset }
                        )
                    }
                }
                BuiltInLooks
                    .filter { category.includes(it.id) }
                    .forEach { preset ->
                        LookTile(
                            key = preset.key,
                            label = labels.getValue(preset.id),
                            thumbnail = thumbnails[preset.id],
                            selected = look.id == preset.id,
                            enabled = enabled && !state.interactionActive,
                            onClick = { applyLook(preset) }
                        )
                    }
            }
        }
        // 3. Why the create tile is inert, when it is.
        if (category == LooksCategory.Mine && gradeIsEmpty) {
            Text(
                text = nothingToSave,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    .testTag(TAG_LOOK_PRESET_EMPTY_HINT)
            )
        }
        // 4. The catalogue chips along the card's bottom edge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                // Pinned to the viewport, not the content, so the chips
                // fade at the edges while a cut chip is softened; the
                // width is the strip's inset so a resting chip is not.
                .softHorizontalEdges(Color(0xFF141416), width = 16.dp)
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LooksCategory.entries.forEach { entry ->
                val selected = entry == category
                val chipBg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(180),
                    label = "lookCategoryBg"
                )
                val chipText by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(180),
                    label = "lookCategoryText"
                )
                val chipScale by animateFloatAsState(
                    targetValue = if (selected) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "lookCategoryScale"
                )
                Text(
                    text = categoryLabels.getValue(entry),
                    style = MaterialTheme.typography.labelLarge,
                    color = chipText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(chipBg)
                        .clickable(
                            enabled = enabled,
                            role = Role.Tab,
                            onClickLabel = categoryLabels.getValue(entry)
                        ) { category = entry }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("editor.look.category.${entry.key}")
                )
            }
        }
    }

    dialog?.let { pending ->
        val isRename = pending.mode == PresetMode.Rename
        val preset = pending.preset
        val fallbackName = stringResource(
            Res.string.editor_look_preset_default_name, pending.nextNumber
        )
        // A rename only edits the name; create/update show the grade they
        // would capture.
        val color = if (isRename) preset?.color ?: ColorAdjustments() else edits.color
        val lookId = if (isRename) preset?.lookId ?: PhotoLookId.None else look.id
        val lookAmount = if (isRename) preset?.lookAmount ?: 1f else look.amount
        PresetSheet(
            title = when (pending.mode) {
                PresetMode.Create -> stringResource(
                    Res.string.editor_look_preset_save_title
                )
                PresetMode.Update -> stringResource(
                    Res.string.editor_look_preset_update_title
                )
                PresetMode.Rename -> stringResource(
                    Res.string.editor_look_preset_rename_title
                )
            },
            confirmLabel = if (pending.mode == PresetMode.Update) {
                stringResource(Res.string.editor_look_preset_update_confirm)
            } else {
                stringResource(Res.string.editor_save)
            },
            initialName = pending.initialName.ifBlank { fallbackName },
            showPreview = !isRename,
            previewSource = previewSource,
            color = color,
            lookId = lookId,
            lookAmount = lookAmount,
            adjustments = adjustmentsOf(color, lookId, lookAmount),
            emptyReason = if (adjustmentsOf(color, lookId, lookAmount).isEmpty()) {
                nothingToSave
            } else null,
            onConfirm = { name ->
                if (store != null) {
                    when (pending.mode) {
                        PresetMode.Create -> {
                            store.add(
                                LookPreset(
                                    id = store.newId(),
                                    name = name,
                                    color = edits.color,
                                    lookId = look.id,
                                    lookAmount = look.amount
                                )
                            )
                            category = LooksCategory.Mine
                        }
                        PresetMode.Rename -> preset?.let {
                            store.rename(it.id, name)
                        }
                        PresetMode.Update -> {
                            preset?.let {
                                store.update(it.id, edits.color, look.id, look.amount)
                            }
                            category = LooksCategory.Mine
                        }
                    }
                }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.editor_look_preset_delete)) },
            text = { Text(preset.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store?.remove(preset.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag(TAG_LOOK_PRESET_DELETE_CONFIRM)
                ) {
                    Text(stringResource(Res.string.editor_look_preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.editor_cancel))
                }
            }
        )
    }
}

private const val THUMBNAIL_EDGE = 320
private const val PREVIEW_EDGE = 480

private enum class PresetMode { Create, Rename, Update }

/** The pending preset dialog. */
private data class PresetDialog(
    val mode: PresetMode,
    val preset: LookPreset?,
    val initialName: String,
    val nextNumber: Int
)

@Composable
private fun LookTile(
    key: String,
    label: String,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(LookTileWidth)
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .testTag("editor.look.$key"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TileThumbnail(
            thumbnail = thumbnail,
            selected = selected,
            thumbTag = "editor.look.thumb.$key",
            checkTag = "editor.look.check.$key"
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun UserPresetTile(
    preset: LookPreset,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    canUpdate: Boolean,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(Res.string.editor_look_preset_options, preset.name)
    Column(
        modifier = Modifier
            .width(LookTileWidth)
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = preset.name,
                onClick = onApply
            )
            .semantics { this.selected = selected }
            .testTag("editor.look.preset.${preset.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            TileThumbnail(
                thumbnail = thumbnail,
                selected = selected,
                thumbTag = "editor.look.preset.thumb.${preset.id}",
                checkTag = "editor.look.preset.check.${preset.id}"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = optionsLabel
                    ) { menuOpen = true }
                    .semantics { contentDescription = optionsLabel }
                    .testTag("editor.look.preset.menu.${preset.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_look_preset_rename)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                    modifier = Modifier.testTag(TAG_LOOK_PRESET_RENAME)
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(Res.string.editor_look_preset_update))
                    },
                    enabled = canUpdate,
                    onClick = {
                        menuOpen = false
                        onUpdate()
                    },
                    modifier = Modifier.testTag(TAG_LOOK_PRESET_UPDATE)
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_look_preset_delete)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    modifier = Modifier.testTag(TAG_LOOK_PRESET_DELETE)
                )
            }
        }
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun NewPresetTile(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.editor_look_new_preset)
    Column(
        modifier = Modifier
            .width(LookTileWidth)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .testTag(TAG_LOOK_NEW_PRESET),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LookThumbHeight)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (enabled) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                            .copy(alpha = 0.4f)
                    },
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun TileThumbnail(
    thumbnail: ImageBitmap?,
    selected: Boolean,
    thumbTag: String,
    checkTag: String
) {
    // The selected tile rounds up: a visibly softer card while it is
    // the active look, back to crisp when it is not.
    val corner by animateDpAsState(
        targetValue = if (selected) 14.dp else 8.dp,
        animationSpec = tween(160),
        label = "lookTileCorner"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LookThumbHeight)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(corner)
                    )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LookThumbHeight)
                    .testTag(thumbTag)
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag(checkTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PresetSheet(
    title: String,
    confirmLabel: String,
    initialName: String,
    showPreview: Boolean,
    previewSource: SrgbPixels?,
    color: ColorAdjustments,
    lookId: PhotoLookId,
    lookAmount: Float,
    adjustments: List<Pair<String, String>>,
    emptyReason: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName.take(40)) }
    // The preview is the recipe itself, rendered from the same source
    // sample the tiles use.
    val preview by produceState<ImageBitmap?>(
        initialValue = null,
        previewSource,
        color,
        lookId,
        lookAmount,
        showPreview
    ) {
        if (!showPreview) return@produceState
        val full = previewSource ?: return@produceState
        value = withContext(Dispatchers.Default) {
            renderPhotoPresetPreview(
                full.sampledTo(PREVIEW_EDGE),
                color,
                PhotoLook(lookId, lookAmount)
            ).toEditorImageBitmap()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        val bottomInset = WindowInsets.safeDrawing
            .asPaddingValues()
            .calculateBottomPadding()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF141416),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_LOOK_PRESET_SHEET)
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 16.dp + bottomInset
                        )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (showPreview) {
                        Spacer(Modifier.height(12.dp))
                        SheetCard {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                            ) {
                                if (preview != null) {
                                    Image(
                                        bitmap = preview!!,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .testTag(TAG_LOOK_PRESET_PREVIEW)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SheetCard {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            singleLine = true,
                            label = {
                                Text(stringResource(Res.string.editor_look_preset_name))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TAG_LOOK_PRESET_NAME_FIELD)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SheetCard {
                        if (emptyReason != null) {
                            Text(
                                text = emptyReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            adjustments.forEachIndexed { index, (label, value) ->
                                if (index > 0) {
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag(TAG_LOOK_PRESET_CANCEL)
                        ) {
                            Text(stringResource(Res.string.editor_cancel))
                        }
                        Spacer(Modifier.weight(1f))
                        WorkbenchPrimaryAction(
                            label = confirmLabel,
                            onClick = { onConfirm(name.trim()) },
                            enabled = name.isNotBlank() && emptyReason == null,
                            icon = EditorSymbols.Check,
                            compact = true,
                            modifier = Modifier.testTag(TAG_LOOK_PRESET_CONFIRM)
                        )
                    }
                }
            }
        }
    }
}

/** One inset card inside the preset sheet. */
@Composable
private fun SheetCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}
