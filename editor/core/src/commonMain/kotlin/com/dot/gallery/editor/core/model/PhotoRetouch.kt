package com.dot.gallery.editor.core.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * The retouch stage of a photo recipe, run after finishing in a fixed order:
 * colour tone, posterize, edges, markup, background removal, border. A neutral
 * stage is bit-preserving and costs no render-graph node.
 */
data class PhotoRetouch(
    val tone: PhotoColourTone = PhotoColourTone(),
    val effects: PhotoEffects = PhotoEffects(),
    val markup: PhotoMarkup = PhotoMarkup(),
    val matte: PhotoCutoutMatte? = null
) {
    val isNeutral: Boolean
        get() = tone.isNeutral && effects.isNeutral && markup.isEmpty &&
            matte == null

    /** A recipe with a matte carries transparency and must export as PNG. */
    val hasTransparency: Boolean get() = matte != null
}

/**
 * The colour tools the Colour board does not cover, in model units (the UI
 * speaks -100..100, hue in degrees): [skin] shifts the red-green balance,
 * [blue] the blue channel, [hue] rotates by up to half a turn and
 * [blackWhite] desaturates by its magnitude.
 */
data class PhotoColourTone(
    val skin: Float = 0f,
    val blue: Float = 0f,
    val hue: Float = 0f,
    val blackWhite: Float = 0f
) {
    init {
        require(skin.isFinite() && skin in -1f..1f) { "skin tone outside -1..1: $skin" }
        require(blue.isFinite() && blue in -1f..1f) { "blue tone outside -1..1: $blue" }
        require(hue.isFinite() && hue in -1f..1f) { "hue outside -1..1: $hue" }
        require(blackWhite.isFinite() && blackWhite in -1f..1f) {
            "black and white outside -1..1: $blackWhite"
        }
    }

    val isNeutral: Boolean
        get() = skin == 0f && blue == 0f && hue == 0f && blackWhite == 0f
}

/**
 * Stylized finishing in model units: [posterize] reduces tonal levels,
 * [edges] blends toward a Sobel edge map, and [borderWidth] paints an inset
 * [borderColor] ring up to [MAX_BORDER_FRACTION] of the shorter output side.
 */
data class PhotoEffects(
    val posterize: Float = 0f,
    val edges: Float = 0f,
    val borderWidth: Float = 0f,
    val borderColor: Int = DEFAULT_BORDER_COLOR
) {
    init {
        require(posterize.isFinite() && posterize in 0f..1f) {
            "posterize outside 0..1: $posterize"
        }
        require(edges.isFinite() && edges in 0f..1f) { "edges outside 0..1: $edges" }
        require(borderWidth.isFinite() && borderWidth in 0f..1f) {
            "border width outside 0..1: $borderWidth"
        }
    }

    val isNeutral: Boolean
        get() = posterize == 0f && edges == 0f && borderWidth == 0f

    companion object {
        const val DEFAULT_BORDER_COLOR: Int = -1 // Opaque white.
        const val MAX_BORDER_FRACTION = 0.12f
    }
}

enum class PhotoMarkupType { Pen, Highlighter, Blur, Mosaic }

/**
 * One brush stroke in normalized output coordinates. [size] and [strength]
 * are the tool's 1..100 / 0..100 settings; pixel reach always derives from
 * the output's long edge, so previews and full-size exports agree.
 */
data class PhotoMarkupStroke(
    val type: PhotoMarkupType,
    val points: PersistentList<NormalizedPoint>,
    val size: Float,
    val strength: Float = 70f,
    val color: Int = DEFAULT_COLOR
) {
    init {
        require(points.isNotEmpty()) { "a markup stroke needs at least one point" }
        require(size.isFinite() && size in 1f..100f) { "markup size outside 1..100: $size" }
        require(strength.isFinite() && strength in 0f..100f) {
            "markup strength outside 0..100: $strength"
        }
    }

    companion object {
        const val DEFAULT_COLOR: Int = -0x10000 // Opaque red.
    }
}

/**
 * One text label centred at normalized output coordinates and rotated about
 * that centre. Newlines separate left-aligned lines. See [PhotoMarkupTextLayout]
 * for the bounds every edit keeps.
 */
data class PhotoMarkupText(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Int,
    /** Font size as a fraction of the output's height. */
    val size: Float = DEFAULT_SIZE,
    /** Clockwise degrees. */
    val rotation: Float = 0f
) {
    init {
        require(text.isNotBlank()) { "markup text must not be blank" }
        require(x.isFinite() && x in 0f..1f) { "markup text x outside 0..1: $x" }
        require(y.isFinite() && y in 0f..1f) { "markup text y outside 0..1: $y" }
        require(size.isFinite() && size in PhotoMarkupTextLayout.MIN_SIZE..PhotoMarkupTextLayout.MAX_SIZE) {
            "markup text size: $size"
        }
        require(rotation.isFinite()) { "markup text rotation: $rotation" }
    }

    companion object {
        const val DEFAULT_SIZE = 0.07f
    }
}

/**
 * Markup anchored to the output frame, like layers and the Lens blur centre:
 * strokes paint in order, then text. Blur and Mosaic read the frame as it
 * stood before markup, so a later Blur fully hides earlier ink beneath it.
 */
data class PhotoMarkup(
    val strokes: PersistentList<PhotoMarkupStroke> = persistentListOf(),
    val texts: PersistentList<PhotoMarkupText> = persistentListOf()
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && texts.isEmpty()
}

/**
 * The pixel reach of one markup stroke at a given output size. Reach caps
 * keep a full-size export's band memory bounded.
 */
object PhotoMarkupMetrics {
    private const val MAX_BLUR_RADIUS = 128
    private const val MAX_MOSAIC_BLOCK = 256

    fun strokeWidth(size: Float, output: PixelSize): Float =
        longEdge(output) * (0.002f + size * 0.0007f)

    fun blurRadius(strength: Float, output: PixelSize): Int =
        min(
            MAX_BLUR_RADIUS,
            max(1, ceil(longEdge(output) * (0.001f + strength * 0.00009f)).toInt())
        )

    fun mosaicBlock(strength: Float, output: PixelSize): Int =
        min(
            MAX_MOSAIC_BLOCK,
            max(2, ceil(longEdge(output) * (0.006f + strength * 0.0003f)).toInt())
        )

    /** How far a stroke reads beyond the rows it paints. */
    fun readReach(stroke: PhotoMarkupStroke, output: PixelSize): Int =
        when (stroke.type) {
            PhotoMarkupType.Pen, PhotoMarkupType.Highlighter -> 0
            // The two box passes each reach one radius.
            PhotoMarkupType.Blur -> blurRadius(stroke.strength, output) * 2
            PhotoMarkupType.Mosaic -> mosaicBlock(stroke.strength, output)
        }

    private fun longEdge(output: PixelSize): Int = max(output.width, output.height)
}

/**
 * A subject mask at the segmenter's resolution: one alpha byte per pixel,
 * row-major, stretched over the output frame. Instances are immutable and
 * compared by content so undo steps and autosaves stay honest.
 */
class PhotoCutoutMatte(val width: Int, val height: Int, alpha: ByteArray) {
    private val storage: ByteArray = alpha.copyOf()
    private val contentHash: Int = storage.contentHashCode()

    init {
        require(width > 0 && height > 0) { "matte must have a positive size" }
        require(storage.size.toLong() == width.toLong() * height) {
            "matte holds ${storage.size} bytes, expected ${width}x$height"
        }
    }

    /** Alpha in 0..255 at a matte pixel. */
    fun alphaAt(x: Int, y: Int): Int = storage[y * width + x].toInt() and 0xFF

    /** A defensive copy for encoders. */
    fun copyAlpha(): ByteArray = storage.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || (other is PhotoCutoutMatte && width == other.width &&
            height == other.height && contentHash == other.contentHash &&
            storage.contentEquals(other.storage))

    override fun hashCode(): Int = 31 * (31 * width + height) + contentHash
}
