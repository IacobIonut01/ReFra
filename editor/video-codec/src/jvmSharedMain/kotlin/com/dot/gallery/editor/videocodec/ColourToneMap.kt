package com.dot.gallery.editor.videocodec

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_SMPTE431
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_SMPTE432
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084

/**
 * Maps HDR or wide-gamut video into the editor's sRGB working space, so a
 * phone's HLG or HDR10 clip previews and exports with the colours a viewer
 * expects rather than a flat, washed-out picture.
 *
 * Input is non-linear R'G'B' at 16 bits per channel. Each channel goes through
 * the source transfer's inverse to display-linear light (1.0 is SDR reference
 * white, 203 nits per BT.2408), then the matrix into BT.709 primaries, a soft
 * shoulder over everything above [KNEE] that keeps the hue by scaling all three
 * channels together, and the sRGB encoding.
 *
 * Instances are immutable and shared. Use [forTags] and treat null as "already
 * in the working space".
 */
internal class ColourToneMap private constructor(
    private val transfer: Transfer,
    gamut: Gamut
) {
    internal enum class Transfer { Pq, Hlg, Gamma }
    internal enum class Gamut { Bt709, Bt2020, DisplayP3 }

    private val m = gamut.toBt709

    /** Non-linear signal, 12-bit index → linear light (scene-linear for HLG). */
    private val toLinear = FloatArray(LUT_SIZE) { index ->
        val signal = index / (LUT_SIZE - 1).toFloat()
        when (transfer) {
            Transfer.Pq -> pqToRelative(signal)
            Transfer.Hlg -> hlgToSceneLinear(signal)
            Transfer.Gamma -> signal.pow(2.4f)
        }
    }

    /** HLG only: scene luminance, 12-bit index → the display OOTF's gain. */
    private val hlgGain = if (transfer == Transfer.Hlg) {
        FloatArray(LUT_SIZE) { index ->
            val luminance = index / (LUT_SIZE - 1).toFloat()
            max(luminance, 1e-4f).pow(HLG_GAMMA - 1f) * HLG_PEAK_RELATIVE
        }
    } else {
        null
    }

    /**
     * Converts packed little-endian RGBA64 samples, read as shorts, to opaque
     * `0xAARRGGBB`. [argb] must hold `rgba64.size / 4` entries.
     */
    fun apply(rgba64: ShortArray, argb: IntArray) {
        val gain = hlgGain
        var i = 0
        for (pixel in argb.indices) {
            var r = toLinear[(rgba64[i].toInt() and 0xFFFF) ushr 4]
            var g = toLinear[(rgba64[i + 1].toInt() and 0xFFFF) ushr 4]
            var b = toLinear[(rgba64[i + 2].toInt() and 0xFFFF) ushr 4]
            i += 4
            if (gain != null) {
                val luminance = 0.2627f * r + 0.6780f * g + 0.0593f * b
                val index = (luminance * (LUT_SIZE - 1)).toInt().coerceIn(0, LUT_SIZE - 1)
                val scale = gain[index]
                r *= scale
                g *= scale
                b *= scale
            }
            var lr = m[0] * r + m[1] * g + m[2] * b
            var lg = m[3] * r + m[4] * g + m[5] * b
            var lb = m[6] * r + m[7] * g + m[8] * b
            lr = max(lr, 0f)
            lg = max(lg, 0f)
            lb = max(lb, 0f)
            val peak = max(lr, max(lg, lb))
            if (peak > KNEE) {
                val scale = shoulder(peak) / peak
                lr *= scale
                lg *= scale
                lb *= scale
            }
            argb[pixel] = OPAQUE or
                (encode(lr) shl 16) or
                (encode(lg) shl 8) or
                encode(lb)
        }
    }

    internal companion object {
        private const val LUT_SIZE = 4096
        private const val OPAQUE = 0xFF shl 24
        private const val KNEE = 0.8f
        private const val REFERENCE_WHITE_NITS = 203f
        private const val HLG_GAMMA = 1.2f
        private const val HLG_PEAK_RELATIVE = 1000f / REFERENCE_WHITE_NITS

        private val SRGB_ENCODE = IntArray(LUT_SIZE) { index ->
            val linear = index / (LUT_SIZE - 1).toFloat()
            val encoded = if (linear <= 0.0031308f) {
                12.92f * linear
            } else {
                1.055f * linear.pow(1f / 2.4f) - 0.055f
            }
            (encoded * 255f).roundToInt().coerceIn(0, 255)
        }

        private val instances = mutableMapOf<Pair<Transfer, Gamut>, ColourToneMap>()

        /**
         * The mapping a frame with these `AVCOL_TRC_*`/`AVCOL_PRI_*` tags needs,
         * or null for SDR BT.709-class video. Untagged primaries on an HDR
         * transfer mean BT.2020, the only gamut those transfers ship with.
         */
        fun forTags(trc: Int, primaries: Int): ColourToneMap? {
            val transfer = when (trc) {
                AVCOL_TRC_SMPTE2084 -> Transfer.Pq
                AVCOL_TRC_ARIB_STD_B67 -> Transfer.Hlg
                else -> Transfer.Gamma
            }
            val gamut = when (primaries) {
                AVCOL_PRI_BT2020 -> Gamut.Bt2020
                AVCOL_PRI_SMPTE431, AVCOL_PRI_SMPTE432 -> Gamut.DisplayP3
                AVCOL_PRI_UNSPECIFIED ->
                    if (transfer == Transfer.Gamma) Gamut.Bt709 else Gamut.Bt2020
                else -> Gamut.Bt709
            }
            if (transfer == Transfer.Gamma && gamut == Gamut.Bt709) return null
            return of(transfer, gamut)
        }

        @Synchronized
        internal fun of(transfer: Transfer, gamut: Gamut): ColourToneMap =
            instances.getOrPut(transfer to gamut) { ColourToneMap(transfer, gamut) }

        private val Gamut.toBt709: FloatArray
            get() = when (this) {
                Gamut.Bt709 -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
                Gamut.Bt2020 -> floatArrayOf(
                    1.6605f, -0.5876f, -0.0728f,
                    -0.1246f, 1.1329f, -0.0083f,
                    -0.0182f, -0.1006f, 1.1187f
                )
                Gamut.DisplayP3 -> floatArrayOf(
                    1.2249f, -0.2247f, 0f,
                    -0.0420f, 1.0419f, 0f,
                    -0.0197f, -0.0786f, 1.0979f
                )
            }

        /** SMPTE ST 2084 EOTF, relative to SDR reference white. */
        private fun pqToRelative(signal: Float): Float {
            val power = signal.pow(1f / 78.84375f)
            val numerator = max(power - 0.8359375f, 0f)
            val denominator = 18.8515625f - 18.6875f * power
            return (numerator / denominator).pow(1f / 0.1593017578125f) *
                10_000f / REFERENCE_WHITE_NITS
        }

        /** The BT.2100 HLG inverse OETF. */
        private fun hlgToSceneLinear(signal: Float): Float =
            if (signal <= 0.5f) {
                signal * signal / 3f
            } else {
                (exp((signal - 0.55991073f) / 0.17883277f) + 0.28466892f) / 12f
            }

        /** Linear below [KNEE]; above it, approaches 1.0 without ever clipping. */
        private fun shoulder(value: Float): Float {
            val span = 1f - KNEE
            return KNEE + span * (1f - exp(-(value - KNEE) / span))
        }

        private fun encode(linear: Float): Int =
            SRGB_ENCODE[(min(linear, 1f) * (LUT_SIZE - 1)).toInt()]
    }
}
