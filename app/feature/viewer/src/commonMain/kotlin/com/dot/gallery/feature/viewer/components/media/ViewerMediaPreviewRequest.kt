/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.idLessKey
import com.dot.gallery.domain.util.isEncrypted
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision

/** Long edge of the low-res preview the grid prefetches on tap. */
const val MEDIA_PREVIEW_SIZE = 600

/**
 * Cache version extra for the low-res preview: busts the entry when the underlying file is
 * replaced in place (#1004).
 */
fun mediaPreviewVersion(media: Media): String = "${media.timestamp}:${media.size}"

/**
 * The one definition of the viewer's low-res preview request.
 *
 * Everything that reads the preview must build it here — the click-time prefetch in the grid,
 * the viewer's shared-element thumbnail, and the blurred backdrop. They share Sketch's memory
 * cache key, so a tap that prefetched makes the viewer's copy resolve with no decode at all.
 * Any drift between the call sites turns that cache hit into a fresh decode (and, for the
 * backdrop, a late fade-in).
 */
fun ImageRequest.Builder.mediaPreviewOptions(
    media: Media,
    width: Int = MEDIA_PREVIEW_SIZE,
    height: Int = MEDIA_PREVIEW_SIZE,
) {
    resize(width = width, height = height, precision = Precision.LESS_PIXELS)
    setExtra("realMimeType", media.mimeType)
    setExtra(key = "mediaVersion", value = mediaPreviewVersion(media))
    if (media.isEncrypted) {
        setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
    }
}
