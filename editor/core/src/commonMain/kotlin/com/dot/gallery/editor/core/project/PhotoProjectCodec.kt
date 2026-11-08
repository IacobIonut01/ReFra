package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoCurvePoint
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PhotoColourTone
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoEffects
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupText
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PhotoRawColourSpace
import com.dot.gallery.editor.core.model.PhotoRawDemosaic
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoRawHighlights
import com.dot.gallery.editor.core.model.PhotoRawNoiseReduction
import com.dot.gallery.editor.core.model.PhotoRawWhiteBalance
import com.dot.gallery.editor.core.model.PhotoRetouch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.collections.immutable.toPersistentList

/**
 * Deterministic JSON codec for a [PhotoProjectSnapshot] — the full photo
 * recipe: colour, geometry and every image layer with its transform,
 * blend, clip flag and mask strokes. Decoding is strict about shape and
 * version but ignores unknown keys, so a document written by a newer
 * build still restores what it can. Every model invariant is re-checked
 * by the rebuilt types' own validation — a malformed or corrupt project
 * throws [IllegalArgumentException], never yields a half-loaded recipe.
 */
object PhotoProjectCodec {

    /**
     * The current format — version 7 adds the retouch stage (absent files
     * decode neutral), version 6 stores tone curves as free points,
     * version 5 adds the colour grade, version 4 the finishing look. Version
     * 1 files decode with a neutral finishing and spatial stage; version 2
     * files decode with a neutral spatial stage; versions 1-3 decode with no
     * look selected; versions 1-4 decode with a neutral grade; versions 2-5
     * hold five quarter-spaced curve ordinates, read as points.
     */
    const val VERSION = 7

    /** The oldest readable format. */
    const val MIN_VERSION = 1

    private const val MAX_MATTE_PIXELS = 16_000_000L

    fun encode(snapshot: PhotoProjectSnapshot): String {
        val out = ProjectJson.Writer()
        encodeSnapshot(out, snapshot)
        return out.toString()
    }

    fun decode(json: String): PhotoProjectSnapshot {
        val value = ProjectJson.parse(json)
        val root = value as? ProjectJson.Object
            ?: throw IllegalArgumentException("project root must be an object")
        val version = root.reqLong("version")
        require(version in MIN_VERSION.toLong()..VERSION.toLong()) {
            "unsupported project version: $version"
        }
        return decodeSnapshot(root)
    }

    // ----- encoding -----------------------------------------------------

    private fun encodeSnapshot(out: ProjectJson.Writer, s: PhotoProjectSnapshot) {
        out.beginObject()
        out.key("version").value(VERSION)
        out.key("adjustments"); encodeAdjustments(out, s.edits.color)
        out.key("geometry"); encodeGeometry(out, s.edits.geometry)
        out.key("layers").beginArray()
        for (layer in s.edits.layers) encodeLayer(out, layer)
        out.endArray()
        out.key("spatial"); encodeSpatial(out, s.edits.spatial)
        out.key("finishing"); encodeFinishing(out, s.edits.finishing)
        out.key("retouch"); encodeRetouch(out, s.edits.retouch)
        out.key("develop"); encodeDevelop(out, s.edits.develop)
        out.endObject()
    }

    private fun encodeAdjustments(out: ProjectJson.Writer, a: ColorAdjustments) {
        out.beginObject()
        out.key("exposureStops").value(a.exposureStops)
        out.key("contrast").value(a.contrast)
        out.key("saturation").value(a.saturation)
        out.key("highlights").value(a.highlights)
        out.key("shadows").value(a.shadows)
        out.key("whites").value(a.whites)
        out.key("blacks").value(a.blacks)
        out.endObject()
    }

    private fun encodeGeometry(out: ProjectJson.Writer, g: PhotoGeometry) {
        out.beginObject()
        out.key("crop").beginObject()
        out.key("left").value(g.crop.left)
        out.key("top").value(g.crop.top)
        out.key("right").value(g.crop.right)
        out.key("bottom").value(g.crop.bottom)
        out.endObject()
        out.key("quarterTurns").value(g.quarterTurns)
        out.key("flipHorizontal").value(g.flipHorizontal)
        out.key("flipVertical").value(g.flipVertical)
        out.key("warp").beginObject()
        out.key("rotationDegrees").value(g.warp.rotationDegrees)
        out.key("verticalPerspective").value(g.warp.verticalPerspective)
        out.key("horizontalPerspective").value(g.warp.horizontalPerspective)
        out.key("scale").value(g.warp.scale)
        out.endObject()
        out.endObject()
    }

    private fun encodeLayer(out: ProjectJson.Writer, layer: PhotoImageLayer) {
        out.beginObject()
        out.key("id").value(layer.id)
        out.key("name").value(layer.name)
        out.key("source").beginObject()
        out.key("id").value(layer.source.id)
        out.key("reference").value(layer.source.reference)
        out.key("width").value(layer.source.size.width)
        out.key("height").value(layer.source.size.height)
        out.endObject()
        out.key("transform").beginObject()
        out.key("translationX").value(layer.transform.translationX)
        out.key("translationY").value(layer.transform.translationY)
        out.key("scale").value(layer.transform.scale)
        out.key("rotationDegrees").value(layer.transform.rotationDegrees)
        out.key("flipHorizontal").value(layer.transform.flipHorizontal)
        out.key("flipVertical").value(layer.transform.flipVertical)
        out.endObject()
        out.key("blendMode").value(layer.blendMode.name)
        out.key("opacity").value(layer.opacity)
        out.key("visible").value(layer.visible)
        out.key("locked").value(layer.locked)
        out.key("clipToBelow").value(layer.clipToBelow)
        out.key("mask"); encodeMask(out, layer.mask)
        out.endObject()
    }

    private fun encodeGrade(out: ProjectJson.Writer, grade: PhotoColourGrade) {
        out.beginObject()
        out.key("vibrance").value(grade.vibrance)
        out.key("protectSkin").value(grade.protectSkin)
        out.key("hsl").beginObject()
        PhotoHslBand.entries.forEach { band ->
            val adjustment = grade.adjustment(band)
            out.key(band.name).beginObject()
            out.key("hue").value(adjustment.hue)
            out.key("saturation").value(adjustment.saturation)
            out.key("luminance").value(adjustment.luminance)
            out.endObject()
        }
        out.endObject()
        encodeWheel(out, "shadows", grade.shadows)
        encodeWheel(out, "midtones", grade.midtones)
        encodeWheel(out, "highlights", grade.highlights)
        out.endObject()
    }

    private fun encodeWheel(
        out: ProjectJson.Writer,
        name: String,
        wheel: PhotoColourWheel
    ) {
        out.key(name).beginObject()
        out.key("hueDegrees").value(wheel.hueDegrees)
        out.key("amount").value(wheel.amount)
        out.key("luminance").value(wheel.luminance)
        out.endObject()
    }

    private fun encodeFinishing(out: ProjectJson.Writer, f: PhotoFinishing) {
        out.beginObject()
        out.key("curves").beginObject()
        encodeCurve(out, "master", f.curves.master)
        encodeCurve(out, "red", f.curves.red)
        encodeCurve(out, "green", f.curves.green)
        encodeCurve(out, "blue", f.curves.blue)
        out.endObject()
        out.key("whiteBalance").beginObject()
        out.key("temperatureKelvin").value(f.whiteBalance.temperatureKelvin)
        out.key("tint").value(f.whiteBalance.tint)
        out.endObject()
        out.key("grade"); encodeGrade(out, f.grade)
        out.key("vignette").beginObject()
        out.key("amount").value(f.vignette.amount)
        out.key("midpoint").value(f.vignette.midpoint)
        out.key("feather").value(f.vignette.feather)
        out.key("roundness").value(f.vignette.roundness)
        out.key("highlights").value(f.vignette.highlights)
        out.endObject()
        out.key("grain").beginObject()
        out.key("amount").value(f.grain.amount)
        out.key("size").value(f.grain.size)
        out.key("roughness").value(f.grain.roughness)
        out.key("seed").value(f.grain.seed)
        out.endObject()
        out.key("look").beginObject()
        out.key("id").value(f.look.id.name)
        out.key("amount").value(f.look.amount)
        out.endObject()
        out.endObject()
    }

    private fun encodeSpatial(out: ProjectJson.Writer, s: PhotoSpatialEffects) {
        out.beginObject()
        out.key("details").beginObject()
        out.key("texture").value(s.details.texture)
        out.key("clarity").value(s.details.clarity)
        out.key("sharpening").value(s.details.sharpening)
        out.key("noiseReduction").value(s.details.noiseReduction)
        out.endObject()
        out.key("lensBlur").beginObject()
        out.key("mode").value(s.lensBlur.mode.name)
        out.key("strength").value(s.lensBlur.strength)
        out.key("transition").value(s.lensBlur.transition)
        out.key("highlightBokeh").value(s.lensBlur.highlightBokeh)
        out.key("centerX").value(s.lensBlur.centerX)
        out.key("centerY").value(s.lensBlur.centerY)
        out.key("angleDegrees").value(s.lensBlur.angleDegrees)
        out.endObject()
        out.endObject()
    }

    private fun encodeCurve(
        out: ProjectJson.Writer,
        name: String,
        curve: PhotoToneCurve
    ) {
        out.key(name).beginArray()
        for (point in curve.points) {
            out.beginObject()
            out.key("x").value(point.x)
            out.key("y").value(point.y)
            out.endObject()
        }
        out.endArray()
    }

    private fun encodeMask(out: ProjectJson.Writer, mask: PhotoLayerMask) {
        out.beginObject()
        out.key("baseAlpha").value(mask.baseAlpha)
        out.key("inverted").value(mask.inverted)
        out.key("strokes").beginArray()
        for (stroke in mask.strokes) encodeStroke(out, stroke)
        out.endArray()
        out.endObject()
    }

    private fun encodeStroke(out: ProjectJson.Writer, stroke: PhotoMaskStroke) {
        out.beginObject()
        out.key("points").beginArray()
        for (point in stroke.points) {
            out.beginObject()
            out.key("x").value(point.x)
            out.key("y").value(point.y)
            out.endObject()
        }
        out.endArray()
        out.key("diameter").value(stroke.diameter)
        out.key("hardness").value(stroke.hardness)
        out.key("flow").value(stroke.flow)
        out.key("mode").value(stroke.mode.name)
        out.endObject()
    }

    // ----- decoding -----------------------------------------------------

    private fun decodeSnapshot(root: ProjectJson.Object): PhotoProjectSnapshot {
        val edits = PhotoEdits(
            color = root.reqObj("adjustments").let(::decodeAdjustments),
            geometry = root.reqObj("geometry").let(::decodeGeometry),
            layers = root.reqArray("layers").values.map { any ->
                decodeLayer(any as? ProjectJson.Object ?: malformed("layer"))
            }.toPersistentList(),
            // Absent (v1/v2) defaults to a neutral spatial stage.
            spatial = root.optObj("spatial")?.let(::decodeSpatial)
                ?: PhotoSpatialEffects(),
            // Absent (v1) defaults to a neutral finishing.
            finishing = root.optObj("finishing")?.let(::decodeFinishing)
                ?: PhotoFinishing(),
            // Absent (v1-v6) defaults to a neutral retouch stage.
            retouch = root.optObj("retouch")?.let(::decodeRetouch)
                ?: PhotoRetouch(),
            develop = root.optObj("develop")?.let(::decodeDevelop)
                ?: PhotoRawDevelop()
        )
        return PhotoProjectSnapshot(edits)
    }

    private fun encodeDevelop(out: ProjectJson.Writer, d: PhotoRawDevelop) {
        out.beginObject()
        out.key("whiteBalance").value(d.whiteBalance.name)
        out.key("temperatureKelvin").value(d.temperatureKelvin)
        out.key("tint").value(d.tint)
        out.key("exposure").value(d.exposure)
        out.key("brightness").value(d.brightness)
        out.key("contrast").value(d.contrast)
        out.key("shadows").value(d.shadows)
        out.key("highlights").value(d.highlights)
        out.key("sharpen").value(d.sharpen)
        out.key("saturation").value(d.saturation)
        out.key("vibrance").value(d.vibrance)
        out.key("noiseReduction").value(d.noiseReduction.name)
        out.key("highlightRecovery").value(d.highlightRecovery.name)
        out.key("colourSpace").value(d.colourSpace.name)
        out.key("demosaic").value(d.demosaic.name)
        out.key("halfSize").value(d.halfSize)
        out.endObject()
    }

    private fun decodeDevelop(obj: ProjectJson.Object): PhotoRawDevelop {
        val neutral = PhotoRawDevelop()
        return PhotoRawDevelop(
            whiteBalance = obj.optString("whiteBalance")?.let(PhotoRawWhiteBalance::valueOf)
                ?: neutral.whiteBalance,
            temperatureKelvin = obj.optFloat("temperatureKelvin") ?: neutral.temperatureKelvin,
            tint = obj.optFloat("tint") ?: 0f,
            exposure = obj.optFloat("exposure") ?: 0f,
            brightness = obj.optFloat("brightness") ?: 0f,
            contrast = obj.optFloat("contrast") ?: 0f,
            shadows = obj.optFloat("shadows") ?: 0f,
            highlights = obj.optFloat("highlights") ?: 0f,
            sharpen = obj.optFloat("sharpen") ?: 0f,
            saturation = obj.optFloat("saturation") ?: 0f,
            vibrance = obj.optFloat("vibrance") ?: 0f,
            noiseReduction = obj.optString("noiseReduction")?.let(PhotoRawNoiseReduction::valueOf)
                ?: neutral.noiseReduction,
            highlightRecovery = obj.optString("highlightRecovery")?.let(PhotoRawHighlights::valueOf)
                ?: neutral.highlightRecovery,
            colourSpace = obj.optString("colourSpace")?.let(PhotoRawColourSpace::valueOf)
                ?: neutral.colourSpace,
            demosaic = obj.optString("demosaic")?.let(PhotoRawDemosaic::valueOf)
                ?: neutral.demosaic,
            halfSize = obj.optBoolean("halfSize") ?: false
        )
    }

    private fun encodeRetouch(out: ProjectJson.Writer, r: PhotoRetouch) {
        out.beginObject()
        out.key("tone").beginObject()
        out.key("skin").value(r.tone.skin)
        out.key("blue").value(r.tone.blue)
        out.key("hue").value(r.tone.hue)
        out.key("blackWhite").value(r.tone.blackWhite)
        out.endObject()
        out.key("effects").beginObject()
        out.key("posterize").value(r.effects.posterize)
        out.key("edges").value(r.effects.edges)
        out.key("borderWidth").value(r.effects.borderWidth)
        out.key("borderColor").value(r.effects.borderColor)
        out.endObject()
        out.key("strokes").beginArray()
        for (stroke in r.markup.strokes) {
            out.beginObject()
            out.key("type").value(stroke.type.name)
            out.key("size").value(stroke.size)
            out.key("strength").value(stroke.strength)
            out.key("color").value(stroke.color)
            out.key("points").beginArray()
            for (point in stroke.points) {
                out.value(point.x)
                out.value(point.y)
            }
            out.endArray()
            out.endObject()
        }
        out.endArray()
        out.key("texts").beginArray()
        for (text in r.markup.texts) {
            out.beginObject()
            out.key("id").value(text.id)
            out.key("text").value(text.text)
            out.key("x").value(text.x)
            out.key("y").value(text.y)
            out.key("color").value(text.color)
            out.key("size").value(text.size)
            out.key("rotation").value(text.rotation)
            out.endObject()
        }
        out.endArray()
        r.matte?.let { matte ->
            out.key("matte").beginObject()
            out.key("width").value(matte.width)
            out.key("height").value(matte.height)
            out.key("alpha").value(encodeMatteAlpha(matte))
            out.endObject()
        }
        out.endObject()
    }

    private fun decodeRetouch(obj: ProjectJson.Object): PhotoRetouch {
        val tone = obj.optObj("tone")?.let { t ->
            PhotoColourTone(
                skin = t.optFloat("skin") ?: 0f,
                blue = t.optFloat("blue") ?: 0f,
                hue = t.optFloat("hue") ?: 0f,
                blackWhite = t.optFloat("blackWhite") ?: 0f
            )
        } ?: PhotoColourTone()
        val effects = obj.optObj("effects")?.let { e ->
            PhotoEffects(
                posterize = e.optFloat("posterize") ?: 0f,
                edges = e.optFloat("edges") ?: 0f,
                borderWidth = e.optFloat("borderWidth") ?: 0f,
                borderColor = e.optInt("borderColor") ?: PhotoEffects.DEFAULT_BORDER_COLOR
            )
        } ?: PhotoEffects()
        val strokes = obj.optArray("strokes")?.values.orEmpty().map { any ->
            val s = any as? ProjectJson.Object ?: malformed("markup stroke")
            val coordinates = s.reqArray("points").values.map {
                (it as? ProjectJson.Num)?.float("markup point")
                    ?: malformed("markup point")
            }
            PhotoMarkupStroke(
                type = PhotoMarkupType.valueOf(s.reqString("type")),
                points = coordinates.chunked(2).map { (x, y) ->
                    NormalizedPoint(x, y)
                }.toPersistentList(),
                size = s.optFloat("size") ?: 30f,
                strength = s.optFloat("strength") ?: 70f,
                color = s.optInt("color") ?: PhotoMarkupStroke.DEFAULT_COLOR
            )
        }.toPersistentList()
        val texts = obj.optArray("texts")?.values.orEmpty().map { any ->
            val t = any as? ProjectJson.Object ?: malformed("markup text")
            PhotoMarkupText(
                id = t.reqLong("id"),
                text = t.reqString("text"),
                x = t.optFloat("x") ?: 0.5f,
                y = t.optFloat("y") ?: 0.5f,
                color = t.optInt("color") ?: PhotoEffects.DEFAULT_BORDER_COLOR,
                size = t.optFloat("size") ?: PhotoMarkupText.DEFAULT_SIZE,
                rotation = t.optFloat("rotation") ?: 0f
            )
        }.toPersistentList()
        val matte = obj.optObj("matte")?.let { m ->
            decodeMatteAlpha(m.reqInt("width"), m.reqInt("height"), m.reqString("alpha"))
        }
        return PhotoRetouch(tone, effects, PhotoMarkup(strokes, texts), matte)
    }

    /** Run-length pairs of (count, alpha) in Base64 — mattes are mostly flat. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeMatteAlpha(matte: PhotoCutoutMatte): String {
        val alpha = matte.copyAlpha()
        val runs = ByteArray(alpha.size * 2)
        var size = 0
        var index = 0
        while (index < alpha.size) {
            var run = 1
            while (run < 255 && index + run < alpha.size && alpha[index + run] == alpha[index]) {
                run++
            }
            runs[size++] = run.toByte()
            runs[size++] = alpha[index]
            index += run
        }
        return Base64.encode(runs, 0, size)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeMatteAlpha(width: Int, height: Int, encoded: String): PhotoCutoutMatte {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_MATTE_PIXELS) {
            "matte size out of range: ${width}x$height"
        }
        val runs = Base64.decode(encoded)
        require(runs.size % 2 == 0) { "malformed matte runs" }
        val alpha = ByteArray(width * height)
        var size = 0
        for (pair in runs.indices step 2) {
            val run = runs[pair].toInt() and 0xFF
            require(run > 0 && size + run <= alpha.size) { "malformed matte runs" }
            alpha.fill(runs[pair + 1], size, size + run)
            size += run
        }
        require(size == alpha.size) { "matte runs cover $size of ${alpha.size} pixels" }
        return PhotoCutoutMatte(width, height, alpha)
    }

    private fun decodeAdjustments(obj: ProjectJson.Object) = ColorAdjustments(
        exposureStops = obj.optFloat("exposureStops") ?: 0f,
        contrast = obj.optFloat("contrast") ?: 1f,
        saturation = obj.optFloat("saturation") ?: 1f,
        highlights = obj.optFloat("highlights") ?: 0f,
        shadows = obj.optFloat("shadows") ?: 0f,
        whites = obj.optFloat("whites") ?: 0f,
        blacks = obj.optFloat("blacks") ?: 0f
    )

    private fun decodeGrade(obj: ProjectJson.Object): PhotoColourGrade {
        val hsl = obj.optObj("hsl")
        return PhotoColourGrade(
            vibrance = obj.optFloat("vibrance") ?: 0f,
            protectSkin = obj.optBoolean("protectSkin") ?: false,
            hsl = PhotoHslBand.entries.map { band ->
                hsl?.optObj(band.name)?.let { a ->
                    PhotoHslAdjustment(
                        hue = a.optFloat("hue") ?: 0f,
                        saturation = a.optFloat("saturation") ?: 0f,
                        luminance = a.optFloat("luminance") ?: 0f
                    )
                } ?: PhotoHslAdjustment()
            },
            shadows = decodeWheel(obj.optObj("shadows")),
            midtones = decodeWheel(obj.optObj("midtones")),
            highlights = decodeWheel(obj.optObj("highlights"))
        )
    }

    private fun decodeWheel(obj: ProjectJson.Object?) = PhotoColourWheel(
        hueDegrees = obj?.optFloat("hueDegrees") ?: 0f,
        amount = obj?.optFloat("amount") ?: 0f,
        luminance = obj?.optFloat("luminance") ?: 0f
    )

    private fun decodeFinishing(obj: ProjectJson.Object) = PhotoFinishing(
        curves = obj.optObj("curves")?.let(::decodeCurves) ?: PhotoCurves(),
        // Absent (v1-v4) defaults to a neutral grade.
        grade = obj.optObj("grade")?.let(::decodeGrade) ?: PhotoColourGrade(),
        whiteBalance = obj.optObj("whiteBalance")?.let { wb ->
            PhotoWhiteBalance(
                temperatureKelvin = wb.optFloat("temperatureKelvin") ?: 6500f,
                tint = wb.optFloat("tint") ?: 0f
            )
        } ?: PhotoWhiteBalance(),
        vignette = obj.optObj("vignette")?.let { v ->
            PhotoVignette(
                amount = v.optFloat("amount") ?: 0f,
                midpoint = v.optFloat("midpoint") ?: 0.5f,
                feather = v.optFloat("feather") ?: 0.5f,
                roundness = v.optFloat("roundness") ?: 0f,
                highlights = v.optFloat("highlights") ?: 0f
            )
        } ?: PhotoVignette(),
        grain = obj.optObj("grain")?.let { g ->
            PhotoGrain(
                amount = g.optFloat("amount") ?: 0f,
                size = g.optFloat("size") ?: 0.24f,
                roughness = g.optFloat("roughness") ?: 0.35f,
                seed = g.optInt("seed") ?: 1337
            )
        } ?: PhotoGrain(),
        // Absent (v1-v3) defaults to no look selected.
        look = obj.optObj("look")?.let { l ->
            PhotoLook(
                id = l.optString("id")?.let { name ->
                    PhotoLookId.entries.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException(
                            "unknown photo look: $name"
                        )
                } ?: PhotoLookId.None,
                amount = l.optFloat("amount") ?: 1f
            )
        } ?: PhotoLook()
    )

    private fun decodeSpatial(obj: ProjectJson.Object) = PhotoSpatialEffects(
        details = obj.optObj("details")?.let { d ->
            PhotoDetails(
                texture = d.optFloat("texture") ?: 0f,
                clarity = d.optFloat("clarity") ?: 0f,
                sharpening = d.optFloat("sharpening") ?: 0f,
                noiseReduction = d.optFloat("noiseReduction") ?: 0f
            )
        } ?: PhotoDetails(),
        lensBlur = obj.optObj("lensBlur")?.let { b ->
            PhotoLensBlur(
                mode = b.optString("mode")?.let { name ->
                    LensBlurMode.entries.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException(
                            "unknown lens blur mode: $name"
                        )
                } ?: LensBlurMode.Radial,
                strength = b.optFloat("strength") ?: 0f,
                transition = b.optFloat("transition") ?: 0.6f,
                highlightBokeh = b.optFloat("highlightBokeh") ?: 0.12f,
                centerX = b.optFloat("centerX") ?: 0.5f,
                centerY = b.optFloat("centerY") ?: 0.5f,
                angleDegrees = b.optFloat("angleDegrees") ?: 0f
            )
        } ?: PhotoLensBlur()
    )

    private fun decodeCurves(obj: ProjectJson.Object) = PhotoCurves(
        master = decodeCurve(obj, "master"),
        red = decodeCurve(obj, "red"),
        green = decodeCurve(obj, "green"),
        blue = decodeCurve(obj, "blue")
    )

    private fun decodeCurve(
        obj: ProjectJson.Object,
        name: String
    ): PhotoToneCurve {
        val array = obj.optArray(name) ?: return PhotoToneCurve()
        if (array.values.firstOrNull() is ProjectJson.Object) {
            return PhotoToneCurve(
                points = array.values.map { any ->
                    val point = any as? ProjectJson.Object ?: malformed("curve")
                    PhotoCurvePoint(
                        x = point.reqFloat("x"),
                        y = point.reqFloat("y")
                    )
                }.toPersistentList()
            )
        }
        return PhotoToneCurve.ofQuarterOrdinates(
            array.values.map { any ->
                val num = any as? ProjectJson.Num ?: malformed("curve")
                num.double(name).toFloat()
            }
        )
    }

    private fun decodeGeometry(obj: ProjectJson.Object) = PhotoGeometry(
        crop = obj.reqObj("crop").let { crop ->
            NormalizedCrop(
                left = crop.optDouble("left") ?: 0.0,
                top = crop.optDouble("top") ?: 0.0,
                right = crop.optDouble("right") ?: 1.0,
                bottom = crop.optDouble("bottom") ?: 1.0
            )
        },
        quarterTurns = obj.optInt("quarterTurns") ?: 0,
        flipHorizontal = obj.optBoolean("flipHorizontal") ?: false,
        flipVertical = obj.optBoolean("flipVertical") ?: false,
        warp = obj.optObj("warp")?.let(::decodeWarp) ?: PhotoWarp()
    )

    private fun decodeWarp(obj: ProjectJson.Object) = PhotoWarp(
        rotationDegrees = obj.optFloat("rotationDegrees") ?: 0f,
        verticalPerspective = obj.optFloat("verticalPerspective") ?: 0f,
        horizontalPerspective = obj.optFloat("horizontalPerspective") ?: 0f,
        scale = obj.optFloat("scale") ?: 1f
    )

    private fun decodeLayer(obj: ProjectJson.Object): PhotoImageLayer {
        val sourceObj = obj.reqObj("source")
        return PhotoImageLayer(
            id = obj.reqString("id"),
            name = obj.reqString("name"),
            source = MediaAsset.Image(
                id = sourceObj.reqString("id"),
                reference = sourceObj.reqString("reference"),
                size = PixelSize(
                    sourceObj.reqInt("width"),
                    sourceObj.reqInt("height")
                )
            ),
            transform = obj.optObj("transform")?.let(::decodeTransform)
                ?: PhotoLayerTransform(),
            blendMode = obj.optString("blendMode")?.let { name ->
                PhotoBlendMode.entries.firstOrNull { it.name == name }
                    ?: throw IllegalArgumentException("unknown blend mode: $name")
            } ?: PhotoBlendMode.Normal,
            opacity = obj.optFloat("opacity") ?: 1f,
            visible = obj.optBoolean("visible") ?: true,
            locked = obj.optBoolean("locked") ?: false,
            clipToBelow = obj.optBoolean("clipToBelow") ?: false,
            mask = obj.optObj("mask")?.let(::decodeMask) ?: PhotoLayerMask()
        )
    }

    private fun decodeTransform(obj: ProjectJson.Object) = PhotoLayerTransform(
        translationX = obj.optFloat("translationX") ?: 0f,
        translationY = obj.optFloat("translationY") ?: 0f,
        scale = obj.optFloat("scale") ?: 1f,
        rotationDegrees = obj.optFloat("rotationDegrees") ?: 0f,
        flipHorizontal = obj.optBoolean("flipHorizontal") ?: false,
        flipVertical = obj.optBoolean("flipVertical") ?: false
    )

    private fun decodeMask(obj: ProjectJson.Object) = PhotoLayerMask(
        baseAlpha = obj.optFloat("baseAlpha") ?: 1f,
        inverted = obj.optBoolean("inverted") ?: false,
        strokes = (obj.optArray("strokes")?.values ?: emptyList()).map { any ->
            decodeStroke(any as? ProjectJson.Object ?: malformed("stroke"))
        }.toPersistentList()
    )

    private fun decodeStroke(obj: ProjectJson.Object) = PhotoMaskStroke(
        points = obj.reqArray("points").values.map { any ->
            val point = any as? ProjectJson.Object ?: malformed("point")
            NormalizedPoint(
                x = point.reqFloat("x"),
                y = point.reqFloat("y")
            )
        }.toPersistentList(),
        diameter = obj.reqFloat("diameter"),
        hardness = obj.optFloat("hardness") ?: 1f,
        flow = obj.optFloat("flow") ?: 1f,
        mode = obj.optString("mode")?.let { name ->
            PhotoMaskMode.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("unknown mask mode: $name")
        } ?: PhotoMaskMode.Paint
    )

    private fun ProjectJson.Object.reqFloat(name: String): Float =
        optFloat(name) ?: throw IllegalArgumentException(
            "missing required key: $name"
        )

    private fun malformed(what: String): Nothing =
        throw IllegalArgumentException("malformed $what in project")
}
