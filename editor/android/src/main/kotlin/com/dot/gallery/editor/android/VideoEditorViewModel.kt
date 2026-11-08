package com.dot.gallery.editor.android

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.project.ProjectChecksum
import com.dot.gallery.editor.core.project.VideoProjectCodec
import com.dot.gallery.editor.core.project.VideoProjectSnapshot
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.io.FileProjectStore
import com.dot.gallery.editor.render.ProjectAutosave
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoEditorState
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the [VideoEditorSession] across configuration changes and persists
 * the source URI plus the committed edit document — every source, track,
 * clip recipe and audio lane — as [VideoProjectCodec] JSON through the
 * atomic store under `noBackupFilesDir`, so a process-death restore
 * reopens the video and replays the edit as a dirty restored step.
 * [savedState] carries only the source key and the file's name + checksum —
 * never a bulk payload; a private file survives process death without
 * ever leaving the device in a backup. A packed cut array written by an
 * older build still restores through the legacy key set.
 *
 * [start] is idempotent: the activity calls it on every creation, but only
 * the first call opens the session. Media is never opened from [init] —
 * intent extras flow into [savedState], so an untrusted caller could plant
 * `editor.video.source` and have it opened unchecked. The validated intent
 * data is the only authority; persisted state counts only on a genuine
 * restore naming the same source.
 */
class VideoEditorViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    /** Set by the host activity; defaults to a logger so nothing is lost. */
    @Volatile
    var errorReporter: (Throwable) -> Unit = { error ->
        Logger.getLogger(TAG).log(Level.WARNING, "video editor error", error)
    }

    private val media = AndroidVideoMediaAccess(
        context = application,
        onError = { errorReporter(it) }
    )

    // The focus-loss callback fires before the session property exists; the
    // indirection lets playback reach the owner without a global DI slot.
    private var sessionRef: VideoEditorSession? = null

    val session = VideoEditorSession(
        scope = viewModelScope,
        media = media,
        playback = media.createPlayback(
            workerDispatcher = Dispatchers.IO,
            onFocusLost = { viewModelScope.launch { sessionRef?.pause() } }
        ),
        voiceRecorder = AndroidVoiceRecorder(
            context = application,
            onError = { errorReporter(it) }
        ),
        onError = { errorReporter(it) }
    ).also { sessionRef = it }

    @Volatile
    private var started = false

    /** A pending restore applies once, after the source has opened. */
    private class PendingRestore(
        val cuts: List<TimeRange>,
        val project: VideoProjectSnapshot?,
        val selectedIndex: Int,
        val position: EditorTime
    )

    private var pendingRestore: PendingRestore? = null

    /**
     * A persisted-state *format* failure on a genuine restore — reported
     * through [VideoEditorSession.reportRestoreFailure] once the source is
     * open, because calling it before `open()` would be wiped by the
     * opening state reset.
     */
    private var pendingRestoreFailure: Throwable? = null

    private val projectStore = FileProjectStore(
        File(application.noBackupFilesDir, "editor")
    )

    /**
     * What decides whether a project write is needed — the document and
     * its selection. The position is deliberately excluded: it republishes
     * at playback rate and is captured fresh inside the write instead.
     */
    private data class PersistKey(
        val sequence: VideoSequence,
        val selectedClipId: String?
    )

    private val autosave = ProjectAutosave<VideoEditorState, PersistKey>(
        scope = viewModelScope,
        state = session.state,
        snapshot = { state ->
            // Drafts are transient — a live trim gesture must never become
            // the restored document. Committed edits only.
            if (state.opening || state.isDrafting) null
            else state.sequence?.let { PersistKey(it, state.selectedClipId) }
        },
        persist = { key -> persistProject(key) }
    )

    init {
        autosave.start()
        viewModelScope.launch {
            session.state.collect { state ->
                if (state.opening || state.sequence == null) {
                    return@collect
                }
                val pending = pendingRestore
                if (pending != null) {
                    // The whole-source sequence must never overwrite the
                    // persisted document before the restore is applied.
                    pendingRestore = null
                    val project = pending.project
                    if (project != null) {
                        session.restoreProject(
                            project.sequence,
                            project.selectedClipId,
                            pending.position
                        )
                    } else {
                        session.restoreCuts(
                            pending.cuts,
                            pending.selectedIndex,
                            pending.position
                        )
                    }
                    return@collect
                }
                pendingRestoreFailure?.let { failure ->
                    pendingRestoreFailure = null
                    session.reportRestoreFailure(failure)
                }
            }
        }
    }

    /**
     * Opens [source] — the activity's already-validated intent data —
     * unless a session is already started, so re-delivered intents cannot
     * lose the current edits.
     *
     * [restoring] is true only when the activity was recreated with prior
     * instance state. Persisted cut keys are trusted only then, and only
     * when the persisted source names the same URI as this request;
     * anything else an external caller planted in the extras is dropped
     * before it can act as an authority.
     */
    fun start(source: Uri, persistable: Boolean, restoring: Boolean) {
        if (started) return
        started = true
        val reference = source.toString()
        val plantedSource = savedString(KEY_SOURCE)
        val restored = if (restoring && plantedSource == reference) {
            readRestored()
        } else {
            clearPersistedState()
            null
        }
        if (source.scheme != ContentResolver.SCHEME_CONTENT) {
            errorReporter(
                IllegalArgumentException("Only content:// media can be edited")
            )
            return
        }
        if (persistable) {
            try {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(
                        source, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            } catch (error: SecurityException) {
                // A transient grant still lets this session read; log and go on.
                errorReporter(error)
            }
        }
        savedState[KEY_SOURCE] = reference
        pendingRestore = restored
        // A new open re-persists from scratch — the autosave's write-
        // through memo must not carry a previous document's identity
        // into this session (the old file was just cleared).
        autosave.reset()
        session.open(reference)
    }

    /** Retries opening the persisted source after an open failure. */
    fun retryOpen() {
        savedString(KEY_SOURCE)?.let(session::open)
    }

    /**
     * Flushes the pending autosave and writes the current position into
     * [savedState] — called by the host on stop/save so a restore lands
     * the last committed document back where the user was.
     */
    fun flushState() {
        savedState[KEY_POSITION] = session.state.value.position.ticks
        viewModelScope.launch { autosave.flush() }
    }

    /**
     * The export receipt reference the activity already acted on — the
     * auto-finish fires once per *new* saved copy, so a later undo or a
     * cancelled re-export cannot retroactively close the editor.
     */
    val handledExportRef: String?
        get() = savedString(KEY_HANDLED_EXPORT)

    fun markExportHandled(reference: String) {
        savedState[KEY_HANDLED_EXPORT] = reference
    }

    override fun onCleared() {
        autosave.cancel()
        session.close()
    }

    /**
     * Writes the committed document through the atomic store — fsynced
     * staging + rename so a crash mid-write cannot leave a torn file —
     * then publishes the file's name + checksum into [savedState]. The
     * document comes from the autosave's snapshot so a draft that began
     * while the write was queued can never be persisted; the position is
     * read fresh. A failed write is reported and leaves the previous file
     * authoritative.
     */
    private suspend fun persistProject(key: PersistKey) {
        try {
            val json = VideoProjectCodec.encode(
                VideoProjectSnapshot(
                    sequence = key.sequence,
                    selectedClipId = key.selectedClipId,
                    position = session.state.value.position
                )
            )
            withContext(Dispatchers.IO) {
                projectStore.write(PROJECT_FILE, json)
            }
            savedState[KEY_PROJECT] = PROJECT_FILE
            savedState[KEY_PROJECT_CHECKSUM] = ProjectChecksum.of(json)
        } catch (error: Exception) {
            errorReporter(error)
        }
    }

    /** Drops the project file + markers on a fresh launch. */
    private fun clearProjectFile() {
        savedState.remove<String>(KEY_PROJECT)
        savedState.remove<String>(KEY_PROJECT_CHECKSUM)
        projectStore.delete(PROJECT_FILE)
    }

    private fun readRestored(): PendingRestore? {
        // The project marker wins over any stray cuts payload — the file
        // was the last committed document's form.
        if (savedState.get<String>(KEY_PROJECT) != null) {
            return readRestoredProject()
        }
        val raw = try {
            savedState.get<LongArray>(KEY_CUTS)
        } catch (error: RuntimeException) {
            // A genuine restore's malformed payload surfaces on screen, not
            // just in the log — the source still opens unmodified.
            pendingRestoreFailure = error
            errorReporter(error)
            return null
        } ?: return null
        if (raw.size % 2 != 0 || raw.size / 2 > MAX_RESTORED_SEGMENTS) {
            val malformed =
                IllegalArgumentException("persisted cut list is malformed")
            pendingRestoreFailure = malformed
            errorReporter(malformed)
            return null
        }
        // An empty packed array is a real document — "delete every clip" —
        // not a missing payload; the session validates index/position.
        val cuts = try {
            (0 until raw.size / 2).map { TimeRange(
                EditorTime(raw[it * 2]), EditorTime(raw[it * 2 + 1])
            ) }
        } catch (error: RuntimeException) {
            pendingRestoreFailure = error
            errorReporter(error)
            return null
        }
        val selected = savedState.get<Int>(KEY_SELECTED_INDEX) ?: -1
        val position = EditorTime(savedState.get<Long>(KEY_POSITION) ?: 0L)
        return PendingRestore(cuts, null, selected, position)
    }

    /**
     * Reads the committed project file. A missing/corrupt file is an
     * explicit restore failure — the source still opens, and the session's
     * own restore probes every referenced source, so missing media fails
     * there rather than flattening the document to what is readable.
     * The position prefers the stop-time [KEY_POSITION] flush (fresher
     * than the file's write-time value), falling back to the snapshot's.
     */
    private fun readRestoredProject(): PendingRestore? {
        val json = try {
            projectStore.read(PROJECT_FILE)
        } catch (error: Exception) {
            val failure = IllegalArgumentException(
                "persisted video project is unreadable", error
            )
            pendingRestoreFailure = failure
            errorReporter(failure)
            return null
        }
        val failure = when {
            json == null -> IllegalArgumentException(
                "persisted video project is missing"
            )
            savedState.get<String>(KEY_PROJECT_CHECKSUM)?.let {
                it != ProjectChecksum.of(json)
            } == true -> IllegalArgumentException(
                "persisted video project failed its checksum"
            )
            else -> null
        }
        if (failure != null) {
            pendingRestoreFailure = failure
            errorReporter(failure)
            return null
        }
        val snapshot = try {
            VideoProjectCodec.decode(requireNotNull(json))
        } catch (error: Exception) {
            val decodeFailure = IllegalArgumentException(
                "persisted video project is corrupt", error
            )
            pendingRestoreFailure = decodeFailure
            errorReporter(decodeFailure)
            return null
        }
        val position = savedState.get<Long>(KEY_POSITION)?.let(::EditorTime)
            ?: snapshot.position
        return PendingRestore(
            cuts = emptyList(),
            project = snapshot.copy(position = position),
            selectedIndex = -1,
            position = position
        )
    }

    /** Extras of the wrong type are caller-controlled data, not a crash. */
    private fun savedString(key: String): String? =
        try {
            savedState.get<String>(key)
        } catch (error: RuntimeException) {
            errorReporter(error)
            null
        }

    private fun clearPersistedState() {
        savedState.remove<String>(KEY_SOURCE)
        savedState.remove<LongArray>(KEY_CUTS)
        savedState.remove<Int>(KEY_SELECTED_INDEX)
        savedState.remove<Long>(KEY_POSITION)
        clearProjectFile()
        // Owned receipt metadata too — a fresh launch trusts nothing the
        // caller planted, including a handledExport that would suppress a
        // later save's auto-finish.
        savedState.remove<String>(KEY_HANDLED_EXPORT)
    }

    private companion object {
        const val TAG = "editor.video"
        const val KEY_SOURCE = "editor.video.source"
        const val KEY_CUTS = "editor.video.cuts"
        const val KEY_PROJECT = "editor.video.project"
        const val KEY_PROJECT_CHECKSUM = "editor.video.projectChecksum"
        const val PROJECT_FILE = "video-project.json"
        const val KEY_SELECTED_INDEX = "editor.video.selectedIndex"
        const val KEY_POSITION = "editor.video.positionTicks"
        const val KEY_HANDLED_EXPORT = "editor.video.handledExport"
        const val MAX_RESTORED_SEGMENTS = 10_000
    }
}
