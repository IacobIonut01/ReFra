package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotoLayerModelTest {

    private fun source(id: String, w: Int = 4, h: Int = 4) =
        MediaAsset.Image(id, "ref://$id", PixelSize(w, h))

    private fun layer(id: String, sourceId: String = "src-$id") = PhotoImageLayer(
        id = id,
        name = "Layer $id",
        source = source(sourceId)
    )

    @Test
    fun `transform and opacity bounds reject non-finite and out of range`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerTransform(scale = 0.009f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerTransform(scale = 10.01f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerTransform(rotationDegrees = 181f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerTransform(translationX = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            layer("a").copy(opacity = 1.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            layer("a").copy(opacity = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `layer ids and names must be non-blank and distinct from source id`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoImageLayer(id = " ", name = "n", source = source("s"))
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoImageLayer(id = "l", name = "", source = source("s"))
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoImageLayer(id = "same", name = "n", source = source("same"))
        }
    }

    @Test
    fun `mask stroke validation bounds points diameter hardness and flow`() {
        val two = persistentListOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 1f))
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(points = persistentListOf(), diameter = .5f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(
                points = List(257) { NormalizedPoint(0f, 0f) }.toPersistentList(),
                diameter = .5f
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(points = two, diameter = 0.0009f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(points = two, diameter = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(points = two, diameter = .5f, hardness = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoMaskStroke(points = two, diameter = .5f, flow = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPoint(1.5f, 0f)
        }
        // Boundary values are legal.
        PhotoMaskStroke(
            points = List(256) { NormalizedPoint(0f, 0f) }.toPersistentList(),
            diameter = 0.001f, hardness = 0f, flow = 1f
        )
    }

    @Test
    fun `mask validation bounds base alpha strokes and total points`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerMask(baseAlpha = 1.01f)
        }
        val stroke = PhotoMaskStroke(
            points = persistentListOf(NormalizedPoint(0f, 0f)),
            diameter = .5f
        )
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerMask(strokes = List(65) { stroke }.toPersistentList())
        }
        val heavy = PhotoMaskStroke(
            points = List(256) { NormalizedPoint(0f, 0f) }.toPersistentList(),
            diameter = .5f
        )
        // 8 x 256 = 2048 is legal; 9 strokes would exceed the point budget.
        PhotoLayerMask(strokes = List(8) { heavy }.toPersistentList())
        assertFailsWith<IllegalArgumentException> {
            PhotoLayerMask(strokes = List(9) { heavy }.toPersistentList())
        }
    }

    @Test
    fun `edits reject too many layers and duplicate ids`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoEdits(
                layers = List(17) { layer("l$it") }.toPersistentList()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoEdits(layers = persistentListOf(layer("dup"), layer("dup", "src-b")))
        }
        // Duplicated layers may share one immutable source id.
        val shared = layer("a")
        assertEquals(
            2,
            PhotoEdits(
                layers = persistentListOf(shared, shared.copy(id = "b"))
            ).layers.size
        )
        // 16 distinct layers are legal.
        PhotoEdits(layers = List(16) { layer("l$it") }.toPersistentList())
    }

    @Test
    fun `document layer source ids must differ from the base source id`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoDocument(
                source = source("base"),
                layers = persistentListOf(layer("l1", "base"))
            )
        }
        // The same host reference under a distinct source id is legal.
        val document = PhotoDocument(
            source = MediaAsset.Image("base", "ref://shared", PixelSize(4, 4)),
            layers = persistentListOf(
                PhotoImageLayer(
                    id = "l1", name = "n",
                    source = MediaAsset.Image("ls-1", "ref://shared", PixelSize(4, 4))
                )
            )
        )
        assertEquals(1, document.layers.size)
    }

    @Test
    fun `toEdits and toDocument round-trip the whole recipe`() {
        val layers: PersistentList<PhotoImageLayer> = persistentListOf(layer("l1"))
        val edits = PhotoEdits(
            color = ColorAdjustments(exposureStops = 1f),
            geometry = PhotoGeometry(quarterTurns = 1),
            layers = layers
        )
        val document = edits.toDocument(source("base"))
        assertEquals(edits, document.toEdits())
        assertEquals(layers, document.layers)
    }

    @Test
    fun `recipe copies preserve layers through global tool reconstructions`() {
        val edits = PhotoEdits(
            layers = persistentListOf(layer("l1"), layer("l2"))
        )
        val document = edits.toDocument(source("base"))
        val recolored = document.toEdits().copy(
            color = ColorAdjustments(exposureStops = -0.5f)
        )
        assertEquals(2, recolored.layers.size)
        val regemetried = document.toEdits().copy(
            geometry = PhotoGeometry(flipHorizontal = true)
        )
        assertEquals(2, regemetried.layers.size)
    }
}
