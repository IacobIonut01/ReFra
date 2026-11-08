package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PixelSize

/**
 * An immutable frame of linear-light straight-alpha RGBA stored as floats,
 * `width * height * 4` components in row-major order. Colour channels may hold
 * out-of-range scene values (negatives, above one); only alpha is constrained
 * to `[0, 1]` and every component must be finite.
 *
 * The public constructor copies the input array, so callers cannot mutate a
 * shared frame. [copyPixels] is the only way to read the buffer back out;
 * kernels inside this module use [component] to avoid a copy per sample, and
 * [fromOwned] to wrap a freshly computed buffer without an extra copy.
 */
class LinearRgbaFrame private constructor(
    val size: PixelSize,
    private val storage: FloatArray,
    @Suppress("unused") ownership: Ownership
) {

    /** Distinguishes the trusted no-copy constructor from the copying public one. */
    private enum class Ownership { Owned }

    /** Copies [pixels] into a validated frame. */
    constructor(size: PixelSize, pixels: FloatArray) :
        this(size, pixels.copyOf(), Ownership.Owned)

    init {
        require(size.pixelCount <= Int.MAX_VALUE.toLong() / COMPONENTS_PER_PIXEL) {
            "frame too large: ${size.width}x${size.height} exceeds a FloatArray"
        }
        val expected = size.pixelCount * COMPONENTS_PER_PIXEL
        require(storage.size.toLong() == expected) {
            "pixel buffer holds ${storage.size} components but ${size.width}x${size.height} " +
                "RGBA needs $expected"
        }
        for (index in storage.indices) {
            val value = storage[index]
            require(value.isFinite()) { "non-finite component at index $index" }
            if (index % COMPONENTS_PER_PIXEL == ALPHA_OFFSET) {
                require(value in 0f..1f) { "alpha out of [0, 1] at index $index: $value" }
            }
        }
    }

    /** Defensive copy of the pixel buffer. */
    fun copyPixels(): FloatArray = storage.copyOf()

    internal fun component(index: Int): Float = storage[index]

    /** The live buffer, for hot loops that only read it. Never mutate it. */
    internal val readOnlyStorage: FloatArray get() = storage

    internal val componentCount: Int get() = storage.size

    internal companion object {

        /**
         * Wraps [pixels] without copying — the caller transfers ownership and
         * must not mutate the array afterwards. Same validation as the public
         * constructor.
         */
        internal fun fromOwned(size: PixelSize, pixels: FloatArray): LinearRgbaFrame =
            LinearRgbaFrame(size, pixels, Ownership.Owned)

        const val COMPONENTS_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
    }
}
