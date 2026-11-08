package com.dot.gallery.editor.io

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.render.BackendUnavailableException
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.FrameEffect
import com.dot.gallery.editor.render.LinearRgbaFrame
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * `color.basic` v2 backed by the packaged C kernel (`refra_editor_jni`) when
 * the host can load it, by [BasicColorEffect] when it cannot. The kernel
 * implements the same ordered exposure/tone/contrast/saturation contract on
 * linear-light straight-alpha RGBA, so both backends produce the same frame.
 *
 * The library is probed lazily on the first render — synchronised, so a
 * shared effect under concurrent exports loads once. Only known load
 * failures ([IOException], [UnsatisfiedLinkError], [SecurityException],
 * [BackendUnavailableException]) fall back; each is reported through
 * [onUnavailable] exactly once. A non-zero kernel status is a contract
 * violation, not a load failure: it propagates as [IllegalStateException]
 * rather than silently hiding a C semantics bug.
 */
class NativeColorEffect(
    private val loader: EditorNativeLoader,
    private val onUnavailable: (Throwable) -> Unit
) : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.ColorBasic

    /** Resolved after the first render probes the library. */
    var backend: NativeColorBackend = NativeColorBackend.Uninitialized
        private set

    private val fallback = BasicColorEffect()

    private val nativeReady: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        probe()
    }

    private fun probe(): Boolean {
        val failure = try {
            loader.load()
            val abi = NativeColorBridge.abiVersion()
            if (abi == EXPECTED_ABI) {
                null
            } else {
                BackendUnavailableException(
                    "refra_editor_jni reports ABI $abi, expected $EXPECTED_ABI"
                )
            }
        } catch (error: IOException) {
            error
        } catch (error: UnsatisfiedLinkError) {
            error
        } catch (error: SecurityException) {
            error
        } catch (error: BackendUnavailableException) {
            error
        }
        return if (failure == null) {
            backend = NativeColorBackend.Native
            true
        } else {
            backend = NativeColorBackend.KotlinFallback
            onUnavailable(failure)
            false
        }
    }

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "color.basic takes exactly 1 input, got ${inputs.size}"
            )
        }
        val color = parameters as? EffectParameters.Color
            ?: throw UnsupportedOperationException(
                "color.basic requires EffectParameters.Color, got $parameters"
            )
        if (!nativeReady) {
            return fallback.render(inputs, parameters)
        }
        val frame = inputs.single()
        coroutineContext.ensureActive()
        val source = frame.copyPixels()
        val output = FloatArray(source.size)
        val adjustments = color.adjustments
        val status = NativeColorBridge.applyColor(
            source,
            output,
            frame.size.width,
            frame.size.height,
            adjustments.exposureStops,
            adjustments.contrast,
            adjustments.saturation,
            adjustments.highlights,
            adjustments.shadows,
            adjustments.whites,
            adjustments.blacks
        )
        coroutineContext.ensureActive()
        if (status != 0) {
            throw IllegalStateException(
                "native colour kernel rejected the frame (status $status)"
            )
        }
        return LinearRgbaFrame(frame.size, output)
    }

    private companion object {
        const val EXPECTED_ABI = 2
    }
}
