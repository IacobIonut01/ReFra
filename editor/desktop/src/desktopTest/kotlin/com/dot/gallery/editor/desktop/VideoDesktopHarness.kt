package com.dot.gallery.editor.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dot.gallery.editor.ui.EditorTheme
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Dev harness for the desktop video editor — run it with the Gradle task
 * `:editor:desktop:runVideoEditor -PeditorFile=/absolute/video.mp4` (or
 * directly with the file path as the only argument). No bundled sample: a
 * real file path is required. Exported copies land beside the source.
 *
 * Closing the window goes through the editor's own close flow (discard /
 * cancel-export prompts) instead of dropping unsaved edits.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) {
        "usage: runVideoEditor -PeditorFile=/absolute/video.mp4"
    }
    val source = Path.of(args[0]).toAbsolutePath()
    val logger = Logger.getLogger("editor.video")
    // Optional review size, e.g. REFRA_EDITOR_WINDOW=412x892.
    val windowSize = System.getenv("REFRA_EDITOR_WINDOW")
        ?.split('x')?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.size == 2 }
        ?.let { (w, h) -> DpSize(w.dp, h.dp) }
        ?: DpSize(1280.dp, 800.dp)
    application {
        var closeRequests by remember { mutableIntStateOf(0) }
        Window(
            onCloseRequest = { closeRequests++ },
            title = "ReFra video editor",
            state = rememberWindowState(size = windowSize)
        ) {
            EditorTheme {
                DesktopVideoEditor(
                    source = source,
                    onClose = { exitApplication() },
                    closeRequest = closeRequests,
                    onError = { logger.log(Level.WARNING, "editor error", it) }
                )
            }
        }
    }
}
