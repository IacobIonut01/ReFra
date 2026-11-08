package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * Round-trip contract for [PhotoProjectCodec]: every colour, geometry and
 * layer field — transform, blend, opacity, flags and every mask stroke —
 * survives encode/decode unchanged, the encoding is byte-deterministic,
 * and a malformed or wrong-versioned document fails loudly.
 */
class PhotoProjectCodecTest {

    private fun layer(
        id: String,
        reference: String,
        name: String = "layer-$id",
        transform: PhotoLayerTransform = PhotoLayerTransform(),
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        opacity: Float = 1f,
        visible: Boolean = true,
        locked: Boolean = false,
        clipToBelow: Boolean = false,
        mask: PhotoLayerMask = PhotoLayerMask()
    ) = PhotoImageLayer(
        id = id,
        name = name,
        source = MediaAsset.Image("src-$id", reference, PixelSize(4, 2)),
        transform = transform,
        blendMode = blendMode,
        opacity = opacity,
        visible = visible,
        locked = locked,
        clipToBelow = clipToBelow,
        mask = mask
    )

    private fun richEdits() = PhotoEdits(
        color = ColorAdjustments(
            exposureStops = 1.25f,
            contrast = 1.4f,
            saturation = 0.8f,
            highlights = -0.3f,
            shadows = 0.6f,
            whites = 0.2f,
            blacks = -0.4f
        ),
        geometry = PhotoGeometry(
            crop = NormalizedCrop(
                left = 0.05, top = 0.10, right = 0.95, bottom = 0.90
            ),
            quarterTurns = 1,
            flipHorizontal = true,
            flipVertical = true,
            warp = PhotoWarp(
                rotationDegrees = 12.5f,
                verticalPerspective = 0.35f,
                horizontalPerspective = -0.2f,
                scale = 1.15f
            )
        ),
        layers = persistentListOf(
            layer(
                id = "one",
                reference = "content://media/\"quoted\"\\path/é",
                transform = PhotoLayerTransform(
                    translationX = 0.1f,
                    translationY = -0.2f,
                    scale = 0.65f,
                    rotationDegrees = 45f,
                    flipHorizontal = true,
                    flipVertical = false
                ),
                blendMode = PhotoBlendMode.Screen,
                opacity = 0.65f,
                visible = true,
                locked = true,
                clipToBelow = true,
                mask = PhotoLayerMask(
                    baseAlpha = 0.5f,
                    inverted = true,
                    strokes = persistentListOf(
                        PhotoMaskStroke(
                            points = persistentListOf(
                                NormalizedPoint(0.1f, 0.2f),
                                NormalizedPoint(0.3f, 0.4f),
                                NormalizedPoint(0.5f, 0.6f)
                            ),
                            diameter = 0.05f,
                            hardness = 0.7f,
                            flow = 0.8f,
                            mode = PhotoMaskMode.Paint
                        ),
                        PhotoMaskStroke(
                            points = persistentListOf(
                                NormalizedPoint(0.7f, 0.8f)
                            ),
                            diameter = 0.2f,
                            hardness = 0.4f,
                            flow = 0.9f,
                            mode = PhotoMaskMode.Erase
                        )
                    )
                )
            ),
            layer(
                id = "two",
                reference = "content://media/second",
                blendMode = PhotoBlendMode.Multiply,
                opacity = 0.4f,
                visible = false
            )
        ),
        finishing = PhotoFinishing(
            curves = PhotoCurves(
                master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
                ),
                red = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)
                ),
                blue = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.2f, 0.5f, 0.8f, 0.9f)
                )
            ),
            whiteBalance = PhotoWhiteBalance(
                temperatureKelvin = 4200f, tint = 12f
            ),
            vignette = PhotoVignette(
                amount = -0.4f, midpoint = 0.7f, feather = 0.3f,
                roundness = 0.5f, highlights = 0.6f
            ),
            grain = PhotoGrain(
                amount = 0.35f, size = 0.5f, roughness = 0.8f, seed = 42
            )
        )
    )

    @Test
    fun `a full recipe round-trips every field`() {
        val snapshot = PhotoProjectSnapshot(richEdits())
        val decoded = PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(snapshot)
        )
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `the encoding is byte-deterministic`() {
        val snapshot = PhotoProjectSnapshot(richEdits())
        assertEquals(
            PhotoProjectCodec.encode(snapshot),
            PhotoProjectCodec.encode(snapshot)
        )
    }

    @Test
    fun `a default recipe round-trips`() {
        val snapshot = PhotoProjectSnapshot(PhotoEdits())
        assertEquals(
            snapshot,
            PhotoProjectCodec.decode(PhotoProjectCodec.encode(snapshot))
        )
    }

    @Test
    fun `every blend mode and mask mode encodes by name`() {
        for (mode in PhotoBlendMode.entries) {
            val snapshot = PhotoProjectSnapshot(
                PhotoEdits(
                    layers = persistentListOf(
                        layer("m", "ref://m", blendMode = mode)
                    )
                )
            )
            val decoded = PhotoProjectCodec.decode(
                PhotoProjectCodec.encode(snapshot)
            )
            assertEquals(mode, decoded.edits.layers.single().blendMode)
        }
        for (mode in PhotoMaskMode.entries) {
            val snapshot = PhotoProjectSnapshot(
                PhotoEdits(
                    layers = persistentListOf(
                        layer(
                            "m", "ref://m",
                            mask = PhotoLayerMask(
                                strokes = persistentListOf(
                                    PhotoMaskStroke(
                                        points = persistentListOf(
                                            NormalizedPoint(0f, 0f)
                                        ),
                                        diameter = 0.1f,
                                        mode = mode
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val decoded = PhotoProjectCodec.decode(
                PhotoProjectCodec.encode(snapshot)
            )
            assertEquals(
                mode,
                decoded.edits.layers.single().mask.strokes.single().mode
            )
        }
    }

    @Test
    fun `missing optional keys decode to their defaults`() {
        // A minimal layer — only the required fields — restores the
        // identity transform, Normal blend and an empty mask.
        val json = """
            {"version":1,
             "adjustments":{},
             "geometry":{"crop":{}},
             "layers":[{"id":"l","name":"n","source":
               {"id":"s","reference":"r","width":4,"height":2}}]}
        """.trimIndent()
        val decoded = PhotoProjectCodec.decode(json)
        val layer = decoded.edits.layers.single()
        assertEquals(PhotoLayerTransform(), layer.transform)
        assertEquals(PhotoBlendMode.Normal, layer.blendMode)
        assertEquals(1f, layer.opacity)
        assertEquals(PhotoLayerMask(), layer.mask)
        assertEquals(ColorAdjustments(), decoded.edits.color)
        assertEquals(PhotoGeometry(), decoded.edits.geometry)
    }

    @Test
    fun `unknown keys are ignored so newer builds still restore`() {
        val json = PhotoProjectCodec.encode(
            PhotoProjectSnapshot(richEdits())
        ).replace(
            "\"adjustments\":{",
            "\"adjustments\":{\"futureKnob\":42,"
        )
        assertNotEquals(json.indexOf("futureKnob"), -1)
        val decoded = PhotoProjectCodec.decode(json)
        assertEquals(richEdits().color, decoded.edits.color)
    }

    @Test
    fun `unsupported versions fail loudly`() {
        val base = PhotoProjectCodec.encode(
            PhotoProjectSnapshot(PhotoEdits())
        )
        val future = base.replaceFirst(
            "\"version\":${PhotoProjectCodec.VERSION}",
            "\"version\":${PhotoProjectCodec.VERSION + 1}"
        )
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(future)
        }
        val ancient = base.replaceFirst(
            "\"version\":${PhotoProjectCodec.VERSION}", "\"version\":0"
        )
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(ancient)
        }
    }

    @Test
    fun `a malformed document fails loudly`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode("not json")
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode("[]")
        }
        // A required field absent — the layer id — is not a half-load.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                """
                {"version":1,"adjustments":{},"geometry":{"crop":{}},
                 "layers":[{"name":"n","source":
                   {"id":"s","reference":"r","width":4,"height":2}}]}
                """.trimIndent()
            )
        }
        // An unknown enum value is corruption, not a silent fallback.
        assertFailsWith<IllegalArgumentException> {
            PhotoProjectCodec.decode(
                PhotoProjectCodec.encode(
                    PhotoProjectSnapshot(
                        PhotoEdits(
                            layers = persistentListOf(layer("l", "ref://l"))
                        )
                    )
                ).replace("\"Normal\"", "\"Dissolve\"")
            )
        }
    }
}
