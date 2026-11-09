/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.dot.gallery.core.image.thumbnail.HeavyThumbFormat
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailClassifier
import com.dot.gallery.core.logging.printWarn

/**
 * Whether a HEIF MIME decoder should step aside for [MediaStoreThumbnailModelLoader].
 *
 * `MimeInputStreamModelLoader` is the first `Uri` loader Glide consults, so without this every
 * grid-sized HEIC cell read the whole file and ran a full HEVC decode, while JPEG fell through to
 * the platform's cached thumbnail (#1385). Returning `null` from the decoder lets Glide move on to
 * the MediaStore loader.
 *
 * Only grid-sized requests on `content://media` URIs that the platform decodes correctly defer.
 * Over-10-bit HEIF ([HeavyThumbFormat.HEIF_SW]) keeps the software decode, because the platform
 * thumbnailer corrupts it.
 */
internal fun shouldDeferHeifToPlatformThumbnail(
    isMediaStoreUri: Boolean,
    width: Int,
    height: Int,
    heavyFormat: HeavyThumbFormat?,
    maxDimension: Int = MediaStoreThumbnailModelLoader.maxDimension,
): Boolean = isMediaStoreUri &&
    heavyFormat == null &&
    MediaStoreThumbnailModelLoader.coversSize(width, height, maxDimension)

/** Applies [shouldDeferHeifToPlatformThumbnail] to a fetched [MimeInputStream]. */
internal fun MimeInputStream.defersToPlatformThumbnail(
    fallbackContext: Context,
    width: Int,
    height: Int,
): Boolean {
    val uri = uri ?: return false
    val isMediaStoreUri = uri.scheme == ContentResolver.SCHEME_CONTENT &&
        uri.authority == MediaStore.AUTHORITY
    // Cheap checks first: classification may probe the file header.
    if (!isMediaStoreUri || !MediaStoreThumbnailModelLoader.coversSize(width, height)) return false
    val resolver = (context ?: fallbackContext).contentResolver
    val heavyFormat = runCatching { HeavyThumbnailClassifier.classify(resolver, uri) }
        .getOrElse {
            // Unknown depth: keep the software decode, which is correct for every HEIF.
            printWarn(
                "decode.heif",
                "classification failed; keeping software decode",
                ctx = mapOf("uri" to uri.toString(), "error" to it.toString()),
            )
            return false
        }
    return shouldDeferHeifToPlatformThumbnail(isMediaStoreUri, width, height, heavyFormat)
}
