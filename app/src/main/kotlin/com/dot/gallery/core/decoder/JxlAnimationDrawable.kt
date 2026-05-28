package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import com.awxkee.jxlcoder.JxlAnimatedImage

/**
 * Memory-efficient animated JXL drawable that decodes frames lazily.
 *
 * Unlike [JxlAnimatedImage.animatedDrawable] which eagerly loads every frame
 * into an [android.graphics.drawable.AnimationDrawable], this drawable only
 * keeps **one frame bitmap** in memory at a time and decodes the next frame
 * on-demand via [JxlAnimatedImage.getFrame].
 *
 * The [JxlAnimatedImage] native coordinator is retained for the lifetime of
 * this drawable; its `finalize()` releases the native memory when GC collects it.
 *
 * @param animatedImage  the decoded JXL animation handle (caller must NOT close it)
 * @param scaleWidth     target width for frame scaling (0 = original)
 * @param scaleHeight    target height for frame scaling (0 = original)
 */
class JxlAnimationDrawable(
    private val animatedImage: JxlAnimatedImage,
    private val scaleWidth: Int = 0,
    private val scaleHeight: Int = 0,
) : Drawable(), Animatable {

    private val frameCount: Int = animatedImage.numberOfFrames
    private val intrinsicW: Int = animatedImage.getWidth()
    private val intrinsicH: Int = animatedImage.getHeight()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val frameDurations = IntArray(frameCount) { animatedImage.getFrameDuration(it) }

    private var currentFrameIndex = 0
    private var currentBitmap: Bitmap? = null
    private var _isRunning = false
    private var _isOneShot = false

    private val advanceRunnable = Runnable { advanceFrame() }

    init {
        // Pre-decode the first frame so the drawable is immediately drawable.
        currentBitmap = animatedImage.getFrame(0, scaleWidth, scaleHeight)
    }

    var isOneShot: Boolean
        get() = _isOneShot
        set(value) { _isOneShot = value }

    // ---- Drawable ----

    override fun getIntrinsicWidth(): Int = intrinsicW
    override fun getIntrinsicHeight(): Int = intrinsicH

    override fun draw(canvas: Canvas) {
        val bmp = currentBitmap ?: return
        canvas.drawBitmap(bmp, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ---- Animatable ----

    override fun start() {
        if (_isRunning || frameCount <= 1) return
        _isRunning = true
        scheduleNextFrame()
    }

    override fun stop() {
        _isRunning = false
        unscheduleSelf(advanceRunnable)
    }

    override fun isRunning(): Boolean = _isRunning

    // ---- Internal ----

    private fun advanceFrame() {
        if (!_isRunning) return

        val nextIndex = (currentFrameIndex + 1) % frameCount

        // One-shot: stop after playing through all frames once.
        if (_isOneShot && nextIndex == 0) {
            _isRunning = false
            return
        }

        currentFrameIndex = nextIndex

        // Decode the next frame; let GC reclaim the previous bitmap.
        currentBitmap = animatedImage.getFrame(currentFrameIndex, scaleWidth, scaleHeight)

        invalidateSelf()
        scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
        // Ensure at least ~16 ms to avoid busy-looping on very fast durations.
        val delay = maxOf(frameDurations[currentFrameIndex].toLong(), 16L)
        scheduleSelf(advanceRunnable, SystemClock.uptimeMillis() + delay)
    }
}
