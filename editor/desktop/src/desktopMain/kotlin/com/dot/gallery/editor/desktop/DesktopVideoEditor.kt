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
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.project.VideoProjectCodec
import com.dot.gallery.editor.core.project.VideoProjectSnapshot
import com.dot.gallery.editor.io.RecentProjects
import com.dot.gallery.editor.render.ProjectAutosave
import com.dot.gallery.editor.render.video.VideoEditorResult
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_open_export_failed
import com.dot.gallery.editor.ui.video.VideoEditorScreen
import java.awt.Desktop
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/**
 * Embeds the video editor in a Compose desktop window. Owns a
 * [VideoEditorSession] over [DesktopVideoMediaAccess] with the packaged
 * FFmpeg decoder/exporter and a JavaSound preview sink: the session's
 * identity is the `(source, outputDirectory)` pair — passing a different
 * source abandons the old session entirely, so the host is expected to
 * confirm unsaved edits before switching input. The session is closed on
 * disposal.
 *
 * [onClose] fires when the user is done editing; it receives the latest
 * [VideoEditorResult.Saved] copy or [VideoEditorResult.Cancelled]. Saving a
 * copy does not close the editor — normal desktop editing continues.
 * [outputDirectory] overrides where saved copies land (source directory by
 * default). [closeRequest] is the window-close bridge: increment it (e.g.
 * from `Window(onCloseRequest = { requests++ })`) to run the editor's own
 * dirty/discard flow instead of closing blind. The host supplies the
 * surrounding theme — use [EditorTheme] standalone.
 *
 * [mediaFilePicker] overrides the add-media chooser in tests, which cannot
 * drive the native [FileDialog]; the default is a real OS dialog filtered
 * to video containers, multi-select. [audioFilePicker] is the same seam
 * for audio containers.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DesktopVideoEditor(
    source: Path,
    onClose: (VideoEditorResult) -> Unit,
    modifier: Modifier = Modifier,
    outputDirectory: Path? = null,
    closeRequest: Int = 0,
    mediaFilePicker: ((Window?) -> List<Path>)? = null,
    audioFilePicker: ((Window?) -> List<Path>)? = null,
    onError: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestOnError by rememberUpdatedState(onError)
    val session = remember(source, outputDirectory) {
        val media = DesktopVideoMediaAccess(
            outputDirectory = outputDirectory,
            onError = { latestOnError(it) }
        )
        VideoEditorSession(
            scope = scope,
            media = media,
            playback = media.createPlayback(Dispatchers.IO),
            voiceRecorder = JavaSoundVoiceRecorder(
                onError = { latestOnError(it) }
            ),
            onError = { latestOnError(it) }
        )
    }

    // Project persistence: the last committed document — sources, tracks,
    // clip recipes, audio lanes and DSP — lives in one file per source
    // under ~/.refra-projects; the recents index maps it back for the
    // workbench's reopen menu. Draft gestures and the opening transient
    // are never persisted — only committed documents.
    val projectStore = remember { desktopProjectStore() }
    val recents = remember(projectStore) { RecentProjects(projectStore) }
    val reference = source.toString()
    val projectFile = remember(reference) {
        projectFileName("video", reference)
    }
    // The autosave key is (sequence, selection) only — the position
    // republishes at playback rate and is captured fresh inside the
    // write, the same contract the Android host uses.
    val persist: (PersistKey) -> Unit = remember(
        projectStore, recents, projectFile, reference
    ) {
        { key ->
            try {
                projectStore.write(
                    projectFile,
                    VideoProjectCodec.encode(
                        VideoProjectSnapshot(
                            sequence = key.sequence,
                            selectedClipId = key.selectedClipId,
                            position = session.state.value.position
                        )
                    )
                )
                recents.record(
                    RecentProjects.Entry(
                        name = source.fileName?.toString() ?: reference,
                        reference = reference,
                        projectFile = projectFile
                    )
                )
            } catch (error: Exception) {
                latestOnError(error)
            }
        }
    }
    val autosave = remember(session, persist) {
        ProjectAutosave<VideoEditorState, PersistKey>(
            scope = scope,
            state = session.state,
            snapshot = { state ->
                if (state.opening || state.isDrafting) null
                else state.sequence?.let {
                    PersistKey(it, state.selectedClipId)
                }
            },
            persist = persist
        ).also { it.start() }
    }

    // Open with the persisted project when one exists: the snapshot is
    // applied once the base source lands — a missing referenced source
    // becomes the session's recovery state, never silently flattened;
    // a corrupt file opens clean and reports the restore failure.
    LaunchedEffect(session, source) {
        var snapshot: VideoProjectSnapshot? = null
        var failure: Throwable? = null
        try {
            projectStore.read(projectFile)?.let { json ->
                snapshot = VideoProjectCodec.decode(json)
            }
        } catch (error: Exception) {
            failure = IllegalArgumentException(
                "persisted video project is corrupt", error
            )
        }
        session.open(reference)
        session.state.first { state -> !state.opening }
        if (session.state.value.sequence != null) {
            snapshot?.let {
                session.restoreProject(
                    it.sequence, it.selectedClipId, it.position
                )
            }
            failure?.let(session::reportRestoreFailure)
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
                state.sequence?.let {
                    persist(PersistKey(it, state.selectedClipId))
                }
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
    val pickMedia = mediaFilePicker ?: ::pickVideoFiles
    val pickAudio = audioFilePicker ?: ::pickAudioFiles

    VideoEditorScreen(
        session = session,
        onClose = {
            // `exported` always names the last published copy — a failed or
            // cancelled second export never erases the first save.
            val exported = session.state.value.exported
            onClose(
                exported?.let(VideoEditorResult::Saved)
                    ?: VideoEditorResult.Cancelled
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
        onRequestAddMedia = { insertion ->
            for (path in pickMedia(windowOwner)) {
                session.addVideoSource(path.toString(), insertion)
            }
        },
        onRequestAddAudio = {
            for (path in pickAudio(windowOwner)) {
                session.addAudioSource(path.toString())
            }
        },
        // JavaSound has no runtime prompt — the mic gate is capability,
        // so Continue just re-queries support.
        onRequestMicrophone = { session.refreshVoicePermission() },
        // A missing source can be video or audio — one dialog accepting
        // both keeps the relink honest; the replacement keeps the source
        // id so every clip stays in place.
        onRequestRelink = { missing ->
            pickAnyMedia(windowOwner)?.let { path ->
                session.relinkRestore(missing, path.toString())
            }
        },
        closeRequest = closeRequest
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

/** The add-media chooser — a real OS dialog filtered to video containers. */
private fun pickVideoFiles(owner: Window?): List<Path> {
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, "Add video", FileDialog.LOAD)
        is Dialog -> FileDialog(owner, "Add video", FileDialog.LOAD)
        else -> FileDialog(null as Frame?, "Add video", FileDialog.LOAD)
    }
    try {
        dialog.setFilenameFilter { _, name ->
            name.substringAfterLast('.', "")
                .lowercase() in VIDEO_EXTENSIONS
        }
        dialog.isMultipleMode = true
        dialog.isVisible = true
        return dialog.files.map { it.toPath() }
    } finally {
        // FileDialog holds a native peer; cancel must release it too.
        dialog.dispose()
    }
}

/** The add-audio chooser — a real OS dialog filtered to audio containers. */
private fun pickAudioFiles(owner: Window?): List<Path> {
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, "Add audio", FileDialog.LOAD)
        is Dialog -> FileDialog(owner, "Add audio", FileDialog.LOAD)
        else -> FileDialog(null as Frame?, "Add audio", FileDialog.LOAD)
    }
    try {
        dialog.setFilenameFilter { _, name ->
            name.substringAfterLast('.', "")
                .lowercase() in AUDIO_EXTENSIONS
        }
        dialog.isMultipleMode = true
        dialog.isVisible = true
        return dialog.files.map { it.toPath() }
    } finally {
        // FileDialog holds a native peer; cancel must release it too.
        dialog.dispose()
    }
}

/** The relink chooser — video and audio, since either can go missing. */
private fun pickAnyMedia(owner: Window?): Path? {
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, "Relink media", FileDialog.LOAD)
        is Dialog -> FileDialog(owner, "Relink media", FileDialog.LOAD)
        else -> FileDialog(null as Frame?, "Relink media", FileDialog.LOAD)
    }
    try {
        dialog.setFilenameFilter { _, name ->
            name.substringAfterLast('.', "")
                .lowercase() in VIDEO_EXTENSIONS + AUDIO_EXTENSIONS
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory, file)
    } finally {
        // FileDialog holds a native peer; cancel must release it too.
        dialog.dispose()
    }
}

/**
 * What the autosave treats as "the document changed" — the sequence and
 * its selection. The position is captured inside the write instead, so
 * the playback tick never queues a project write.
 */
private data class PersistKey(
    val sequence: VideoSequence,
    val selectedClipId: String?
)

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mov", "qt", "3gp", "3g2", "mkv", "webm", "avi", "wmv", "asf",
    "flv", "f4v", "mpg", "mpeg", "mpe", "m2v", "ts", "mts", "m2ts", "vob", "ogv",
    "mxf", "dv", "rm", "rmvb", "divx"
)
private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma"
)
