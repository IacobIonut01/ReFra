package com.dot.gallery.feature_node.presentation.search.util

/**
 * Copyright 2023 Viacheslav Barkov
 * Copyright 2021 Microsoft Corporation.
 *
 * Parts of the following code are a derivative work of the code from the ONNX Runtime project,
 * which is licensed MIT.
 */

import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.nio.FloatBuffer

const val DIM_BATCH_SIZE = 1
const val DIM_PIXEL_SIZE = 3
const val IMAGE_SIZE_X = 224
const val IMAGE_SIZE_Y = 224

fun preProcess(bitmap: Bitmap): FloatBuffer {
    val imgData = FloatBuffer.allocate(
        DIM_BATCH_SIZE * DIM_PIXEL_SIZE * IMAGE_SIZE_X * IMAGE_SIZE_Y
    )
    imgData.rewind()
    val stride = IMAGE_SIZE_X * IMAGE_SIZE_Y
    val bmpData = IntArray(stride)
    bitmap.getPixels(bmpData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (i in 0 until IMAGE_SIZE_X) {
        for (j in 0 until IMAGE_SIZE_Y) {
            val idx = IMAGE_SIZE_Y * i + j
            val pixelValue = bmpData[idx]
            imgData.put(idx, (((pixelValue shr 16 and 0xFF) / 255f - 0.48145467f) / 0.26862955f))
            imgData.put(
                idx + stride, (((pixelValue shr 8 and 0xFF) / 255f - 0.4578275f) / 0.2613026f)
            )
            imgData.put(
                idx + stride * 2, (((pixelValue and 0xFF) / 255f - 0.40821072f) / 0.2757771f)
            )
        }
    }

    imgData.rewind()
    return imgData
}

fun centerCrop(bitmap: Bitmap, imageSize: Int): Bitmap {
    val cropX: Int
    val cropY: Int
    val cropSize: Int
    if (bitmap.width >= bitmap.height) {
        cropX = bitmap.width / 2 - bitmap.height / 2
        cropY = 0
        cropSize = bitmap.height
    } else {
        cropX = 0
        cropY = bitmap.height / 2 - bitmap.width / 2
        cropSize = bitmap.width
    }
    var bitmapCropped = Bitmap.createBitmap(
        bitmap, cropX, cropY, cropSize, cropSize
    )
    bitmapCropped = bitmapCropped.scale(imageSize, imageSize, false)
    return bitmapCropped
}