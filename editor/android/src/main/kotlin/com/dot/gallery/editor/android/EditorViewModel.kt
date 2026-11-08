package com.dot.gallery.editor.android

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.project.PhotoProjectCodec
import com.dot.gallery.editor.core.project.PhotoProjectSnapshot
import com.dot.gallery.editor.core.project.ProjectChecksum
import com.dot.gallery.editor.io.FileProjectStore
import com.dot.gallery.editor.render.ProjectAutosave
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoEditorState
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the [PhotoEditorSession] across configuration changes and persists the
 * source URI plus the committed recipe — colour, geometry and every layer
 * with its transform, blend and mask strokes — as [PhotoProjectCodec] JSON
 * under `noBackupFilesDir`, so a process-death restore reopens the photo
 * and replays the recipe as a dirty restored edit. [savedState] carries
 * only the source key and the project file's name + checksum — never a
 * bulk payload; a private file survives process death without ever leaving
 * the device in a backup. A flat recipe written by an older build still
 * restores through the legacy key set.
 *
 * [start] is idempotent: the activity calls it on every creation, but only
 * the first call opens the session. Media is never opened from [init] —
 * intent extras flow into [savedState], so an untrusted caller could plant
 * `editor.source` and have it opened unchecked. The validated intent data
 * is the only authority; persisted state counts only on a genuine restore
 * naming the same source.
 */
class EditorViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    /** Set by the host activity; defaults to a logger so nothing is lost. */
    @Volatile
    var errorReporter: (Throwable) -> Unit = { error ->
        Logger.getLogger(TAG).log(Level.WARNING, "photo editor error", error)
    }

    private val media = AndroidPhotoMediaAccess(
        context = application,
        onError = { errorReporter(it) }
    )

    val session = PhotoEditorSession(
        scope = viewModelScope,
        media = media,
        onError = { errorReporter(it) }
    )

    private val projectStore = FileProjectStore(
        File(application.noBackupFilesDir, "editor")
    )

    private val autosave = ProjectAutosave<PhotoEditorState, PhotoEdits>(
        scope = viewModelScope,
        state = session.state,
        snapshot = { state ->
            // Only committed recipes persist — mid-gesture draft values
            // would otherwise bake half an edit into a restored session.
            if (state.opening || state.isDrafting) null
            else state.document?.toEdits()
        },
        persist = { edits -> persistProject(edits) }
    )

    @Volatile
    private var started = false

    /**
     * A persisted-state *format* failure on a genuine restore — reported
     * through [PhotoEditorSession.reportRestoreFailure] once the source
     * is open, because calling it before `open()` would be wiped by the
     * opening state reset.
     */
    private var pendingRestoreFailure: Throwable? = null

    init {
        autosave.start()
        viewModelScope.launch {
            session.state.collect { state ->
                if (state.opening || state.document == null) return@collect
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
     * instance state. Persisted recipe keys are trusted only then, and only
     * when the persisted source names the same URI as this request;
     * anything else an external caller planted in the extras is dropped
     * before it can act as an authority.
     */
    fun start(source: Uri, persistable: Boolean, restoring: Boolean) {
        if (started) return
        started = true
        val reference = source.toString()
        val plantedSource = savedString(KEY_SOURCE)
        val restoredEdits = if (restoring && plantedSource == reference) {
            readRestoredEdits()
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
        // A new open re-persists from scratch — the autosave's write-
        // through memo must not carry a previous document's identity
        // into this session (the old file was just cleared).
        autosave.reset()
        session.open(reference, restoredEdits = restoredEdits)
    }

    /** Retries opening the persisted source after an open failure. */
    fun retryOpen() {
        savedString(KEY_SOURCE)?.let(session::open)
    }

    /**
     * Forces the pending autosave to land — called by the host on stop
     * so the last committed edit is not left inside the debounce window
     * when the process can be killed.
     */
    fun flushState() {
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
     * Writes the committed recipe through the atomic store, then publishes
     * the file's name + checksum into [savedState]. A default recipe —
     * the user reset everything — deletes the file and markers so a
     * restore opens clean rather than replaying a stale edit. A failed
     * write is reported and leaves the previous file authoritative.
     */
    private suspend fun persistProject(edits: PhotoEdits) {
        try {
            if (edits == PhotoEdits()) {
                withContext(Dispatchers.IO) {
                    projectStore.delete(PROJECT_FILE)
                }
                savedState.remove<String>(KEY_PROJECT)
                savedState.remove<String>(KEY_PROJECT_CHECKSUM)
                return
            }
            val json = PhotoProjectCodec.encode(PhotoProjectSnapshot(edits))
            withContext(Dispatchers.IO) {
                projectStore.write(PROJECT_FILE, json)
            }
            savedState[KEY_PROJECT] = PROJECT_FILE
            savedState[KEY_PROJECT_CHECKSUM] = ProjectChecksum.of(json)
        } catch (error: Exception) {
            errorReporter(error)
        }
    }

    /** Extras of the wrong type are caller-controlled data, not a crash. */
    private fun savedString(key: String): String? =
        try {
            savedState.get<String>(key)
        } catch (error: RuntimeException) {
            errorReporter(error)
            null
        }

    /**
     * The committed recipe currently held in [savedState] — exactly what a
     * process-death restore would replay. Instrumentation surface for the
     * "uncommitted drafts never persist" contract; not used by the app.
     */
    internal fun persistedEdits(): PhotoEdits? = readRestoredEdits()

    /**
     * Reads the committed project file. The marker names the file and
     * its checksum — a missing, truncated or undecodable file is an
     * explicit restore failure surfaced once the source opens, never a
     * silently dropped recipe.
     */
    private fun readRestoredProject(): PhotoEdits? {
        val json = try {
            projectStore.read(PROJECT_FILE)
        } catch (error: Exception) {
            val failure = IllegalArgumentException(
                "persisted photo project is unreadable", error
            )
            pendingRestoreFailure = failure
            errorReporter(failure)
            return null
        }
        val failure = when {
            json == null -> IllegalArgumentException(
                "persisted photo project is missing"
            )
            savedState.get<String>(KEY_PROJECT_CHECKSUM)?.let {
                it != ProjectChecksum.of(json)
            } == true -> IllegalArgumentException(
                "persisted photo project failed its checksum"
            )
            else -> null
        }
        if (failure != null) {
            pendingRestoreFailure = failure
            errorReporter(failure)
            return null
        }
        val snapshot = try {
            PhotoProjectCodec.decode(requireNotNull(json))
        } catch (error: Exception) {
            val decodeFailure = IllegalArgumentException(
                "persisted photo project is corrupt", error
            )
            pendingRestoreFailure = decodeFailure
            errorReporter(decodeFailure)
            return null
        }
        return snapshot.edits.takeIf { it != PhotoEdits() }
    }

    private fun readRestoredEdits(): PhotoEdits? {
        // The project marker wins over any stray legacy keys — the file
        // was the last committed recipe's form.
        if (savedState.contains(KEY_PROJECT)) {
            return readRestoredProject()
        }
        // A pre-project recipe written by an older build still restores.
        if (!savedState.contains(KEY_EXPOSURE)) return null
        val edits = try {
            PhotoEdits(
                color = ColorAdjustments(
                    exposureStops = savedState.get<Float>(KEY_EXPOSURE) ?: 0f,
                    contrast = savedState.get<Float>(KEY_CONTRAST) ?: 1f,
                    saturation = savedState.get<Float>(KEY_SATURATION) ?: 1f,
                    highlights = savedState.get<Float>(KEY_HIGHLIGHTS) ?: 0f,
                    shadows = savedState.get<Float>(KEY_SHADOWS) ?: 0f,
                    whites = savedState.get<Float>(KEY_WHITES) ?: 0f,
                    blacks = savedState.get<Float>(KEY_BLACKS) ?: 0f
                ),
                geometry = PhotoGeometry(
                    crop = NormalizedCrop(
                        left = savedState.get<Double>(KEY_CROP_LEFT) ?: 0.0,
                        top = savedState.get<Double>(KEY_CROP_TOP) ?: 0.0,
                        right = savedState.get<Double>(KEY_CROP_RIGHT) ?: 1.0,
                        bottom = savedState.get<Double>(KEY_CROP_BOTTOM) ?: 1.0
                    ),
                    quarterTurns = savedState.get<Int>(KEY_TURNS) ?: 0,
                    flipHorizontal = savedState.get<Boolean>(KEY_FLIP_H) ?: false,
                    flipVertical = savedState.get<Boolean>(KEY_FLIP_V) ?: false,
                    warp = PhotoWarp(
                        rotationDegrees =
                            savedState.get<Float>(KEY_ROTATION) ?: 0f,
                        verticalPerspective =
                            savedState.get<Float>(KEY_PERSPECTIVE_V) ?: 0f,
                        horizontalPerspective =
                            savedState.get<Float>(KEY_PERSPECTIVE_H) ?: 0f,
                        scale = savedState.get<Float>(KEY_WARP_SCALE) ?: 1f
                    )
                )
            )
        } catch (error: RuntimeException) {
            // Persisted state arrived from a caller's extras — malformed
            // recipe types report once and open the source unmodified.
            errorReporter(error)
            return null
        }
        // A recipe identical to the default isn't a restore — opening clean
        // keeps the new session un-dirty.
        return edits.takeIf { it != PhotoEdits() }
    }

    private fun clearPersistedState() {
        savedState.remove<String>(KEY_SOURCE)
        savedState.remove<Float>(KEY_EXPOSURE)
        savedState.remove<Float>(KEY_CONTRAST)
        savedState.remove<Float>(KEY_SATURATION)
        savedState.remove<Float>(KEY_HIGHLIGHTS)
        savedState.remove<Float>(KEY_SHADOWS)
        savedState.remove<Float>(KEY_WHITES)
        savedState.remove<Float>(KEY_BLACKS)
        savedState.remove<Double>(KEY_CROP_LEFT)
        savedState.remove<Double>(KEY_CROP_TOP)
        savedState.remove<Double>(KEY_CROP_RIGHT)
        savedState.remove<Double>(KEY_CROP_BOTTOM)
        savedState.remove<Int>(KEY_TURNS)
        savedState.remove<Boolean>(KEY_FLIP_H)
        savedState.remove<Boolean>(KEY_FLIP_V)
        savedState.remove<Float>(KEY_ROTATION)
        savedState.remove<Float>(KEY_PERSPECTIVE_V)
        savedState.remove<Float>(KEY_PERSPECTIVE_H)
        savedState.remove<Float>(KEY_WARP_SCALE)
        savedState.remove<String>(KEY_PROJECT)
        savedState.remove<String>(KEY_PROJECT_CHECKSUM)
        // The stale file leaves with its markers — a stray project must
        // not outlive the session that wrote it.
        projectStore.delete(PROJECT_FILE)
        // A fresh launch owns no receipt either — a caller could plant
        // handledExport to suppress the auto-finish of a later save.
        savedState.remove<String>(KEY_HANDLED_EXPORT)
    }

    private companion object {
        const val TAG = "editor.photo"
        const val KEY_SOURCE = "editor.source"
        const val KEY_EXPOSURE = "editor.exposure"
        const val KEY_CONTRAST = "editor.contrast"
        const val KEY_SATURATION = "editor.saturation"
        const val KEY_HIGHLIGHTS = "editor.highlights"
        const val KEY_SHADOWS = "editor.shadows"
        const val KEY_WHITES = "editor.whites"
        const val KEY_BLACKS = "editor.blacks"
        const val KEY_CROP_LEFT = "editor.cropLeft"
        const val KEY_CROP_TOP = "editor.cropTop"
        const val KEY_CROP_RIGHT = "editor.cropRight"
        const val KEY_CROP_BOTTOM = "editor.cropBottom"
        const val KEY_TURNS = "editor.turns"
        const val KEY_FLIP_H = "editor.flipH"
        const val KEY_FLIP_V = "editor.flipV"
        const val KEY_ROTATION = "editor.rotationDegrees"
        const val KEY_PERSPECTIVE_V = "editor.verticalPerspective"
        const val KEY_PERSPECTIVE_H = "editor.horizontalPerspective"
        const val KEY_WARP_SCALE = "editor.warpScale"
        const val KEY_PROJECT = "editor.project"
        const val KEY_PROJECT_CHECKSUM = "editor.projectChecksum"
        const val PROJECT_FILE = "photo-project.json"
        const val KEY_HANDLED_EXPORT = "editor.handledExport"
    }
}
