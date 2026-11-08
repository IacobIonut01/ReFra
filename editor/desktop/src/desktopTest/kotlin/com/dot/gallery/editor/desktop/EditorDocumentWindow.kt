package com.dot.gallery.editor.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path

/**
 * One open media document: identity (the composition key), its source
 * path and the routed surface kind. [closeRequest] is the guarded-close
 * bridge — incrementing it runs the editor's own prompt flow.
 */
internal class EditorDocumentWindow(
    val id: Long,
    val source: Path,
    val kind: EditorFileKind
) {
    var closeRequest by mutableIntStateOf(0)
}
