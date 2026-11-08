package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoWarpMapping
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/**
 * Skia runtime-shader backend (`RuntimeEffect` over the generated SkSL).
 * The real raster device — GPU or CPU — is Skiko's choice; this type only
 * claims "runtime shader", never GPU hardware.
 *
 * With [layers] non-empty the compiled program is the composite variant:
 * every layer image and mask is bound as a nearest-clamp child shader,
 * and [draw] uploads the visible stack's transforms/blends as uniforms —
 * recipe changes never recompile. A frame whose visible
 * `(layer id, source id)` stack differs from the recorded [signature] is
 * declined without reporting, so the owner can rebuild or fall back to
 * the CPU pipeline instead of drawing a stale composite.
 */
internal actual fun createCompositePhotoShaderRenderer(
    base: ImageBitmap,
    layers: List<PhotoShaderLayerSource>,
    referenceSourceSize: PixelSize,
    onFailure: (Throwable) -> Unit
): PhotoShaderRenderer? {
    return try {
        SkiaPhotoShaderRenderer(base, layers, referenceSourceSize, onFailure)
    } catch (error: RuntimeException) {
        // makeForShader throws when the generated SkSL fails to compile.
        onFailure(error)
        null
    }
}

private class SkiaPhotoShaderRenderer(
    private val source: ImageBitmap,
    layers: List<PhotoShaderLayerSource>,
    private val referenceSourceSize: PixelSize,
    private val onFailure: (Throwable) -> Unit
) : PhotoShaderRenderer {

    private val signature = layers.map { it.layerId to it.sourceId }
    private val layerBitmapSizes =
        layers.map { PixelSize(it.bitmap.width, it.bitmap.height) }
    private val effect: RuntimeEffect
    private val builder: RuntimeShaderBuilder
    private val childShaders = ArrayList<Shader>()

    private var failed = false
    private var closed = false

    init {
        // Partial allocation must not leak the already-created refs — close
        // whatever exists before the constructor escapes. The caller-owned
        // bitmaps are never touched.
        var createdEffect: RuntimeEffect? = null
        var createdBuilder: RuntimeShaderBuilder? = null
        try {
            createdEffect =
                RuntimeEffect.makeForShader(photoShaderSource(layers.size))
            createdBuilder = RuntimeShaderBuilder(createdEffect)
            // Ordinary bitmap child shaders — eval() delivers premultiplied
            // sRGB, matching the shared contract. Nearest sampling keeps
            // texel-centre reads exact like the CPU mapping; the mask
            // texture is bilinear'd manually in the shader.
            createdBuilder.child("sourceImage", bitmapShader(source))
            layers.forEachIndexed { index, layer ->
                createdBuilder.child("layerImage$index", bitmapShader(layer.bitmap))
                createdBuilder.child(
                    "layerMask$index", bitmapShader(layer.maskBitmap)
                )
            }
            effect = createdEffect
            builder = createdBuilder
        } catch (error: RuntimeException) {
            for (shader in childShaders) shader.close()
            childShaders.clear()
            createdBuilder?.close()
            createdEffect?.close()
            throw error
        }
    }

    private fun bitmapShader(bitmap: ImageBitmap): Shader {
        val shader = bitmap.asSkiaBitmap().makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.DEFAULT,
            null
        )
        childShaders += shader
        return shader
    }

    override fun draw(scope: DrawScope, edits: PhotoEdits): Boolean {
        if (closed || failed) return false
        val visible = edits.layers.filter { it.visible }
        // The compiled program is fixed for the constructor's stack — a
        // different visible signature means this renderer is stale; say so
        // without reporting a failure (rebuild/fallback is the remedy).
        if (visible.map { it.id to it.source.id } != signature) return false
        return try {
            applyUniforms(scope, edits, visible)
            val shader = builder.makeShader()
            val paint = Paint()
            try {
                paint.shader = shader
                scope.drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(
                        Rect.makeWH(scope.size.width, scope.size.height), paint
                    )
                }
            } finally {
                paint.close()
                shader.close()
            }
            true
        } catch (error: RuntimeException) {
            failed = true
            onFailure(error)
            false
        }
    }

    private fun applyUniforms(
        scope: DrawScope,
        edits: PhotoEdits,
        visible: List<PhotoImageLayer>
    ) {
        val mapping = PhotoWarpMapping(
            PixelSize(source.width, source.height), orientation = 1, edits.geometry
        )
        val crop = mapping.cropRect
        val color = edits.color
        val warp = edits.geometry.warp
        val radians = warp.rotationDegrees.toDouble() * PI / 180.0
        builder.uniform(
            "paintSize", scope.size.width, scope.size.height
        )
        builder.uniform(
            "outputSize",
            mapping.outputSize.width.toFloat(),
            mapping.outputSize.height.toFloat()
        )
        builder.uniform(
            "cropRect",
            crop.left.toFloat(), crop.top.toFloat(),
            crop.width.toFloat(), crop.height.toFloat()
        )
        builder.uniform("turns", edits.geometry.quarterTurns)
        builder.uniform(
            "flipH", if (edits.geometry.flipHorizontal) 1 else 0
        )
        builder.uniform(
            "flipV", if (edits.geometry.flipVertical) 1 else 0
        )
        builder.uniform(
            "sourceSize", source.width.toFloat(), source.height.toFloat()
        )
        builder.uniform("warpActive", if (warp.isNeutral) 0 else 1)
        builder.uniform("warpCos", cos(radians).toFloat())
        builder.uniform("warpSin", sin(radians).toFloat())
        builder.uniform("warpScale", mapping.effectiveScale.toFloat())
        builder.uniform(
            "perspectiveK",
            warp.horizontalPerspective * 0.0035f,
            warp.verticalPerspective * 0.0035f
        )
        builder.uniform(
            "planeSize",
            mapping.planeSize.width.toFloat(), mapping.planeSize.height.toFloat()
        )
        builder.uniform(
            "planeOffset",
            mapping.planeOffset.x.toFloat(), mapping.planeOffset.y.toFloat()
        )
        builder.uniform(
            "exposureGain",
            2.0.pow(color.exposureStops.toDouble()).toFloat()
        )
        builder.uniform("contrast", color.contrast)
        builder.uniform("saturation", color.saturation)
        builder.uniform("highlights", color.highlights)
        builder.uniform("shadows", color.shadows)
        builder.uniform("whites", color.whites)
        builder.uniform("blacks", color.blacks)
        val finishing = PhotoShaderFinishing(edits.finishing)
        builder.uniform(
            "wbActive", if (finishing.whiteBalanceActive) 1 else 0
        )
        builder.uniform("wbGain", finishing.whiteBalanceGains)
        builder.uniform("gradeVibrance", finishing.gradeVibrance)
        builder.uniform(
            "gradeProtectSkin", if (finishing.gradeProtectSkin) 1 else 0
        )
        builder.uniform(
            "gradeHslActive", if (finishing.gradeHslActive) 1 else 0
        )
        finishing.gradeHsl.forEachIndexed { band, values ->
            builder.uniform("gradeHsl$band", values)
        }
        builder.uniform(
            "gradeWheelsActive", if (finishing.gradeWheelsActive) 1 else 0
        )
        builder.uniform("gradeShadows", finishing.gradeShadows)
        builder.uniform("gradeMidtones", finishing.gradeMidtones)
        builder.uniform("gradeHighlights", finishing.gradeHighlights)
        builder.uniform(
            "curvesActive", if (finishing.curvesActive) 1 else 0
        )
        uploadCurve("curveM", finishing.curveMaster)
        uploadCurve("curveR", finishing.curveRed)
        uploadCurve("curveG", finishing.curveGreen)
        uploadCurve("curveB", finishing.curveBlue)
        builder.uniform("lookId", finishing.lookId)
        builder.uniform("lookAmount", finishing.lookAmount)
        builder.uniform(
            "vigActive", if (finishing.vignetteActive) 1 else 0
        )
        builder.uniform("vigAmount", finishing.vignetteAmount)
        builder.uniform("vigMidpoint", finishing.vignetteMidpoint)
        builder.uniform("vigFeather", finishing.vignetteFeather)
        builder.uniform("vigRoundness", finishing.vignetteRoundness)
        builder.uniform("vigHighlights", finishing.vignetteHighlights)
        val spatial = PhotoShaderSpatial(edits.spatial)
        builder.uniform("detActive", if (spatial.detailsActive) 1 else 0)
        builder.uniform("detTexture", spatial.detailTexture)
        builder.uniform("detClarity", spatial.detailClarity)
        builder.uniform("detSharpen", spatial.detailSharpening)
        builder.uniform("detNoise", spatial.detailNoiseReduction)
        if (visible.isEmpty()) return
        // Layer translations live in full-resolution base-output pixels —
        // the shader scales them by outputSize/referenceOutputSize.
        val referenceOutput = PhotoMapping(
            referenceSourceSize, orientation = 1, edits.geometry
        ).outputSize
        builder.uniform(
            "referenceOutputSize",
            referenceOutput.width.toFloat(),
            referenceOutput.height.toFloat()
        )
        visible.forEachIndexed { index, layer ->
            val source = layerBitmapSizes[index]
            val transform = layer.transform
            val layerRadians =
                transform.rotationDegrees.toDouble() * PI / 180.0
            builder.uniform(
                "layerSourceSize$index",
                source.width.toFloat(), source.height.toFloat()
            )
            builder.uniform(
                "layerTranslation$index",
                transform.translationX, transform.translationY
            )
            builder.uniform("layerScale$index", transform.scale)
            builder.uniform(
                "layerRotCos$index", cos(layerRadians).toFloat()
            )
            builder.uniform(
                "layerRotSin$index", sin(layerRadians).toFloat()
            )
            builder.uniform(
                "layerFlipH$index",
                if (transform.flipHorizontal) 1 else 0
            )
            builder.uniform(
                "layerFlipV$index",
                if (transform.flipVertical) 1 else 0
            )
            builder.uniform("layerOpacity$index", layer.opacity)
            builder.uniform(
                "layerBlendMode$index", layer.blendMode.ordinal
            )
            builder.uniform(
                "layerClip$index", if (layer.clipToBelow) 1 else 0
            )
        }
    }

    /** One curve's gate, end points and spline segments. */
    private fun uploadCurve(name: String, curve: PhotoShaderCurve) {
        builder.uniform("${name}Active", if (curve.active) 1 else 0)
        builder.uniform("${name}Ends", curve.ends)
        builder.uniform("${name}Starts", curve.starts)
        builder.uniform("${name}Coefficients", curve.coefficients)
    }

    override fun close() {
        // The bitmaps belong to the caller — only shader objects this
        // renderer created are released, and only once.
        if (closed) return
        closed = true
        for (shader in childShaders) shader.close()
        childShaders.clear()
        builder.close()
        effect.close()
    }
}
