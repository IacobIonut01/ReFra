/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.dot.gallery.core.decryption.VaultDecryptCache
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.libs.panoramaviewer.PanoramaImageLoader
import com.dot.gallery.libs.panoramaviewer.PanoramaLog
import java.io.File

/**
 * A [PanoramaImageLoader] that decrypts encrypted vault media before decoding.
 *
 * This loader decrypts the `.enc` file using [KeychainHolder] to obtain the raw
 * image bytes, then creates a [BitmapRegionDecoder] from those bytes for
 * progressive region loading — exactly like the default loader, but with a
 * decryption step up front.
 *
 * The decrypted bytes are held in memory for the lifetime of the loader so that
 * [loadBase] and [loadRegion] can create region decoders without re-decrypting.
 * Call [close] to release the decoder and allow the byte array to be GC'd.
 *
 * ## Usage
 *
 * ```kotlin
 * val loader = remember(media) {
 *     EncryptedPanoramaImageLoader(keychainHolder, encryptedFile)
 * }
 * PanoramaViewer(
 *     imageUri       = Uri.EMPTY,
 *     projectionType = projectionType,
 *     imageLoader    = loader,
 *     modifier       = Modifier.fillMaxSize()
 * )
 * ```
 *
 * @param keychainHolder The keychain used to decrypt the encrypted media file.
 * @param encryptedFile  The `.enc` file on disk containing the encrypted image.
 */
class EncryptedPanoramaImageLoader(
    private val keychainHolder: KeychainHolder,
    private val encryptedFile: File
) : PanoramaImageLoader {

    private var decoder: BitmapRegionDecoder? = null
    private var decryptedHandle: VaultDecryptCache.Handle? = null

    override var imageWidth: Int = 0
        private set
    override var imageHeight: Int = 0
        private set

    override fun initialize(): Boolean {
        PanoramaLog.d("EncryptedPanoramaImageLoader.initialize() file=${encryptedFile.name}")

        // Borrow the shared canonical decrypted file (#1282)
        val handle = try {
            VaultDecryptStore.acquire(keychainHolder, encryptedFile)
        } catch (e: Exception) {
            PanoramaLog.e("EncryptedPanoramaImageLoader.initialize() decryption failed", e)
            printError("decode.panorama", "vault panorama decryption failed", e)
            return false
        }
        decryptedHandle = handle
        val path = handle.file.absolutePath

        // Read dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        imageWidth = opts.outWidth
        imageHeight = opts.outHeight
        PanoramaLog.d("EncryptedPanoramaImageLoader.initialize() size=${imageWidth}x${imageHeight}")

        if (imageWidth <= 0 || imageHeight <= 0) {
            PanoramaLog.e("EncryptedPanoramaImageLoader.initialize() invalid dimensions")
            printError("decode.panorama", "vault panorama invalid dimensions ${imageWidth}x${imageHeight}")
            return false
        }

        // Create region decoder
        decoder = try {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(path, false)
        } catch (e: Exception) {
            PanoramaLog.e("EncryptedPanoramaImageLoader.initialize() BitmapRegionDecoder failed", e)
            printError("decode.panorama", "vault panorama region decoder failed", e)
            return false
        }

        PanoramaLog.d("EncryptedPanoramaImageLoader.initialize() decoder created: ${decoder != null}")
        return decoder != null
    }

    override fun loadBase(maxDimension: Int): Bitmap? {
        val d = decoder ?: run {
            // Fallback: decode full image from the decrypted file
            val path = decryptedHandle?.file?.absolutePath ?: return null
            var sampleSize = 1
            while (imageWidth / sampleSize > maxDimension || imageHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return try {
                BitmapFactory.decodeFile(path, opts)
            } catch (e: Exception) {
                PanoramaLog.e("EncryptedPanoramaImageLoader.loadBase() fallback failed", e)
                printError("decode.panorama", "vault panorama base fallback decode failed", e)
                null
            }
        }

        var sampleSize = 1
        while (imageWidth / sampleSize > maxDimension || imageHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            d.decodeRegion(Rect(0, 0, imageWidth, imageHeight), opts)
        } catch (e: Exception) {
            PanoramaLog.e("EncryptedPanoramaImageLoader.loadBase() decodeRegion failed", e)
            printError("decode.panorama", "vault panorama base decodeRegion failed", e)
            null
        }
    }

    override fun loadRegion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        maxDimension: Int
    ): Bitmap? {
        val d = decoder ?: return null

        val clampedLeft = left.coerceIn(0, imageWidth)
        val clampedTop = top.coerceIn(0, imageHeight)
        val clampedRight = right.coerceIn(0, imageWidth)
        val clampedBottom = bottom.coerceIn(0, imageHeight)

        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null

        val regionW = clampedRight - clampedLeft
        val regionH = clampedBottom - clampedTop
        var sampleSize = 1
        while (regionW / sampleSize > maxDimension || regionH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            d.decodeRegion(Rect(clampedLeft, clampedTop, clampedRight, clampedBottom), opts)
        } catch (e: Exception) {
            PanoramaLog.e("EncryptedPanoramaImageLoader.loadRegion() failed", e)
            printError("decode.panorama", "vault panorama loadRegion failed", e)
            null
        }
    }

    override fun close() {
        PanoramaLog.d("EncryptedPanoramaImageLoader.close()")
        decoder?.recycle()
        decoder = null
        decryptedHandle?.release()
        decryptedHandle = null
    }
}
