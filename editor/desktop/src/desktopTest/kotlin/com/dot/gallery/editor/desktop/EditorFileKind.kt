package com.dot.gallery.editor.desktop

import java.nio.file.Path
import kotlin.io.path.extension

/** The editor surface a picked file routes to. */
internal enum class EditorFileKind { Photo, Video }

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mov", "qt", "3gp", "3g2", "mkv", "webm", "avi", "wmv", "asf",
    "flv", "f4v", "mpg", "mpeg", "mpe", "m2v", "ts", "mts", "m2ts", "vob", "ogv",
    "mxf", "dv", "rm", "rmvb", "divx"
)

/**
 * Extension-based routing for the workbench — containers, not content
 * sniffing: a `.mp4` always lands in the video editor. Unknown extensions
 * default to the photo editor, whose own open-error UI reports an
 * unreadable file honestly.
 */
internal fun editorFileKind(path: Path): EditorFileKind =
    when (path.extension.lowercase()) {
        in VIDEO_EXTENSIONS -> EditorFileKind.Video
        else -> EditorFileKind.Photo
    }
