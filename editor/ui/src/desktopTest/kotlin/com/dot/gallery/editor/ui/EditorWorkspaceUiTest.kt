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
    fun hostBackRequestCancelsTheToolBeforeClosingTheEditor() = runComposeUiTest {
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
        onNodeWithTag(TAG_CATEGORY_CROP).assertIsDisplayed()

        // Picking a tool closes the catalogue and opens its panel.
        onNodeWithTag(TAG_CATEGORY_CROP).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertDoesNotExist()
        onNodeWithTag(TAG_CROP_ASPECT).assertIsDisplayed()
    }

    @Test
    fun toolTransactionExitsThroughBackAndTopApply() = runComposeUiTest {
        val s = liveSession()
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
        // V3 drops the bottom action row: the context header's filled
        // Check is the only Apply and Back is the cancel — no Done or
        // Cancel anywhere.
        onNodeWithTag(TAG_PANEL_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_BACK).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun layersPinOpensLayersAndContextMoreMenuReachesSource() =
        runComposeUiTest {
        val s = liveSession()
        val upright = s.state.value.info!!.uprightSize

        // The pinned Layers destination opens the real layer workspace —
        // the locked base row, Add layer and the disabled Group
        // placeholder. The header's Back is the only exit — no Done.
        openLayersWorkspace()
        onNodeWithTag(TAG_LAYER_BASE).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ADD).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_GROUP).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYERS_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_LAYER_BASE).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()

        // The Source pane lives behind a context header's More menu —
        // reachable e.g. from a tool surface.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_SOURCE)
        onNodeWithText("Source ${upright.width} × ${upright.height}")
            .assertIsDisplayed()
        onNodeWithTag(TAG_PANEL_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun editsPanelListsAppliedTonalValues() = runComposeUiTest {
        val s = liveSession()
        // Apply a Shadows-only edit through the Light tool — the Edits
        // summary must list it rather than render an empty pane.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_PARAM_SHADOWS).performScrollTo().performClick()
        onNodeWithTag(TAG_SLIDER_SHADOWS).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(30f) }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.adjustments?.shadows == 0.3f
        }
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.toolEditing && !s.state.value.isDrafting
        }

        // The Edits summary lives behind a context header's More menu —
        // open a tool surface to reach one.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_EDITS)
        onNodeWithText("Shadows").assertIsDisplayed()
        onNodeWithText("+30").assertIsDisplayed()
    }

    @Test
    fun editsPanelListsGeometryValues() = runComposeUiTest {
        val s = liveSession()
        // A committed geometry recipe — crop plus every non-neutral
        // warp term — must be plainly listed in the Edits summary.
        runOnUiThread {
            s.applyEdits(
                "Geometry",
                PhotoEdits(
                    geometry = PhotoGeometry(
                        crop = NormalizedCrop(0.2, 0.0, 0.8, 1.0),
                        warp = PhotoWarp(
                            rotationDegrees = -1.2f,
                            verticalPerspective = 6f,
                            horizontalPerspective = -4f,
                            scale = 1.04f
                        )
                    )
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            s.state.value.document?.geometry?.warp?.scale == 1.04f
        }

        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        openPhonePane(TAG_PANEL_EDITS)
        onNodeWithText("Crop & straighten").assertIsDisplayed()
        onNodeWithText("240 × 400").assertIsDisplayed()
        onNodeWithText("Straighten").assertIsDisplayed()
        onNodeWithText("-1.2°").assertIsDisplayed()
        onNodeWithText("Vertical").assertIsDisplayed()
        onNodeWithText("+6").assertIsDisplayed()
        onNodeWithText("Horizontal").assertIsDisplayed()
        onNodeWithText("-4").assertIsDisplayed()
        onNodeWithText("Scale").assertIsDisplayed()
        onNodeWithText("104%").assertIsDisplayed()
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
        val s = liveSession()

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

        // A tool transaction plus focus mode: unwind order is focus, then
        // the tool (Cancel), never straight to close.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }
        // The phone's focused Light tool keeps the document chrome and
        // only shrinks the card to the ruler.
        onNodeWithTag(TAG_FOCUS_PARAM_LABEL).assertIsDisplayed()
        onNodeWithTag(TAG_FOCUS_EXIT).assertDoesNotExist()

        // Escape 1: focus mode unwinds; the tool transaction survives.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        assertTrue(s.state.value.toolEditing)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()

        // Escape 2: the tool is cancelled — pane collapses, pill returns.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
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
        val s = liveSession(fontScale = 2f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
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
        assertTrue(
            card.bottom <= dock.top,
            "focus card ${card.bottom} overlaps the dock at ${dock.top}"
        )
        for (tag in listOf(TAG_FOCUS_PARAM_LABEL, TAG_FOCUS_CAROUSEL)) {
            val node = onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(
                bounds.left >= root.left && bounds.right <= root.right &&
                    bounds.top >= card.top && bounds.bottom <= card.bottom,
                "$tag clipped: $bounds escapes $card"
            )
        }
        onNodeWithTag("editor.slider.exposure").assertIsDisplayed()
    }

    // ── Floating chrome targets ──────────────────────────────────────────

    @Test
    fun floatingPillSlotsMeetTouchTargets() = runComposeUiTest {
        liveSession()
        // The six-slot Stitch dock: Crop, Light, Curves, Looks, Tools,
        // Colour in a 68 dp capsule spanning the lane minus its 10 dp
        // insets. Every destination keeps a ≥48 dp touch target.
        onNodeWithTag(TAG_EDITOR_PINS)
            .assertWidthIsEqualTo(370.dp)
            .assertHeightIsEqualTo(68.dp)
        listOf(
            photoPinTag(EditorToolIds.PhotoCrop.value),
            photoPinTag(EditorToolIds.PhotoLight.value),
            photoPinTag(EditorToolIds.PhotoMarkup.value),
            photoPinTag(EditorToolIds.PhotoLooks.value),
            TAG_CATALOGUE,
            photoPinTag(EditorToolIds.PhotoColour.value)
        ).forEach { tag ->
            val bounds = onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsEqualTo(68.dp)
                .assertTouchHeightIsEqualTo(68.dp)
                .getUnclippedBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue(
                actual = width >= 48.dp,
                message = "$tag slot is $width, under the 48 dp target"
            )
        }
        onNodeWithText("Tools").assertIsDisplayed()
        onNodeWithText("Colour").assertIsDisplayed()
        // Layers is catalogue-only now — no dock slot, no duplicate.
        onNodeWithTag(photoPinTag(PHOTO_PIN_LAYERS)).assertDoesNotExist()
        // The pinned Crop slot carries the pill's short label — the full
        // registry title stays in the catalogue and tool headers.
        onNodeWithText("Crop").assertIsDisplayed()
        onAllNodes(
            hasText("Crop & straighten") and
                hasAnyAncestor(hasTestTag(TAG_EDITOR_PINS))
        ).assertCountEquals(0)
        // Source/Edits and the overflow are not pill destinations — the
        // header owns its own More button now.
        onNodeWithTag(TAG_PANEL_SOURCE).assertDoesNotExist()
        onNodeWithTag(TAG_PANEL_EDITS).assertDoesNotExist()
        onAllNodes(
            hasTestTag(TAG_OVERFLOW) and
                hasAnyAncestor(hasTestTag(TAG_EDITOR_PINS))
        ).assertCountEquals(0)
    }

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

        // Dirty the session through the Light tool's one-interaction
        // apply — the same dirty signal gates Save on the home.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performTouchInput {
            down(center)
            moveTo(centerRight)
            up()
        }
        waitUntil(timeoutMillis = 15_000) { !s.state.value.interactionActive }
        onNodeWithTag(TAG_TOOL_APPLY).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.toolEditing && s.state.value.isDirty
        }

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
                    onShowTools = { opens++ }
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
    fun catalogueGridUsesThreeEqualColumnsAndTwoLineLabels() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()

        // The first All row is Light/Colour/Curves — three equal
        // ≈121.3 dp cells with 8 dp gaps inside the 16 dp-inset body.
        val light =
            onNodeWithTag(TAG_CATEGORY_LIGHT).getUnclippedBoundsInRoot()
        val colour =
            onNodeWithTag(TAG_CATEGORY_COLOUR).getUnclippedBoundsInRoot()
        val curves =
            onNodeWithTag(TAG_CATEGORY_CURVES).getUnclippedBoundsInRoot()
        val cellWidth = (412f - 32f - 16f) / 3f
        listOf(light, colour, curves).forEach { cell ->
            assertEquals(cellWidth, (cell.right - cell.left).value, 1f)
        }
        assertEquals(light.top.value, colour.top.value, 0.5f)
        assertEquals(light.top.value, curves.top.value, 0.5f)
        assertEquals(8f, (colour.left - light.right).value, 1f)
        assertEquals(8f, (curves.left - colour.right).value, 1f)
        // Row two starts one cell-plus-gap below — White balance leads
        // the finishing row in registry order, Details and Vignette
        // filling the row after it (Lens blur and Grain wrap to row 3).
        val whiteBalance = onNodeWithTag(TAG_CATEGORY_WHITE_BALANCE)
            .getUnclippedBoundsInRoot()
        assertEquals(
            (light.bottom + 8.dp).value, whiteBalance.top.value, 1f
        )
        assertEquals(light.left.value, whiteBalance.left.value, 0.5f)
        val details = onNodeWithTag(TAG_CATEGORY_DETAILS)
            .getUnclippedBoundsInRoot()
        assertEquals(colour.left.value, details.left.value, 0.5f)
        assertEquals(whiteBalance.top.value, details.top.value, 0.5f)
        val vignette = onNodeWithTag(TAG_CATEGORY_VIGNETTE)
            .getUnclippedBoundsInRoot()
        assertEquals(curves.left.value, vignette.left.value, 0.5f)
        assertEquals(whiteBalance.top.value, vignette.top.value, 0.5f)

        // "Crop & straighten" paints its full name over two centred
        // 14 sp lines — a truncated single line would measure ≈14 dp.
        // Row three may sit below the lazy grid's composed window —
        // scroll the cell in first.
        onNodeWithTag(TAG_CATALOGUE_GRID).performScrollToNode(
            hasTestTag(TAG_CATEGORY_CROP)
        )
        val cropLabel = onNode(
            hasText("Crop & straighten") and
                hasAnyAncestor(hasTestTag(TAG_CATEGORY_CROP)),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        assertTrue(
            (cropLabel.bottom - cropLabel.top).value in 24f..36f,
            "crop label should wrap two lines, measured " +
                "${cropLabel.bottom - cropLabel.top}"
        )

        // Each 48 dp pin toggle stays inside its own cell's bounds and
        // never reaches the adjacent cell or the centre click path.
        listOf(
            TAG_CATEGORY_LIGHT to EditorToolIds.PhotoLight.value,
            TAG_CATEGORY_CURVES to EditorToolIds.PhotoCurves.value,
            TAG_CATEGORY_CROP to EditorToolIds.PhotoCrop.value
        ).forEach { (cellTag, id) ->
            val cell =
                onNodeWithTag(cellTag).getUnclippedBoundsInRoot()
            val pin = onNodeWithTag(
                cataloguePinTag(id), useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            assertTrue(
                pin.left >= cell.left && pin.right <= cell.right &&
                    pin.top >= cell.top && pin.bottom <= cell.bottom,
                "$id pin ${pin} escapes its cell ${cell}"
            )
            val cx = (cell.left + cell.right) / 2
            val cy = (cell.top + cell.bottom) / 2
            assertFalse(
                cx in pin.left..pin.right && cy in pin.top..pin.bottom,
                "$id pin covers its cell's centre"
            )
        }
    }

    @Test
    fun cataloguePinToggleUpdatesThePill() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveSession(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()

        // The fixed Colour slot has no pin toggle — only the four
        // favourites are pinnable.
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoColour.value))
            .assertDoesNotExist()

        // The four defaults fill the cap, so White balance refuses.
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoWhiteBalance.value))
            .assertIsNotSelected()
            .performClick()
        waitForIdle()
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoWhiteBalance.value))
            .assertIsNotSelected()

        // Unpinning Crop frees a lane; White balance takes it.
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoCrop.value))
            .assertIsSelected()
            .performClick()
        waitForIdle()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value))
            .assertDoesNotExist()
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoWhiteBalance.value))
            .performClick()
        waitForIdle()
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoWhiteBalance.value))
            .assertIsSelected()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoWhiteBalance.value))
            .assertIsDisplayed()
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
            .assertHeightIsEqualTo(68.dp)
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
        val s = liveSession(widthDp = 412f, heightDp = 832f)
        openPhotoTool(TAG_CATEGORY_LIGHT)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
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
        // Apply replaces Save while the tool owns the draft.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertDoesNotExist()
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
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

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
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
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
        onNodeWithText("All").assertIsDisplayed()
        onNodeWithText("Adjust").assertIsDisplayed()
        onNodeWithText("Retouch").assertIsDisplayed()
        onNodeWithText("Compose").assertIsDisplayed()
        onNodeWithTag(TAG_CATEGORY_LIGHT).assertIsDisplayed()

        // V3 pin UI: the Edit pins affordance and a 48 dp pin toggle per
        // cell — the pinned default reads selected, an unpinned cell not.
        onNodeWithTag(TAG_EDITOR_PINS_EDIT).assertIsDisplayed()
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoLight.value))
            .assertIsDisplayed()
        // Colour owns the dock's fixed last slot, so it is not pinnable
        // and its cell carries no dead toggle.
        onNodeWithTag(cataloguePinTag(EditorToolIds.PhotoColour.value))
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
        // The card's axis chip carries the three real rulers; the wide
        // inspector keeps labelled tabs.
        onNodeWithTag(TAG_CROP_AXIS).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_AXIS).performClick()
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
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
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
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        openPhotoTool(TAG_CATEGORY_CROP)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_ADJUSTMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
        openPhotoTool(TAG_CATEGORY_PERSPECTIVE)
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_V3_SHEET_HANDLE, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(TAG_BACK).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
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
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }

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

        // The V3 layers surface: the floating capsule header, the
        // weighted canvas, then the flat inline inspector — no
        // breadcrumb, no Done strip, no Apply/Cancel. Header Back is the
        // only exit.
        onNodeWithTag(TAG_PHOTO_HEADER)
            .assertTopPositionInRootIsEqualTo(12.dp)
            .assertHeightIsEqualTo(56.dp)
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertTopPositionInRootIsEqualTo(68.dp)
        onNodeWithTag(TAG_PHOTO_BREADCRUMB).assertDoesNotExist()
        onNodeWithTag(TAG_V3_INLINE_INSPECTOR).assertIsDisplayed()
        onNodeWithTag(TAG_V3_SHEET_HANDLE).assertDoesNotExist()

        // Header anatomy: Back / Layers / the 48 dp add-image action.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNode(
            hasTestTag(TAG_PHOTO_HEADER) and
                hasAnyDescendant(hasText("Layers"))
        ).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ADD_HEADER).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_UTILITY).assertDoesNotExist()

        // Inspector anatomy: the 48 dp toolbar (Add layer, disabled
        // Group) over the honest list rows.
        onNodeWithTag(TAG_LAYERS_TOOLBAR)
            .assertHeightIsEqualTo(48.dp)
        onNodeWithTag(TAG_LAYER_ADD).assertIsEnabled()
        onNodeWithTag(TAG_LAYER_GROUP).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYER_BASE).assertIsDisplayed()
        onNodeWithText("editor_sample.jpg").assertIsDisplayed()
        onNodeWithTag(TAG_LAYERS_DONE).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_CANCEL).assertDoesNotExist()
        onNodeWithTag(TAG_TOOL_APPLY).assertDoesNotExist()

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

    @Test
    fun desktopFourPaneRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        liveSession(widthDp = 1440f, heightDp = 960f)

        // The x-photo-desktop grid: header 64 over a body of
        // rail 80 | canvas 776 | layers 280 | properties 304.
        onNodeWithTag(TAG_PHOTO_HEADER)
            .assertTopPositionInRootIsEqualTo(0.dp)
            .assertHeightIsEqualTo(64.dp)
        onNodeWithTag(TAG_DESKTOP_RAIL)
            .assertLeftPositionInRootIsEqualTo(0.dp)
            .assertTopPositionInRootIsEqualTo(64.dp)
            .assertWidthIsEqualTo(80.dp)
            .assertHeightIsEqualTo(896.dp)
        onNodeWithTag(TAG_PHOTO_CANVAS)
            .assertLeftPositionInRootIsEqualTo(80.dp)
            .assertTopPositionInRootIsEqualTo(64.dp)
            .assertWidthIsEqualTo(776.dp)
            .assertHeightIsEqualTo(896.dp)
        onNodeWithTag(TAG_DESKTOP_LAYERS_PANE)
            .assertLeftPositionInRootIsEqualTo(856.dp)
            .assertTopPositionInRootIsEqualTo(64.dp)
            .assertWidthIsEqualTo(280.dp)
            .assertHeightIsEqualTo(896.dp)
        onNodeWithTag(TAG_DESKTOP_PROPS_PANE)
            .assertLeftPositionInRootIsEqualTo(1136.dp)
            .assertTopPositionInRootIsEqualTo(64.dp)
            .assertWidthIsEqualTo(304.dp)
            .assertHeightIsEqualTo(896.dp)

        // Header anatomy: Back, the source name, the centred 240x40
        // Edit/Layers/Compare segment and Save — no visible Undo/Redo,
        // More or overflow. The source name also labels the base layer
        // row, so the title measure is the one inside the header.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        val header = onNodeWithTag(TAG_PHOTO_HEADER)
            .fetchSemanticsNode().boundsInRoot
        val title = onAllNodes(hasText("editor_sample.jpg"))
            .fetchSemanticsNodes()
            .first {
                it.boundsInRoot.top >= header.top &&
                    it.boundsInRoot.bottom <= header.bottom
            }
        onNodeWithTag(TAG_DESKTOP_SEGMENT)
            .assertWidthIsEqualTo(240.dp)
            .assertHeightIsEqualTo(40.dp)
        val segment = onNodeWithTag(TAG_DESKTOP_SEGMENT)
            .fetchSemanticsNode().boundsInRoot
        val save = onNodeWithTag(TAG_SAVE)
            .fetchSemanticsNode().boundsInRoot
        val segCentre = (segment.left + segment.right) / 2
        val freeCentre = (title.boundsInRoot.right + save.left) / 2
        assertTrue(
            abs(segCentre - freeCentre) <= 2f,
            "segment centre $segCentre must sit mid-way between the " +
                "title and Save ($freeCentre)"
        )
        onNodeWithTag(TAG_DESKTOP_SEGMENT_EDIT).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_SEGMENT_COMPARE).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertDoesNotExist()
        onNodeWithTag(TAG_REDO).assertDoesNotExist()
        onNodeWithTag(TAG_HEADER_MORE).assertDoesNotExist()
        onNodeWithTag(TAG_OVERFLOW).assertDoesNotExist()

        // No phone chrome leaks into the desktop composition — and no
        // second inspector next to the two panes.
        onNodeWithTag(TAG_PHOTO_UTILITY).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_TOOLBAR_REGION).assertDoesNotExist()
        onNodeWithTag(TAG_PHOTO_INSPECTOR).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertDoesNotExist()

        // Rail anatomy: Layers, the catalogued tools top to bottom (the
        // contextual-only Mask stays out), Focus — all 56x56 icon cells.
        // The rail scrolls once the finishing set overflows its height —
        // late cells scroll into view before asserting.
        onNodeWithTag(TAG_LAYERS)
            .assertWidthIsEqualTo(56.dp)
            .assertHeightIsEqualTo(56.dp)
        listOf(
            TAG_CATEGORY_LIGHT, TAG_CATEGORY_COLOUR, TAG_CATEGORY_CROP,
            TAG_CATEGORY_PERSPECTIVE, TAG_CATEGORY_DOUBLE_EXPOSURE,
            TAG_CATEGORY_TRANSFORM, TAG_CATEGORY_BLEND
        ).forEach { tag ->
            onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .assertWidthIsEqualTo(56.dp)
                .assertHeightIsEqualTo(56.dp)
        }
        onNodeWithTag("editor.category.mask").assertDoesNotExist()
        onNodeWithTag(TAG_FOCUS)
            .assertWidthIsEqualTo(56.dp)
            .assertHeightIsEqualTo(56.dp)

        // The bottom-left 120x40 zoom field sits at canvas-local x16 —
        // root x96 — with its bottom 16 dp off the body floor.
        onNodeWithTag(TAG_DESKTOP_ZOOM)
            .assertLeftPositionInRootIsEqualTo(96.dp)
            .assertTopPositionInRootIsEqualTo(904.dp)
            .assertWidthIsEqualTo(120.dp)
            .assertHeightIsEqualTo(40.dp)
        onNodeWithTag(TAG_DESKTOP_ZOOM_FIT).assertIsDisplayed()
    }

    @Test
    fun desktopLayersSurfaceSharesSelectionAcrossPanesAndCanvas() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveSession(widthDp = 1440f, heightDp = 960f)
        val layerId = stageSkyLayer(s)

        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()

        // One workspace selection drives the row, the canvas outline
        // and the properties pane — no second inspector exists.
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsSelected()
        onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROPS_PANE)
            .assertIsDisplayed()
        onNode(
            hasTestTag(TAG_DESKTOP_PROPS_PANE) and
                hasAnyDescendant(hasText("Sky"))
        ).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_BLEND).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_POSITION).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_ROTATION).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_ALIGN).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_FLIP_H).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_FLIP_V).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_MASK).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_EFFECTS).assertIsNotEnabled()
        onNodeWithTag(TAG_PHOTO_INSPECTOR).assertDoesNotExist()

        // The layer properties end at Effects — no tool-published or
        // global reset reads on a surface it doesn't reset.
        onNodeWithTag(TAG_DESKTOP_PROP_RESET).assertDoesNotExist()
        onNodeWithText("Reset edits").assertDoesNotExist()
    }

    @Test
    fun desktopPropertyRowsMutateTheSelectedLayerAndUndo() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveSession(widthDp = 1440f, heightDp = 960f)
        stageSkyLayer(s)
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()
        fun layer() = s.state.value.document!!.layers.single()

        // Opacity through the exact-entry dialog lands one history step.
        onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("50")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.interactionActive && layer().opacity == 0.5f
        }

        // Blend mode through the dropdown — one step.
        onNodeWithTag(TAG_DESKTOP_PROP_BLEND).performClick()
        onNodeWithTag(TAG_DESKTOP_BLEND_MENU).assertIsDisplayed()
        onNodeWithText("Screen").performClick()
        waitUntil(timeoutMillis = 15_000) {
            layer().blendMode == PhotoBlendMode.Screen
        }

        // Rotation through the dialog.
        onNodeWithTag(TAG_DESKTOP_PROP_ROTATION).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("45")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.interactionActive &&
                layer().transform.rotationDegrees == 45f
        }

        // Position pair dialog — both fields validated atomically.
        onNodeWithTag(TAG_DESKTOP_PROP_POSITION).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("20")
        onNodeWithTag(TAG_VALUE_FIELD_SECOND).performTextReplacement("-10")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 15_000) {
            !s.state.value.interactionActive &&
                layer().transform.translationX == 20f &&
                layer().transform.translationY == -10f
        }

        // Align centre zeroes the translation; Flip toggles — each its
        // own global history step.
        onNodeWithTag(TAG_DESKTOP_PROP_ALIGN).performClick()
        waitUntil(timeoutMillis = 15_000) {
            layer().transform.translationX == 0f &&
                layer().transform.translationY == 0f
        }
        onNodeWithTag(TAG_DESKTOP_PROP_FLIP_H).performClick()
        waitUntil(timeoutMillis = 15_000) {
            layer().transform.flipHorizontal
        }

        // Undo walks the same history — the flip unwinds first.
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        waitUntil(timeoutMillis = 15_000) {
            !layer().transform.flipHorizontal
        }

        // The Mask field routes into the mask tool's real transaction —
        // its surface owns the properties pane and the pane's own
        // Cancel closes it.
        onNodeWithTag(TAG_DESKTOP_PROP_MASK).performClick()
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_MASK_ERASE).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).assertIsDisplayed()
        onNodeWithTag(TAG_TOOL_CANCEL).performClick()
        waitUntil(timeoutMillis = 15_000) { !s.state.value.toolEditing }
    }

    @Test
    fun desktopAddImageRaisesTheHostPicker() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        var picked = false
        liveSession(
            widthDp = 1440f,
            heightDp = 960f,
            onRequestAddImage = { picked = true }
        )
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        // The outlined 260x48 Add image under the list calls the host's
        // picker seam — the session import stays host-owned.
        onNodeWithTag(TAG_LAYER_ADD)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(260.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertTrue(picked)
    }

    @Test
    fun desktopResizeCollapsesToSidebarKeepingSelectionToolAndZoom() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = openedSession(samplePixels())
        session = s
        var width by mutableStateOf(1440f)
        var height by mutableStateOf(960f)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width.dp, height.dp).fillMaxSize()) {
                    PhotoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = {
                            s.open("fixture://editor_sample.jpg")
                        }
                    )
                }
            }
        }
        awaitImage(s)
        val layerId = stageSkyLayer(s)
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsSelected()

        // Zoom past fit — the hoisted viewport must survive the layout
        // flip, so the percent readout is the honest probe.
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Equals); keyUp(Key.Equals)
        }
        waitForIdle()
        onNode(
            hasTestTag(TAG_DESKTOP_ZOOM) and
                hasAnyDescendant(hasText("115%"))
        ).assertIsDisplayed()

        // 1440 -> 1000 collapses the four panes into the adaptive
        // rail + canvas + single inspector; selection and surface keep.
        runOnUiThread { width = 1000f; height = 800f }
        waitForIdle()
        onNodeWithTag(TAG_DESKTOP_RAIL).assertDoesNotExist()
        onNodeWithTag(TAG_DESKTOP_LAYERS_PANE).assertDoesNotExist()
        onNodeWithTag(TAG_DESKTOP_PROPS_PANE).assertDoesNotExist()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId)
            .assertIsDisplayed()
            .assertIsSelected()

        // Growing back restores the four panes with the same selection
        // and the same zoom.
        runOnUiThread { width = 1440f; height = 960f }
        waitForIdle()
        onNodeWithTag(TAG_DESKTOP_RAIL).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_LAYERS_PANE).assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROPS_PANE).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + layerId).assertIsSelected()
        onNode(
            hasTestTag(TAG_DESKTOP_ZOOM) and
                hasAnyDescendant(hasText("115%"))
        ).assertIsDisplayed()
    }

    @Test
    fun desktopKeyboardTraversalAndArrowKeys() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveSession(widthDp = 1440f, heightDp = 960f)
        stageSkyLayer(s)
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()
        fun layer() = s.state.value.document!!.layers.single()

        // Back is the first stop: walk backwards from the canvas to it,
        // then forward through the documented order.
        var guard = 0
        while (!isFocusedTag(TAG_BACK) && guard++ < 40) {
            onNodeWithTag(TAG_CANVAS).performKeyInput {
                keyDown(Key.ShiftLeft)
                keyDown(Key.Tab); keyUp(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
        }
        onNodeWithTag(TAG_BACK).assertIsFocused()
        tabToFocused(TAG_DESKTOP_SEGMENT_EDIT, steps = 2)
        tabToFocused(TAG_DESKTOP_SEGMENT_LAYERS, steps = 2)
        tabToFocused(TAG_DESKTOP_SEGMENT_COMPARE, steps = 2)
        tabToFocused(TAG_SAVE, steps = 2)
        tabToFocused(TAG_LAYERS, steps = 2)
        // Rail cells, the canvas and the layer rows pass by — the next
        // properties stop after them is the Opacity row.
        tabToFocused(TAG_DESKTOP_PROP_OPACITY)
        onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).assertIsFocused()

        // Arrow keys on the focused numeric row nudge the value — Left
        // steps -1%, landing one history step per key.
        val before = layer().opacity
        onNodeWithTag(TAG_DESKTOP_PROP_OPACITY).performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        waitUntil(timeoutMillis = 15_000) {
            layer().opacity < before
        }
        tabToFocused(TAG_DESKTOP_PROP_MASK)
        onNodeWithTag(TAG_DESKTOP_PROP_MASK).assertIsFocused()
    }

    @Test
    fun desktopLayersSegmentCanvasPanStaysReachable() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveSession(widthDp = 1440f, heightDp = 960f)
        stageSkyLayer(s)
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()

        // The selection quad is display-only — an interactive overlay
        // would swallow the canvas's own gestures in the Layers
        // surface. Zoom past fit so a drag has pan room, then drag
        // through the outlined layer region.
        onNodeWithTag(TAG_LAYER_SELECTION).assertIsDisplayed()
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.Equals); keyUp(Key.Equals)
            keyDown(Key.Equals); keyUp(Key.Equals)
        }
        waitForIdle()
        val before = onNodeWithTag(TAG_IMAGE).getUnclippedBoundsInRoot()
        onNodeWithTag(TAG_CANVAS).performTouchInput {
            down(center)
            moveBy(Offset(80f, 40f))
            up()
        }
        waitForIdle()
        val after = onNodeWithTag(TAG_IMAGE).getUnclippedBoundsInRoot()
        assertTrue(
            before != after,
            "canvas pan must reach the viewport through the display " +
                "selection overlay — before=$before after=$after"
        )
    }

    @Test
    fun desktopFont2PropertiesStayReachable() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveSession(
            widthDp = 1440f, heightDp = 960f, fontScale = 2f
        )
        stageSkyLayer(s)
        onNodeWithTag(TAG_DESKTOP_SEGMENT_LAYERS).performClick()
        waitForIdle()

        // At 2x text the pane scrolls — every row and the pinned Add
        // button stay reachable and unclipped.
        onNodeWithTag(TAG_DESKTOP_PROP_EFFECTS)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_MASK)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_DESKTOP_PROP_OPACITY)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ADD)
            .performScrollTo()
            .assertIsDisplayed()
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
        waitUntil(timeoutMillis = 15_000) { s.state.value.toolEditing }
        onNodeWithTag(TAG_CANVAS).performKeyInput {
            keyDown(Key.F); keyUp(Key.F)
        }

        // Focus keeps the header and the six-slot dock: the card is the
        // only thing that shrinks. No generic navigator chrome, no
        // composite layer chrome, and no Apply row (edits land per
        // interaction).
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        // The header still owns the open tool's commit; Save returns when
        // the transaction settles.
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertDoesNotExist()
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
