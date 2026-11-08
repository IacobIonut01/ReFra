package com.dot.gallery.editor.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.project.PhotoProjectCodec
import com.dot.gallery.editor.core.project.PhotoProjectSnapshot
import com.dot.gallery.editor.io.FileProjectStore
import com.dot.gallery.editor.io.RecentProjects
import com.dot.gallery.editor.render.ProjectAutosave
import com.dot.gallery.editor.render.photo.PhotoEditorResult
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.ui.PhotoEditorScreen
import com.dot.gallery.editor.ui.PhotoRenderPreference
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_open_export_failed
import java.io.File
import java.awt.Desktop
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/**
 * Embeds the photo editor in a Compose desktop window. Owns a
 * [PhotoEditorSession] over [DesktopPhotoMediaAccess]: the session's
 * identity is the `(source, outputDirectory)` pair — passing a different
 * source abandons the old session entirely, so the host is expected to
 * confirm unsaved edits before switching input. The session is closed on
 * disposal.
 *
 * [onClose] fires when the user is done editing; it receives the latest
 * [PhotoEditorResult.Saved] copy or [PhotoEditorResult.Cancelled]. Saving a
 * copy does not close the editor — normal desktop editing continues.
 * [outputDirectory] overrides where saved copies land (source directory by
 * default). [closeRequest] is the window-close bridge: increment it (e.g.
 * from `Window(onCloseRequest = { requests++ })`) to run the editor's own
 * dirty/discard flow instead of closing blind. The host supplies the
 * surrounding theme — use `com.dot.gallery.editor.ui.EditorTheme`
 * standalone.
 *
 * The Layers surface's Add affordance opens the platform [FileDialog]
 * filtered to the formats [DesktopPhotoMediaAccess] decodes (JPEG/PNG);
 * [imageFilePicker] overrides that chooser in tests, which cannot drive
 * a native dialog.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DesktopPhotoEditor(
    source: Path,
    onClose: (PhotoEditorResult) -> Unit,
    modifier: Modifier = Modifier,
    outputDirectory: Path? = null,
    closeRequest: Int = 0,
    renderPreference: PhotoRenderPreference = PhotoRenderPreference.Auto,
    imageFilePicker: ((Window?) -> Path?)? = null,
    onError: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestOnError by rememberUpdatedState(onError)
    val session = remember(source, outputDirectory) {
        PhotoEditorSession(
            scope = scope,
            media = DesktopPhotoMediaAccess(
                outputDirectory = outputDirectory,
                onError = { latestOnError(it) }
            ),
            onError = { latestOnError(it) }
        )
    }

    // Project persistence: the last committed recipe lives in one file
    // per source under ~/.refra-projects; the recents index maps it back
    // for the workbench's reopen menu. Draft gestures and the opening
    // transient are never persisted — only committed documents.
    val projectStore = remember { desktopProjectStore() }
    val recents = remember(projectStore) { RecentProjects(projectStore) }
    val reference = source.toString()
    val projectFile = remember(reference) {
        projectFileName("photo", reference)
    }
    val persist: (PhotoEdits) -> Unit = remember(
        projectStore, recents, projectFile, reference
    ) {
        { edits ->
            try {
                if (edits == PhotoEdits()) {
                    // A reset document is not a project — remove the file
                    // and its index entry rather than restore nothing.
                    projectStore.delete(projectFile)
                    recents.remove(reference)
                } else {
                    projectStore.write(
                        projectFile,
                        PhotoProjectCodec.encode(PhotoProjectSnapshot(edits))
                    )
                    recents.record(
                        RecentProjects.Entry(
                            name = source.fileName?.toString() ?: reference,
                            reference = reference,
                            projectFile = projectFile
                        )
                    )
                }
            } catch (error: Exception) {
                latestOnError(error)
            }
        }
    }
    val autosave = remember(session, persist) {
        ProjectAutosave<PhotoEditorState, PhotoEdits>(
            scope = scope,
            state = session.state,
            snapshot = { state ->
                if (state.opening || state.isDrafting) null
                else state.document?.toEdits()
            },
            persist = persist
        ).also { it.start() }
    }

    // Open with the persisted recipe when one exists: a missing/corrupt
    // file opens clean and reports the restore failure after the open
    // lands (reporting mid-open would be wiped by the state reset); a
    // recipe whose layer media is gone becomes the session's recovery
    // state — never silently flattened.
    LaunchedEffect(session, source) {
        var restored: PhotoEdits? = null
        var failure: Throwable? = null
        try {
            projectStore.read(projectFile)?.let { json ->
                restored = PhotoProjectCodec.decode(json).edits
            }
        } catch (error: Exception) {
            failure = IllegalArgumentException(
                "persisted photo project is corrupt", error
            )
        }
        session.open(reference, restoredEdits = restored)
        failure?.let {
            session.state.first { state -> !state.opening }
            session.reportRestoreFailure(it)
        }
    }
    DisposableEffect(autosave) {
        onDispose {
            autosave.cancel()
            // The composition scope is already going away — land the last
            // committed document synchronously instead of losing it to
            // the debounce window.
            val state = session.state.value
            if (!state.opening && !state.isDrafting) {
                state.document?.toEdits()?.let(persist)
            }
            session.close()
        }
    }

    // The Open affordance only exists when the platform can actually open
    // the exported file — no dead button.
    val canOpenExport = remember {
        runCatching {
            Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
        }.getOrDefault(false)
    }
    var openFailed by remember { mutableStateOf(false) }

    // The AWT window parents the modal FileDialog; a non-Frame owner
    // (Dialog/undecorated) falls back to the shared frame.
    val windowOwner = LocalAwtWindow.current
    val pickImage = imageFilePicker ?: ::pickLayerImageFile

    PhotoEditorScreen(
        session = session,
        onClose = {
            // `exported` always names the last published copy — a failed or
            // cancelled second export never erases the first save.
            val exported = session.state.value.exported
            onClose(
                exported?.let(PhotoEditorResult::Saved)
                    ?: PhotoEditorResult.Cancelled
            )
        },
        onRetryOpen = { session.open(source.toString()) },
        modifier = modifier,
        onOpenExport = if (canOpenExport) {
            { result ->
                try {
                    Desktop.getDesktop().open(
                        Path.of(URI.create(result.reference)).toFile()
                    )
                } catch (error: Exception) {
                    latestOnError(error)
                    openFailed = true
                }
            }
        } else {
            null
        },
        closeRequest = closeRequest,
        renderPreference = renderPreference,
        onRequestAddImage = {
            pickImage(windowOwner)?.let { path ->
                session.addImageLayer(path.toString())
            }
        },
        // A missing layer source relinks through the same image picker —
        // the replacement keeps the layer id, transform, blend and mask.
        onRequestRelink = { missing ->
            pickImage(windowOwner)?.let { path ->
                session.relinkRestore(missing, path.toString())
            }
        }
    )

    if (openFailed) {
        AlertDialog(
            onDismissRequest = { openFailed = false },
            text = {
                Text(stringResource(Res.string.editor_open_export_failed))
            },
            confirmButton = {
                TextButton(onClick = { openFailed = false }) {
                    Text(stringResource(Res.string.editor_dismiss))
                }
            }
        )
    }
}

/** The layer-image chooser — a real OS dialog filtered to JPEG/PNG. */
private fun pickLayerImageFile(owner: Window?): Path? {
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, "Add image layer", FileDialog.LOAD)
        is Dialog -> FileDialog(owner, "Add image layer", FileDialog.LOAD)
        else -> FileDialog(null as Frame?, "Add image layer", FileDialog.LOAD)
    }
    try {
        dialog.setFilenameFilter { _, name ->
            name.substringAfterLast('.', "")
                .lowercase() in LAYER_IMAGE_EXTENSIONS
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory, file)
    } finally {
        // FileDialog holds a native peer; cancel must release it too.
        dialog.dispose()
    }
}

private val LAYER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png")
