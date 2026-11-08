package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal const val TAG_PHOTO_SHEET_MATERIAL = "editor.toolSheet.material"

/** Null outside a compact tool sheet: static desktop inspectors keep their details. */
internal val LocalPhotoToolSheetExpanded = compositionLocalOf<Boolean?> { null }

/**
 * Tools whose card is the whole card: no declared collapsed/expanded
 * stages, so the sheet measures the composed content instead of a
 * hardcoded height. Everything else keeps its explicit stages.
 */
private val AutoSizedSheetTools = setOf(
    EditorToolIds.PhotoLight,
    EditorToolIds.PhotoColour,
    EditorToolIds.PhotoCurves,
    EditorToolIds.PhotoLooks,
    EditorToolIds.PhotoMarkup,
    EditorToolIds.PhotoCutout,
    EditorToolIds.PhotoRemoveBackground,
    EditorToolIds.PhotoColourTone,
    EditorToolIds.PhotoPosterize,
    EditorToolIds.PhotoEdges,
    EditorToolIds.PhotoBorders,
    EditorToolIds.PhotoDevelop
)

internal fun photoToolSheetIsAutoSized(tool: EditorToolId): Boolean =
    tool in AutoSizedSheetTools

/** The first frame's stand-in before the content has been measured. */
internal val AutoSizedSheetFallback = 180.dp

internal fun photoToolSheetPeek(tool: EditorToolId) = when (tool) {
    // Light at rest: 40 dp chips and the ruler slab with its readout
    // pill riding its top edge, plus the card's padding. The painted
    // window rides the scrubber's track ends, so no caption row, and a
    // fixed card paints no grab strip.
    EditorToolIds.PhotoLight -> 136.dp
    // Looks: the compact intensity rail, the small tile row, the
    // optional empty-grade hint and the catalogue chips at the card's
    // bottom edge (no grab strip).
    EditorToolIds.PhotoLooks -> 232.dp
    EditorToolIds.PhotoWhiteBalance -> 232.dp
    // Crop & straighten (Perspective is its Vertical tab): the aspect strip
    // and the control row only — the actions float under the header, so the
    // card is deliberately short and the frame owns the window.
    EditorToolIds.PhotoCrop, EditorToolIds.PhotoPerspective -> 152.dp
    EditorToolIds.PhotoLayerMask -> 260.dp
    EditorToolIds.PhotoDoubleExposure, EditorToolIds.PhotoBlend -> 280.dp
    EditorToolIds.PhotoTransform -> 280.dp
    EditorToolIds.PhotoLensBlur -> 232.dp
    EditorToolIds.PhotoGrain -> 292.dp
    EditorToolIds.PhotoVignette -> 236.dp
    EditorToolIds.PhotoDetail -> 260.dp
    else -> 156.dp
}

internal fun photoToolSheetHeight(tool: EditorToolId): Dp {
    // Light and Looks have no expanded state: their resting card is the
    // whole card (chips/catalogue and the shared controls).
    if (tool == EditorToolIds.PhotoLight || tool == EditorToolIds.PhotoLooks) {
        return photoToolSheetPeek(tool)
    }
    val base = when (tool) {
        EditorToolIds.PhotoTransform -> 320.dp
        EditorToolIds.PhotoBlend,
        EditorToolIds.PhotoDoubleExposure -> 330.dp
        EditorToolIds.PhotoCrop, EditorToolIds.PhotoPerspective -> 250.dp
        EditorToolIds.PhotoLayerMask -> 380.dp
        EditorToolIds.PhotoLooks -> 232.dp
        EditorToolIds.PhotoWhiteBalance -> 420.dp
        EditorToolIds.PhotoVignette -> 460.dp
        EditorToolIds.PhotoDetail -> 460.dp
        EditorToolIds.PhotoGrain -> 460.dp
        EditorToolIds.PhotoLensBlur -> 380.dp
        else -> 240.dp
    }
    // A tool whose secondary rows only compose once expanded still needs
    // real travel: the ceiling is never the peek.
    return maxOf(base, photoToolSheetPeek(tool) + 8.dp)
}

internal fun photoToolSheetExpansion(
    offset: Float,
    viewportHeight: Float,
    sheetHeight: Float,
    peekHeight: Float
): Float {
    if (!offset.isFinite() || viewportHeight <= 0f || sheetHeight <= 0f) return 0f
    val collapsed = (viewportHeight - peekHeight).coerceAtLeast(0f)
    val expanded = (viewportHeight - sheetHeight).coerceAtLeast(0f)
    val travel = collapsed - expanded
    if (travel <= 0f) return if (peekHeight >= viewportHeight) 1f else 0f
    if (abs(collapsed - offset) <= 0.5f) return 0f
    return ((collapsed - offset) / travel).coerceIn(0f, 1f)
}

/**
 * The floating tool card's material — the approved Stitch card, rounded
 * on every corner and always painted (the card is never a bare control
 * strip). [expansion] still drives the inner layout through
 * [LocalPhotoToolSheetExpanded]; it no longer gates the blur.
 */
@Composable
internal fun PhotoToolSheetSurface(
    expansion: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                // The tool card sits darker than the platform surface and
                // blurs deeper, so the photo's colours stay out of the
                // controls.
                .editorSheetBlur(
                    shape = RoundedCornerShape(28.dp),
                    baseColor = Color(0xFF141416),
                    blurRadius = 32.dp
                )
                .testTag(TAG_PHOTO_SHEET_MATERIAL)
        )
        content()
    }
}
