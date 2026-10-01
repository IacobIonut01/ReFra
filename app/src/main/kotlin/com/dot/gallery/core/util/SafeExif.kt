/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Memory-safe [ExifInterface] access.
 *
 * `ExifInterface(InputStream)` wraps the stream in a `BufferedInputStream` whose buffer grows up to
 * the *mark limit* (the byte offset it wants to skip to) regardless of how many bytes actually
 * exist. On a container whose metadata references a far-away offset — e.g. a large 16-bit TIFF,
 * where the file *is* the TIFF/EXIF structure and a strip offset can be hundreds of MB in — that
 * buffer growth allocates the whole gap at once and throws [OutOfMemoryError] (an `Error`, so it is
 * never caught by `catch (Exception)`).
 *
 * Constructing [ExifInterface] over a seekable [android.os.ParcelFileDescriptor] instead uses
 * `lseek` and never performs that buffered skip, so a far-away/invalid offset just fails per-tag
 * and falls back to defaults. This helper always uses that path and swallows any residual
 * [Throwable] (including OOM) so callers degrade gracefully to `null` instead of crashing.
 */
object SafeExif {

    /** Open an [ExifInterface] over [uri] using a seekable file descriptor. Returns null on failure. */
    fun open(context: Context, uri: Uri): ExifInterface? = try {
        context.openUnredactedFileDescriptor(uri)?.use { pfd ->
            ExifInterface(pfd.fileDescriptor)
        }
    } catch (_: Throwable) {
        null
    }

    /** Read the EXIF/TIFF orientation tag, defaulting to [ExifInterface.ORIENTATION_NORMAL]. */
    fun orientation(context: Context, uri: Uri): Int =
        open(context, uri)?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL
}
