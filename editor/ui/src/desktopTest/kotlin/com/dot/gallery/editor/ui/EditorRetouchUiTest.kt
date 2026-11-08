package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoMarkupTextLayout
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.render.measureMarkupTextEm
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Drives the retouch tools through the real screen over a live session. The
 * PNGs under `build/review/retouch` are the design-review captures.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorRetouchUiTest {

    private fun ComposeUiTest.staged(): PhotoEditorSession {
        val session = openedSession(samplePixels())
        setContent {
            TestEditorRoot(darkTheme = true) {
                CompositionLocalProvider(
                    LocalEditorHostTools provides EditorHostTools(openInExternalEditor = {})
                ) {
                    PhotoEditorScreen(
                        session = session,
                        onClose = {},
                        onRetryOpen = { session.open("fixture://editor_sample.jpg") },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        awaitImage(session)
        return session
    }

    private fun ComposeUiTest.save(name: String) {
        waitForIdle()
        val directory = File("build/review/retouch").apply { mkdirs() }
        // A dialog is its own root, and the newest root is the one on top.
        val image = Image.makeFromBitmap(
            onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()
        )
        File(directory, "$name.png").writeBytes(
            image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        )
    }

    private fun ComposeUiTest.drawStroke(
        session: PhotoEditorSession,
        expected: Int,
        y: Float = 0.4f
    ) {
        onNodeWithTag(TAG_MARKUP_SURFACE).performTouchInput {
            swipe(
                Offset(width * 0.2f, height * y),
                Offset(width * 0.8f, height * (y + 0.2f)),
                durationMillis = 300
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.document?.retouch?.markup?.strokes?.size == expected
        }
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.previewDocument?.retouch?.markup?.strokes?.size == expected
        }
    }

    @Test
    fun markupDrawsFromTheDefaultDock() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                save("home")
                onNodeWithTag(photoPinTag(EditorToolIds.PhotoMarkup.value)).performClick()
                onNodeWithTag("editor.markup").assertIsDisplayed()
                drawStroke(session, 1)
                save("markup-pen")
                assertEquals(
                    PhotoMarkupType.Pen,
                    session.state.value.document!!.retouch.markup.strokes.single().type
                )
                onNodeWithTag("editor.markup.tab.Blur").performClick()
                drawStroke(session, 2, y = 0.1f)
                save("markup-blur")
                onNodeWithTag("editor.markup.tab.Mosaic").performClick()
                drawStroke(session, 3, y = 0.7f)
                save("markup-mosaic")
                // The stroke really rewrote pixels along its path.
                val preview = session.state.value.preview!!
                val original = session.state.value.originalPreview!!
                val x = preview.size.width / 2
                val y = (preview.size.height * 0.8f).toInt()
                assertNotEquals(original.argbAt(x, y), preview.argbAt(x, y))
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun theEraserRemovesAStrokeItCrosses() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                onNodeWithTag(photoPinTag(EditorToolIds.PhotoMarkup.value)).performClick()
                drawStroke(session, 1)
                onNodeWithTag("editor.markup.ink.erase").performClick()
                waitForIdle()
                onNodeWithTag(TAG_MARKUP_SURFACE).performTouchInput {
                    swipe(
                        Offset(width * 0.5f, height * 0.3f),
                        Offset(width * 0.5f, height * 0.7f),
                        durationMillis = 300
                    )
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document?.retouch?.markup?.strokes?.isEmpty() == true
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun effectsRenderTheirRecipes() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                onNodeWithTag(TAG_CATALOGUE).performClick()
                onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
                save("catalogue")
                onNodeWithTag(TAG_CATALOGUE_GRID)
                    .performScrollToNode(hasTestTag(TAG_CATEGORY_POSTERIZE))
                onNodeWithTag(TAG_CATEGORY_POSTERIZE).performClick()
                onNodeWithTag("editor.effects").assertIsDisplayed()
                runOnUiThread {
                    val edits = session.state.value.document!!.toEdits()
                    session.applyEdits(
                        "Posterize",
                        edits.copy(
                            retouch = edits.retouch.copy(
                                effects = edits.retouch.effects.copy(posterize = 0.8f)
                            )
                        )
                    )
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.previewDocument?.retouch?.effects?.posterize == 0.8f
                }
                save("effects-posterize")
                onNodeWithTag("editor.effects.tab.Borders").performClick()
                runOnUiThread {
                    val edits = session.state.value.document!!.toEdits()
                    session.applyEdits(
                        "Border",
                        edits.copy(
                            retouch = edits.retouch.copy(
                                effects = edits.retouch.effects.copy(borderWidth = 0.4f)
                            )
                        )
                    )
                }
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.previewDocument?.retouch?.effects?.borderWidth == 0.4f
                }
                save("effects-borders")
            } finally {
                runOnUiThread { session.close() }
            }
        }

    private fun ComposeUiTest.openFromCatalogue(tag: String) {
        onNodeWithTag(TAG_CATALOGUE).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun colourToneOpensFromTheCatalogue() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                openFromCatalogue(TAG_CATEGORY_COLOUR_TONE)
                onNodeWithTag("editor.colourTone").assertIsDisplayed()
                save("colour-tone")
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun textIsAddedSelectedAndCaptured() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                onNodeWithTag(photoPinTag(EditorToolIds.PhotoMarkup.value)).performClick()
                onNodeWithTag("editor.markup.tab.Text").performClick()
                waitForIdle()
                save("markup-text-entry")
                onNodeWithTag(TAG_MARKUP_TEXT_FIELD).performTextInput("Fort Point")
                save("markup-text-typed")
                onNodeWithTag("editor.markup.text.done").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.previewDocument?.retouch?.markup?.texts?.size == 1
                }
                save("markup-text")
            } finally {
                runOnUiThread { session.close() }
            }
        }

    private fun ComposeUiTest.stageText(session: PhotoEditorSession, text: String) {
        onNodeWithTag(photoPinTag(EditorToolIds.PhotoMarkup.value)).performClick()
        onNodeWithTag("editor.markup.tab.Text").performClick()
        waitForIdle()
        onNodeWithTag(TAG_MARKUP_TEXT_FIELD).performTextInput(text)
        onNodeWithTag("editor.markup.text.done").performClick()
        waitUntil(timeoutMillis = 15_000) {
            session.state.value.previewDocument?.retouch?.markup?.texts?.size == 1
        }
        waitForIdle()
    }

    private fun PhotoEditorSession.label() = state.value.document!!.retouch.markup.texts.single()

    /** Drags from a node's centre by a window-space offset. */
    private fun ComposeUiTest.dragFrom(tag: String, delta: Offset) {
        onNodeWithTag(tag).performTouchInput {
            swipe(center, center + delta, durationMillis = 300)
        }
        waitForIdle()
    }

    @Test
    fun textRotatesResizesAndDeletesFromItsHandles() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                stageText(session, "Fort Point")
                save("markup-text-handles")

                dragFrom("editor.markup.text.handle.rotate", Offset(0f, 90f))
                waitUntil(timeoutMillis = 15_000) { session.label().rotation != 0f }
                assertTrue(session.label().rotation > 10f, "rotation ${session.label().rotation}")
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.previewDocument?.retouch?.markup?.texts?.single()?.rotation ==
                        session.label().rotation
                }
                save("markup-text-rotated")

                val before = session.label().size
                dragFrom("editor.markup.text.handle.resizeEnd", Offset(0f, 60f))
                waitUntil(timeoutMillis = 15_000) { session.label().size != before }
                assertTrue(session.label().size > before)
                save("markup-text-resized")

                onNodeWithTag("editor.markup.text.handle.delete").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.document!!.retouch.markup.texts.isEmpty()
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun aHugeResizeStopsAtTheFrame() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                stageText(session, "A long line of text")
                dragFrom("editor.markup.text.handle.resizeEnd", Offset(0f, 600f))
                waitForIdle()
                val text = session.label()
                val (emW, emH) = measureMarkupTextEm(text.text)!!
                val frame = session.state.value.preview!!.size
                val px = text.size * frame.height
                val (extentW, extentH) = PhotoMarkupTextLayout.rotatedExtent(
                    emW * px, emH * px, text.rotation
                )
                assertTrue(text.size <= PhotoMarkupTextLayout.MAX_SIZE)
                assertTrue(extentW <= frame.width * 1.01f, "$extentW over ${frame.width}")
                assertTrue(extentH <= frame.height * 1.01f, "$extentH over ${frame.height}")
                assertTrue(text.x * frame.width - extentW / 2 >= -1f)
                assertTrue(text.x * frame.width + extentW / 2 <= frame.width + 1f)
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun aDragCannotPushTextOffThePhoto() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                stageText(session, "Fort Point")
                // Drag the body to the window's left edge, past where the box fits.
                val text = session.label()
                onNodeWithTag(TAG_MARKUP_SURFACE).performTouchInput {
                    val start = Offset(width * text.x, height * text.y)
                    swipe(start, Offset(1f, start.y), durationMillis = 300)
                }
                waitUntil(timeoutMillis = 15_000) { session.label().x != text.x }
                waitForIdle()
                val moved = session.label()
                val (emW, emH) = measureMarkupTextEm(moved.text)!!
                val frame = session.state.value.preview!!.size
                val px = moved.size * frame.height
                val (extentW, _) = PhotoMarkupTextLayout.rotatedExtent(emW * px, emH * px, moved.rotation)
                assertEquals(extentW / 2 / frame.width, moved.x, 0.01f)
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun multilineTextDropsBlankEdgeLinesAndBlankTextLeavesNoLabel() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = staged()
            try {
                stageText(session, "\n\nOne\n\nTwo\n\n")
                assertEquals("One\n\nTwo", session.label().text)
                save("markup-text-multiline")
                onNodeWithTag("editor.markup.addText").performClick()
                waitForIdle()
                onNodeWithTag(TAG_MARKUP_TEXT_FIELD).performTextInput("  \n  ")
                onNodeWithTag("editor.markup.text.done").performClick()
                waitForIdle()
                assertEquals(1, session.state.value.document!!.retouch.markup.texts.size)
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun selectionToolsDriveTheHostSegmenter() =
        runDesktopComposeUiTest(width = 412, height = 832) {
            val session = openedSession(samplePixels())
            val fake = object : EditorSegmenterSession {
                override suspend fun segment(points: List<EditorSegmentPoint>) =
                    PhotoCutoutMatte(4, 4, ByteArray(16) { if (it in 5..10) -1 else 0 })
                override fun close() = Unit
            }
            setContent {
                TestEditorRoot(darkTheme = true) {
                    CompositionLocalProvider(
                        LocalEditorHostTools provides EditorHostTools(
                            openSegmenter = { fake },
                            copySubject = { true },
                            shareSubject = { true }
                        )
                    ) {
                        PhotoEditorScreen(
                            session = session,
                            onClose = {},
                            onRetryOpen = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            awaitImage(session)
            try {
                openFromCatalogue(TAG_CATEGORY_CUTOUT)
                onNodeWithTag("editor.cutout").assertIsDisplayed()
                waitForIdle()
                save("cutout")
                // Cutout never touches the recipe.
                assertEquals(null, session.state.value.document!!.retouch.matte)
                runOnUiThread { session.cancelToolEdit() }
                onNodeWithTag(TAG_CATALOGUE).performClick()
                onNodeWithTag(TAG_CATALOGUE_GRID)
                    .performScrollToNode(hasTestTag(TAG_CATEGORY_REMOVE_BACKGROUND))
                onNodeWithTag(TAG_CATEGORY_REMOVE_BACKGROUND).performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session.state.value.previewDocument?.retouch?.matte != null
                }
                save("remove-background")
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun markupFitsLandscapeAndTablet() {
        listOf(Triple(884, 390, "markup-landscape"), Triple(1280, 800, "markup-tablet"))
            .forEach { (width, height, name) ->
                runDesktopComposeUiTest(width = width, height = height) {
                    val session = staged()
                    try {
                        onNodeWithTag(TAG_CATEGORY_MARKUP).performScrollTo().performClick()
                        onNodeWithTag("editor.markup").assertIsDisplayed()
                        drawStroke(session, 1)
                        save(name)
                    } finally {
                        runOnUiThread { session.close() }
                    }
                }
            }
    }
}
