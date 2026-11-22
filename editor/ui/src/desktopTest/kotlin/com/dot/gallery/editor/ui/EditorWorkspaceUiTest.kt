package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick

import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.video.videoToolEntries
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The B0 workspace: registry contents, saveable workspace state, the
 * catalogue → context-panel flow, Source/Edits panes, focus-mode unwind and
 * navigator geometry. Driven against a real session over the fixture media.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorWorkspaceUiTest {

    private var session: PhotoEditorSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.liveSession(
        widthDp: Float = 390f,
        heightDp: Float = 844f,
        fontScale: Float = 1f,
        onRequestAddImage: () -> Unit = {}
    ): PhotoEditorSession {
        val s = openedSession(samplePixels())
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale)
                ) {
                    Box(Modifier.size(widthDp.dp, heightDp.dp).fillMaxSize()) {
                        PhotoEditorScreen(
                            session = s,
                            onClose = {},
                            onRetryOpen = { s.open("fixture://editor_sample.jpg") },
                            onRequestAddImage = onRequestAddImage
                        )
                    }
                }
            }
        }
        awaitImage(s)
        return s
    }

    private fun noopActions() = PhotoEditorActions(
        beginEdit = {},
        updateEdits = {},
        commitEdit = {},
        cancelEdit = {},
        applyEdits = { _, _ -> },
        undo = {},
        redo = {},
        reset = {},
        saveCopy = {},
        cancelExport = {},
        retryOpen = {},
        retryPreview = {},
        clearError = {},
        close = {}
    )

    @Test
    fun zoomReadoutExpiresThreeSecondsAfterTheLastChange() = runComposeUiTest {
        mainClock.autoAdvance = false
        val viewport = EditorViewportState()
        val state = fixtureState()
        val bitmap = state.preview!!.toEditorImageBitmap()
        setContent {
            TestEditorRoot {
                EditorCanvas(
                    state = state,
                    actions = noopActions(),
                    viewport = viewport,
                    bitmap = bitmap,
                    imageDescription = "Photo",
                    compactUtility = true,
                    showFooter = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(TAG_ZOOM_HUD).assertDoesNotExist()

        runOnUiThread { viewport.zoom = 2f }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(TAG_ZOOM_HUD).assertIsDisplayed()
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag(TAG_ZOOM_HUD).assertIsDisplayed()

        runOnUiThread { viewport.zoom = 3f }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag(TAG_ZOOM_HUD).assertIsDisplayed()
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag(TAG_ZOOM_HUD).assertDoesNotExist()
    }

    @Test
    fun zoomedPhotoDrawsBehindTheFloatingUtilityLane() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val color = 0xFFBB3322.toInt()
            val pixels = SrgbPixels(PixelSize(800, 600), IntArray(800 * 600) { color })
            val state = fixtureState().copy(preview = pixels, originalPreview = pixels)
            val viewport = EditorViewportState(zoom = 4f)
            val bitmap = pixels.toEditorImageBitmap()
            setContent {
                TestEditorRoot {
                    EditorCanvas(
                        state = state,
                        actions = noopActions(),
                        viewport = viewport,
                        bitmap = bitmap,
                        imageDescription = "Photo",
                        compactUtility = true,
                        showFooter = false,
                        mediaPadding = PaddingValues(8.dp),
                        fitFieldInsets = PaddingValues(
                            start = 8.dp, top = 132.dp, end = 8.dp, bottom = 76.dp
                        ),
                        expandFieldOnZoom = true,
                        fieldCornerRadius = 28.dp,
                        surroundColor = Color.Black,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            waitForIdle()
            val image = onRoot().captureToImage().toPixelMap()
            assertEquals(color, image[image.width / 2, image.height * 96 / 832].toArgb())
        }

    @Test
    fun catalogueCanReopenAfterRepeatedEscapeDismissals() = runComposeUiTest {
        liveSession()
        repeat(4) {
            onNodeWithTag(TAG_CATALOGUE).performClick()
            onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
            onNodeWithTag(TAG_CANVAS).performKeyInput {
                keyDown(Key.Escape); keyUp(Key.Escape)
            }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(TAG_CATALOGUE_GRID).fetchSemanticsNodes().isEmpty()
            }
        }
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
    }

    @Test
    fun zoomImageHoldComparesOnlyUntilThePointerIsReleased() = runComposeUiTest {
        mainClock.autoAdvance = false
        val viewport = EditorViewportState()
        val state = fixtureState()
        val bitmap = state.preview!!.toEditorImageBitmap()
        setContent {
            TestEditorRoot {
                EditorCanvas(
                    state = state,
                    actions = noopActions(),
                    viewport = viewport,
                    bitmap = bitmap,
                    imageDescription = "Photo",
                    compactUtility = true,
                    showFooter = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithText("Before").assertDoesNotExist()
        onNodeWithTag(TAG_IMAGE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(700)
        onNodeWithText("Before").assertIsDisplayed()
        runOnUiThread { assertTrue(viewport.compareBefore) }
        onNodeWithTag(TAG_IMAGE).performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(32)
        onNodeWithText("Before").assertDoesNotExist()
        runOnUiThread { assertFalse(viewport.compareBefore) }
    }

    @Test
    fun hostBackRequestClosesTheToolBeforeClosingTheEditor() = runComposeUiTest {
        val s = openedSession(samplePixels())
        session = s
        var closeRequest by mutableStateOf(0)
        var closes = 0
        setContent {
            TestEditorRoot {
                PhotoEditorScreen(
                    session = s,
                    onClose = { closes++ },
                    onRetryOpen = {},
                    closeRequest = closeRequest
                )
            }
        }
        awaitImage(s)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        runOnUiThread { closeRequest++ }
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        onNodeWithTag(TAG_SAVE).assertIsDisplayed()
        runOnIdle { assertEquals(0, closes) }
        runOnUiThread { closeRequest++ }
        runOnIdle { assertEquals(1, closes) }
    }

    // ── Registry ─────────────────────────────────────────────────────────

    @Test
    fun photoRegistryListsWorkingToolsInOrder() {
        val entries = photoToolEntries()
        assertEquals(
            listOf(
                EditorToolIds.PhotoLight,
                EditorToolIds.PhotoColour,
                EditorToolIds.PhotoCurves,
                EditorToolIds.PhotoWhiteBalance,
                EditorToolIds.PhotoDetail,
                EditorToolIds.PhotoVignette,
                EditorToolIds.PhotoLensBlur,
                EditorToolIds.PhotoGrain,
                EditorToolIds.PhotoLooks,
                EditorToolIds.PhotoCrop,
                EditorToolIds.PhotoPerspective,
                EditorToolIds.PhotoDoubleExposure,
                EditorToolIds.PhotoTransform,
                EditorToolIds.PhotoBlend,
                EditorToolIds.PhotoLayerMask,
                EditorToolIds.PhotoMarkup,
                EditorToolIds.PhotoCutout,
                EditorToolIds.PhotoRemoveBackground,
                EditorToolIds.PhotoColourTone,
                EditorToolIds.PhotoPosterize,
                EditorToolIds.PhotoEdges,
                EditorToolIds.PhotoBorders,
                EditorToolIds.PhotoDevelop
            ),
            entries.map { it.descriptor.id }
        )
        assertEquals(
            List(9) { EditorToolGroup.Adjust } +
                List(2) { EditorToolGroup.Geometry } +
                List(4) { EditorToolGroup.Compose } +
                List(3) { EditorToolGroup.Retouch } +
                List(5) { EditorToolGroup.Adjust },
            entries.map { it.descriptor.group }
        )
        // Every photo tool uses the V4 expandable sheet.
        assertEquals(
            EditorToolPresentation.AdjustmentSheet,
            entries.single {
                it.descriptor.id == EditorToolIds.PhotoCurves
            }.presentation
        )
        assertEquals(
            List(entries.size) { EditorToolPresentation.AdjustmentSheet },
            entries.map { it.presentation }
        )
        assertEquals(
            listOf(
                TAG_CATEGORY_LIGHT, TAG_CATEGORY_COLOUR,
                TAG_CATEGORY_CURVES, TAG_CATEGORY_WHITE_BALANCE,
                TAG_CATEGORY_DETAILS, TAG_CATEGORY_VIGNETTE,
                TAG_CATEGORY_LENS_BLUR, TAG_CATEGORY_GRAIN,
                TAG_CATEGORY_LOOKS, TAG_CATEGORY_CROP,
                TAG_CATEGORY_PERSPECTIVE, TAG_CATEGORY_DOUBLE_EXPOSURE,
                TAG_CATEGORY_TRANSFORM, TAG_CATEGORY_BLEND,
                "editor.category.mask",
                TAG_CATEGORY_MARKUP, TAG_CATEGORY_CUTOUT,
                TAG_CATEGORY_REMOVE_BACKGROUND, TAG_CATEGORY_COLOUR_TONE,
                TAG_CATEGORY_POSTERIZE, TAG_CATEGORY_EDGES,
                TAG_CATEGORY_BORDERS, TAG_CATEGORY_DEVELOP
            ),
            entries.map { it.tag }
        )
        // Mask is contextual-only: registered for dispatch and titles
        // but never a catalogue cell. Cutout and Remove background need a
        // host segmenter and Develop a RAW source, so this registry skips them.
        assertEquals(
            List(14) { true } + listOf(false) + listOf(true, false, false) +
                List(4) { true } + listOf(false),
            entries.map { it.catalogued }
        )
    }

    @Test
    fun videoRegistryExposesTheWorkingTools() {
        val entries = videoToolEntries()
        assertEquals(
            listOf(
                EditorToolIds.VideoAddMedia,
                EditorToolIds.VideoArrange,
                EditorToolIds.VideoTrim,
                EditorToolIds.VideoTransform,
                EditorToolIds.VideoColour,
                EditorToolIds.VideoLayers,
                EditorToolIds.VideoAddAudio,
                EditorToolIds.VideoClipAudio,
                EditorToolIds.VideoMixer,
                EditorToolIds.VideoRouting,
                EditorToolIds.VideoDucking,
                EditorToolIds.VideoEq,
                EditorToolIds.VideoVoice,
                EditorToolIds.VideoExport
            ),
            entries.map { it.descriptor.id }
        )
        assertEquals(
            listOf(
                EditorToolGroup.Edit,
                EditorToolGroup.Edit,
                EditorToolGroup.Edit,
                EditorToolGroup.Picture,
                EditorToolGroup.Picture,
                EditorToolGroup.Edit,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Audio,
                EditorToolGroup.Deliver
            ),
            entries.map { it.descriptor.group }
        )
    }

    @Test
    fun unknownToolPanelFallsBackToLight() = runComposeUiTest {
        setContent {
            TestEditorRoot {
                PhotoToolPanel(
                    state = fixtureState(),
                    actions = noopActions(),
                    tool = EditorToolId("photo.retired")
                )
            }
        }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
    }

    // ── Workspace state + saver ──────────────────────────────────────────

    @Test
    fun workspaceSaverRoundTripsToolPanelFocusAndPane() {
        val workspace = EditorWorkspaceState().apply {
            selectTool(EditorToolIds.PhotoCrop)
            togglePanel(EditorWorkspacePanel.Layers)
            focusMode = true
            selectedLayerId = "layer-3"
        }
        val saved = with(SaverScope { true }) {
            with(EditorWorkspaceState.Saver) { save(workspace) }
        }!!
        val restored = EditorWorkspaceState.Saver.restore(saved)!!
        assertEquals(EditorToolIds.PhotoCrop, restored.activeTool)
        assertEquals(EditorWorkspacePanel.Layers, restored.panel)
        assertTrue(restored.toolPanelExpanded)
        assertTrue(restored.focusMode)
        assertEquals("layer-3", restored.selectedLayerId)
    }

    @Test
    fun workspaceSaverUnknownToolAndPanelRestoreWithoutCrashing() {
        val saved = listOf<Any>(
            "photo.retired", true, "NoSuchPane", true, false
        )
        val restored = EditorWorkspaceState.Saver.restore(saved)!!
        assertEquals(EditorToolId("photo.retired"), restored.activeTool)
        // Foreign panel names fall back to the tool pane, and a five-item
        // save (before the layer selection existed) restores no selection.
        assertEquals(EditorWorkspacePanel.Edit, restored.panel)
        kotlin.test.assertNull(restored.selectedLayerId)
    }

    @Test
    fun workspaceSaverRestoresPinsAndDropsForeignValues() {
        // An old 5-item save carries no pins slot — the defaults apply.
        val old = listOf<Any?>(
            "photo.light", false, "Edit", false, false
        )
        val restored = EditorWorkspaceState.Saver.restore(old)!!
        assertEquals(DEFAULT_PHOTO_PINS, restored.pinnedPhotoDestinations)
        assertTrue(!restored.pinEditorOpen)

        // A current save round-trips a custom order; unknown and
        // duplicate destinations drop out, and the list clamps at 4.
        val saved = listOf<Any?>(
            "photo.light", false, "Edit", false, false, null,
            "photo.colour,photo.retired,workspace.layers," +
                "photo.colour,photo.transform,photo.blend,photo.grain",
            true
        )
        val withPins = EditorWorkspaceState.Saver.restore(saved)!!
        // Colour and the unknown id drop out; the fixed Colour dock slot
        // owns the colour tool.
        assertEquals(
            listOf(
                PHOTO_PIN_LAYERS,
                EditorToolIds.PhotoTransform.value,
                EditorToolIds.PhotoBlend.value,
                EditorToolIds.PhotoGrain.value
            ),
            withPins.pinnedPhotoDestinations
        )
        assertTrue(withPins.pinEditorOpen)
    }

    @Test
    fun photoPinCapReorderAndUnpin() {
        val workspace = EditorWorkspaceState()
        assertEquals(DEFAULT_PHOTO_PINS, workspace.pinnedPhotoDestinations)

        // The cap is 4 and the four defaults fill it — a fifth tool,
        // the fixed Colour slot and foreign ids are no-ops.
        workspace.togglePhotoPin(EditorToolIds.PhotoWhiteBalance.value)
        workspace.togglePhotoPin(EditorToolIds.PhotoColour.value)
        workspace.togglePhotoPin("photo.retired")
        assertEquals(DEFAULT_PHOTO_PINS, workspace.pinnedPhotoDestinations)

        // Unpin removes; a new pin appends at the end.
        workspace.togglePhotoPin(EditorToolIds.PhotoLight.value)
        workspace.togglePhotoPin(EditorToolIds.PhotoWhiteBalance.value)
        assertEquals(
            listOf(
                EditorToolIds.PhotoCrop.value,
                EditorToolIds.PhotoMarkup.value,
                EditorToolIds.PhotoLooks.value,
                EditorToolIds.PhotoWhiteBalance.value
            ),
            workspace.pinnedPhotoDestinations
        )

        // Reorder moves one slot at a time and clamps at the ends;
        // unknown destinations never move.
        workspace.movePhotoPin(EditorToolIds.PhotoWhiteBalance.value, -1)
        workspace.movePhotoPin(EditorToolIds.PhotoCrop.value, -1)
        workspace.movePhotoPin("photo.retired", 1)
        assertEquals(
            listOf(
                EditorToolIds.PhotoCrop.value,
                EditorToolIds.PhotoMarkup.value,
                EditorToolIds.PhotoWhiteBalance.value,
                EditorToolIds.PhotoLooks.value
            ),
            workspace.pinnedPhotoDestinations
        )
    }

    // ── Catalogue → panel flow ───────────────────────────────────────────

    @Test
    fun portraitHomeOpensToolPanelThroughCatalogue() = runComposeUiTest {
        liveSession()

        // Collapsed home: no inspector content, no catalogue yet.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertDoesNotExist()
        onNodeWithTag(TAG_CATEGORY_CROP).assertDoesNotExist()

        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE_GRID).performScrollToNode(hasTestTag(TAG_CATEGORY_CROP))
        onNodeWithTag(TAG_CATEGORY_CROP).assertIsDisplayed()

        // Picking a tool closes the catalogue and opens its panel.
        onNodeWithTag(TAG_CATEGORY_CROP).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertDoesNotExist()
        onNodeWithTag(TAG_CROP_ASPECT).assertIsDisplayed()
    }

    @Test
    fun toolExitsThroughBackOrTheToolBarDone() = runComposeUiTest {
        liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
        // The tool bar under the header carries Done (TAG_TOOL_APPLY);
        // there is no Cancel, and Back keeps the changes like Done.
        onNodeWithTag(TAG_PANEL_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        awaitToolSheetGone()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun layersPinOpensLayers() =
        runComposeUiTest {
        liveSession()

        // The pinned Layers destination opens the real layer workspace —
        // the locked base row, Add layer and the disabled Group
        // placeholder. Layers wears the tool chrome: the tool bar's Done
        // and the header's Back both leave it.
        openLayersWorkspace()
        onNodeWithTag(TAG_LAYER_BASE).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ADD).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_GROUP).assertIsNotEnabled()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_LAYER_BASE).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    // ── Focus mode ───────────────────────────────────────────────────────

    @Test
    fun focusModeHidesChromeAndFUnwindsIt() = runComposeUiTest {
        liveSession()

        // Compact has no Focus button — F on the canvas is the entry.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        onNodeWithTag(TAG_BACK).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertDoesNotExist()
        onNodeWithTag(TAG_FOCUS_EXIT).assertIsDisplayed()

        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun escapeUnwindsInOrderCatalogueFocusThenTool() = runComposeUiTest {
        liveSession()

        // The catalogue is the shallowest surface — it closes first.
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_CATALOGUE_GRID)
                .fetchSemanticsNodes().isEmpty()
        }

        // A tool surface plus focus mode: unwind order is focus, then
        // the tool. Studio's slider tools hold no transaction, so the
        // second Escape collapses the surface rather than cancelling a
        // draft.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        // The phone's focused Light tool keeps the document chrome and
        // only shrinks the card to the ruler.
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()

        // Escape 1: focus mode unwinds; the tool surface survives.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag(TAG_FOCUS_PARAM_LABEL)
                .fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()

        // Escape 2: the tool collapses — pane closes, pill returns.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        awaitToolSheetGone()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun navigatorClearsTheFocusTopBar() = runComposeUiTest {
        liveSession()
        // The navigator only appears past fit; the home has no zoom
        // control, so pinch the photo like a user.
        pinchToZoomIn()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }

        onNodeWithTag(TAG_NAVIGATOR).assertIsDisplayed()
        // Focus mode gives the navigator an 88 dp top inset — it lands
        // below the 16dp-inset ≥56dp top bar instead of sliding under it.
        onNodeWithTag(TAG_NAVIGATOR)
            .assertTopPositionInRootIsEqualTo(88.dp)
    }

    @Test
    fun focusFont2KeepsTheRulerUnclipped() = runComposeUiTest {
        liveSession(fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertIsDisplayed()

        // At 2× text the card's own controls still fit: the row, the
        // ruler and the step buttons stay inside the card and above the
        // dock instead of clipping.
        val root = onRoot().getBoundsInRoot()
        val card = onNodeWithTag(TAG_V3_SHEET_BODY).getUnclippedBoundsInRoot()
        val dock = onNodeWithTag(TAG_EDITOR_PINS).getUnclippedBoundsInRoot()
        // The card runs under the dock to the bottom edge; its controls
        // must end above the dock.
        for (tag in listOf(TAG_FOCUS_PARAM_LABEL, TAG_FOCUS_CAROUSEL)) {
            val node = onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(
                bounds.left >= root.left && bounds.right <= root.right &&
                    bounds.top >= card.top && bounds.bottom <= card.bottom &&
                    bounds.bottom <= dock.top,
                "$tag clipped: $bounds escapes $card above the dock at ${dock.top}"
            )
        }
        onNodeWithTag("editor.slider.exposure").assertIsDisplayed()
    }

    // ── Floating chrome targets ──────────────────────────────────────────

    @Test
    fun compactHomeCarriesBeforePillAndFilledCheck() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)

        // The Stitch home capsule: Close, Undo/Redo, the compare toggle
        // and the Save split pill — no title, no overflow, no text
        // button.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_REDO).assertIsDisplayed()
        onNodeWithTag(TAG_OVERFLOW).assertDoesNotExist()
        onNodeWithTag(TAG_BEFORE_CHIP)
            .assertIsDisplayed()
            .assertHasClickAction()

        // The pill is the compare toggle — a tap flips the viewport
        // into the before frame and flips it back.
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsNotSelected()
        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsSelected()
        onNodeWithTag(TAG_BEFORE_CHIP).performClick()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsNotSelected()

        // D8: the clean session's Save is present but disabled.
        onNodeWithTag(TAG_SAVE)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assertHasClickAction()

        // Dirty the session through the Light tool — Studio's slider
        // edits land on the global history per gesture, the same dirty
        // signal that gates Save on the home.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(centerRight)
            up()
        }
        waitUntil(timeoutMillis = 15_000) { !s.state.value.interactionActive }
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        awaitToolSheetGone()
        waitUntil(timeoutMillis = 15_000) { s.state.value.isDirty }

        // Save opens its options; the copy is the first one.
        onNodeWithTag(TAG_SAVE).performClick()
        onNodeWithTag(TAG_SAVE_MENU_COPY).assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.exported != null
        }
    }

    @Test
    fun expandedHomeKeepsTheTextSave() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
        liveSession(widthDp = 1000f, heightDp = 800f)

        // Noncompact stays exactly as before: Undo/Redo/More icon
        // buttons plus the labelled Save copy action.
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_REDO).assertIsDisplayed()
        onNodeWithTag(TAG_OVERFLOW).assertIsDisplayed()
        onNodeWithText("Save copy").assertIsDisplayed()
    }

    @Test
    fun pillSwipeUpOpensTheCatalogueModal() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)

        // An upward drag past 24 dp on the pill opens the same modal
        // catalogue the Tools slot's tap does.
        onNodeWithTag(TAG_EDITOR_PINS).performTouchInput {
            down(center)
            moveBy(Offset(0f, -160f))
            up()
        }
        onNodeWithTag(TAG_EDITOR_CATALOGUE_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        // Exactly one handle: the modal's own DragHandle — the
        // catalogue's standalone start handle is suppressed inside it.
        onNodeWithTag(TAG_CATALOGUE_HANDLE).assertDoesNotExist()
    }

    @Test
    fun pillShowAllToolsSemanticsActionOpensTheCatalogue() =
        runComposeUiTest {
        liveSession()

        val actions = onNodeWithTag(TAG_EDITOR_PINS)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val showAll = actions.first { it.label == "Show all tools" }
        runOnUiThread { showAll.action() }
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
    }

    @Test
    fun disabledPillIgnoresSwipeAndShowAllAction() = runComposeUiTest {
        var opens = 0
        setContent {
            TestEditorRoot {
                EditorFloatingToolbar(
                    workspace = EditorWorkspaceState(),
                    state = PhotoEditorState(),
                    entries = photoToolEntries(),
                    enabled = false,
                    onSelectTool = {},
                    onShowTools = { opens++ },
                    auto = PhotoAutoDock(
                        selected = false,
                        enabled = false,
                        onSelect = {}
                    )
                )
            }
        }

        // The disabled pill swallows neither the swipe route nor the
        // accessibility route into the catalogue.
        onNodeWithTag(TAG_EDITOR_PINS).performTouchInput {
            down(center)
            moveBy(Offset(0f, -160f))
            up()
        }
        assertEquals(0, opens)
        val actions = onNodeWithTag(TAG_EDITOR_PINS)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val showAll = actions.first { it.label == "Show all tools" }
        runOnUiThread { assertFalse(showAll.action()) }
        assertEquals(0, opens)
    }

    @Test
    fun toolsModalDocksAFiniteSheetOverTheHome() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_EDITOR_CATALOGUE_SHEET).assertIsDisplayed()

        // V4's 480 dp body plus its 32 dp handle leaves the home visible.
        val handleArea = onNodeWithTag(
            TAG_MODAL_DRAG_HANDLE, useUnmergedTree = true
        ).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            handleArea.top.value in 317f..323f,
            "sheet top ${handleArea.top} should sit near 320 dp"
        )
        val content = onNodeWithTag(TAG_CATALOGUE_CONTENT)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(832f, content.bottom.value, 0.5f)
        onNodeWithText("All tools").assertIsDisplayed()
        // Home stays composed behind the modal's scrim.
        onNodeWithTag(TAG_PHOTO_CANVAS).assertExists()
        // Exactly one handle — the modal's own DragHandle; the
        // catalogue's standalone start handle is suppressed inside it.
        onAllNodesWithTag(TAG_MODAL_DRAG_HANDLE, useUnmergedTree = true)
            .assertCountEquals(1)
        onNodeWithTag(TAG_CATALOGUE_HANDLE).assertDoesNotExist()
    }

    @Test
    fun scrollingTheToolsListGrowsTheSheetUpTheWindow() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val before = onNodeWithTag(TAG_CATALOGUE_CONTENT).getUnclippedBoundsInRoot()
        assertEquals(480f, (before.bottom - before.top).value, 0.5f)

        // Scrolling the rows lifts the sheet before they move.
        onNodeWithTag(TAG_CATALOGUE_GRID).performTouchInput {
            down(center)
            moveBy(Offset(0f, -200f))
            moveBy(Offset(0f, -200f))
            up()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val grown = onNodeWithTag(TAG_CATALOGUE_CONTENT).getUnclippedBoundsInRoot()
        assertTrue(
            (grown.bottom - grown.top).value > 560f,
            "the sheet grew: ${grown.bottom - grown.top}"
        )
        assertEquals(832f, grown.bottom.value, 0.5f)
        // It stops below the window's top edge.
        val handle = onNodeWithTag(TAG_MODAL_DRAG_HANDLE, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(handle.top.value >= 11.5f, "the handle stays on screen: ${handle.top}")
    }

    @Test
    fun catalogueListsToolsAsFullWidthRowsUnderSectionHeaders() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()

        // One tool per row: Colour sits under Light at the same width,
        // each row at least 72 dp tall and wide enough for its line.
        val light = onNodeWithTag(TAG_CATEGORY_LIGHT).getUnclippedBoundsInRoot()
        val colour = onNodeWithTag(TAG_CATEGORY_COLOUR).getUnclippedBoundsInRoot()
        assertTrue(colour.top >= light.bottom - 0.5.dp, "Colour $colour is not under Light $light")
        assertEquals(light.left, colour.left)
        assertEquals(light.right, colour.right)
        assertTrue((light.right - light.left).value > 300f, "row width ${light.right - light.left}")
        assertTrue((light.bottom - light.top).value >= 71.5f, "row height ${light.bottom - light.top}")
        // Each row says what the tool does.
        onNodeWithText("Exposure, contrast, highlights and shadows").assertIsDisplayed()
    }

    @Test
    fun longPressWithAFullDockOpensThePinManager() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()

        // The four defaults fill the dock: a long press on White balance
        // opens the pin manager instead of silently doing nothing.
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(TAG_CATEGORY_WHITE_BALANCE))
        onNodeWithTag(TAG_CATEGORY_WHITE_BALANCE).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag(TAG_PIN_MANAGER).assertIsDisplayed()
    }

    @Test
    fun longPressOnAPinnedRowUnpinsIt() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value)).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(TAG_CATEGORY_CROP))
        onNodeWithTag(TAG_CATEGORY_CROP).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value)).assertDoesNotExist()
    }

    @Test
    fun pinManagerSheetReordersAndPinsTools() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_EDITOR_PINS_EDIT).performClick()
        onNodeWithTag(TAG_PIN_MANAGER).assertIsDisplayed()

        // Everything below is scoped to the manager — the catalogue
        // stays composed behind it with its own pin toggles and titles.
        val inManager = hasAnyAncestor(hasTestTag(TAG_PIN_MANAGER))

        // The four defaults pin in order: rows carry Move up/down and
        // Unpin; the boundary move is disabled honestly.
        onNode(hasText("Edit pins") and inManager).assertIsDisplayed()
        onNode(hasText("Pinned") and inManager).assertIsDisplayed()
        onAllNodes(hasContentDescription("Move down") and inManager)
            .fetchSemanticsNodes().let { assertEquals(4, it.size) }
        onAllNodes(hasContentDescription("Unpin") and inManager)
            .fetchSemanticsNodes().let { assertEquals(4, it.size) }
        onAllNodes(hasContentDescription("Move up") and inManager)[0]
            .assertIsNotEnabled()

        // Move the third pinned row (Curves) up a lane.
        onAllNodes(hasContentDescription("Move up") and inManager)[2]
            .performClick()
        waitForIdle()

        // Unpin the first pinned row (Crop) — the dock drops its slot
        // and it re-enters the available list with a Pin action.
        onAllNodes(hasContentDescription("Unpin") and inManager)[0]
            .performClick()
        waitForIdle()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value))
            .assertDoesNotExist()
        onAllNodes(hasContentDescription("Pin") and inManager)
            .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun pinManagerFont2ScrollKeepsRowsReachable() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f, fontScale = 2f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_EDITOR_PINS_EDIT).performClick()
        onNodeWithTag(TAG_PIN_MANAGER).assertIsDisplayed()

        // At 2x text the sheet's list scrolls — every pinnable tool row
        // stays reachable. Scoped to the manager: the catalogue behind
        // carries the same titles and pin actions.
        val inManager = hasAnyAncestor(hasTestTag(TAG_PIN_MANAGER))
        onNode(hasText("Blend & opacity") and inManager)
            .performScrollTo()
            .assertIsDisplayed()
        onAllNodes(hasContentDescription("Unpin") and inManager)
            .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    // ── Canonical phone regions (412 × 832 editor content) ──────────────
    // runDesktopComposeUiTest sizes the scene itself — runComposeUiTest's
    // default window is only 768 tall, which would clamp every Box below.

    @Test
    fun canonicalWorkspaceRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        // The chrome lays out after the decoded image lands; measure the
        // settled frame, not a mid-layout snapshot.
        waitForIdle()

        // The Stitch home capsule floats on the 12 dp window inset — the
        // header region wraps it: 12 + 56 = 68 dp tall, full width.
        onNodeWithTag(TAG_PHOTO_HEADER)
            .assertTopPositionInRootIsEqualTo(0.dp)
            .assertHeightIsEqualTo(68.dp)
        // V4 home: the media field runs edge to edge under the floating
        // chrome — the fit card clears the header inside the field, and
        // the 70 dp toolbar region is pinned at the field's foot at 762.
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertTopPositionInRootIsEqualTo(0.dp)
        onNodeWithTag(TAG_PHOTO_TOOLBAR_REGION)
            .assertTopPositionInRootIsEqualTo(762.dp)
            .assertHeightIsEqualTo(70.dp)

        // Media: the image is the window — no inset field, no card.
        onNodeWithTag(TAG_IMAGE)
            .assertLeftPositionInRootIsEqualTo(0.dp)
            .assertWidthIsEqualTo(412.dp)

        // The capsule owns Close/Undo/Redo/compare/Save; the histogram
        // pill floats at 72 and the home carries no zoom control (the
        // photo is pinched; the crop tool has its own camera).
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_REDO).assertIsDisplayed()
        onNodeWithTag(TAG_ZOOM_LABEL).assertDoesNotExist()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL).assertIsDisplayed()

        // The data-driven dock spans the lane minus its 10 dp insets.
        onNodeWithTag(TAG_EDITOR_PINS)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(392.dp)
            .assertHeightIsEqualTo(70.dp)
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoLight.value))
            .assertIsDisplayed()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value))
            .assertIsDisplayed()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoMarkup.value))
            .assertIsDisplayed()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoColour.value))
            .assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun canonicalToolRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        // Let the scaffold settle on its resting peek before measuring.
        waitForIdle()

        // The document header is the same on the tool surface as on the
        // home: a 56 dp bar on the shared 12 dp window inset, with no
        // tool title and no overflow.
        onNodeWithTag(TAG_PHOTO_HEADER)
            .assertTopPositionInRootIsEqualTo(12.dp)
            .assertWidthIsEqualTo(412.dp)
            .assertHeightIsEqualTo(56.dp)
        // The tool surface is the same window as the home: the media
        // fills it edge to edge and the header, the card and the dock
        // float over the photo.
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET)
            .assertTopPositionInRootIsEqualTo(0.dp)
            .assertHeightIsEqualTo(832.dp)
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertTopPositionInRootIsEqualTo(0.dp)
            .assertHeightIsEqualTo(832.dp)
        // Light's card is fixed: it paints no grab strip.
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_BREADCRUMB).assertDoesNotExist()

        // The header is the document header unchanged — Close, the
        // commands capsule and Save copy, with no tool title, no More
        // and no top-right Check. The floating card owns Apply.
        onAllNodes(
            hasTestTag(TAG_PHOTO_HEADER) and
                hasAnyDescendant(hasText("Light"))
        ).assertCountEquals(0)
        onNodeWithTag(TAG_HEADER_MORE).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_BEFORE_CHIP).assertIsDisplayed()
        // The header keeps Save while a tool is open; the tool bar carries Done.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_UTILITY).assertDoesNotExist()

        // The collapsed sheet contains only the ruler and parameter strip.
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun lightChipsAndExposureRulerMatchThePrecisionWindow() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()

        // Content-measured chips start at x36 — the card's 12 dp float
        // inset, then the inspector's 16 and the strip's 8: label plus
        // the live value, no fixed cell widths. The selected chip pops to
        // 1.04×, which expands its painted bounds by ~2.6 dp per side, so
        // allow for the pop while still guarding the layout.
        val exposureLeft = onNodeWithTag(TAG_PARAM_EXPOSURE)
            .getUnclippedBoundsInRoot().left
        assertTrue(
            abs(exposureLeft.value - 36f) <= 4f,
            "exposure chip left $exposureLeft is not ~36 dp"
        )
        onNodeWithTag(TAG_PARAM_EXPOSURE).assertIsDisplayed()
        onNodeWithTag(TAG_PARAM_CONTRAST).assertIsDisplayed()

        // The chip shows the live value — a step takes Exposure to
        // +0.1 EV and the dot+value appear on the chip. The value reads
        // twice on the sheet: chip value and floating pill.
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0f,
                range = -5f..5f,
                steps = 0
            )
        )
        onAllNodesWithText("+0.1 EV").assertCountEquals(0)
        onNodeWithTag("editor.step.plus.exposure").performClick()
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops == 0.1f
        }
        onAllNodesWithText("+0.1 EV").assertCountEquals(2)
        onNodeWithTag("editor.step.minus.exposure").performClick()
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.exposureStops == 0f
        }

        // The painted window rides the scrubber's track ends (painted,
        // not semantics), so the ±5 EV claim is the slider's range above.

        // Exact entry keeps the authored ±10 EV domain — +10 validates
        // through the dialog and lands on the document.
        onNodeWithTag(TAG_VALUE_EXPOSURE).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("10")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.let {
                !it.interactionActive &&
                    it.document?.adjustments?.exposureStops == 10f
            }
        }
    }

    @Test
    fun setProgressAfterParameterSwitchRoutesToTheActiveParameter() =
        runDesktopComposeUiTest(width = 390, height = 844) {
        val s = liveSession(widthDp = 390f, heightDp = 844f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        // Switch to Shadows, then drive the ruler through semantics — the
        // write must target the active parameter (a stale callback
        // reference routed here into exposure instead).
        onNodeWithTag(TAG_PARAM_SHADOWS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TAG_SLIDER_SHADOWS).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(30f) }
        waitForIdle()
        val adjustments = s.state.value.document?.adjustments
        assertNotNull(adjustments)
        assertEquals(0.3f, adjustments.shadows)
        assertEquals(0f, adjustments.exposureStops)
    }

    @Test
    fun canonicalCatalogueRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()

        // The catalogue is a real ModalBottomSheet over the unchanged
        // home — the preview region and crop thumbnail from the V2
        // surface are gone.
        onNodeWithTag(TAG_EDITOR_CATALOGUE_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE_SEARCH).assertIsDisplayed()
        // The filter chips: All, then one per task section.
        onNodeWithTag(TAG_CATALOGUE_CHIPS).assertIsDisplayed()
        onNodeWithTag("editor.catalogue.chip.all").assertIsDisplayed()
        onNodeWithTag("editor.catalogue.chip.Adjust").assertIsDisplayed()
        onNodeWithTag(TAG_CATEGORY_LIGHT).assertIsDisplayed()

        // Pins are a long press now: the title row keeps the pin
        // manager, and no row carries a pin toggle.
        onNodeWithTag(TAG_EDITOR_PINS_EDIT).assertIsDisplayed()
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoLight.value))
            .assertDoesNotExist()

        // The home stays composed behind the sheet.
        onNodeWithTag(TAG_PHOTO_HEADER).assertHeightIsEqualTo(68.dp)
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertTopPositionInRootIsEqualTo(0.dp)
        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
    }

    // ── Geometry tools (P2b) ─────────────────────────────────────────────

    @Test
    fun canonicalCropRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_CROP)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        // Let the scaffold settle on its resting peek before measuring.
        waitForIdle()

        // The document header floats over the photo on the shared 12 dp
        // window inset; the media fills the window behind it.
        onNodeWithTag(TAG_PHOTO_HEADER)
            .assertTopPositionInRootIsEqualTo(12.dp)
            .assertHeightIsEqualTo(56.dp)
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertTopPositionInRootIsEqualTo(0.dp)
        // A plain geometry tool names no second line — the photo is the
        // document, and the header carries no title (D21).
        onNodeWithTag(TAG_PHOTO_BREADCRUMB).assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()
        // The approved floating card is painted at rest.
        onNodeWithTag(TAG_PHOTO_SHEET_MATERIAL).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()

        // A plain geometry tool writes no breadcrumb — the card's own
        // aspect readout names the crop, and the header names nothing.
        onNodeWithTag(TAG_PHOTO_SWITCHER).assertDoesNotExist()

        // Collapsed anatomy: the centred value over the ruler, the three
        // ruler tabs and the aspect strip with its orientation swap.
        onNodeWithTag(TAG_STRAIGHTEN).assertIsDisplayed()
        onNodeWithTag(TAG_STRAIGHTEN_VALUE).assertIsDisplayed()
        // The three real rulers sit on a visible Straighten / Vertical /
        // Horizontal row.
        onNodeWithTag(TAG_CROP_TAB_STRAIGHTEN).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_TAB_VERTICAL).assertExists()
        onNodeWithTag(TAG_CROP_TAB_HORIZONTAL).assertExists()
        onNodeWithTag(TAG_CROP_TAB_STRAIGHTEN).performClick()
        waitForIdle()
        // The aspect choice is a split dropdown pill: the current ratio
        // opens the presets, the attached segment swaps orientation.
        onNodeWithTag(TAG_CROP_ASPECT).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_ORIENTATION).assertIsDisplayed()
        openCropAspectMenu()
        onNodeWithTag(TAG_CROP_FREE).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_ORIGINAL).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_1_1).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_4_5).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_16_9).assertExists()
        // Dismiss without re-applying an aspect (a second selection would
        // chain the ratio maths and perturb the float).
        onNodeWithTag(TAG_CROP_ASPECT).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(400)
        waitForIdle()
        expandPhotoToolSheet()
        onNodeWithTag(TAG_ROTATE_LEFT).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_GUIDES_TOGGLE).assertIsDisplayed()
        onNodeWithTag(TAG_FLIP_H).assertIsDisplayed()
        onNodeWithTag(TAG_FLIP_V).assertIsDisplayed()
        // The header owns the commit; the tool keeps its labelled reset
        // inline next to the rotate/flip actions.
        onNodeWithTag(TAG_RESET_CROP)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(40.dp)
        onNodeWithTag(TAG_RESET_CROP_FOOTER).assertDoesNotExist()
        collapsePhotoToolSheet()

        // Fixed frame, moving photo: after the 4:5 pick the frame is a
        // 0.8-aspect rect re-centred and zoomed to fill the media card —
        // the camera moved the photo, not the frame off-centre.
        selectCropAspect(TAG_CROP_4_5)
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.geometry?.crop?.right == 0.9
        }
        waitUntil(timeoutMillis = 15_000) {
            val box = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
            abs((box.right - box.left).value / (box.bottom - box.top).value - 0.8f) < 0.01f
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        run {
            val canvas = onNodeWithTag(TAG_CROP_CANVAS).getBoundsInRoot()
            val box = onNodeWithTag(TAG_CROP_BOX).getBoundsInRoot()
            val cx = abs((canvas.left.value + canvas.right.value) / 2f - (box.left.value + box.right.value) / 2f)
            assertTrue(cx < 2f, "frame $box must be centred in $canvas")
            assertTrue(
                box.left.value >= canvas.left.value && box.right.value <= canvas.right.value &&
                    box.top.value >= canvas.top.value && box.bottom.value <= canvas.bottom.value,
                "frame $box must sit inside the media field $canvas"
            )
        }
        onNodeWithTag(TAG_CROP_GRID).assertIsDisplayed()

        // Each corner handle's invisible 48x48 dp hit region is
        // anchored entirely inward of the corner, so its actual
        // clipped touch bounds are the full 48 dp even at the canvas
        // edge — the accessible target a user can really hit.
        val canvasBounds = onNodeWithTag(TAG_PHOTO_CANVAS).getBoundsInRoot()
        listOf(
            TAG_CROP_HANDLE_TL, TAG_CROP_HANDLE_TR,
            TAG_CROP_HANDLE_BL, TAG_CROP_HANDLE_BR
        ).forEach { tag ->
            onNodeWithTag(tag)
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
            val bounds = onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(
                bounds.left >= canvasBounds.left &&
                    bounds.right <= canvasBounds.right &&
                    bounds.top >= canvasBounds.top &&
                    bounds.bottom <= canvasBounds.bottom,
                "$tag hit region $bounds escapes the canvas $canvasBounds"
            )
        }
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
    }

    @Test
    fun keystoneTabsDriveTheSharedRulerAndFont2KeepsActionsReachable() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f, fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_CROP)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // Vertical / Horizontal swap the one ruler; the value readout
        // follows the tab.
        selectCropAxis(TAG_CROP_TAB_VERTICAL)
        onNodeWithTag("editor.crop.ruler.vertical").assertExists()
        onNodeWithTag(TAG_STRAIGHTEN).assertDoesNotExist()
        selectCropAxis(TAG_CROP_TAB_HORIZONTAL)
        onNodeWithTag("editor.crop.ruler.horizontal").assertExists()

        // At 2x font everything scrolls into reach; the header's filled
        // Check is the only commit.
        expandPhotoToolSheet()
        listOf(TAG_ROTATE_LEFT, TAG_FLIP_H, TAG_FLIP_V, TAG_CROP_GUIDES_TOGGLE)
            .forEach { onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        // The foot is pinned under the card's scroll area.
        onNodeWithTag(TAG_RESET_CROP).assertIsDisplayed()
        onNodeWithTag(TAG_RESET_CROP_FOOTER).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
    }

    @Test
    fun adjustmentGeometryAndContextualToolsShareTheV4Sheet() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)

        // AdjustmentSheet tools ride the scaffold — handle, no inline
        // inspector.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET).assertIsDisplayed()
        // Light has no expanded state and no grab strip, and no
        // Light/Colour/Detail switcher (that row was retired).
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_SWITCHER).assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()

        // Colour is the other AdjustmentSheet tool — same scaffold,
        // reached from the dock.
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoColour.value))
            .performClick()
        waitForIdle()
        // The same document header plus the same floating card — the
        // Colour tool's own saturation row is the proof.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_SLIDER_SATURATION).assertIsDisplayed()
        // Colour is a single-stage card too: measured, no grab strip.
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()

        // Geometry and contextual tools use the same sheet shell.
        onNodeWithTag(TAG_BACK).performClick()
        awaitToolSheetGone()
        openPhotoTool(TAG_CATEGORY_CROP)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        awaitToolSheetGone()
        openPhotoTool(TAG_CATEGORY_PERSPECTIVE)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        awaitToolSheetGone()
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.layers?.size == 1
        }
        openPhotoTool(TAG_CATEGORY_TRANSFORM)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun lightCardHasNoExpandedStateAndNoInlineHistogram() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()

        // The resting card is the whole card — chips and the ruler, with
        // no grab strip and no secondary row; the histogram lives in the
        // pill's panel, never in the card.
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_SWITCHER).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM).assertDoesNotExist()
        onNodeWithTag(TAG_RESET_TOOL).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_CANVAS).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM).assertDoesNotExist()

        // The header owns the commit while the tool is open.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_HISTOGRAM_PILL_OPEN).assertIsDisplayed()
    }

    // ── Layers workspace (P3b1) ─────────────────────────────────────────

    @Test
    fun canonicalLayersRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openLayersWorkspace()
        waitForIdle()

        // Layers wears the tool chrome: the document header (Save kept),
        // the tool bar with Done, the canvas, the floating layer card and
        // the dock. There is no Cancel.
        onNodeWithTag(TAG_PHOTO_HEADER).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsDisplayed()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        val header = onNodeWithTag(TAG_PHOTO_HEADER).getUnclippedBoundsInRoot()
        val bar = onNodeWithTag(TAG_TOOL_ACTIONS).getUnclippedBoundsInRoot()
        assertTrue(bar.top >= header.bottom - 0.5.dp, "tool bar $bar is not under the header $header")
        onNode(hasTestTag(TAG_TOOL_ACTIONS) and hasAnyDescendant(hasText("Layers")))
            .assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_TOOLBAR_REGION).assertIsDisplayed()

        // Card anatomy: the 48 dp toolbar (Add layer, disabled Group) over
        // the honest list rows.
        onNodeWithTag(TAG_LAYERS_TOOLBAR)
            .assertHeightIsEqualTo(48.dp)
        onNodeWithTag(TAG_LAYER_ADD).assertIsEnabled()
        onNodeWithTag(TAG_LAYER_GROUP).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYER_BASE).assertIsDisplayed()
        onNodeWithText("editor_sample.jpg").assertIsDisplayed()

        // Import is immediate — the session stays clean of drafts.
        assertTrue(!s.state.value.isDrafting && !s.state.value.toolEditing)
    }

    @Test
    fun layersFont2ScrollKeepsListReachable() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f, fontScale = 2f)
        runOnUiThread { s.addImageLayer("fixture://editor_sample.jpg") }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.layers?.size == 1
        }
        openLayersWorkspace()

        // Every row keeps its fixed 64 dp cell; the list scrolls at 2x
        // and the toolbar stays reachable — there is no Done strip.
        val layerId = s.state.value.document!!.layers.single().id
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId)
            .assertHeightIsEqualTo(64.dp)
            .assertTouchHeightIsEqualTo(64.dp)
        onNodeWithTag(TAG_LAYER_BASE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsEqualTo(64.dp)
        onNodeWithTag(TAG_LAYER_ADD).assertIsDisplayed()
        onNodeWithTag(TAG_LAYERS_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
    }

    // ── Contextual composition tools (P3b2a) ────────────────────────────

    @Test
    fun composeToolBreadcrumbNamesTheSelectedLayer() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.layers?.size == 1
        }

        openPhotoTool(TAG_CATEGORY_BLEND)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        // The breadcrumb is the layer selection, not the source name.
        onNodeWithText("Selected · Sky").assertIsDisplayed()
        onNodeWithTag(TAG_PHOTO_BREADCRUMB).assertIsDisplayed()
        // And the Adjust breadcrumb keeps its section form — the
        // geometry adjustment switcher stays absent in Compose tools.
        onNodeWithTag(TAG_PHOTO_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun transformFont2ScrollKeepsRowsAndActionsReachable() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f, fontScale = 2f)
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.layers?.size == 1
        }
        openPhotoTool(TAG_CATEGORY_TRANSFORM)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // The inspector scrolls at 2x — every row and the switch stay
        // reachable; the header's filled Check is the only action.
        onNodeWithTag(TAG_LAYER_TRANSFORM_ROTATION)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_LOCK_PROPORTIONS)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FLIP)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ALIGN)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
    }

    // ── Desktop four-pane composition (P3b2b) ───────────────────────────

    /** One imported "Sky" layer through the real session import path. */
    private fun androidx.compose.ui.test.ComposeUiTest.stageSkyLayer(
        s: PhotoEditorSession
    ): String {
        runOnUiThread { s.addImageLayer("fixture://one.png", name = "Sky") }
        waitUntil(timeoutMillis = 15_000) {
            (s.state.value.document?.layers?.size ?: 0) == 1 &&
                !s.state.value.importingLayer
        }
        return s.state.value.document!!.layers.single().id
    }

    private fun androidx.compose.ui.test.ComposeUiTest.isFocusedTag(
        tag: String
    ): Boolean = onAllNodes(isFocused() and hasTestTag(tag))
        .fetchSemanticsNodes().isNotEmpty()

    /** Tabs forward until [tag] holds focus, or fails after [steps]. */
    private fun androidx.compose.ui.test.ComposeUiTest.tabToFocused(
        tag: String,
        steps: Int = 30
    ) {
        repeat(steps) {
            if (isFocusedTag(tag)) return
            onNodeWithTag(TAG_CANVAS).performKeyInput {
                keyDown(Key.Tab); keyUp(Key.Tab)
            }
        }
        onNodeWithTag(tag).assertIsFocused()
    }

    // ── Composite layer-focus chrome (P3b2b) ────────────────────────────

    @Test
    fun layerFocusChromeBoundsAndActions() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        val layerId = stageSkyLayer(s)
        // The x-photo-composite recipe — Screen 65% with a real offset
        // so Align centre is live.
        runOnUiThread {
            val doc = s.state.value.document!!
            s.applyEdits(
                "Layer recipe",
                doc.toEdits().copy(
                    layers = doc.layers.replacingAt(
                        0,
                        doc.layers.single().copy(
                            transform = PhotoLayerTransform(
                                translationX = 20f,
                                translationY = -10f,
                                scale = 0.65f,
                                rotationDegrees = -8f
                            ),
                            blendMode = PhotoBlendMode.Screen,
                            opacity = 0.65f
                        )
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document!!.layers.single().opacity == 0.65f
        }

        openLayersWorkspace()
        waitForIdle()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).performClick()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        waitForIdle()

        // The composite chrome replaces the generic navigator bars —
        // top 56 dp pill, bottom 72 dp pill, both inset 16.
        onNodeWithTag(TAG_LAYER_FOCUS_TOP)
            .assertLeftPositionInRootIsEqualTo(16.dp)
            .assertTopPositionInRootIsEqualTo(16.dp)
            .assertWidthIsEqualTo(380.dp)
            .assertHeightIsEqualTo(56.dp)
        onNodeWithTag(TAG_LAYER_FOCUS_BOTTOM)
            .assertLeftPositionInRootIsEqualTo(16.dp)
            .assertTopPositionInRootIsEqualTo(732.dp)
            .assertWidthIsEqualTo(380.dp)
            .assertHeightIsEqualTo(72.dp)
        onNodeWithText("Sky · Screen").assertIsDisplayed()
        onNodeWithText("65%").assertIsDisplayed()
        onNodeWithText("Opacity 65%").assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_EXIT).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_LAYERS).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_ALIGN).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_FOCUS_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()
        onNodeWithTag(TAG_NAVIGATOR).assertDoesNotExist()

        // Align centre zeroes the staged translation in one step.
        onNodeWithTag(TAG_LAYER_FOCUS_ALIGN).performClick()
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document!!.layers.single().transform
                .let { it.translationX == 0f && it.translationY == 0f }
        }

        // With no tool transaction open, Apply simply exits focus.
        onNodeWithTag(TAG_LAYER_FOCUS_APPLY).performClick()
        waitForIdle()
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        // The Layers surface stays up — its flat inspector, no Done.
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertIsDisplayed()
        onNodeWithTag(TAG_LAYERS_DONE).assertDoesNotExist()
    }

    @Test
    fun layerFocusLayersButtonExitsIntoTheLayersSurface() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        val layerId = stageSkyLayer(s)
        openLayersWorkspace()
        waitForIdle()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).performClick()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertIsDisplayed()

        onNodeWithTag(TAG_LAYER_FOCUS_LAYERS).performClick()
        waitForIdle()
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertIsDisplayed()
        onNodeWithTag(TAG_LAYERS_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsSelected()
    }

    @Test
    fun layerFocusApplyCommitsTheOpenToolTransaction() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        stageSkyLayer(s)
        openPhotoTool(TAG_CATEGORY_TRANSFORM)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

        // The contextual tool surface swaps in the same composite
        // chrome — the transform overlay stays interactive under it.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()

        // Apply commits the transaction before exiting — one global
        // step, the tool surface gone.
        onNodeWithTag(TAG_LAYER_FOCUS_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
    }

    @Test
    fun lightFocusKeepsTheDocumentChrome() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        awaitToolSheet()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }

        // Focus keeps the header and the six-slot dock: the card is the
        // only thing that shrinks. No generic navigator chrome, no
        // composite layer chrome, and no Apply row (edits land per
        // interaction).
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        // The tool bar keeps Done; the header keeps Save.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsDisplayed()
        onNodeWithTag(TAG_EDITOR_PINS).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertIsDisplayed()
        onNodeWithTag(TAG_V3_SHEET_HANDLE).assertDoesNotExist()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()
        onNodeWithTag(TAG_LAYER_FOCUS_TOP).assertDoesNotExist()
        onNodeWithTag(TAG_LAYER_FOCUS_BOTTOM).assertDoesNotExist()
    }

    // ── Navigator geometry ───────────────────────────────────────────────

    @Test
    fun navigatorVisibleRectMatchesViewportMapping() {
        // 2:1 image fit-centred in a square canvas at 2x zoom: the
        // viewport sees the middle half of the image's width, all height.
        val rect = visibleImageRect(
            canvasWidth = 100f, canvasHeight = 100f,
            imageWidth = 200f, imageHeight = 100f,
            zoom = 2f, panX = 0f, panY = 0f
        )
        assertEquals(Rect(0.25f, 0f, 0.75f, 1f), rect)

        assertEquals(
            Rect(0f, 0f, 1f, 1f),
            visibleImageRect(
                canvasWidth = 100f, canvasHeight = 100f,
                imageWidth = 200f, imageHeight = 100f,
                zoom = 1f, panX = 0f, panY = 0f
            )
        )
    }

    @Test
    fun navigatorVisibleRectTallImageZoom4() {
        // Tall image (500x1000) in a wide canvas (1000x500) at 4x: fit is
        // 250x500 -> display 1000x2000, so the viewport covers the full
        // width and the middle quarter of the height.
        val rect = visibleImageRect(
            canvasWidth = 1000f, canvasHeight = 500f,
            imageWidth = 500f, imageHeight = 1000f,
            zoom = 4f, panX = 0f, panY = 0f
        )
        assertEquals(Rect(0f, 0.375f, 1f, 0.625f), rect)
    }

    /**
     * The floating-panel material must stay readable even over the
     * brightest imagery: the panel's sampled centre pixel keeps >= 4.5
     * contrast against onSurfaceVariant in the dark scheme — the
     * guaranteed 88% container tint when blur is on, the opaque fill
     * when it is off. WCAG's (lighter + .05) / (darker + .05).
     */
    @Test
    fun floatingPanelContrastOverWhiteBlurAndOpaque() = runComposeUiTest {
        val results = mutableListOf<Pair<Boolean, Float>>()
        listOf(true, false).forEach { blur ->
            var foreground = Color.Unspecified
            setContent {
                TestEditorRoot(darkTheme = true) {
                    CompositionLocalProvider(
                        LocalEditorUiPreferences provides
                            EditorUiPreferences(blurEnabled = blur)
                    ) {
                        foreground = MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            EditorFloatingPanel(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .size(160.dp)
                                    .align(Alignment.Center)
                                    .testTag("panel.contrast")
                            ) {}
                        }
                    }
                }
            }
            waitForIdle()
            val pixels = onNodeWithTag("panel.contrast")
                .captureToImage().toPixelMap()
            val centre = pixels[pixels.width / 2, pixels.height / 2]
            val lighter = maxOf(foreground.luminance(), centre.luminance())
            val darker = minOf(foreground.luminance(), centre.luminance())
            results += blur to (lighter + 0.05f) / (darker + 0.05f)
        }
        results.forEach { (blur, contrast) ->
            assertTrue(
                contrast >= 4.5f,
                "blur=$blur panel-over-white contrast $contrast must be " +
                    ">= 4.5 against onSurfaceVariant"
            )
        }
    }

    @Test
    fun navigatorTapProducesClampedRecentringPan() {
        assertEquals(
            Offset.Zero,
            navigatorPanForCenter(
                canvasWidth = 100f, canvasHeight = 100f,
                imageWidth = 200f, imageHeight = 100f,
                zoom = 2f, fx = 0.5f, fy = 0.5f
            )
        )
        // Display is 200 wide in a 100 canvas → pan clamps at ±50.
        assertEquals(
            Offset(50f, 0f),
            navigatorPanForCenter(
                canvasWidth = 100f, canvasHeight = 100f,
                imageWidth = 200f, imageHeight = 100f,
                zoom = 2f, fx = 0f, fy = 0f
            )
        )
    }
}
