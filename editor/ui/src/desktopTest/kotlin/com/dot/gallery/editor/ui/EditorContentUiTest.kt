package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoErrorKind
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoRecovery
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real-content UI tests for [PhotoEditorContent]: a fake hoisted state plus
 * a recording [PhotoEditorActions] drive the actual composable — no session,
 * no ViewModel, no I/O.
 */
@OptIn(ExperimentalTestApi::class)
class EditorContentUiTest {

    private class RecordingActions {
        val events = mutableListOf<String>()
        var lastEdits: PhotoEdits? = null
        var lastApplyLabel: String? = null

        val actions = PhotoEditorActions(
            beginEdit = { events += "begin:$it" },
            updateEdits = { events += "update"; lastEdits = it },
            commitEdit = { events += "commit" },
            cancelEdit = { events += "cancelEdit" },
            applyEdits = { label, edits ->
                events += "apply:$label"
                lastApplyLabel = label
                lastEdits = edits
            },
            undo = { events += "undo" },
            redo = { events += "redo" },
            reset = { events += "reset" },
            saveCopy = { events += "save" },
            cancelExport = { events += "cancelExport" },
            retryOpen = { events += "retryOpen" },
            retryPreview = { events += "retryPreview" },
            clearError = { events += "clearError" },
            close = { events += "close" },
            beginToolEdit = { events += "beginTool:$it" },
            applyToolEdit = { events += "applyTool" },
            cancelToolEdit = { events += "cancelTool" },
            requestAddImage = { events += "addImage" },
            cancelLayerImport = { events += "cancelImport" },
            retryRecovery = { events += "recoveryRetry" },
            discardRecovery = { events += "recoveryDiscard" },
            requestRelink = { events += "relink:$it" }
        )
    }

    /** A loaded state carrying one visible image layer plus its thumb. */
    private fun layeredState(
        extra: com.dot.gallery.editor.render.photo.PhotoEditorState.() ->
            com.dot.gallery.editor.render.photo.PhotoEditorState = { this }
    ): com.dot.gallery.editor.render.photo.PhotoEditorState {
        val base = fixtureState()
        val layer = PhotoImageLayer(
            id = "layer-1",
            name = "Sky",
            source = MediaAsset.Image(
                "src-1", "file:///sky.png", PixelSize(4, 4)
            )
        )
        return base.copy(
            document = base.document!!.copy(
                layers = persistentListOf(layer)
            ),
            layerPreviews = persistentMapOf("layer-1" to base.preview!!)
        ).extra()
    }

    @Test
    fun cropCategoryShowsPresetsAndRotateEmitsOneEdit() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        openPhotoTool(TAG_CATEGORY_CROP)
        onNodeWithTag(TAG_CROP_ASPECT).assertIsDisplayed()
        openCropAspectMenu()
        onNodeWithTag(TAG_CROP_1_1).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_4_5).assertIsDisplayed()
        onNodeWithTag(TAG_CROP_ORIGINAL).performClick()
        waitForIdle()
        expandPhotoToolSheet()
        onNodeWithTag(TAG_ROTATE_LEFT).assertIsDisplayed()

        onNodeWithTag(TAG_ROTATE_LEFT).performClick()
        assertTrue(rec.events.any { it.startsWith("apply:") })
        assertEquals(3, rec.lastEdits?.geometry?.quarterTurns)
        collapsePhotoToolSheet()

        rec.events.clear()
        selectCropAspect(TAG_CROP_1_1)
        val crop = rec.lastEdits?.geometry?.crop
        // 4:3 source cropped to 1:1 trims width symmetrically.
        assertTrue(crop != null && crop.left > 0.0 && crop.right < 1.0)
        assertEquals(1.0 - crop.left, crop.right, 1e-9)
    }

    @Test
    fun exposureRulerSetProgressAppliesOneToolStep() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        // Light is the default tool — open its panel through the
        // catalogue; the precision ruler edits the active parameter.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertIsDisplayed()
        rec.events.clear()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress ->
            // M3's SetProgress forwards the requested *value* (in the
            // ruler's own range), not a 0..1 fraction.
            setProgress(2.0f)
        }
        waitForIdle()
        // SetProgress commits one interaction through the tool seam:
        // the panel-scoped applyEdits ensures the tool transaction first.
        assertEquals("beginTool:Light", rec.events.first())
        assertEquals(
            listOf("beginTool:Light", "apply:Adjust Exposure"),
            rec.events
        )
        assertEquals(2.0f, rec.lastEdits!!.color.exposureStops, 0.01f)
    }

    @Test
    fun recoverySurfaceListsMissingAndDrivesTheThreeActions() =
        runComposeUiTest {
            val rec = RecordingActions()
            val state = fixtureState().copy(
                recovery = PhotoRecovery(
                    persistentListOf(
                        "content://gone/sky.png",
                        "content://gone/grass.png"
                    )
                )
            )
            setContent {
                TestEditorRoot {
                    Box(Modifier.size(390.dp, 844.dp)) {
                        PhotoEditorContent(state, rec.actions)
                    }
                }
            }
            // The state screen owns the surface: the title, every
            // missing leaf name and all three honest actions.
            onNodeWithTag(TAG_RECOVERY_SURFACE).assertIsDisplayed()
            onNodeWithText("sky.png").assertIsDisplayed()
            onNodeWithText("grass.png").assertIsDisplayed()

            onNodeWithTag(
                TAG_RECOVERY_RELINK_PREFIX + "content://gone/sky.png"
            ).performClick()
            assertEquals(
                listOf("relink:content://gone/sky.png"), rec.events
            )
            rec.events.clear()
            onNodeWithTag(TAG_RECOVERY_RETRY).performClick()
            onNodeWithTag(TAG_RECOVERY_DISCARD).performClick()
            assertEquals(
                listOf("recoveryRetry", "recoveryDiscard"), rec.events
            )
        }

    @Test
    fun undoRedoReflectStateAndMinTouchTargets() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(
                        fixtureState(canUndo = true, canRedo = false),
                        rec.actions
                    )
                }
            }
        }
        onNodeWithTag(TAG_UNDO).assertIsEnabled()
        onNodeWithTag(TAG_REDO).assertIsNotEnabled()
        // 40dp visual bounds expand to the 48dp minimum touch target.
        onNodeWithTag(TAG_BACK).assertTouchHeightIsEqualTo(48.dp)
        onNodeWithTag(TAG_SAVE).assertTouchHeightIsEqualTo(48.dp)
        onNodeWithTag(TAG_UNDO).performClick()
        assertEquals(listOf("undo"), rec.events)
        // The numeric value badge is small paint on a ≥48dp touch region —
        // the tool surface has no undo/redo of its own.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_VALUE_EXPOSURE).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun workbenchTooltipShowsOnHover() = runComposeUiTest {
        // The one test that runs tooltips enabled — every other test turns
        // them off because a tooltip popup is a second subscene that can
        // outlive its owner on the test render loop.
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalWorkbenchTooltipsEnabled provides true
                ) {
                    Box(Modifier.size(390.dp, 844.dp)) {
                        PhotoEditorContent(fixtureState(), rec.actions)
                    }
                }
            }
        }
        // A bare enter() only marks the cursor hovered — the Enter pointer
        // event is synthesized off the move that follows it.
        onNodeWithTag(TAG_UNDO).performMouseInput {
            enter()
            moveTo(center + Offset(1f, 0f))
        }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        // The header chip is an icon-only control, so its label lives in
        // the content description; the hover tooltip adds a text node.
        val undoNodes = onAllNodes(
            hasText("Undo") or hasContentDescription("Undo"),
            useUnmergedTree = true
        )
        assertEquals(2, undoNodes.fetchSemanticsNodes().size)
        onNodeWithTag(TAG_UNDO).performMouseInput {
            moveTo(center - Offset(1f, 0f))
            exit()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(
            1,
            onAllNodes(
                hasText("Undo") or hasContentDescription("Undo"),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        )
    }

    @Test
    fun errorRetryDispatchesByKind() = runComposeUiTest {
        val rec = RecordingActions()
        var state by mutableStateOf(
            fixtureState(error = "x", errorKind = PhotoErrorKind.Open)
        )
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(state, rec.actions)
                }
            }
        }
        onNodeWithTag(TAG_ERROR_RETRY).performClick()
        assertEquals(listOf("retryOpen"), rec.events)

        rec.events.clear()
        state = fixtureState(error = "x", errorKind = PhotoErrorKind.Preview)
        waitForIdle()
        onNodeWithTag(TAG_ERROR_RETRY).performClick()
        assertEquals(listOf("retryPreview"), rec.events)

        rec.events.clear()
        state = fixtureState(error = "x", errorKind = PhotoErrorKind.Export)
        waitForIdle()
        onNodeWithTag(TAG_ERROR_RETRY).performClick()
        assertEquals(listOf("save"), rec.events)

        // Capability failures offer no retry.
        state = fixtureState(error = "x", errorKind = PhotoErrorKind.Unsupported)
        waitForIdle()
        onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        onNodeWithTag(TAG_ERROR_RETRY).assertDoesNotExist()
        onNodeWithTag(TAG_ERROR_DISMISS).performClick()
        assertTrue(rec.events.last() == "clearError")
    }

    @Test
    fun dirtyBackShowsDiscardThenClosesOnce() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(isDirty = true), rec.actions)
                }
            }
        }
        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_DISCARD_CONFIRM).assertIsDisplayed()
        onNodeWithTag(TAG_DISCARD_KEEP).performClick()
        assertTrue(rec.events.isEmpty())

        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_DISCARD_CONFIRM).performClick()
        assertEquals(listOf("close"), rec.events)
    }

    @Test
    fun exportInFlightBackPromptsThenWaitsForWinddown() = runComposeUiTest {
        val rec = RecordingActions()
        var state by mutableStateOf(fixtureState(exporting = true))
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(state, rec.actions)
                }
            }
        }
        onNodeWithTag(TAG_SAVE_PROGRESS).assertIsDisplayed()
        onNodeWithTag(TAG_EXPORT_CANCEL).assertIsEnabled()
        // The modal covers the editor: the header underneath takes no taps.
        onNodeWithTag(TAG_BACK).performClick()
        waitForIdle()
        onNodeWithTag(TAG_EXPORT_DIALOG_CANCEL).assertDoesNotExist()
        assertTrue(rec.events.isEmpty())
        // Back (Escape) on the modal runs the close unwind, which asks first.
        onNodeWithTag(TAG_EXPORT_CANCEL).assertIsFocused()
        onNodeWithTag(TAG_EXPORT_CANCEL).performKeyInput {
            keyDown(Key.Escape); keyUp(Key.Escape)
        }
        onNodeWithTag(TAG_EXPORT_DIALOG_CANCEL).performClick()
        assertEquals(listOf("cancelExport"), rec.events)

        // While the export winds down nothing closes; when it finishes the
        // pending close lands once.
        state = fixtureState(
            exporting = false,
            exported = PhotoExportResult(
                "file:///out.png", "image/png", PixelSize(800, 600)
            )
        )
        waitForIdle()
        assertEquals(listOf("cancelExport", "close"), rec.events)
    }

    @Test
    fun resizingKeepsCategorySelection() = runComposeUiTest {
        val rec = RecordingActions()
        var width by mutableStateOf(390.dp)
        var height by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width, height)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        openPhotoTool(TAG_CATEGORY_COLOUR)
        onNodeWithTag(TAG_SLIDER_SATURATION).assertIsDisplayed()

        width = 1280.dp
        height = 800.dp
        waitForIdle()
        // Same composition, sidebar layout: the Colour slider is still there.
        onNodeWithTag(TAG_SLIDER_SATURATION).assertIsDisplayed()
        onNodeWithTag(TAG_SLIDER_EXPOSURE).assertDoesNotExist()
    }

    @Test
    fun saveDisabledWhileDrafting() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(isDrafting = true), rec.actions)
                }
            }
        }
        onNodeWithTag(TAG_SAVE).assertIsNotEnabled()
    }

    @Test
    fun largeFontKeepsActionsReachable() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density, fontScale = 2f
                    )
                ) {
                    Box(Modifier.size(390.dp, 844.dp)) {
                        PhotoEditorContent(fixtureState(), rec.actions)
                    }
                }
            }
        }
        // The action cluster can never be pushed off by the filename.
        onNodeWithTag(TAG_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_REDO).assertIsDisplayed()
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoCrop.value))
            .assertIsDisplayed()
        onNodeWithTag(TAG_SAVE).assertIsDisplayed().assertHasClickAction()
        // Tool navigation stays reachable at 2x font — through the
        // catalogue, which is the compact layout's tool picker.
        openPhotoTool(TAG_CATEGORY_CROP)
        onNodeWithTag(TAG_CROP_ASPECT).assertExists()
    }

    @Test
    fun toolHeaderPairStaysReachableAt2xFont() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density, fontScale = 2f
                    )
                ) {
                    Box(Modifier.size(390.dp, 844.dp)) {
                        PhotoEditorContent(fixtureState(), rec.actions)
                    }
                }
            }
        }
        // While a tool owns the draft the pill is gone; the context
        // header's Back/filled-Check pair is the compact layout's
        // cancel/commit and must stay reachable at 2× text.
        openPhotoTool(TAG_CATEGORY_LIGHT)
        onNodeWithTag(TAG_BACK).assertIsDisplayed().assertHasClickAction()
        onNodeWithTag(TAG_TOOL_APPLY).assertIsDisplayed().performClick()
        assertEquals(listOf("beginTool:Light", "applyTool"), rec.events)
    }

    @Test
    fun smallWindowToolsStayReachable() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(320.dp, 640.dp)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        openPhotoTool(TAG_CATEGORY_CROP)
        // The bounded tool panel scrolls — every control exists even when
        // the last rows sit below the fold.
        onNodeWithTag(TAG_CROP_ASPECT).assertExists()
        expandPhotoToolSheet()
        onNodeWithTag(TAG_ROTATE_LEFT).assertExists()
        onNodeWithTag(TAG_FLIP_V).assertExists()
        onNodeWithTag(TAG_RESET_CROP).assertExists()
    }

    // ── Layers workspace (P3b1) ─────────────────────────────────────────

    @Test
    fun layersToolbarOpensWorkspaceAndAddFiresImportCallback() =
        runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(layeredState(), rec.actions)
                }
            }
        }
        openLayersWorkspace()
        // The honest top-to-bottom stack: layer row, then the locked base.
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + "layer-1")
            .assertIsDisplayed()
        onNodeWithText("Sky").assertIsDisplayed()
        onNodeWithText("Normal · 100%").assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_BASE).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_GROUP).assertIsNotEnabled()

        // Both Add affordances route through the host picker seam.
        onNodeWithTag(TAG_LAYER_ADD).performClick()
        onNodeWithTag(TAG_LAYER_ADD_HEADER).performClick()
        assertEquals(listOf("addImage", "addImage"), rec.events)

        // Header Back is the V3 exit — the workspace collapses home.
        onNodeWithTag(TAG_BACK).performClick()
        onNodeWithTag(TAG_LAYER_ROW_PREFIX + "layer-1").assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE).assertIsDisplayed()
    }

    @Test
    fun layersPanelSurfacesImportProgressAndError() = runComposeUiTest {
        val rec = RecordingActions()
        var state by mutableStateOf(
            layeredState { copy(importingLayer = true) }
        )
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(state, rec.actions)
                }
            }
        }
        openLayersWorkspace()

        // An in-flight decode gates the picker buttons and offers a real
        // cancel through the session seam.
        onNodeWithTag(TAG_LAYER_ADD).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYER_ADD_HEADER).assertIsNotEnabled()
        onNodeWithTag(TAG_LAYER_IMPORT_CANCEL).performClick()
        assertEquals(listOf("cancelImport"), rec.events)

        // A failed import shows a local retry/dismiss — both wired.
        state = layeredState { copy(layerImportError = true) }
        onNodeWithTag(TAG_LAYER_IMPORT_RETRY).performClick()
        onNodeWithTag(TAG_LAYER_IMPORT_DISMISS).performClick()
        assertEquals(
            listOf("cancelImport", "addImage", "clearError"),
            rec.events
        )
    }

    @Test
    fun layerRowActionsEmitOneRecipeStepEach() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(layeredState(), rec.actions)
                }
            }
        }
        openLayersWorkspace()

        // The visibility toggle preserves every other recipe field.
        onNodeWithTag(TAG_LAYER_VISIBILITY_PREFIX + "layer-1")
            .performClick()
        assertEquals("apply:Toggle layer visibility", rec.events.last())
        assertEquals(false, rec.lastEdits!!.layers.single().visible)
        assertEquals("layer-1", rec.lastEdits!!.layers.single().id)

        // The More menu lists every action; at the stack bounds the move
        // entries are disabled — a single-layer list is both.
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + "layer-1").performClick()
        onNodeWithText("Rename").assertIsDisplayed()
        onNodeWithText("Duplicate").assertIsDisplayed()
        onNodeWithText("Move up").assertIsNotEnabled()
        onNodeWithText("Move down").assertIsNotEnabled()
        onNodeWithText("Lock").assertIsDisplayed()
        onNodeWithText("Delete").assertIsDisplayed()

        // Delete lands as one recipe step removing the layer.
        onNodeWithText("Delete").performClick()
        assertEquals("apply:Delete layer", rec.events.last())
        assertTrue(rec.lastEdits!!.layers.isEmpty())
    }

    // ── Contextual composition tools (P3b2a) ────────────────────────────

    @Test
    fun catalogueAvailabilityFollowsTheLayerList() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        onNodeWithTag(TAG_CATALOGUE).performClick()
        // No image layer: Transform and Blend cells paint disabled and
        // swallow the click; Double exposure stays reachable — its
        // empty surface is the real Add action.
        onNodeWithTag(TAG_CATALOGUE_GRID)
            .performScrollToNode(hasTestTag(TAG_CATEGORY_TRANSFORM))
        onNodeWithTag(TAG_CATEGORY_TRANSFORM).assertIsNotEnabled()
        onNodeWithTag(TAG_CATEGORY_BLEND).assertIsNotEnabled()
        onNodeWithTag(TAG_CATEGORY_DOUBLE_EXPOSURE).assertIsEnabled()
        onNodeWithTag(TAG_CATEGORY_TRANSFORM).performClick()
        assertTrue(rec.events.none { it.startsWith("beginTool") })
        onNodeWithTag(TAG_CATEGORY_DOUBLE_EXPOSURE).performClick()
        assertEquals(listOf("beginTool:Double exposure"), rec.events)
    }

    @Test
    fun doubleExposureEmptySurfaceFiresHostPicker() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(fixtureState(), rec.actions)
                }
            }
        }
        openPhotoTool(TAG_CATEGORY_DOUBLE_EXPOSURE)
        // With no layer the surface is the honest Add image action, not
        // fake controls.
        onNodeWithTag(TAG_DOUBLE_BASE).assertDoesNotExist()
        onNodeWithTag(TAG_DOUBLE_ADD).assertIsDisplayed().performClick()
        assertEquals("addImage", rec.events.last())
    }

    @Test
    fun layerRowMenuRoutesIntoContextualTools() = runComposeUiTest {
        val rec = RecordingActions()
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    PhotoEditorContent(layeredState(), rec.actions)
                }
            }
        }
        openLayersWorkspace()
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + "layer-1").performClick()
        // The route entries sit above the recipe actions; clicking one
        // selects this layer and opens the tool — no nested draft.
        onNodeWithTag(TAG_LAYER_ROUTE_TRANSFORM).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ROUTE_BLEND).assertIsDisplayed()
        onNodeWithTag(TAG_LAYER_ROUTE_MASK).assertIsDisplayed()
        onNodeWithText("Rename").assertIsDisplayed()
        onNodeWithText("Transform").performClick()
        assertEquals(listOf("beginTool:Transform"), rec.events)
        onNodeWithTag(TAG_LAYER_TRANSFORM_X).assertIsDisplayed()

        // Route to the mask via the same menu — the inspector shows the
        // real brush controls; the contextual-only id keeps no cell.
        rec.events.clear()
        onNodeWithTag(TAG_BACK).performClick()
        openLayersWorkspace()
        onNodeWithTag(TAG_LAYER_MORE_PREFIX + "layer-1").performClick()
        onNodeWithText("Edit mask").performClick()
        assertEquals(listOf("cancelTool", "beginTool:Mask"), rec.events)
        onNodeWithTag(TAG_MASK_ERASE).assertIsDisplayed()
        onNodeWithTag(TAG_MASK_PAINT).assertIsDisplayed()
    }
}
