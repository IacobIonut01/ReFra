package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.SrgbPixels

/**
 * One decoded frame: [pixels] are sRGB, upright (container rotation already
 * applied), premultiplied-agnostic opaque ARGB; [time] is the frame's actual
 * timestamp on the source's media clock, not the requested seek target.
 */
data class VideoFrame(val time: EditorTime, val pixels: SrgbPixels)
