/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

internal object OcrDecodePolicy {
    private val flatExtensions = setOf(
        "apng", "gif", "j2c", "j2k", "jp2", "jpc", "jpx", "jxl", "psb", "psd", "svg",
        "tif", "tiff", "arw", "cr2", "cr3", "dng", "nef", "nrw", "orf", "pef", "raf", "rw2", "srw",
    )

    fun isHeif(mimeType: String, label: String): Boolean {
        val mime = mimeType.lowercase()
        val extension = label.substringAfterLast('.', "").lowercase()
        return mime in setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence", "image/avif", "image/avis") ||
            extension in setOf("avif", "heic", "heif", "hif")
    }

    fun allowsPlatformRegion(mimeType: String, label: String): Boolean {
        val mime = mimeType.lowercase()
        val extension = label.substringAfterLast('.', "").lowercase()
        if (mime.endsWith("-sequence") || mime == "image/avis") return false
        if (extension in flatExtensions) return false
        if (mime in setOf(
                "image/apng", "image/gif", "image/jp2", "image/jpeg2000", "image/jpx", "image/jxl",
                "image/svg+xml", "image/tif", "image/tiff", "image/vnd.adobe.photoshop", "image/x-photoshop",
            )
        ) return false
        if (mime.startsWith("image/x-") || mime.startsWith("image/vnd.")) return false
        return true
    }
}
