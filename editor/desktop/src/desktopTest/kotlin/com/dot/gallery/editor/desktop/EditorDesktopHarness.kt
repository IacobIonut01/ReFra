package com.dot.gallery.editor.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dot.gallery.editor.io.RecentProjects
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.EditorUiPreferences
import com.dot.gallery.editor.ui.LocalEditorUiPreferences
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.name

/**
 * The workbench's open-document list. Each window's session is keyed on
 * its document id, so opening or closing one document can never touch
 * another's composition — a second window does not dispose the first's
 * dirty session. Kept as a plain holder so the window-independence
 * contract is testable without AWT.
 */
internal class WorkbenchDocuments {
    private val ids = AtomicLong(0L)
    val windows = mutableStateListOf<EditorDocumentWindow>()

    fun open(source: Path): EditorDocumentWindow {
        val window = EditorDocumentWindow(
            id = ids.getAndIncrement(),
            source = source,
            kind = editorFileKind(source)
        )
        windows += window
        return window
    }

    fun close(window: EditorDocumentWindow) {
        windows.remove(window)
    }
}

/**
 * The desktop editor workbench — run it with the Gradle task
 * `:editor:desktop:runEditor` (optionally `-PeditorFile=/abs/media` to open
 * that file directly). With no argument a welcome window offers a native
 * Open dialog; every picked file becomes its own document window, routed
 * by extension to the photo or video surface. Each window's close goes
 * through the editor's own discard/cancel-export flow; the app exits when
 * the last window closes.
 */
fun main(args: Array<String>) {
    val initial = args.firstOrNull()?.let { Path.of(it).toAbsolutePath() }
    val logger = Logger.getLogger("editor.workbench")
    application {
        val documents = remember {
            WorkbenchDocuments().apply { initial?.let(::open) }
        }
        // Shared workbench preferences: every window reads the same values,
        // so Appearance/View toggles apply to welcome and all documents.
        var darkTheme by remember { mutableStateOf(true) }
        var blurEnabled by remember { mutableStateOf(true) }
        val prefs = EditorUiPreferences(blurEnabled = blurEnabled)

        // The recents index shared with the editor hosts: any committed
        // document already landed here through the hosts' autosave, so
        // the menu is a reopen seam, not a second persistence path.
        val recents = remember { RecentProjects(desktopProjectStore()) }

        if (documents.windows.isEmpty()) {
            WelcomeWindow(
                onOpen = documents::open,
                onClose = ::exitApplication,
                darkTheme = darkTheme,
                onDarkTheme = { darkTheme = it },
                prefs = prefs,
                onToggleBlur = { blurEnabled = !blurEnabled },
                recents = recents
            )
        }
        documents.windows.forEach { window ->
            key(window.id) {
                DocumentWindow(
                    window = window,
                    onOpen = documents::open,
                    onClosed = {
                        documents.close(window)
                        if (documents.windows.isEmpty()) exitApplication()
                    },
                    onError = { logger.log(Level.WARNING, "editor error", it) },
                    darkTheme = darkTheme,
                    onDarkTheme = { darkTheme = it },
                    prefs = prefs,
                    onToggleBlur = { blurEnabled = !blurEnabled },
                    recents = recents
                )
            }
        }
    }
}

/**
 * The per-window menus, shared by welcome and document windows. Open
 * (Cmd/Ctrl+O) spawns a new document window; Close Window (Cmd/Ctrl+W)
 * runs that window's own guarded close, never a blind dispose. The
 * Appearance section toggles the shared theme and the floating-chrome
 * blur preference — the active mode is marked with a check.
 */
@Composable
private fun FrameWindowScope.WorkbenchMenus(
    frame: Frame,
    onOpen: (Path) -> Unit,
    onCloseWindow: () -> Unit,
    darkTheme: Boolean,
    onDarkTheme: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onToggleBlur: () -> Unit,
    recents: RecentProjects
) {
    val macShortcuts = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    MenuBar {
        Menu("File") {
            Item(
                "Open…",
                shortcut = KeyShortcut(Key.O, meta = macShortcuts, ctrl = !macShortcuts)
            ) {
                pickMediaFile(frame)?.let(onOpen)
            }
            // Reopen a recent project — the source loads and its persisted
            // recipe restores through the same project file the host wrote.
            Menu("Reopen recent") {
                val entries = recents.list()
                if (entries.isEmpty()) {
                    Item("No recent projects", enabled = false) {}
                }
                entries.forEach { entry ->
                    Item(entry.name) {
                        onOpen(Path.of(entry.reference))
                    }
                }
            }
            Item(
                "Close Window",
                shortcut = KeyShortcut(Key.W, meta = macShortcuts, ctrl = !macShortcuts),
                onClick = onCloseWindow
            )
        }
        Menu("Appearance") {
            Item(if (darkTheme) "✓ Dark" else "Dark") { onDarkTheme(true) }
            Item(if (!darkTheme) "✓ Light" else "Light") { onDarkTheme(false) }
            Separator()
            Item(
                if (blurEnabled) {
                    "✓ Blur Floating Panels"
                } else {
                    "Blur Floating Panels"
                },
                onClick = onToggleBlur
            )
        }
    }
}

/** The empty-workspace window: one real action — Open via the OS picker. */
@Composable
private fun WelcomeWindow(
    onOpen: (Path) -> Unit,
    onClose: () -> Unit,
    darkTheme: Boolean,
    onDarkTheme: (Boolean) -> Unit,
    prefs: EditorUiPreferences,
    onToggleBlur: () -> Unit,
    recents: RecentProjects
) {
    Window(
        onCloseRequest = onClose,
        title = "ReFra Editor",
        state = rememberWindowState(size = DpSize(420.dp, 320.dp))
    ) {
        val frame = window
        WorkbenchMenus(
            frame = frame,
            onOpen = onOpen,
            onCloseWindow = onClose,
            darkTheme = darkTheme,
            onDarkTheme = onDarkTheme,
            blurEnabled = prefs.blurEnabled,
            onToggleBlur = onToggleBlur,
            recents = recents
        )
        CompositionLocalProvider(LocalEditorUiPreferences provides prefs) {
            EditorTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ReFra Editor",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { pickMediaFile(frame)?.let(onOpen) }) {
                            Text("Open media…")
                        }
                    }
                }
            }
        }
    }
}

/**
 * One media document in its own guarded window — a native File→Open menu
 * spawns further windows; the window close runs the editor's own prompt
 * flow before the window is allowed to disappear.
 */
@Composable
private fun DocumentWindow(
    window: EditorDocumentWindow,
    onOpen: (Path) -> Unit,
    onClosed: () -> Unit,
    onError: (Throwable) -> Unit,
    darkTheme: Boolean,
    onDarkTheme: (Boolean) -> Unit,
    prefs: EditorUiPreferences,
    onToggleBlur: () -> Unit,
    recents: RecentProjects
) {
    val previewLabel = System.getProperty("refra.editor.previewLabel")
        ?.trim()?.takeIf { it.isNotEmpty() }
    val titlePrefix =
        if (previewLabel == null) "ReFra Editor" else "ReFra Editor · $previewLabel"
    Window(
        onCloseRequest = { window.closeRequest++ },
        title = "$titlePrefix — ${window.source.name}",
        state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    ) {
        val frame = this.window
        WorkbenchMenus(
            frame = frame,
            onOpen = onOpen,
            onCloseWindow = { window.closeRequest++ },
            darkTheme = darkTheme,
            onDarkTheme = onDarkTheme,
            blurEnabled = prefs.blurEnabled,
            onToggleBlur = onToggleBlur,
            recents = recents
        )
        CompositionLocalProvider(LocalEditorUiPreferences provides prefs) {
            EditorTheme(darkTheme = darkTheme) {
                when (window.kind) {
                    EditorFileKind.Photo -> DesktopPhotoEditor(
                        source = window.source,
                        onClose = { onClosed() },
                        closeRequest = window.closeRequest,
                        onError = onError
                    )
                    EditorFileKind.Video -> DesktopVideoEditor(
                        source = window.source,
                        onClose = { onClosed() },
                        closeRequest = window.closeRequest,
                        onError = onError
                    )
                }
            }
        }
    }
}

/** The OS file dialog — a real native picker, not a fabricated list. */
private fun pickMediaFile(parent: Frame): Path? {
    val dialog = FileDialog(parent, "Open media", FileDialog.LOAD)
    try {
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(dir, file)
    } finally {
        // FileDialog holds a native peer; cancel must release it too.
        dialog.dispose()
    }
}
