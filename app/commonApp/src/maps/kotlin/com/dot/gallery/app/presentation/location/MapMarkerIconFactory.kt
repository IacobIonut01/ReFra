package com.dot.gallery.app.presentation.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.model.UriMedia
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.sketch
import java.text.NumberFormat

/**
 * Photo pins of the locations map: a rounded-square photo with a white ring and a soft shadow,
 * and a pill with the cluster's item count over its top-right corner. The selected pin swaps
 * the white ring for a thicker accent one. [sizePx] is the photo's side; the bitmap adds room
 * around it for the shadow and the pill while keeping the photo centred on the anchor.
 */
object MapMarkerIconFactory {
    suspend fun load(
        context: Context,
        media: UriMedia,
        count: Int,
        sizePx: Int,
        borderColor: Int,
        badgeColor: Int,
        badgeContentColor: Int,
        selected: Boolean = false,
    ): Bitmap? {
        val appContext = context.applicationContext
        val source = try {
            appContext.sketch.execute(
                ImageRequest(appContext, media.getUri().toString()) {
                    size(sizePx, sizePx)
                    scale(Scale.CENTER_CROP)
                    setExtra(key = "mediaSignature", value = media.toString())
                }
            ).image?.asBitmapOrNull()
        } catch (e: Exception) {
            printWarn(TAG, "map pin thumbnail failed", ctx = mapOf("error" to e.toString()))
            null
        } ?: return null
        return try {
            render(
                source = source,
                count = count,
                sizePx = sizePx,
                borderColor = borderColor,
                badgeColor = badgeColor,
                badgeContentColor = badgeContentColor,
                selected = selected,
            )
        } catch (e: Exception) {
            printWarn(TAG, "map pin render failed", ctx = mapOf("error" to e.toString()))
            null
        }
    }

    fun placeholder(sizePx: Int, borderColor: Int, fillColor: Int): Bitmap {
        val geometry = PinGeometry(sizePx, badgeWidth = 0f)
        val output = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawFrame(canvas, geometry, borderColor)
        canvas.drawRoundRect(geometry.inner, geometry.innerRadius, geometry.innerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
        })
        return output
    }

    private fun render(
        source: Bitmap,
        count: Int,
        sizePx: Int,
        borderColor: Int,
        badgeColor: Int,
        badgeContentColor: Int,
        selected: Boolean,
    ): Bitmap {
        val unit = sizePx / PHOTO_DP
        val label = if (count > 1) NumberFormat.getIntegerInstance().format(count) else null
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeContentColor
            textAlign = Paint.Align.CENTER
            textSize = 12f * unit
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeWidth = label?.let { textPaint.measureText(it) + 16f * unit } ?: 0f
        val geometry = PinGeometry(sizePx, badgeWidth)
        val output = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val ringColor = if (selected) badgeColor else borderColor
        val ring = if (selected) 4f * unit else geometry.ring
        drawFrame(canvas, geometry, ringColor)

        val inner = RectF(geometry.photo).apply { inset(ring, ring) }
        val innerRadius = (geometry.radius - ring).coerceAtLeast(0f)
        val scale = maxOf(inner.width() / source.width, inner.height() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                inner.left + (inner.width() - source.width * scale) / 2f,
                inner.top + (inner.height() - source.height * scale) / 2f,
            )
        }
        canvas.drawRoundRect(inner, innerRadius, innerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                it.setLocalMatrix(matrix)
            }
        })

        if (label != null) {
            val left = geometry.photo.left + 38f * unit
            val top = geometry.photo.top - 8f * unit
            val pill = RectF(left, top, left + badgeWidth, top + 20f * unit)
            val stroke = 2f * unit
            canvas.drawRoundRect(
                RectF(pill).apply { inset(-stroke / 2f, -stroke / 2f) },
                pill.height(), pill.height(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
            )
            canvas.drawRoundRect(
                RectF(pill).apply { inset(stroke / 2f, stroke / 2f) },
                pill.height(), pill.height(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColor },
            )
            canvas.drawText(label, pill.centerX(), pill.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
        return output
    }

    /** The shadowed outer rounded square in [color], which shows as the ring around the photo. */
    private fun drawFrame(canvas: Canvas, geometry: PinGeometry, color: Int) {
        val unit = geometry.unit
        canvas.drawRoundRect(geometry.photo, geometry.radius, geometry.radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            setShadowLayer(4f * unit, 0f, 2f * unit, Color.argb(64, 0, 0, 0))
        })
    }

    /** Photo rectangle centred in a bitmap big enough for the shadow and the count pill. */
    private class PinGeometry(sizePx: Int, badgeWidth: Float) {
        val unit = sizePx / PHOTO_DP
        val ring = 3f * unit
        val radius = 18f * unit
        private val half = sizePx / 2f
        private val shadowPad = 10f * unit
        private val halfWidth = maxOf(half + shadowPad, 38f * unit + badgeWidth + 2f * unit - half)
        private val halfHeight = half + maxOf(shadowPad, 10f * unit)
        val width = (halfWidth * 2f).toInt()
        val height = (halfHeight * 2f).toInt()
        val photo = RectF(width / 2f - half, height / 2f - half, width / 2f + half, height / 2f + half)
        val inner = RectF(photo).apply { inset(ring, ring) }
        val innerRadius = radius - ring
    }

    private const val PHOTO_DP = 60f
    private const val TAG = "ui.locations.map"
}
