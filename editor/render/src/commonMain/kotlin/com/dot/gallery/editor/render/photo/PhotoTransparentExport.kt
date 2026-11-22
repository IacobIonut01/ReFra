package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.hasTransparency

/** The containers that keep a transparent output's alpha. */
val PhotoAlphaExportFormats: Set<PhotoExportFormat> =
    setOf(PhotoExportFormat.PNG, PhotoExportFormat.WEBP)

/**
 * Whether saving [document] in [requested] (null = the source's own
 * container) would drop its transparency — a removed background or a
 * shaped crop written into a container without alpha, such as a JPEG
 * source's own. The editor asks for PNG or WebP before saving then.
 */
fun losesTransparency(
    document: PhotoDocument,
    requested: PhotoExportFormat?,
    sourceKeepsAlpha: Boolean
): Boolean = document.hasTransparency && when (requested) {
    null -> !sourceKeepsAlpha
    else -> requested !in PhotoAlphaExportFormats
}

/**
 * The container [document] is actually written in when [requested] was asked
 * for: a transparent output that would lose its alpha falls back to PNG, so
 * no caller can write a shaped crop or cutout onto an opaque background.
 */
fun transparentExportFormat(
    document: PhotoDocument,
    requested: PhotoExportFormat?,
    sourceKeepsAlpha: Boolean
): PhotoExportFormat? =
    if (losesTransparency(document, requested, sourceKeepsAlpha)) PhotoExportFormat.PNG
    else requested
