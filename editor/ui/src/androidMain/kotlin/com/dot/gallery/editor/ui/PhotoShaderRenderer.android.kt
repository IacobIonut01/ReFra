package com.dot.gallery.editor.ui

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

internal actual fun createCompositePhotoShaderRenderer(
    base: ImageBitmap,
    layers: List<PhotoShaderLayerSource>,
    referenceSourceSize: PixelSize,
    onFailure: (Throwable) -> Unit
): PhotoShaderRenderer? {
    // AGSL runtime shaders exist only on API 33+; below that the caller
    // keeps the CPU preview — an ordinary capability absence, not an error.
    if (Build.VERSION.SDK_INT < 33) return null
    return AgslPhotoShaderRenderer.create(
        base, layers, referenceSourceSize, onFailure
    )
}

private class AgslPhotoShaderRenderer private constructor(
    private val source: ImageBitmap,
    layers: List<PhotoShaderLayerSource>,
    private val referenceSourceSize: PixelSize,
    private val onFailure: (Throwable) -> Unit
) : PhotoShaderRenderer {

    private val signature = layers.map { it.layerId to it.sourceId }
    private val layerBitmapSizes =
        layers.map { PixelSize(it.bitmap.width, it.bitmap.height) }

    private val runtimeShader =
        RuntimeShader(photoShaderSource(layers.size)).apply {
            // Ordinary bitmap child shaders: eval() then delivers
            // premultiplied sRGB, which is exactly what the shared source
            // expects. Nearest sampling keeps texel-centre reads exact like
            // the CPU mapping; the mask texture is bilinear'd manually.
            setInputShader("sourceImage", bitmapShader(source))
            layers.forEachIndexed { index, layer ->
                setInputShader("layerImage$index", bitmapShader(layer.bitmap))
                setInputShader(
                    "layerMask$index", bitmapShader(layer.maskBitmap)
                )
            }
        }
    private val paint = Paint()
    private var failed = false
    private var closed = false

    init {
        paint.shader = runtimeShader
    }

    private fun bitmapShader(bitmap: ImageBitmap): BitmapShader =
        BitmapShader(
            bitmap.asAndroidBitmap(),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        ).apply {
            setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
        }

    override fun draw(scope: DrawScope, edits: PhotoEdits): Boolean {
        if (closed || failed) return false
        val visible = edits.layers.filter { it.visible }
        // The compiled program is fixed for the constructor's stack — a
        // different visible signature means this renderer is stale; say so
        // without reporting a failure (rebuild/fallback is the remedy).
        if (visible.map { it.id to it.source.id } != signature) return false
        var drawn = true
        try {
            scope.drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (!native.isHardwareAccelerated) {
                    // Software canvas (tests, fallback raster) — hand the
                    // frame to the CPU pipeline instead of pretending.
                    drawn = false
                    return@drawIntoCanvas
                }
                applyUniforms(scope, edits, visible)
                native.drawRect(0f, 0f, scope.size.width, scope.size.height, paint)
            }
        } catch (error: RuntimeException) {
            // A backend failure after a healthy compile must not repeat —
            // report once and let the CPU path take over for good.
            failed = true
            drawn = false
            onFailure(error)
        }
        return drawn
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
        runtimeShader.setFloatUniform(
            "paintSize", scope.size.width, scope.size.height
        )
        runtimeShader.setFloatUniform(
            "outputSize",
            mapping.outputSize.width.toFloat(),
            mapping.outputSize.height.toFloat()
        )
        runtimeShader.setFloatUniform(
            "cropRect",
            crop.left.toFloat(), crop.top.toFloat(),
            crop.width.toFloat(), crop.height.toFloat()
        )
        runtimeShader.setIntUniform("turns", edits.geometry.quarterTurns)
        runtimeShader.setIntUniform(
            "flipH", if (edits.geometry.flipHorizontal) 1 else 0
        )
        runtimeShader.setIntUniform(
            "flipV", if (edits.geometry.flipVertical) 1 else 0
        )
        runtimeShader.setFloatUniform(
            "sourceSize", source.width.toFloat(), source.height.toFloat()
        )
        runtimeShader.setIntUniform("warpActive", if (warp.isNeutral) 0 else 1)
        runtimeShader.setFloatUniform("warpCos", cos(radians).toFloat())
        runtimeShader.setFloatUniform("warpSin", sin(radians).toFloat())
        runtimeShader.setFloatUniform("warpScale", mapping.effectiveScale.toFloat())
        runtimeShader.setFloatUniform(
            "perspectiveK",
            warp.horizontalPerspective * 0.0035f,
            warp.verticalPerspective * 0.0035f
        )
        runtimeShader.setFloatUniform(
            "planeSize",
            mapping.planeSize.width.toFloat(), mapping.planeSize.height.toFloat()
        )
        runtimeShader.setFloatUniform(
            "planeOffset",
            mapping.planeOffset.x.toFloat(), mapping.planeOffset.y.toFloat()
        )
        runtimeShader.setFloatUniform(
            "exposureGain",
            2.0.pow(color.exposureStops.toDouble()).toFloat()
        )
        runtimeShader.setFloatUniform("contrast", color.contrast)
        runtimeShader.setFloatUniform("saturation", color.saturation)
        runtimeShader.setFloatUniform("highlights", color.highlights)
        runtimeShader.setFloatUniform("shadows", color.shadows)
        runtimeShader.setFloatUniform("whites", color.whites)
        runtimeShader.setFloatUniform("blacks", color.blacks)
        val finishing = PhotoShaderFinishing(edits.finishing)
        runtimeShader.setIntUniform(
            "wbActive", if (finishing.whiteBalanceActive) 1 else 0
        )
        runtimeShader.setFloatUniform(
            "wbGain", finishing.whiteBalanceGains
        )
        runtimeShader.setFloatUniform("gradeVibrance", finishing.gradeVibrance)
        runtimeShader.setIntUniform(
            "gradeProtectSkin", if (finishing.gradeProtectSkin) 1 else 0
        )
        runtimeShader.setIntUniform(
            "gradeHslActive", if (finishing.gradeHslActive) 1 else 0
        )
        finishing.gradeHsl.forEachIndexed { band, values ->
            runtimeShader.setFloatUniform("gradeHsl$band", values)
        }
        runtimeShader.setIntUniform(
            "gradeWheelsActive", if (finishing.gradeWheelsActive) 1 else 0
        )
        runtimeShader.setFloatUniform("gradeShadows", finishing.gradeShadows)
        runtimeShader.setFloatUniform("gradeMidtones", finishing.gradeMidtones)
        runtimeShader.setFloatUniform("gradeHighlights", finishing.gradeHighlights)
        runtimeShader.setIntUniform(
            "curvesActive", if (finishing.curvesActive) 1 else 0
        )
        uploadCurve("curveM", finishing.curveMaster)
        uploadCurve("curveR", finishing.curveRed)
        uploadCurve("curveG", finishing.curveGreen)
        uploadCurve("curveB", finishing.curveBlue)
        runtimeShader.setIntUniform("lookId", finishing.lookId)
        runtimeShader.setFloatUniform("lookAmount", finishing.lookAmount)
        runtimeShader.setIntUniform(
            "vigActive", if (finishing.vignetteActive) 1 else 0
        )
        runtimeShader.setFloatUniform("vigAmount", finishing.vignetteAmount)
        runtimeShader.setFloatUniform(
            "vigMidpoint", finishing.vignetteMidpoint
        )
        runtimeShader.setFloatUniform("vigFeather", finishing.vignetteFeather)
        runtimeShader.setFloatUniform(
            "vigRoundness", finishing.vignetteRoundness
        )
        runtimeShader.setFloatUniform(
            "vigHighlights", finishing.vignetteHighlights
        )
        val spatial = PhotoShaderSpatial(edits.spatial)
        runtimeShader.setIntUniform(
            "detActive", if (spatial.detailsActive) 1 else 0
        )
        runtimeShader.setFloatUniform("detTexture", spatial.detailTexture)
        runtimeShader.setFloatUniform("detClarity", spatial.detailClarity)
        runtimeShader.setFloatUniform(
            "detSharpen", spatial.detailSharpening
        )
        runtimeShader.setFloatUniform(
            "detNoise", spatial.detailNoiseReduction
        )
        if (visible.isEmpty()) return
        // Layer translations live in full-resolution base-output pixels —
        // the shader scales them by outputSize/referenceOutputSize.
        val referenceOutput = PhotoMapping(
            referenceSourceSize, orientation = 1, edits.geometry
        ).outputSize
        runtimeShader.setFloatUniform(
            "referenceOutputSize",
            referenceOutput.width.toFloat(),
            referenceOutput.height.toFloat()
        )
        visible.forEachIndexed { index, layer ->
            val layerSource = layerBitmapSizes[index]
            val transform = layer.transform
            val layerRadians =
                transform.rotationDegrees.toDouble() * PI / 180.0
            runtimeShader.setFloatUniform(
                "layerSourceSize$index",
                layerSource.width.toFloat(), layerSource.height.toFloat()
            )
            runtimeShader.setFloatUniform(
                "layerTranslation$index",
                transform.translationX, transform.translationY
            )
            runtimeShader.setFloatUniform("layerScale$index", transform.scale)
            runtimeShader.setFloatUniform(
                "layerRotCos$index", cos(layerRadians).toFloat()
            )
            runtimeShader.setFloatUniform(
                "layerRotSin$index", sin(layerRadians).toFloat()
            )
            runtimeShader.setIntUniform(
                "layerFlipH$index",
                if (transform.flipHorizontal) 1 else 0
            )
            runtimeShader.setIntUniform(
                "layerFlipV$index",
                if (transform.flipVertical) 1 else 0
            )
            runtimeShader.setFloatUniform("layerOpacity$index", layer.opacity)
            runtimeShader.setIntUniform(
                "layerBlendMode$index", layer.blendMode.ordinal
            )
            runtimeShader.setIntUniform(
                "layerClip$index", if (layer.clipToBelow) 1 else 0
            )
        }
    }

    /** One curve's gate, end points and spline segments. */
    private fun uploadCurve(name: String, curve: PhotoShaderCurve) {
        runtimeShader.setIntUniform("${name}Active", if (curve.active) 1 else 0)
        runtimeShader.setFloatUniform("${name}Ends", curve.ends)
        runtimeShader.setFloatUniform("${name}Starts", curve.starts)
        runtimeShader.setFloatUniform("${name}Coefficients", curve.coefficients)
    }

    override fun close() {
        // RuntimeShader is GC-managed; the source bitmap belongs to the
        // caller — never recycled here.
        closed = true
    }

    companion object {
        fun create(
            source: ImageBitmap,
            layers: List<PhotoShaderLayerSource>,
            referenceSourceSize: PixelSize,
            onFailure: (Throwable) -> Unit
        ): AgslPhotoShaderRenderer? {
            try {
                return AgslPhotoShaderRenderer(
                    source, layers, referenceSourceSize, onFailure
                )
            } catch (error: IllegalArgumentException) {
                // Shader compile error — the platform claim didn't hold.
                onFailure(error)
                return null
            }
        }
    }
}
