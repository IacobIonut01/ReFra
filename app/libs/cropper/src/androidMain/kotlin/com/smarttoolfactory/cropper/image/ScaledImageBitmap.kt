package com.smarttoolfactory.cropper.image

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect

@Composable
internal fun getScaledImageBitmap(
    imageWidth: Dp,
    imageHeight: Dp,
    rect: IntRect,
    bitmap: ImageBitmap,
    contentScale: ContentScale
): ImageBitmap {

    val scaledBitmap =
        remember(bitmap, rect, imageWidth, imageHeight, contentScale) {
            // This bitmap is needed when we crop original bitmap due to scaling mode
            // and aspect ratio result of cropping
            // We might have center section of the image after cropping, and
            // because of that thumbLayout either should have rectangle and some
            // complex calculation for srcOffset and srcSide along side with touch offset
            // or we can create a new bitmap that only contains area bounded by rectangle
            runCatching {
                Bitmap.createBitmap(
                    bitmap.asAndroidBitmap(),
                    rect.left,
                    rect.top,
                    rect.width,
                    rect.height
                ).asImageBitmap()
            }.getOrNull() ?: bitmap
        }
    return scaledBitmap
}
