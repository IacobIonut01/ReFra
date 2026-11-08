package com.dot.gallery.editor.android

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoEditorResult
import com.dot.gallery.editor.render.photo.PhotoEditorState
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The external intent contract of [EditorActivity], run against the real
 * exported activity inside this library's test APK: bad requests fail with
 * [EditorIntents.RESULT_ERROR], a save returns a granted copy, a clean back
 * is a plain cancel, and recreation keeps committed adjustments.
 *
 * Same-UID caveat: the test APK instruments its own library, so the
 * `content://` grant flows are exercised but do not prove cross-UID
 * behaviour against a foreign caller.
 */
@RunWith(AndroidJUnit4::class)
class EditorActivityIntentTest {

    // Unbound rule: the editor lives in the scenario-launched activity; the
    // "editor.save" tag is :editor:ui-internal, hence the literal.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val createdUris = mutableListOf<Uri>()
    private var fixtureBytes = ByteArray(0)
    private var fixtureFile: File? = null

    @Before
    fun setUp() {
        // A 4x2 PNG kept on disk so the source can be compared byte-for-byte
        // after the editor round-trips through MediaStore.
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            IntArray(8) { (0xFF shl 24) or (it * 1000) },
            0, 4, 0, 0, 4, 2
        )
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        fixtureBytes = out.toByteArray()
        fixtureFile = File(context.cacheDir, "intent-fixture.png")
            .apply { writeBytes(fixtureBytes) }
    }

    @After
    fun tearDown() {
        for (uri in createdUris) {
            resolver.delete(uri, null, null)
        }
        fixtureFile?.delete()
    }

    /** Publishes the fixture PNG through MediaStore as a real content:// source. */
    private fun insertSource(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "editor-intent-source.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EditorIntentTest")
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )!!
        resolver.openOutputStream(uri)!!.use { it.write(fixtureBytes) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        createdUris += uri
        return uri
    }

    private fun launchIntent(source: Uri): Intent =
        EditorIntents.create(context, source, EditorActivity::class.java)

    private fun awaitFinished(
        scenario: ActivityScenario<EditorActivity>,
        timeoutMs: Long = 30_000
    ): Instrumentation.ActivityResult {
        composeRule.waitUntil(timeoutMs) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        return scenario.result
    }

    private fun awaitSessionState(
        scenario: ActivityScenario<EditorActivity>,
        timeoutMs: Long = 30_000,
        condition: (PhotoEditorState) -> Boolean
    ) {
        composeRule.waitUntil(timeoutMs) {
            var met = false
            scenario.onActivity { activity ->
                met = condition(activity.viewModel.session.state.value)
            }
            met
        }
    }

    @Test
    fun missingDataIsAnErrorNotACancel() {
        val intent = Intent(context, EditorActivity::class.java)
            .setAction(EditorIntents.ACTION_EDIT)
        ActivityScenario.launchActivityForResult<EditorActivity>(intent).use { scenario ->
            val result = awaitFinished(scenario)
            assertEquals(EditorIntents.RESULT_ERROR, result.resultCode)
            val parsed = EditorIntents.parseResult(result.resultCode, result.resultData)
            assertTrue(parsed is PhotoEditorResult.Failed)
        }
    }

    @Test
    fun platformEditActionOpensTheEditor() {
        val source = insertSource()
        val intent = Intent(context, EditorActivity::class.java)
            .setAction(Intent.ACTION_EDIT)
            .setDataAndType(source, "image/png")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launchActivityForResult<EditorActivity>(intent)
            .use { scenario ->
                // The standard platform action is accepted, not rejected.
                awaitSessionState(scenario) {
                    it.document != null && !it.opening
                }
            }
    }

    @Test
    fun wrongActionAndExtraOutputAreRejected() {
        val source = insertSource()
        val wrongAction = Intent(context, EditorActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(source, "image/png")
        ActivityScenario.launchActivityForResult<EditorActivity>(wrongAction).use { scenario ->
            assertEquals(EditorIntents.RESULT_ERROR, awaitFinished(scenario).resultCode)
        }

        val withOutput = launchIntent(source)
            .putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse("content://x/y"))
        ActivityScenario.launchActivityForResult<EditorActivity>(withOutput).use { scenario ->
            val result = awaitFinished(scenario)
            assertEquals(EditorIntents.RESULT_ERROR, result.resultCode)
            assertTrue(
                EditorIntents.parseResult(result.resultCode, result.resultData)
                    is PhotoEditorResult.Failed
            )
        }
    }

    @Test
    fun fileSchemeIsRejected() {
        val intent = Intent(context, EditorActivity::class.java)
            .setAction(EditorIntents.ACTION_EDIT)
            .setDataAndType(Uri.fromFile(fixtureFile), "image/png")
        ActivityScenario.launchActivityForResult<EditorActivity>(intent).use { scenario ->
            assertEquals(EditorIntents.RESULT_ERROR, awaitFinished(scenario).resultCode)
        }
    }

    @Test
    fun missingReadGrantFailsBeforeOpen() {
        val source = insertSource()
        // Same request shape minus the read grant — the activity must fail
        // before the session ever touches the URI.
        val intent = Intent(context, EditorActivity::class.java)
            .setAction(EditorIntents.ACTION_EDIT)
            .setDataAndType(source, "image/png")
        ActivityScenario.launchActivityForResult<EditorActivity>(intent).use { scenario ->
            val result = awaitFinished(scenario)
            assertEquals(EditorIntents.RESULT_ERROR, result.resultCode)
            assertTrue(
                EditorIntents.parseResult(result.resultCode, result.resultData)
                    is PhotoEditorResult.Failed
            )
        }
    }

    @Test
    fun plantedSourceExtraCannotHijackTheOpen() {
        // An 8x8 "private" file the content:// caller cannot legitimately
        // point the editor at: if the planted SavedStateHandle extra wins,
        // the document size gives it away.
        val privateFile = File(context.cacheDir, "planted-private.png")
        val privateBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        privateBitmap.eraseColor(0xFFFF0000.toInt())
        privateFile.outputStream().use {
            privateBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        privateBitmap.recycle()

        val source = insertSource() // the real, 4x2 content:// source
        val intent = launchIntent(source)
            // SavedStateHandle merges intent extras; an untrusted caller can
            // therefore seed "editor.source" / recipe keys directly.
            .putExtra("editor.source", Uri.fromFile(privateFile).toString())
            .putExtra("editor.exposure", 3f)
            // A wrong-type handledExport would crash the auto-finish check
            // after a real save if it were read unchecked — and a valid one
            // would suppress the finish entirely. Neither may happen.
            .putExtra("editor.handledExport", 123)

        ActivityScenario.launchActivityForResult<EditorActivity>(intent)
            .use { scenario ->
                awaitSessionState(scenario) {
                    it.document != null && !it.opening
                }
                scenario.onActivity { activity ->
                    val state = activity.viewModel.session.state.value
                    val document = requireNotNull(state.document)
                    assertEquals(source.toString(), document.source.reference)
                    assertEquals(PixelSize(4, 2), document.source.size)
                    // The planted recipe is not an authority on a fresh
                    // launch — the session opens clean.
                    assertEquals(
                        0f, document.adjustments.exposureStops, 0.001f
                    )
                    assertTrue(!state.isDirty)
                }

                // A real save must still return a valid file — the planted
                // receipt key can neither crash nor suppress the result.
                composeRule.onNodeWithTag("editor.save").performClick()
                val result = awaitFinished(scenario)
                assertEquals(Activity.RESULT_OK, result.resultCode)
                val parsed = EditorIntents.parseResult(
                    result.resultCode, result.resultData
                )
                assertTrue(
                    "expected Saved, got $parsed",
                    parsed is PhotoEditorResult.Saved
                )
                val saved = (parsed as PhotoEditorResult.Saved).photo
                createdUris += Uri.parse(saved.reference)
                val decoded = resolver.openInputStream(
                    Uri.parse(saved.reference)
                ).use { BitmapFactory.decodeStream(it) }
                assertNotNull(decoded)
            }
    }

    @Test
    fun cleanBackIsCancelled() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<EditorActivity>(launchIntent(source))
            .use { scenario ->
                Espresso.pressBackUnconditionally()
                val result = awaitFinished(scenario)
                assertEquals(Activity.RESULT_CANCELED, result.resultCode)
                assertEquals(
                    PhotoEditorResult.Cancelled,
                    EditorIntents.parseResult(result.resultCode, result.resultData)
                )
            }
    }

    @Test
    fun saveReturnsGrantedPngCopyAndLeavesSourceAlone() {
        val source = insertSource()
        val sourceBytesBefore = resolver.openInputStream(source)!!.use { it.readBytes() }
        val scenario = ActivityScenario.launchActivityForResult<EditorActivity>(
            launchIntent(source)
        )
        try {
            // Wait for the loaded editor, then click "Save copy"; the clean
            // save auto-finishes the external-editor task.
            awaitSessionState(scenario) {
                it.document != null && !it.opening
            }
            composeRule.onNodeWithTag("editor.save").performClick()
            val result = awaitFinished(scenario)
            assertEquals(Activity.RESULT_OK, result.resultCode)

            val parsed = EditorIntents.parseResult(result.resultCode, result.resultData)
            assertTrue("expected Saved, got $parsed", parsed is PhotoEditorResult.Saved)
            val saved = (parsed as PhotoEditorResult.Saved).photo
            assertEquals("image/png", saved.mimeType)
            assertEquals(PixelSize(4, 2), saved.size)
            assertTrue(saved.reference != source.toString())
            // Read grant returned to the caller through the result intent.
            assertTrue(
                result.resultData!!.flags and
                    Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            )
            val outputUri = Uri.parse(saved.reference)
            createdUris += outputUri
            val decoded = resolver.openInputStream(outputUri).use {
                BitmapFactory.decodeStream(it)
            }
            assertNotNull(decoded)
            assertEquals(4, decoded!!.width)
            assertEquals(2, decoded.height)
            // The source bytes are untouched.
            val sourceAfter = resolver.openInputStream(source)!!.use { it.readBytes() }
            org.junit.Assert.assertArrayEquals(sourceBytesBefore, sourceAfter)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun recreationKeepsCommittedAdjustment() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<EditorActivity>(launchIntent(source))
            .use { scenario ->
                awaitSessionState(scenario) {
                    it.document != null && !it.opening
                }
                scenario.onActivity { activity ->
                    activity.viewModel.session.applyEdits(
                        "Rotate right",
                        PhotoEdits(
                            geometry = PhotoGeometry(quarterTurns = 1)
                        )
                    )
                }
                awaitSessionState(scenario, 10_000) {
                    it.document?.geometry?.quarterTurns == 1
                }
                scenario.recreate()
                awaitSessionState(scenario) {
                    it.document?.geometry?.quarterTurns == 1
                }
            }
    }

    @Test
    fun recreationKeepsToolDraftWhilePersistenceSkipsIt() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<EditorActivity>(launchIntent(source))
            .use { scenario ->
                awaitSessionState(scenario) {
                    it.document != null && !it.opening
                }
                // One committed step that must survive recreation.
                scenario.onActivity { activity ->
                    activity.viewModel.session.applyEdits(
                        "Rotate right",
                        PhotoEdits(
                            geometry = PhotoGeometry(quarterTurns = 1)
                        )
                    )
                }
                awaitSessionState(scenario, 10_000) {
                    it.document?.geometry?.quarterTurns == 1
                }
                // An open tool transaction publishes its draft through the
                // document but keeps isDrafting — persistence must skip it.
                scenario.onActivity { activity ->
                    activity.viewModel.session.beginToolEdit("Light")
                    activity.viewModel.session.applyEdits(
                        "Exposure",
                        PhotoEdits(
                            color = ColorAdjustments(exposureStops = 2f),
                            geometry = PhotoGeometry(quarterTurns = 1)
                        )
                    )
                }
                awaitSessionState(scenario) {
                    it.toolEditing && it.isDrafting &&
                        it.document?.adjustments?.exposureStops == 2f
                }
                scenario.recreate()
                // scenario.recreate() is a config-change relaunch — the
                // ViewModelStore survives, so the live session keeps the
                // open tool and its draft (rotation included).
                awaitSessionState(scenario) {
                    it.toolEditing && it.isDrafting &&
                        it.document?.geometry?.quarterTurns == 1 &&
                        it.document?.adjustments?.exposureStops == 2f
                }
                // What a process-death restore would replay, though, is
                // only the committed recipe — the uncommitted tool edits
                // never reached the persisted keys.
                scenario.onActivity { activity ->
                    val persisted = requireNotNull(
                        activity.viewModel.persistedEdits()
                    )
                    assertEquals(1, persisted.geometry.quarterTurns)
                    assertEquals(0f, persisted.color.exposureStops, 0.001f)
                }
            }
    }

    @Test
    fun recreationKeepsCommittedWarpWhileToolDraftStaysUnpersisted() {
        val source = insertSource()
        ActivityScenario.launchActivityForResult<EditorActivity>(launchIntent(source))
            .use { scenario ->
                awaitSessionState(scenario) {
                    it.document != null && !it.opening
                }
                // A committed warp recipe must survive recreation and
                // reach the persisted keys verbatim.
                scenario.onActivity { activity ->
                    activity.viewModel.session.applyEdits(
                        "Straighten",
                        PhotoEdits(
                            geometry = PhotoGeometry(
                                warp = PhotoWarp(
                                    rotationDegrees = -12f,
                                    verticalPerspective = 10f,
                                    horizontalPerspective = -6f,
                                    scale = 1.2f
                                )
                            )
                        )
                    )
                }
                awaitSessionState(scenario, 10_000) {
                    it.document?.geometry?.warp?.rotationDegrees == -12f &&
                        it.document?.geometry?.warp?.scale == 1.2f
                }
                // An open tool's changed warp stays live in the session
                // but must not overwrite the committed persisted values.
                scenario.onActivity { activity ->
                    activity.viewModel.session.beginToolEdit("Perspective")
                    activity.viewModel.session.applyEdits(
                        "Vertical",
                        PhotoEdits(
                            geometry = PhotoGeometry(
                                warp = PhotoWarp(
                                    rotationDegrees = -12f,
                                    verticalPerspective = 40f,
                                    horizontalPerspective = -6f,
                                    scale = 1.2f
                                )
                            )
                        )
                    )
                }
                awaitSessionState(scenario) {
                    it.toolEditing && it.isDrafting &&
                        it.document?.geometry?.warp?.verticalPerspective == 40f
                }
                scenario.recreate()
                awaitSessionState(scenario) {
                    it.toolEditing && it.isDrafting &&
                        it.document?.geometry?.warp?.verticalPerspective == 40f
                }
                scenario.onActivity { activity ->
                    val persisted = requireNotNull(
                        activity.viewModel.persistedEdits()
                    )
                    val warp = persisted.geometry.warp
                    assertEquals(-12f, warp.rotationDegrees, 0.001f)
                    assertEquals(10f, warp.verticalPerspective, 0.001f)
                    assertEquals(-6f, warp.horizontalPerspective, 0.001f)
                    assertEquals(1.2f, warp.scale, 0.001f)
                }
            }
    }

    @Test
    fun pickedContentUriImportsAsAnImageLayer() {
        // The OpenDocument launcher hands a content:// URI straight to
        // session.addImageLayer — drive that same seam with a real
        // MediaStore URI rather than the system picker UI.
        val source = insertSource()
        val layerSource = insertSource()
        ActivityScenario.launchActivityForResult<EditorActivity>(
            launchIntent(source)
        ).use { scenario ->
            awaitSessionState(scenario) {
                it.document != null && !it.opening
            }
            scenario.onActivity { activity ->
                activity.viewModel.session.addImageLayer(
                    layerSource.toString()
                )
            }
            awaitSessionState(scenario) {
                !it.importingLayer &&
                    (it.document?.layers?.size ?: 0) == 1
            }
            scenario.onActivity { activity ->
                val state = activity.viewModel.session.state.value
                val layer = state.document!!.layers.single()
                assertEquals(layerSource.toString(), layer.source.reference)
                assertTrue(state.canUndo)
                assertTrue(!state.isDrafting && !state.toolEditing)
            }
        }
    }
}
