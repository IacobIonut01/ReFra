package com.dot.gallery.core.decoder.glide

import com.bumptech.glide.load.Option

object GalleryGlideOptions {
    // Mirrors Sketch extras
    val REAL_MIME_TYPE: Option<String> =
        Option.memory("com.dot.gallery.glide.realMimeType")
    val MEDIA_KEY: Option<Long> =
        Option.memory("com.dot.gallery.glide.mediaKey")
}