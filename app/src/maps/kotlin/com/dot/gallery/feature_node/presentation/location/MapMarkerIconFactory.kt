package com.dot.gallery.feature_node.presentation.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation

object MapMarkerIconFactory {
    fun load(
        context: Context,
        media: Media.UriMedia,
        count: Int,
        sizePx: Int,
        borderColor: Int,
        badgeColor: Int,
        badgeContentColor: Int,
    ): Bitmap? {
        val glide = Glide.with(context.applicationContext)
        val target = glide
            .asBitmap()
            .load(media.getUri())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .signature(GlideInvalidation.signature(media))
            .submit(sizePx, sizePx)
        return try {
            render(
                source = target.get(),
                count = count,
                sizePx = sizePx,
                borderColor = borderColor,
                badgeColor = badgeColor,
                badgeContentColor = badgeContentColor,
            )
        } catch (_: Exception) {
            null
        } finally {
            glide.clear(target)
        }
    }

    fun placeholder(sizePx: Int, borderColor: Int, fillColor: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val border = (sizePx * 0.06f).coerceAtLeast(2f)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
        })
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - border, Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val border = (sizePx * 0.06f).coerceAtLeast(2f)
        val center = sizePx / 2f
        val radius = center - border
        canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })

        val scale = maxOf(sizePx.toFloat() / source.width, sizePx.toFloat() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (sizePx - source.width * scale) / 2f,
                (sizePx - source.height * scale) / 2f,
            )
        }
        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                it.setLocalMatrix(matrix)
            }
        })

        if (count > 1) {
            val text = if (count > 999) "999+" else count.toString()
            val badgeRadius = sizePx * 0.22f
            val badgeX = sizePx - badgeRadius
            val badgeY = sizePx - badgeRadius
            canvas.drawCircle(badgeX, badgeY, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
            })
            canvas.drawCircle(badgeX, badgeY, badgeRadius - border / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor
            })
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeContentColor
                textAlign = Paint.Align.CENTER
                textSize = if (text.length > 3) sizePx * 0.18f else sizePx * 0.22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(text, badgeX, badgeY - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
        return output
    }
}
