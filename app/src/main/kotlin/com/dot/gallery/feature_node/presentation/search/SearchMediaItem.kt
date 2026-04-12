/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.search

import androidx.compose.runtime.Stable
import com.dot.gallery.feature_node.domain.model.Media

/**
 * A generic item for dynamic search carousels (MIME types, lens models, media modes, etc.).
 *
 * @param key  Unique identifier used for matching and as a stable LazyRow key.
 * @param label Readable display name shown on the card.
 * @param media A representative [Media] thumbnail (first matching item).
 * @param count Total number of matching items.
 */
@Stable
data class SearchMediaItem(
    val key: String,
    val label: String,
    val media: Media?,
    val count: Int = 0
)

// ── MIME-type readability helpers ───────────────────────────────────

private val MIME_TYPE_LABELS = mapOf(
    // Image types
    "image/jpeg" to "JPEGs",
    "image/jpg" to "JPEGs",
    "image/png" to "PNGs",
    "image/gif" to "GIFs",
    "image/webp" to "WebP",
    "image/heif" to "HEIF",
    "image/heic" to "HEIC",
    "image/avif" to "AVIF",
    "image/bmp" to "BMP",
    "image/tiff" to "TIFF",
    "image/svg+xml" to "SVG",
    "image/x-adobe-dng" to "DNG RAW",
    "image/x-sony-arw" to "ARW RAW",
    "image/x-canon-cr2" to "CR2 RAW",
    "image/x-canon-cr3" to "CR3 RAW",
    "image/x-nikon-nef" to "NEF RAW",
    "image/x-samsung-srw" to "SRW RAW",
    "image/x-panasonic-rw2" to "RW2 RAW",
    "image/x-olympus-orf" to "ORF RAW",
    "image/x-fuji-raf" to "RAF RAW",
    // Video types
    "video/mp4" to "MP4 Videos",
    "video/3gpp" to "3GP Videos",
    "video/3gpp2" to "3GP2 Videos",
    "video/webm" to "WebM Videos",
    "video/quicktime" to "MOV Videos",
    "video/x-matroska" to "MKV Videos",
    "video/avi" to "AVI Videos",
    "video/x-msvideo" to "AVI Videos",
    "video/x-ms-wmv" to "WMV Videos",
    "video/ogg" to "OGG Videos",
    "video/mpeg" to "MPEG Videos",
    "video/x-flv" to "FLV Videos",
)

/**
 * Converts a raw MIME type string to a human-readable label.
 *
 * Examples:
 * - `image/jpeg`    → "JPEGs"
 * - `image/png`     → "PNGs"
 * - `video/mp4`     → "MP4 Videos"
 * - `image/x-adobe-dng` → "DNG RAW"
 * - `image/unknown` → "UNKNOWN Images"  (fallback)
 * - `video/unknown` → "UNKNOWN Videos"  (fallback)
 */
fun String.toReadableMimeType(): String {
    MIME_TYPE_LABELS[lowercase()]?.let { return it }

    // Fallback: derive a readable name from the subtype
    val (type, subtype) = split("/", limit = 2).let {
        it.getOrElse(0) { "other" } to it.getOrElse(1) { "" }
    }
    val cleanSubtype = subtype
        .removePrefix("x-")
        .replace("-", " ")
        .uppercase()

    return when (type) {
        "image" -> "$cleanSubtype Images"
        "video" -> "$cleanSubtype Videos"
        "audio" -> "$cleanSubtype Audio"
        else -> "$cleanSubtype Files"
    }
}
