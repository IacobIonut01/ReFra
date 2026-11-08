package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Saveable view state for the editor workspace — which tool is selected,
 * whether its contextual panel is open, which pane is showing, and the
 * focus/catalogue overlays. It deliberately holds no document or history
 * state: the session remains the source of truth for edits.
 *
 * Instances are keyed on the source reference via
 * [rememberEditorWorkspaceState], so a layout flip preserves the selection
 * and a source switch starts clean.
 */
@Stable
class EditorWorkspaceState(
    activeTool: String = DEFAULT_TOOL,
    toolPanelExpanded: Boolean = false,
    panel: EditorWorkspacePanel = EditorWorkspacePanel.Edit,
    focusMode: Boolean = false,
    catalogueOpen: Boolean = false,
    selectedLayerId: String? = null,
    pinnedPhotoDestinations: List<String> = DEFAULT_PHOTO_PINS,
    pinEditorOpen: Boolean = false
) {
    var activeTool by mutableStateOf(EditorToolId(activeTool))
    var toolPanelExpanded by mutableStateOf(toolPanelExpanded)
    var panel by mutableStateOf(panel)
    var focusMode by mutableStateOf(focusMode)
    var catalogueOpen by mutableStateOf(catalogueOpen)
    var pinEditorOpen by mutableStateOf(pinEditorOpen)

    /** The Photo editor settings page covers the workspace. Not saved: it reopens closed. */
    var settingsOpen by mutableStateOf(false)

    /**
     * The ordered destinations pinned to the compact home pill — tool
     * ids plus the reserved [PHOTO_PIN_LAYERS] slot, capped at
     * [MAX_PHOTO_PINS]. The permanent Tools slot is not part of this
     * list, so the pill always keeps a working catalogue route.
     */
    var pinnedPhotoDestinations by mutableStateOf(pinnedPhotoDestinations)

    /**
     * Pin [destination] when absent and the list is under the cap, or
     * unpin it when already pinned. Unknown destinations are ignored so
     * a stale restored id can never pin a tool that no longer exists.
     */
    fun togglePhotoPin(destination: String) {
        val current = pinnedPhotoDestinations
        pinnedPhotoDestinations = when {
            destination in current -> current - destination
            current.size < MAX_PHOTO_PINS &&
                destination in PHOTO_PINNABLE_DESTINATIONS ->
                current + destination
            else -> current
        }
    }

    /** Move a pinned destination [delta] slots within the list. */
    fun movePhotoPin(destination: String, delta: Int) {
        val current = pinnedPhotoDestinations.toMutableList()
        val from = current.indexOf(destination)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (to == from) return
        current.add(to, current.removeAt(from))
        pinnedPhotoDestinations = current
    }

    /**
     * The layer the Layers surface paints as selected — a real layer id,
     * or null for the base image. The list keeps it valid as the
     * document changes (removed layers fall back to the topmost visible
     * layer or the base); it is view state, never part of the recipe.
     */
    var selectedLayerId by mutableStateOf(selectedLayerId)

    /** Opens/closes the Tools catalogue overlay; closing picks nothing. */
    fun toggleCatalogue() {
        catalogueOpen = !catalogueOpen
    }

    /** Selects [tool] and opens its controls in the Edit pane. */
    fun selectTool(tool: EditorToolId) {
        activeTool = tool
        panel = EditorWorkspacePanel.Edit
        toolPanelExpanded = true
        catalogueOpen = false
    }

    /**
     * Shows [next] in the contextual pane; toggling the open pane closes it
     * back to the canvas-only state. The Edit pane is not a toggle target —
     * tools open it via [selectTool].
     */
    fun togglePanel(next: EditorWorkspacePanel) {
        require(next != EditorWorkspacePanel.Edit) {
            "Edit opens through selectTool()"
        }
        if (panel == next && toolPanelExpanded) {
            panel = EditorWorkspacePanel.Edit
            toolPanelExpanded = false
        } else {
            panel = next
            toolPanelExpanded = true
        }
        catalogueOpen = false
    }

    /** Closes whichever contextual surface is open, if any. */
    fun collapseContext(): Boolean {
        return when {
            catalogueOpen -> {
                catalogueOpen = false
                true
            }
            toolPanelExpanded || panel != EditorWorkspacePanel.Edit -> {
                toolPanelExpanded = false
                panel = EditorWorkspacePanel.Edit
                true
            }
            else -> false
        }
    }

    companion object {
        private val DEFAULT_TOOL = EditorToolIds.PhotoLight.value

        val Saver: Saver<EditorWorkspaceState, Any> = listSaver(
            save = {
                listOf(
                    it.activeTool.value,
                    it.toolPanelExpanded,
                    it.panel.name,
                    it.focusMode,
                    it.catalogueOpen,
                    it.selectedLayerId,
                    it.pinnedPhotoDestinations.joinToString(","),
                    it.pinEditorOpen
                )
            },
            restore = { saved ->
                EditorWorkspaceState(
                    activeTool = saved[0] as String,
                    toolPanelExpanded = saved[1] as Boolean,
                    // A stale/foreign panel name restores as the default
                    // Edit pane — never crashes on old save data.
                    panel = EditorWorkspacePanel.entries.firstOrNull {
                        it.name == saved[2] as String
                    } ?: EditorWorkspacePanel.Edit,
                    focusMode = saved[3] as Boolean,
                    catalogueOpen = saved[4] as Boolean,
                    // Added later — a 5-item save has no selection.
                    selectedLayerId = saved.getOrNull(5) as? String,
                    // Added later — restores only on a 7+-item save;
                    // unknown/duplicate values drop out and the defaults
                    // apply only when the slot is absent entirely.
                    pinnedPhotoDestinations =
                        (saved.getOrNull(6) as? String)
                            ?.split(",")
                            ?.filter { it.isNotBlank() }
                            ?.distinct()
                            ?.filter { it in PHOTO_PINNABLE_DESTINATIONS }
                            ?.take(MAX_PHOTO_PINS)
                            ?: DEFAULT_PHOTO_PINS,
                    pinEditorOpen = saved.getOrNull(7) as? Boolean ?: false
                )
            }
        )
    }
}

/**
 * The reserved pin destination for the Layers pane — deliberately not an
 * [EditorToolId] so it can ride the pinned list next to tool ids without
 * colliding with the registry.
 */
internal const val PHOTO_PIN_LAYERS = "workspace.layers"

/** The dock holds at most four saveable favourites plus Tools and Colour. */
internal const val MAX_PHOTO_PINS = 4

internal val DEFAULT_PHOTO_PINS = listOf(
    EditorToolIds.PhotoCrop.value,
    EditorToolIds.PhotoLight.value,
    EditorToolIds.PhotoMarkup.value,
    EditorToolIds.PhotoLooks.value
)

/**
 * Every destination the pin UI may offer — the Layers pane plus the
 * catalogued photo tools. Ids absent from this set can neither be
 * pinned through [EditorWorkspaceState.togglePhotoPin] nor survive a
 * restore. Colour is excluded because the dock's fixed last slot owns
 * it; pinning it would duplicate the slot.
 */
internal val PHOTO_PINNABLE_DESTINATIONS: Set<String> = setOf(
    PHOTO_PIN_LAYERS,
    EditorToolIds.PhotoLight.value,
    EditorToolIds.PhotoCurves.value,
    EditorToolIds.PhotoWhiteBalance.value,
    EditorToolIds.PhotoDetail.value,
    EditorToolIds.PhotoVignette.value,
    EditorToolIds.PhotoLensBlur.value,
    EditorToolIds.PhotoGrain.value,
    EditorToolIds.PhotoLooks.value,
    EditorToolIds.PhotoCrop.value,
    EditorToolIds.PhotoPerspective.value,
    EditorToolIds.PhotoDoubleExposure.value,
    EditorToolIds.PhotoTransform.value,
    EditorToolIds.PhotoBlend.value,
    EditorToolIds.PhotoMarkup.value,
    EditorToolIds.PhotoCutout.value,
    EditorToolIds.PhotoRemoveBackground.value,
    EditorToolIds.PhotoColourTone.value,
    EditorToolIds.PhotoPosterize.value,
    EditorToolIds.PhotoEdges.value,
    EditorToolIds.PhotoBorders.value
)

/**
 * One workspace state per media source: keyed on [sourceReference] so a new
 * document resets the view while rotation and resize keep it. [initialTool]
 * lets each editor seed its own default (the video workbench never lands on
 * a photo tool id); a restored saved selection always wins.
 */
@Composable
internal fun rememberEditorWorkspaceState(
    sourceReference: String?,
    initialTool: EditorToolId = EditorToolIds.PhotoLight
): EditorWorkspaceState = rememberSaveable(
    sourceReference,
    saver = EditorWorkspaceState.Saver
) {
    EditorWorkspaceState(activeTool = initialTool.value)
}
