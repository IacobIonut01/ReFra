package com.dot.gallery.editor.ui

import androidx.compose.ui.unit.dp

/**
 * The fixed region metrics of the approved Figma V3 phone composition
 * (see `editor/figma-review/v3-latest/`). These heights are exact at the
 * canonical 412×832 editor-content frame: header 64, an 8 dp gap, the
 * 48 dp utility row, a 12 dp gap, the weighted canvas field and the
 * pinned 70 dp toolbar region — the canvas absorbs every non-fixed dp,
 * so the utility row lands at y=72 and the toolbar region top at 762.
 * Wide/Desktop geometry keeps its own per-mode constants.
 */
internal object EditorV2Layout {
    val Header = 64.dp
    val PhoneWorkspaceCanvas = 624.dp
    val PhoneUtility = 48.dp
    val PhoneToolbarRegion = 70.dp
    // Tool canvases retain a small inset; V4 uses generous field corners.
    val PhoneFieldMargin = 4.dp
    val PhoneFieldCorner = 28.dp
    // The labelled home pill — three saveable favourites plus Tools.
    val PhonePillWidth = 304.dp
    val PhonePillHeight = 68.dp
    // Default V4 tool peek; graphs and preset lists choose a larger peek.
    val PhoneSheetPeek = 220.dp
    // V4's expanded sheet includes the title, histogram and settings.
    val PhoneAdjustmentSheetMax = 600.dp
    // A 32 dp handle plus this body leaves the home visible above Tools.
    val PhoneToolsSheetContent = 480.dp
    val PhoneInlineInspectorMax = 450.dp
    val PhoneBreadcrumb = 32.dp
    val PhotoHorizontalInset = 16.dp
    val PhotoCorner = 8.dp
    val PanelTopCorner = 28.dp
    val PhoneVideoPreview = 240.dp
    val PhoneVideoTransport = 56.dp
    val PhoneVideoTimeline = 408.dp
    val PhoneVideoToolbar = 64.dp
    val PhoneVideoTrackGutter = 76.dp
}

/** Stable region tags for the Figma V3 phone composition. */
internal const val TAG_PHOTO_HEADER = "editor.region.header"
internal const val TAG_PHOTO_CANVAS = "editor.region.canvas"
internal const val TAG_PHOTO_UTILITY = "editor.region.utility"
internal const val TAG_PHOTO_TOOLBAR_REGION = "editor.region.toolbar"
internal const val TAG_PHOTO_BREADCRUMB = "editor.region.breadcrumb"
internal const val TAG_PHOTO_INSPECTOR = "editor.region.inspector"
internal const val TAG_PHOTO_SWITCHER = "editor.photo.switcher"
internal const val TAG_V3_ADJUSTMENT_SHEET = "editor.v3.adjustmentSheet"
internal const val TAG_V3_SHEET_HANDLE = "editor.v3.sheetHandle"
internal const val TAG_V3_SHEET_BODY = "editor.v3.sheetBody"
internal const val TAG_V3_INLINE_INSPECTOR = "editor.v3.inlineInspector"
internal const val TAG_EDITOR_PINS = "editor.pins"
internal const val TAG_EDITOR_PINS_EDIT = "editor.pins.edit"
internal const val TAG_PHOTO_PIN_PREFIX = "editor.pin."
internal const val TAG_EDITOR_CATALOGUE_SHEET = "editor.v3.toolsSheet"
internal const val TAG_CATALOGUE_CONTENT = "editor.catalogue.content"
internal const val TAG_MODAL_DRAG_HANDLE = "editor.modal.dragHandle"

/** The pinned-destination tag — `editor.pin.<destination>`. */
internal fun photoPinTag(destination: String) = "editor.pin.$destination"

/** The catalogue pin toggle tag — `editor.catalogue.pin.<toolId>`. */
internal fun cataloguePinTag(toolId: String) = "editor.catalogue.pin.$toolId"

/** Stable region tags for the Figma V3 video phone composition. */
internal const val TAG_VIDEO_REGION_HEADER = "videoEditor.region.header"
internal const val TAG_VIDEO_REGION_PREVIEW = "videoEditor.region.preview"
internal const val TAG_VIDEO_REGION_TRANSPORT = "videoEditor.region.transport"
internal const val TAG_VIDEO_REGION_TIMELINE = "videoEditor.region.timeline"
internal const val TAG_VIDEO_REGION_TOOLBAR = "videoEditor.region.toolbar"
internal const val TAG_VIDEO_V3_INLINE_INSPECTOR =
    "videoEditor.v3.inlineInspector"
internal const val TAG_VIDEO_TIMELINE_EFFECT_PREFIX =
    "videoEditor.timeline.effect."
internal const val TAG_VIDEO_TIMELINE_FADE_PREFIX =
    "videoEditor.timeline.fade."
