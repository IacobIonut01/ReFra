package com.dot.gallery.editor.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollToNode
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoErrorKind
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoRaster
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Decodes the committed JPEG fixture (see TEST_ASSETS.md) to pixels. */
internal fun samplePixels(): SrgbPixels {
    val image: BufferedImage =
        EditorTestFixtures::class.java.getResourceAsStream("/editor_sample.jpg")
            .use { ImageIO.read(it) }
    val size = PixelSize(image.width, image.height)
    val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    return SrgbPixels(size, argb)
}

private object EditorTestFixtures

/**
 * Deterministic raster over an in-memory pixel frame — raw regions are real
 * sub-rectangles so the session's preview pipeline does honest work.
 */
internal class FixturePhotoRaster(
    private val pixels: SrgbPixels,
    reference: String
) : PhotoRaster {
    override val info = PhotoInfo(
        reference = reference,
        displayName = reference.substringAfterLast('/'),
        rawSize = pixels.size
    )

    var closed = false
        private set

    override suspend fun readRegion(rect: PixelRect): SrgbPixels {
        val source = pixels.copyPixels()
        val out = IntArray(rect.width * rect.height)
        val stride = pixels.size.width
        for (row in 0 until rect.height) {
            val start = (rect.top + row) * stride + rect.left
            source.copyInto(out, row * rect.width, start, start + rect.width)
        }
        return SrgbPixels(PixelSize(rect.width, rect.height), out)
    }

    override suspend fun readPreview(maxEdge: Int): SrgbPixels = pixels

    override fun close() {
        closed = true
    }
}

/** Constructs and opens a session on the UI thread (sessions are owner-confined). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openedSession(pixels: SrgbPixels): PhotoEditorSession {
    lateinit var session: PhotoEditorSession
    runOnUiThread {
        session = PhotoEditorSession(
            scope = CoroutineScope(Dispatchers.Main),
            media = FixturePhotoMediaAccess { pixels },
            onError = {}
        )
        session.open("fixture://editor_sample.jpg")
    }
    return session
}

/** Waits until the session has a document and the canvas bitmap has landed. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitImage(session: PhotoEditorSession) {
    waitUntil(timeoutMillis = 15_000) {
        session.state.value.let { it.document != null && !it.opening } &&
            onAllNodesWithTag(TAG_IMAGE).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Opens a tool's context panel from either layout: the compact floating
 * pill raises the catalogue first, while the wide rail exposes the cells
 * directly. Tool cells keep the `editor.category.*` tags in both, so
 * callers pass TAG_CATEGORY_* unchanged.
 *
 * The pointer click also proves the pin toggle's corner-only claim: a
 * tap at the cell's centre must fall through to the cell, not the pin.
 * Cells late in registry order may be composed below the sheet's fold —
 * a click there lands on the scrim and dismisses the modal instead, so
 * the lazy grid scrolls its target into view first.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openPhotoTool(tag: String) {
    if (onAllNodesWithTag(TAG_CATALOGUE).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(TAG_CATALOGUE).performClick()
        // The scaffold sheet animates open — the grid's first
        // composition trails the click by a few frames, so wait for it
        // rather than sampling once.
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
    if (onAllNodesWithTag(TAG_CATALOGUE_GRID)
        .fetchSemanticsNodes().isNotEmpty()
    ) {
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(tag))
    }
    onNodeWithTag(tag).performClick()
}

/**
 * Opens the Layers workspace on the six-slot phone dock. Layers is no
 * longer a default pin — the catalogue's real Layers cell is the route,
 * the same one a user takes.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openLayersWorkspace() {
    if (onAllNodesWithTag(TAG_CATALOGUE).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(TAG_CATALOGUE).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
    onNodeWithTag(TAG_CATALOGUE_GRID)
        .performScrollToNode(hasTestTag(TAG_CATALOGUE_LAYERS))
    onNodeWithTag(TAG_CATALOGUE_LAYERS).performClick()
    waitForIdle()
}

/**
 * Opens one of the phone catalogue's real non-tool panes. The tool
 * header no longer carries a More menu, so Source and Edits are
 * catalogue cells beside the tool grid.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openPhonePane(tag: String) {
    if (onAllNodesWithTag(TAG_CATALOGUE).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(TAG_CATALOGUE).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
    onNodeWithTag(TAG_CATALOGUE_GRID)
        .performScrollToNode(hasTestTag(tag))
    onNodeWithTag(tag).performClick()
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.expandPhotoToolSheet() {
    onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).performTouchInput {
        down(center)
        moveBy(Offset(0f, -480f))
        up()
    }
    // Settle the sheet's snap animation so later clicks use final bounds.
    mainClock.advanceTimeBy(600)
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.collapsePhotoToolSheet() {
    onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).performTouchInput {
        down(center)
        moveBy(Offset(0f, 520f))
        up()
    }
    // Settle the sheet's snap animation so later clicks use final bounds.
    mainClock.advanceTimeBy(600)
    waitForIdle()
}

/**
 * Whole-window capture that tolerates modal owners: a ModalBottomSheet
 * adds a second root, so `onRoot()` is ambiguous — the first root is
 * always the window itself, whose render includes the popup's pixels.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.captureWindowImage() =
    onAllNodes(isRoot()).onFirst().captureToImage()

/**
 * Substring text assertion. On the Skiko test runtime
 * `assertTextContains` demands an exact element match, so this reads the
 * node's `Text` semantics directly.
 */
internal fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextHas(
    substring: String
) {
    val texts = fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString("") { it.text }
    kotlin.test.assertTrue(
        substring in texts,
        "node text \"$texts\" does not contain \"$substring\""
    )
}

/** A loaded, honest state — default recipe, clean checkpoint, real pixels. */
internal fun fixtureState(
    isDirty: Boolean = false,
    isDrafting: Boolean = false,
    toolEditing: Boolean = false,
    interactionActive: Boolean = false,
    exporting: Boolean = false,
    exportCancelling: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    error: String? = null,
    errorKind: PhotoErrorKind? = null,
    exported: PhotoExportResult? = null
): PhotoEditorState {
    val size = PixelSize(800, 600)
    val pixels = SrgbPixels(size, IntArray(size.width * size.height) { -0x6600000 })
    return PhotoEditorState(
        info = PhotoInfo("file:///pic.png", "pic.png", size),
        document = PhotoDocument(
            source = MediaAsset.Image("1", "file:///pic.png", size),
            adjustments = ColorAdjustments(),
            geometry = PhotoGeometry()
        ),
        preview = pixels,
        originalPreview = pixels,
        canUndo = canUndo,
        canRedo = canRedo,
        isDirty = isDirty,
        isDrafting = isDrafting,
        toolEditing = toolEditing,
        interactionActive = interactionActive,
        exporting = exporting,
        exportCancelling = exportCancelling,
        exportProgress = if (exporting) 0.4f else null,
        error = error,
        errorKind = errorKind,
        exported = exported
    )
}

/** Switches the crop card's ruler axis through its chip menu. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.selectCropAxis(tag: String) {
    onNodeWithTag(TAG_CROP_AXIS).performClick()
    waitForIdle()
    onNodeWithTag(tag).performClick()
    waitForIdle()
}

/** Opens the crop aspect pill's menu and picks the preset [tag]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.selectCropAspect(tag: String) {
    openCropAspectMenu()
    onNodeWithTag(tag).performClick()
    waitForIdle()
}

/** Opens the crop aspect pill's menu so its presets can be inspected. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openCropAspectMenu() {
    // Let any sheet animation settle before measuring the pill.
    mainClock.advanceTimeBy(400)
    waitForIdle()
    onNodeWithTag(TAG_CROP_ASPECT).performClick()
    waitForIdle()
    // The menu scales in; clicking an item before the clock advances can
    // land on the popup scrim instead of the item.
    mainClock.advanceTimeBy(400)
    waitForIdle()
}

/**
 * Pinches the home canvas above the fit. The home carries no zoom control
 * — the photo is zoomed by gesture, and the crop tool has its own camera —
 * so a test that needs a zoomed viewport pinches like a user.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.pinchToZoomIn() {
    onNodeWithTag(TAG_IMAGE).performTouchInput {
        pinch(
            start0 = center + Offset(-50f, 0f),
            end0 = center + Offset(-140f, 0f),
            start1 = center + Offset(50f, 0f),
            end1 = center + Offset(140f, 0f)
        )
    }
    waitForIdle()
}
