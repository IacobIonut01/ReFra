package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.history.EditHistory
import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.project.VideoProjectSnapshot
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * UI-facing controller for the single-source video editor: owns open/probe,
 * seek/frame fetches, playback and new-copy export behind one [StateFlow] of
 * [VideoEditorState].
 *
 * All public functions are confined to the owning thread; synchronous state
 * (controls, selection, position, history flags) is published before they
 * return, and every write to [state] happens on the owner. Decode and export
 * run on [workerDispatcher]; playback callbacks arrive on the playback
 * worker tagged with the [playGeneration] they were captured under and are
 * funnelled through conflated [MutableStateFlow]s, so a frame from a
 * superseded play — which a non-cancellable engine may still emit while it
 * unwinds — can never overwrite the live one.
 *
 * The document is an immutable [VideoSequence] in an [EditHistory] — one
 * track, one source in this scope; ripple operations re-pack the track
 * consecutively from zero. Export snapshots the committed sequence (same
 * receipt semantics as the photo session: a publication callback that
 * already fired lands as success even if the call was cancelled). Playback
 * is single-flight: [playMutex] serializes the engine call so a new [play]
 * can never overlap a previous one's teardown. Frame fetches are also
 * single-flight: a per-session conflated channel collapses rapid seeks into
 * the latest request and [frameFetchMutex] makes an old native decode finish
 * before the next one starts — dragging a scrubber queues at most one
 * pending fetch, not a hundred decoders.
 */
class VideoEditorSession(
    scope: CoroutineScope,
    private val media: VideoMediaAccess,
    private val playback: VideoPlayback,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val voiceRecorder: VoiceRecorder? = null,
    private val onError: (Throwable) -> Unit
) {

    private val sessionJob = SupervisorJob(scope.coroutineContext[Job])
    private val sessionScope =
        CoroutineScope(scope.coroutineContext.minusKey(Job) + sessionJob)

    private val _state = MutableStateFlow(VideoEditorState())
    val state: StateFlow<VideoEditorState> = _state.asStateFlow()

    /**
     * Whether imported or recorded audio lands on the selected empty audio
     * track instead of a new one — the "Fill empty audio tracks" setting.
     */
    var fillSelectedEmptyAudioTrack: Boolean = true

    /** Tagged progress so a stale export cannot write into a newer state. */
    private data class ExportProgressUpdate(val generation: Long, val value: Float)

    /** What the export job recorded; read only by the owner-side cleanup. */
    private data class ExportOutcome(val generation: Long, val failure: Throwable?)

    private data class PreviewUpdate(val generation: Long, val frame: VideoFrame?)

    /** Playback worker reports, tagged by the play generation that owns them. */
    private data class FrameUpdate(val generation: Long, val frame: VideoFrame)
    private data class PositionUpdate(val generation: Long, val time: EditorTime)
    private data class LevelsUpdate(val generation: Long, val levels: VideoLevels)

    /** One frame request; only the newest is ever queued. */
    private data class SeekRequest(
        val generation: Long,
        val sequence: VideoSequence,
        val at: EditorTime
    )

    // Owner-thread confined.
    private var history: EditHistory<VideoSequence>? = null
    private var openJob: Job? = null
    private var playJob: Job? = null
    private var exportJob: Job? = null
    private var importJob: Job? = null
    private var restoreJob: Job? = null
    /**
     * A restore waiting on missing media — the snapshot stays pending so
     * [retryRestore], [discardRestore] and [relinkRestore] can replay it
     * without the host re-supplying the document.
     */
    private var pendingRestore: VideoProjectSnapshot? = null
    private var openGeneration = 0L
    private var seekGeneration = 0L
    private var playGeneration = 0L
    private var exportGeneration = 0L
    private var importGeneration = 0L
    private var clipCounter = 0
    private var sourceCounter = 0
    private var trackCounter = 0
    private var closed = false

    /** The shared linear-light compositor — preview and export agree. */
    private val compositor = VideoFrameCompositor()

    /**
     * (trackId, clipId) captured by [beginTrim] — the draft's target is
     * fixed for the whole gesture; every later [updateTrim] re-points the
     * same clip rather than re-reading whatever is selected then.
     */
    private var trimTarget: Pair<String, String>? = null

    /**
     * (trackId, clipId) captured by [beginClipEdit] — same frozen-target
     * contract as [trimTarget], for the visual-recipe draft (transform,
     * content mode, opacity, blend, adjustments). The two share the
     * history's single draft slot, so a clip edit and a trim can never be
     * open at once.
     */
    private var recipeTarget: Pair<String, String>? = null

    /**
     * Whether the shared history draft currently belongs to an audio
     * gesture (mixer fader, fade drag, routing slider) opened by
     * [beginAudioEdit] — the trim/recipe targets already prove their own
     * drafts; audio edits carry no fixed target because a mixer can
     * touch several lanes inside one gesture.
     */
    private var audioEditOpen = false

    /**
     * Filmstrip tiles for the clip strip: four instants per clip
     * ([VideoThumbnail.sampleTimes]), one sequential in-flight decode at a
     * time, keyed by (source reference, source instant) so an unaffected
     * clip keeps its tiles across edits. Bounded at [THUMBNAIL_CACHE_LIMIT]
     * decoded images total — not per clip.
     */
    private var thumbnailJob: Job? = null
    private var thumbnailGeneration = 0L
    private val thumbnailCache =
        LinkedHashMap<String, VideoThumbnail>(THUMBNAIL_CACHE_LIMIT, 0.75f, true)

    /**
     * The live voice-over take: [voiceJob] runs the count-in → capture →
     * level-poll sequence, [activeCapture] holds the open [VoiceCapture]
     * between `recorder.start` and `capture.stop`, and [voiceGeneration]
     * invalidates a stale finish after cancel/close.
     */
    private var voiceJob: Job? = null
    private var voiceGeneration = 0L
    private var activeCapture: VoiceCapture? = null

    /**
     * Waveform summaries for the audio lanes: decoded lazily per source
     * reference through `media.waveform`, LRU-bounded like the tile
     * cache. [VideoEditorState.waveforms] mirrors the decoded subset
     * keyed by audio-source id — a lane whose summary is still decoding
     * draws a flat placeholder until the peaks land.
     */
    private var waveformJob: Job? = null
    private var waveformGeneration = 0L
    private val waveformCache =
        LinkedHashMap<String, FloatArray>(WAVEFORM_CACHE_LIMIT, 0.75f, true)

    /** Serializes engine calls across play/pause/play churn. */
    private val playMutex = Mutex()

    /** Makes the blocking native frame fetch strictly single-flight. */
    private val frameFetchMutex = Mutex()

    /** Conflated request stream — the per-session seek worker. */
    private val seekRequests = Channel<SeekRequest>(Channel.CONFLATED)

    // Worker-side reports, conflated; the owner publishes.
    private val previewFrames = MutableStateFlow<PreviewUpdate?>(null)
    private val playbackFrames = MutableStateFlow<FrameUpdate?>(null)
    private val playbackPositions = MutableStateFlow<PositionUpdate?>(null)
    private val playbackLevels = MutableStateFlow<LevelsUpdate?>(null)
    private val exportProgressUpdates =
        MutableStateFlow<ExportProgressUpdate?>(null)
    private val exportOutcome = MutableStateFlow<ExportOutcome?>(null)

    init {
        sessionScope.launch {
            previewFrames.collect { update ->
                if (update != null && update.generation == seekGeneration &&
                    !closed
                ) {
                    _state.value = _state.value.copy(
                        preview = update.frame?.pixels,
                        seeking = false
                    )
                }
            }
        }
        sessionScope.launch {
            playbackFrames.collect { update ->
                // Frames feed the preview only — position comes from the
                // tagged clock updates so two writers can never jitter it.
                if (update != null && update.generation == playGeneration &&
                    !closed && _state.value.playing
                ) {
                    _state.value = _state.value.copy(
                        preview = update.frame.pixels,
                        buffering = false
                    )
                }
            }
        }
        sessionScope.launch {
            playbackPositions.collect { update ->
                if (update != null && update.generation == playGeneration &&
                    !closed && _state.value.playing
                ) {
                    _state.value = _state.value.copy(position = update.time)
                }
            }
        }
        sessionScope.launch {
            playbackLevels.collect { update ->
                if (update != null && update.generation == playGeneration &&
                    !closed && _state.value.playing
                ) {
                    _state.value = _state.value.copy(levels = update.levels)
                }
            }
        }
        sessionScope.launch {
            exportProgressUpdates.collect { update ->
                if (update != null && update.generation == exportGeneration &&
                    !closed && _state.value.exporting
                ) {
                    _state.value =
                        _state.value.copy(exportProgress = update.value)
                }
            }
        }
        // The single frame-fetch worker: latest-request wins for publishing,
        // and the mutex keeps the actual decode single-flight — a cancelled
        // native call always completes before the next one enters it.
        sessionScope.launch {
            seekRequests.consumeAsFlow().collectLatest { request ->
                try {
                    val frame = frameFetchMutex.withLock {
                        withContext(workerDispatcher) {
                            compositeFrame(request.sequence, request.at)
                        }
                    }
                    previewFrames.value = PreviewUpdate(request.generation, frame)
                } catch (e: CancellationException) {
                    // A superseded request is not an error.
                    throw e
                } catch (e: Exception) {
                    if (request.generation == seekGeneration && !closed) {
                        onError(e)
                        _state.value = _state.value.copy(
                            seeking = false,
                            error = errorKindFor(e, VideoErrorKind.Preview)
                        )
                    }
                }
            }
        }
    }

    /** Opens [reference]; ignored while exporting. */
    fun open(reference: String) {
        if (closed || _state.value.exporting) return
        openJob?.cancel()
        playJob?.cancel()
        importJob?.cancel()
        restoreJob?.cancel()
        openGeneration += 1L
        playGeneration += 1L
        seekGeneration += 1L
        importGeneration += 1L
        clearHeldUpdates()
        history = null
        val generation = openGeneration
        _state.value = VideoEditorState(opening = true)
        openJob = sessionScope.launch {
            try {
                val probed = withContext(workerDispatcher) {
                    val info = media.probe(reference)
                    val first = media.frame(reference, EditorTime.Zero, PREVIEW_MAX_EDGE)
                    info to first
                }
                val (info, first) = probed
                if (generation != openGeneration || closed) return@launch
                val sequence = sequenceFor(info.asset)
                val fresh = EditHistory(sequence)
                history = fresh
                clipCounter = 1
                sourceCounter = 1
                trackCounter = 1
                trimTarget = null
                recipeTarget = null
                audioEditOpen = false
                thumbnailCache.clear()
                waveformCache.clear()
                _state.value = VideoEditorState(
                    info = info,
                    sourceInfos = mapOf(info.asset.id to info)
                        .toPersistentMap(),
                    sequence = sequence,
                    selectedClipId = sequence.tracks.firstOrNull()?.clips?.firstOrNull()?.id,
                    position = EditorTime.Zero,
                    preview = first.pixels,
                    canUndo = fresh.canUndo,
                    canRedo = fresh.canRedo,
                    isDirty = fresh.isDirty,
                    isDrafting = fresh.isDrafting
                )
                scheduleThumbnails()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == openGeneration && !closed) {
                    onError(e)
                    _state.value = _state.value.copy(
                        opening = false,
                        error = errorKindFor(e, VideoErrorKind.Open)
                    )
                }
                return@launch
            }
            if (generation == openGeneration && !closed && _state.value.opening) {
                _state.value = _state.value.copy(opening = false)
            }
        }
    }

    fun selectClip(id: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val exists = sequence.tracks.any { t -> t.clips.any { it.id == id } }
        if (exists) {
            _state.value = _state.value.copy(selectedClipId = id)
            // A selection past the first-clips window still earns its
            // tiles — the debounced sampler decodes only cache misses.
            scheduleThumbnails()
        }
    }

    /**
     * Moves the playhead to [at] on the timeline (clamped to the sequence)
     * and fetches the frame covering it. Playing pauses first — a seek
     * during playback means scrubbing; the UI may resume on release.
     * Latest-request wins: a superseded seek can complete its decode but
     * never publishes, and at most one native decode runs at a time.
     */
    fun seek(at: EditorTime) {
        if (closed) return
        val sequence = currentSequence() ?: return
        if (_state.value.playing) pause()
        playGeneration += 1L
        // The sampler yields to interactive work; its debounce reschedules it
        // once scrubbing has actually stopped.
        thumbnailJob?.cancel()
        val target = clampPosition(sequence, at)
        _state.value = _state.value.copy(position = target, seeking = true)
        val generation = ++seekGeneration
        seekRequests.trySend(SeekRequest(generation, sequence, target))
        scheduleThumbnails()
    }

    /**
     * Starts playback from the current position. Single-flight: [playMutex]
     * serializes the engine call so a previous play's teardown always
     * finishes before the next engine run opens new audio handles.
     */
    fun play() {
        if (closed) return
        val sequence = currentSequence() ?: return
        if (_state.value.playing || _state.value.exporting ||
            sequence.duration.ticks <= 0L
        ) return
        // A pending seek must never land over a live playback frame.
        seekGeneration += 1L
        thumbnailJob?.cancel()
        playbackFrames.value = null
        playbackPositions.value = null
        playbackLevels.value = null
        val generation = ++playGeneration
        _state.value = _state.value.copy(
            playing = true, seeking = false, buffering = true
        )
        playJob = sessionScope.launch {
            var failure: Throwable? = null
            try {
                if (generation == playGeneration) {
                    playMutex.withLock {
                        // Re-check inside: a pause+play pair queued behind an
                        // unwinding run must not start a superseded engine.
                        if (generation == playGeneration) {
                            playback.play(
                                sequence,
                                _state.value.position,
                                _state.value.muted,
                                onFrame = {
                                    playbackFrames.value =
                                        FrameUpdate(generation, it)
                                },
                                onPosition = {
                                    playbackPositions.value =
                                        PositionUpdate(generation, it)
                                },
                                onLevels = {
                                    playbackLevels.value =
                                        LevelsUpdate(generation, it)
                                }
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            if (generation == playGeneration && !closed) {
                failure?.let {
                    onError(it)
                    _state.value = _state.value.copy(
                        error = errorKindFor(it, VideoErrorKind.Playback)
                    )
                }
                if (failure == null) {
                    // The engine's final onPosition(duration) rides the
                    // conflated flow and is dropped once `playing` flips —
                    // land the end position synchronously instead. A failure
                    // or cancel keeps wherever the playhead actually is.
                    _state.value =
                        _state.value.copy(position = sequence.duration)
                }
                _state.value = _state.value.copy(
                    playing = false, buffering = false,
                    levels = VideoLevels()
                )
            }
        }
    }

    /** Stops playback immediately; the job winds down on its own. */
    fun pause() {
        if (closed || !_state.value.playing) return
        _state.value = _state.value.copy(
            playing = false, buffering = false, levels = VideoLevels()
        )
        playGeneration += 1L
        playJob?.cancel()
        // Pausing frees the decoder path — resume the bounded sampler.
        scheduleThumbnails()
    }

    /** Preview preference only — the export always keeps the source audio. */
    fun setMuted(muted: Boolean) {
        if (closed || _state.value.muted == muted) return
        val wasPlaying = _state.value.playing
        if (wasPlaying) pause()
        _state.value = _state.value.copy(muted = muted)
        if (wasPlaying) play()
    }

    /** Ripple-trims the selected clip to [sourceRange] in source time. */
    fun trimSelected(sourceRange: TimeRange) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val target = selectedTarget(sequence) ?: return
        applyEdit("Trim clip") {
            it.rippleTrimClip(target.first, target.second, sourceRange)
        }
    }

    /**
     * Opens a live trim draft on the selected clip: playback pauses, the
     * (trackId, clipId) target is captured once, and [updateTrim]/
     * [commitTrim]/[cancelTrim] ride the shared [EditHistory] draft seam —
     * the whole gesture lands as a single undoable step. While drafting,
     * filmstrip sampling sleeps; the source preview keeps refreshing
     * through the conflated seek worker so the dragged endpoint is live.
     */
    fun beginTrim() {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        val sequence = h.current
        val target = selectedTarget(sequence) ?: return
        pause()
        thumbnailJob?.cancel()
        trimTarget = target
        h.begin("Trim clip")
        // Park the playhead on the clip's in-point so the preview shows
        // the endpoint being dragged, not an unrelated frame.
        val clip = sequence.tracks.first { it.id == target.first }
            .clips.first { it.id == target.second }
        if (_state.value.position !in clip.timelineRange) {
            _state.value =
                _state.value.copy(position = clip.timelineStart, seeking = true)
            seekRequests.trySend(
                SeekRequest(++seekGeneration, sequence, clip.timelineStart)
            )
        }
        publishAfterEdit()
    }

    /**
     * Re-points the draft's clip to [sourceRange]. Validation is the
     * sequence's own — an invalid range leaves the prior draft state
     * untouched (no exception escapes to the pointer layer).
     */
    fun updateTrim(sourceRange: TimeRange) {
        if (closed) return
        val h = history ?: return
        val target = trimTarget ?: return
        if (!h.isDrafting) return
        val next = try {
            h.current.rippleTrimClip(target.first, target.second, sourceRange)
        } catch (e: IllegalArgumentException) {
            // The only expected rejection — an out-of-source range. Logged,
            // and the prior valid draft stands; anything else is a
            // programming error and must not be disguised as one.
            onError(e)
            return
        }
        // Identical republishes are a no-op — a drag that returns to its
        // captured origin keeps the draft open but writes nothing.
        if (next == h.current) return
        h.update(next)
        publishAfterEdit()
    }

    /** Lands the open trim draft as one undoable step. */
    fun commitTrim() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting) return
        h.commit()
        trimTarget = null
        publishAfterEdit()
    }

    /** Restores the sequence captured at [beginTrim]. */
    fun cancelTrim() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting) return
        h.cancel()
        trimTarget = null
        publishAfterEdit()
    }

    /**
     * Splits the clip containing the playhead — not merely the selected one —
     * at the current position. The point must lie strictly inside the clip;
     * a playhead on a clip boundary is a no-op. The left part keeps the
     * clip's id and becomes the selection.
     */
    fun splitAtPlayhead() {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val at = _state.value.position
        val target = clipStrictlyContaining(sequence, at) ?: return
        val newId = nextClipId(sequence)
        val applied = applyEdit("Split clip") {
            it.splitClip(target.first, target.second, at, newId)
        }
        if (applied) {
            _state.value = _state.value.copy(selectedClipId = target.second)
        }
    }

    /** Ripple-deletes the selected clip; selection moves to the clip that took its slot. */
    fun deleteSelected() {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val target = selectedTarget(sequence) ?: return
        val index = sequence.tracks.first { it.id == target.first }
            .clips.indexOfFirst { it.id == target.second }
        val applied = applyEdit("Delete clip") {
            it.removeClip(target.first, target.second)
        }
        if (applied) {
            val remaining = _state.value.sequence
                ?.tracks?.firstOrNull { it.id == target.first }?.clips
            _state.value = _state.value.copy(
                selectedClipId = remaining
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { it[minOf(index, it.size - 1)] }?.id
            )
        }
    }

    /** Moves the selected clip [delta] positions within its track (ripple-packed). */
    fun moveSelected(delta: Int) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val target = selectedTarget(sequence) ?: return
        val track = sequence.tracks.first { it.id == target.first }
        val index = track.clips.indexOfFirst { it.id == target.second }
        applyEdit("Move clip") {
            it.moveClip(target.first, target.second, index + delta)
        }
    }

    /**
     * Duplicates the selected clip within its track, placed right after
     * the original (rippling later clips on the packed Main lane), and
     * selects the copy — one undoable step.
     */
    fun duplicateSelected() {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val target = selectedTarget(sequence) ?: return
        val newId = nextClipId(sequence)
        val applied = applyEdit("Duplicate clip") {
            it.duplicateClip(target.first, target.second, newId)
        }
        if (applied) {
            _state.value = _state.value.copy(selectedClipId = newId)
        }
    }

    /**
     * Toggles a visual track's compositing gate — the lane stays in the
     * document, it just stops contributing pixels. One undoable step;
     * a locked track refuses (its header is frozen until unlocked).
     */
    fun setTrackVisible(trackId: String, visible: Boolean) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val track = sequence.tracks.firstOrNull { it.id == trackId } ?: return
        if (track.visible == visible || track.locked) return
        applyEdit(
            if (visible) "Show track" else "Hide track"
        ) {
            it.updateTrack(trackId) { t -> t.copy(visible = visible) }
        }
    }

    /** Locks or unlocks a track — a locked lane refuses clip edits. */
    fun setTrackLocked(trackId: String, locked: Boolean) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val track = sequence.tracks.firstOrNull { it.id == trackId } ?: return
        if (track.locked == locked) return
        applyEdit(
            if (locked) "Lock track" else "Unlock track"
        ) {
            it.updateTrack(trackId) { t -> t.copy(locked = locked) }
        }
    }

    /**
     * Appends an empty overlay lane — the timeline's Add track › Video.
     * Overlay imports and clip drags can land on it. One undoable step.
     */
    fun addVideoTrack() {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val trackId = nextTrackId(sequence)
        applyEdit("Add video track") {
            it.addTrack(VideoTrack(trackId, kind = VideoTrackKind.Overlay))
        }
    }

    /**
     * Appends an empty audio lane called [name] and selects it, so the
     * next audio import lands there. One undoable step.
     */
    fun addAudioTrack(name: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val trackId = nextTrackId(sequence)
        val applied = applyEdit("Add audio track") {
            it.addAudioTrack(AudioTrack(trackId, name = name.ifBlank { trackId }))
        }
        if (applied) {
            _state.value = _state.value.copy(
                selectedAudioTrackId = trackId,
                selectedAudioClipId = null
            )
        }
    }

    /**
     * Removes a non-Main visual lane with its clips — sources nothing else
     * uses leave with it. A locked lane refuses. One undoable step.
     */
    fun removeTrack(trackId: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val track = sequence.tracks.firstOrNull { it.id == trackId } ?: return
        if (track.kind == VideoTrackKind.Main || track.locked) return
        applyEdit("Delete track") { it.removeTrack(trackId) }
    }

    /**
     * Removes an audio lane with its clips — sidechains keyed to it switch
     * off and sources nothing else uses leave with it. A locked lane
     * refuses. One undoable step.
     */
    fun removeAudioTrack(trackId: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val track = sequence.audioTracks.firstOrNull { it.id == trackId } ?: return
        if (track.locked) return
        applyEdit("Delete audio track") { it.removeAudioTrack(trackId) }
    }

    /**
     * Moves a non-Main visual lane one slot up ([delta] < 0) or down
     * among the lanes of its kind; the edge lane stays put.
     */
    fun moveTrack(trackId: String, delta: Int) {
        if (closed || delta == 0) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val track = sequence.tracks.firstOrNull { it.id == trackId } ?: return
        if (track.kind == VideoTrackKind.Main) return
        val peers = sequence.tracks.filter { it.kind == track.kind }
        val index = peers.indexOfFirst { it.id == trackId }
        val neighbour = peers.getOrNull(if (delta < 0) index - 1 else index + 1) ?: return
        applyEdit("Move track") { it.swapTracks(trackId, neighbour.id) }
    }

    /** Moves an audio lane one slot up ([delta] < 0) or down; the edge lane stays put. */
    fun moveAudioTrack(trackId: String, delta: Int) {
        if (closed || delta == 0) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val index = sequence.audioTracks.indexOfFirst { it.id == trackId }
        if (index < 0) return
        val target = if (delta < 0) index - 1 else index + 1
        if (target !in sequence.audioTracks.indices) return
        applyEdit("Move audio track") { it.moveAudioTrack(trackId, target) }
    }

    /**
     * Drops visual clip [clipId] onto lane [toTrackId] at [newStart] —
     * the timeline's clip drag between overlay lanes. A placement the
     * model rejects (overlap, Main lane, other kind) or a locked lane on
     * either end is refused without touching the document; a landed move
     * selects the clip and is one undoable step.
     */
    fun moveVideoClip(clipId: String, toTrackId: String, newStart: EditorTime) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val from = sequence.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return
        val to = sequence.tracks.firstOrNull { it.id == toTrackId } ?: return
        if (from.locked || to.locked) return
        val op: (VideoSequence) -> VideoSequence = {
            it.moveClipToTrack(from.id, clipId, toTrackId, newStart)
        }
        if (!placementAccepted(sequence, op)) return
        if (applyEdit("Move clip", op)) {
            _state.value = _state.value.copy(selectedClipId = clipId)
        }
    }

    /**
     * Drops audio clip [clipId] onto lane [toTrackId] at [newStart], or —
     * with a null [toTrackId] — onto a fresh lane called [newTrackName]
     * appended below the others, in the same undoable step. Overlaps and
     * locked lanes are refused without touching the document; a landed
     * move selects the clip and its new lane.
     */
    fun moveAudioClip(
        clipId: String,
        toTrackId: String?,
        newStart: EditorTime,
        newTrackName: String = ""
    ) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val from = sequence.audioTracks
            .firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return
        if (from.locked) return
        val targetId = toTrackId ?: nextTrackId(sequence)
        if (toTrackId != null) {
            val to = sequence.audioTracks.firstOrNull { it.id == toTrackId } ?: return
            if (to.locked) return
        }
        val op: (VideoSequence) -> VideoSequence = { current ->
            val withLane = if (toTrackId == null) {
                current.addAudioTrack(
                    AudioTrack(targetId, name = newTrackName.ifBlank { targetId })
                )
            } else {
                current
            }
            withLane.moveAudioClipToTrack(from.id, clipId, targetId, newStart)
        }
        if (!placementAccepted(sequence, op)) return
        val label = if (toTrackId == null) "Move audio to new track" else "Move audio clip"
        if (applyEdit(label, op)) {
            _state.value = _state.value.copy(
                selectedAudioClipId = clipId,
                selectedAudioTrackId = targetId
            )
        }
    }

    /**
     * Whether a drag's placement passes the model's own validation — a
     * rejected drop is an ordinary miss, not an error worth a banner.
     */
    private fun placementAccepted(
        sequence: VideoSequence,
        op: (VideoSequence) -> VideoSequence
    ): Boolean = try {
        op(sequence) != sequence
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * Preview-time preference for how Main-lane inserts behave: ripple on
     * shifts the later clips right to make room (packed-timeline insert),
     * off keeps every existing clip anchored — an insert that needs room
     * then honestly fails instead of moving media the user pinned. Not a
     * document edit, so it never touches history or the dirty flag.
     */
    fun setRippleEnabled(enabled: Boolean) {
        if (closed || _state.value.rippleEnabled == enabled) return
        _state.value = _state.value.copy(rippleEnabled = enabled)
    }

    /**
     * Opens a live visual-recipe draft on the selected clip — the
     * transform/content-mode/opacity/blend/adjustments counterpart of
     * [beginTrim], sharing the same single history draft (a recipe edit
     * and a trim can never be open at once). The whole interaction lands
     * as one undoable step on commit; cancel restores the captured
     * sequence.
     */
    fun beginClipEdit(label: String = "Edit clip") {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        val sequence = h.current
        val target = selectedTarget(sequence) ?: return
        pause()
        thumbnailJob?.cancel()
        recipeTarget = target
        h.begin(label)
        publishAfterEdit()
    }

    /**
     * Re-points the draft clip's visual recipe — null fields keep their
     * current value, so a transform slider and a colour slider can drive
     * the same open draft without stomping each other. An invalid recipe
     * (the model's own validation) leaves the prior draft untouched.
     */
    fun updateClipVisuals(visuals: VideoClipVisuals) {
        if (closed) return
        val h = history ?: return
        val target = recipeTarget ?: return
        if (!h.isDrafting) return
        val next = try {
            h.current.updateClip(target.first, target.second) { clip ->
                clip.copy(
                    transform = visuals.transform ?: clip.transform,
                    contentMode = visuals.contentMode ?: clip.contentMode,
                    opacity = visuals.opacity ?: clip.opacity,
                    blendMode = visuals.blendMode ?: clip.blendMode,
                    adjustments = visuals.adjustments ?: clip.adjustments
                )
            }
        } catch (e: IllegalArgumentException) {
            onError(e)
            return
        }
        if (next == h.current) return
        h.update(next)
        publishAfterEdit()
    }

    /** One-shot recipe write — no open draft, one undoable step. */
    fun applyClipVisuals(label: String, visuals: VideoClipVisuals) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val target = selectedTarget(sequence) ?: return
        applyEdit(label) {
            it.updateClip(target.first, target.second) { clip ->
                clip.copy(
                    transform = visuals.transform ?: clip.transform,
                    contentMode = visuals.contentMode ?: clip.contentMode,
                    opacity = visuals.opacity ?: clip.opacity,
                    blendMode = visuals.blendMode ?: clip.blendMode,
                    adjustments = visuals.adjustments ?: clip.adjustments
                )
            }
        }
    }

    /** Lands the open recipe draft as one undoable step. */
    fun commitClipEdit() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting || recipeTarget == null) return
        h.commit()
        recipeTarget = null
        publishAfterEdit()
    }

    /** Restores the sequence captured at [beginClipEdit]. */
    fun cancelClipEdit() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting || recipeTarget == null) return
        h.cancel()
        recipeTarget = null
        publishAfterEdit()
    }

    fun undo() {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        pause()
        playGeneration += 1L
        if (h.undo()) publishAfterEdit()
    }

    fun redo() {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        pause()
        playGeneration += 1L
        if (h.redo()) publishAfterEdit()
    }

    /**
     * Saves the committed sequence as a new H.264/AAC MP4 copy. Single-flight;
     * exports the snapshot taken now — edits that land during the export stay
     * dirty. The media port's `onPublished` callback is the commit receipt: a
     * publication that already fired is reported as success even when
     * cancellation prevents the export call from returning.
     * [VideoEditorState.exported] always names the last published copy.
     */
    fun exportCopy() {
        if (closed) return
        val h = history ?: return
        if (_state.value.exporting || h.isDrafting) return
        val snapshot = h.current
        // Empty timeline: no image to encode, no export job to start.
        if (snapshot.duration.ticks <= 0L) return
        // Encoding shares the decoder path with playback — pause first even
        // when the caller went around the UI's Save action.
        pause()
        thumbnailJob?.cancel()
        val generation = ++exportGeneration
        val receipt = MutableStateFlow<VideoExportResult?>(null)
        exportOutcome.value = null
        _state.value = _state.value.copy(exporting = true, exportProgress = 0f)
        val job = sessionScope.launch(start = CoroutineStart.LAZY) {
            var failure: Throwable? = null
            try {
                withContext(workerDispatcher) {
                    media.export(
                        snapshot,
                        onPublished = { receipt.value = it },
                        onProgress = { value ->
                            exportProgressUpdates.value =
                                ExportProgressUpdate(generation, value)
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            exportOutcome.value = ExportOutcome(generation, failure)
        }
        exportJob = job
        job.invokeOnCompletion {
            // Fires even for a job cancelled before its first dispatch — the
            // cleanup below is what always clears the exporting flag.
            sessionScope.launch {
                finishExport(generation, snapshot, receipt.value)
            }
        }
        job.start()
    }

    private fun finishExport(
        generation: Long,
        snapshot: VideoSequence,
        receipt: VideoExportResult?
    ) {
        if (closed || generation != exportGeneration) return
        val outcome = exportOutcome.value?.takeIf { it.generation == generation }
        _state.value = _state.value.copy(
            exporting = false,
            exportCancelling = false,
            exportProgress = null
        )
        when {
            // A commit receipt outranks cancellation and even a trailing
            // error: the file exists, so the UI must learn about it.
            receipt != null -> {
                outcome?.failure?.let(onError)
                val h = history
                h?.markSaved(snapshot)
                _state.value = _state.value.copy(
                    exported = receipt,
                    isDirty = h?.isDirty == true,
                    canUndo = h?.canUndo == true,
                    canRedo = h?.canRedo == true
                )
            }
            outcome?.failure != null -> {
                onError(outcome.failure)
                _state.value = _state.value.copy(
                    error = errorKindFor(outcome.failure, VideoErrorKind.Export)
                )
            }
            // else: cancelled before publication — no result, no error.
        }
        scheduleThumbnails()
    }

    /**
     * Requests export cancellation. `exporting` stays true while the job
     * winds down — only [VideoEditorState.exportCancelling] changes now.
     */
    fun cancelExport() {
        if (closed || !_state.value.exporting) return
        _state.value = _state.value.copy(exportCancelling = true)
        exportJob?.cancel()
    }

    fun clearError() {
        if (closed) return
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Surfaces a persisted-state *format* failure — persisted primitives
     * that could not even be decoded into ranges on a genuine process
     * restore. The opened sequence is untouched; the error is the fixed
     * Open message, never the raw payload.
     */
    fun reportRestoreFailure(error: Throwable) {
        if (closed) return
        onError(error)
        _state.value = _state.value.copy(
            error = errorKindFor(error, VideoErrorKind.Open)
        )
    }

    /**
     * Cancels all session-owned work, retires the media port and resets
     * state; the caller's scope lives on. [media]'s close only releases
     * retained resources (e.g. a staged import) — decoders still in flight
     * unwind with their cancelled jobs.
     */
    fun close() {
        if (closed) return
        closed = true
        openGeneration += 1L
        seekGeneration += 1L
        playGeneration += 1L
        exportGeneration += 1L
        importGeneration += 1L
        thumbnailGeneration += 1L
        voiceGeneration += 1L
        thumbnailJob?.cancel()
        waveformJob?.cancel()
        importJob?.cancel()
        restoreJob?.cancel()
        voiceJob?.cancel()
        activeCapture?.let { capture ->
            try {
                capture.cancel()
            } catch (e: Exception) {
                onError(e)
            }
        }
        activeCapture = null
        thumbnailCache.clear()
        waveformCache.clear()
        history = null
        playback.close()
        sessionJob.cancel()
        try {
            media.close()
        } catch (e: Exception) {
            onError(e)
        }
        clearHeldUpdates()
        _state.value = VideoEditorState()
    }

    // --- internals, owner-thread confined unless noted ---

    private fun clearHeldUpdates() {
        previewFrames.value = null
        playbackFrames.value = null
        playbackPositions.value = null
    }

    private fun currentSequence(): VideoSequence? =
        _state.value.sequence ?: history?.current

    private fun sequenceFor(asset: MediaAsset.Video): VideoSequence {
        val source = asset.copy(id = SOURCE_ID)
        val clip = VideoClip(
            id = CLIP_ID_PREFIX + "1",
            sourceId = source.id,
            timelineStart = EditorTime.Zero,
            sourceRange = TimeRange(EditorTime.Zero, source.duration)
        )
        return VideoSequence(
            size = uprightSize(source),
            frameRate = OUTPUT_FRAME_RATE,
            sources = persistentListOf(source),
            tracks = persistentListOf(VideoTrack(TRACK_ID, persistentListOf(clip)))
        )
    }

    private fun uprightSize(source: MediaAsset.Video) =
        if (source.rotationDegrees % 180 != 0) {
            PixelSize(source.size.height, source.size.width)
        } else {
            source.size
        }

    /** (trackId, clipId) of the selected clip, or null when nothing valid is selected. */
    private fun selectedTarget(sequence: VideoSequence): Pair<String, String>? {
        val selected = _state.value.selectedClipId ?: return null
        for (track in sequence.tracks) {
            if (track.clips.any { it.id == selected }) return track.id to selected
        }
        return null
    }

    /**
     * (trackId, clipId) of the clip whose timeline placement strictly
     * contains [at], or null when the playhead sits on a boundary or in a
     * gap — splitting there would be a no-op, so the caller must not mutate.
     */
    private fun clipStrictlyContaining(
        sequence: VideoSequence,
        at: EditorTime
    ): Pair<String, String>? {
        for (track in sequence.tracks) {
            for (clip in track.clips) {
                if (at > clip.timelineRange.start &&
                    at < clip.timelineRange.endExclusive
                ) {
                    return track.id to clip.id
                }
            }
        }
        return null
    }

    /** The clip containing [at] half-open, or the first clip as fallback. */
    private fun clipAtPlayheadOrFirst(
        sequence: VideoSequence,
        at: EditorTime
    ): VideoClip? {
        for (track in sequence.tracks) {
            for (clip in track.clips) {
                if (at in clip.timelineRange) return clip
            }
        }
        return sequence.tracks.firstOrNull()?.clips?.firstOrNull()
    }

    private fun nextClipId(sequence: VideoSequence): String {
        var candidate: String
        do {
            candidate = CLIP_ID_PREFIX + (++clipCounter).toString()
        } while (idUsed(sequence, candidate))
        return candidate
    }

    private fun nextSourceId(sequence: VideoSequence): String {
        var candidate: String
        do {
            candidate = SOURCE_ID_PREFIX + (++sourceCounter).toString()
        } while (idUsed(sequence, candidate))
        return candidate
    }

    private fun nextTrackId(
        sequence: VideoSequence,
        reserved: String? = null
    ): String {
        var candidate: String
        do {
            candidate = TRACK_ID_PREFIX + (++trackCounter).toString()
        } while (candidate == reserved || idUsed(sequence, candidate))
        return candidate
    }

    /** Sources, tracks and clips share one id space — check all of it. */
    private fun idUsed(sequence: VideoSequence, id: String): Boolean =
        sequence.sources.any { it.id == id } ||
            sequence.audioSources.any { it.id == id } ||
            sequence.tracks.any { t ->
                t.id == id || t.clips.any { it.id == id }
            } ||
            sequence.audioTracks.any { t ->
                t.id == id || t.clips.any { it.id == id }
            }

    /**
     * The composited preview frame at timeline [at]: every active visual
     * clip — Main first, then overlays in track order — decoded on its own
     * source clock and stacked through the shared [VideoFrameCompositor]
     * on a canvas bounded to [PREVIEW_MAX_EDGE]. At/past the end the final
     * covered instant shows; a fully uncovered time yields null (no frame).
     * Overlay source audio is never decoded here — the Main lane alone
     * carries sound until the mixer exists.
     */
    private suspend fun compositeFrame(
        sequence: VideoSequence,
        at: EditorTime
    ): VideoFrame? {
        var sampleAt = at
        var clips = sequence.activeVisualClips(sampleAt)
        if (clips.isEmpty()) {
            val duration = sequence.duration
            if (duration.ticks <= 0L || at < duration) return null
            sampleAt = EditorTime(duration.ticks - 1L)
            clips = sequence.activeVisualClips(sampleAt)
            if (clips.isEmpty()) return null
        }
        val sourcesById = HashMap<String, MediaAsset.Video>(sequence.sources.size)
        for (source in sequence.sources) sourcesById[source.id] = source
        val scale = minOf(
            1f,
            PREVIEW_MAX_EDGE.toFloat() /
                max(sequence.size.width, sequence.size.height).toFloat()
        )
        val canvas = if (scale >= 1f) {
            sequence.size
        } else {
            PixelSize(
                (sequence.size.width * scale).toInt().coerceAtLeast(1),
                (sequence.size.height * scale).toInt().coerceAtLeast(1)
            )
        }
        val layers = ArrayList<VideoFrameCompositor.Layer>(clips.size)
        for (clip in clips) {
            val source = sourcesById[clip.sourceId] ?: continue
            val sourceT = clip.sourceRange.start + (sampleAt - clip.timelineStart)
            val frame = media.frame(source.reference, sourceT, PREVIEW_MAX_EDGE)
            layers += VideoFrameCompositor.Layer.of(clip, frame.pixels)
        }
        if (layers.isEmpty()) return null
        return VideoFrame(
            sampleAt, compositor.composite(canvas, layers, sequence.size)
        )
    }

    private fun clampPosition(sequence: VideoSequence, at: EditorTime): EditorTime {
        val duration = sequence.duration
        return when {
            at.ticks < 0L -> EditorTime.Zero
            at > duration -> duration
            else -> at
        }
    }

    /**
     * Drops a live trim draft before a structural command runs — select,
     * split, delete, move and trim-as-command all re-point the document the
     * gesture was captured against, so an open draft must never ride along.
     */
    private fun cancelOwnedDraft() {
        val h = history ?: return
        if (!h.isDrafting) return
        h.cancel()
        trimTarget = null
        recipeTarget = null
        audioEditOpen = false
        publishAfterEdit()
    }

    /** Returns whether the edit was applied. */
    private fun applyEdit(
        label: String,
        op: (VideoSequence) -> VideoSequence
    ): Boolean {
        if (closed) return false
        val h = history ?: return false
        if (h.isDrafting) return false
        pause()
        playGeneration += 1L
        val applied = try {
            h.execute(label, op)
        } catch (e: IllegalArgumentException) {
            onError(e)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return false
        } catch (e: IllegalStateException) {
            onError(e)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return false
        }
        if (applied) publishAfterEdit()
        return applied
    }

    /**
     * Rebuilds the track from restored cut ranges — the process-death path
     * that replays persisted primitives onto a freshly opened source. Each
     * range is validated against the probed source duration before anything
     * is applied; invalid input reports an Open error and leaves the
     * current sequence untouched. A valid restore lands as one undoable
     * step on the clean history and marks the session dirty — except a
     * restore that reproduces the opened sequence exactly (deterministic
     * clip ids make a full-source replay identical), which stays clean.
     *
     * An empty cut list is a legal restore — the user genuinely deleted
     * every clip: it requires `selectedIndex == -1` and `position == 0`,
     * and yields an empty (dirty, undoable) sequence rather than
     * resurrecting the whole source.
     */
    fun restoreCuts(
        cuts: List<TimeRange>,
        selectedIndex: Int,
        position: EditorTime
    ) {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        val sequence = currentSequence() ?: return
        val source = sequence.sources.singleOrNull() ?: return
        val total = cuts.fold(EditorTime.Zero) { acc, r -> acc + r.duration }
        if (
            cuts.any { it.endExclusive > source.duration } ||
            (if (cuts.isEmpty()) {
                selectedIndex != -1
            } else {
                selectedIndex !in cuts.indices
            }) ||
            position.ticks < 0L || position > total
        ) {
            val invalid = IllegalArgumentException(
                "restored cuts do not fit the opened source"
            )
            onError(invalid)
            _state.value = _state.value.copy(
                error = errorKindFor(invalid, VideoErrorKind.Open)
            )
            return
        }
        pause()
        playGeneration += 1L
        // Restored clips get the same deterministic ids the original build
        // used, so replaying the untouched full-source range reproduces the
        // initial sequence exactly — applyEdit records no step and the
        // project stays clean. The counter still advances past the restored
        // count so the next split mints a fresh id.
        if (clipCounter < cuts.size) clipCounter = cuts.size
        var restored = false
        applyEdit("Restore cuts") { current ->
            val track = current.tracks.singleOrNull() ?: return@applyEdit current
            // Same packed-from-zero layout every edit produces.
            var cursor = EditorTime.Zero
            val clips = cuts.mapIndexed { index, range ->
                val clip = VideoClip(
                    id = CLIP_ID_PREFIX + (index + 1),
                    sourceId = source.id,
                    timelineStart = cursor,
                    sourceRange = range
                )
                cursor += range.duration
                clip
            }
            val next = current.copy(
                tracks = persistentListOf(
                    track.copy(clips = clips.toPersistentList())
                )
            )
            restored = true
            next
        }
        if (restored) {
            // Selection and position still apply — a no-op restore of the
            // pristine sequence is still where the user's session was.
            val clips = _state.value.sequence?.tracks?.firstOrNull()?.clips
            if (clips != null && selectedIndex in clips.indices) {
                _state.value =
                    _state.value.copy(selectedClipId = clips[selectedIndex].id)
            }
            seek(position)
        }
    }

    /**
     * Imports [reference] as a new source and lands it as one undoable
     * history step — [VideoInsertion.AtPlayhead]/[VideoInsertion.End] on
     * the packed Main lane (a playhead inside a clip splits it first),
     * [VideoInsertion.Overlay] on an overlay lane at the playhead.
     *
     * The worker probes and decodes the first frame before the document is
     * touched, so an unreadable or unsupported source fails while the
     * sequence is untouched — surfaced through [VideoEditorState.error],
     * never dropped. [VideoEditorState.importing] marks the in-flight
     * window; a stale completion (a newer import, [cancelImport], reopen,
     * close) cannot publish. At most [MAX_SOURCES] sources and [MAX_CLIPS]
     * clips are accepted. The imported clip becomes the selection.
     */
    fun addVideoSource(reference: String, insertion: VideoInsertion) {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting || _state.value.exporting ||
            _state.value.importing || _state.value.opening
        ) return
        pause()
        val generation = ++importGeneration
        _state.value = _state.value.copy(importing = true)
        importJob = sessionScope.launch {
            var failure: Throwable? = null
            try {
                val probed = withContext(workerDispatcher) {
                    val info = media.probe(reference)
                    // Decoding the first frame proves the source actually
                    // yields pixels before it becomes part of the document.
                    media.frame(reference, EditorTime.Zero, PREVIEW_MAX_EDGE)
                    info
                }
                if (generation != importGeneration || closed) return@launch
                applyImportedSource(probed, insertion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            if (generation == importGeneration && !closed) {
                failure?.let {
                    onError(it)
                    _state.value = _state.value.copy(
                        error = errorKindFor(it, VideoErrorKind.Open)
                    )
                }
                _state.value = _state.value.copy(importing = false)
            }
        }
    }

    /** Cancels an in-flight [addVideoSource]; a landed import is history. */
    fun cancelImport() {
        if (closed) return
        importGeneration += 1L
        importJob?.cancel()
        if (_state.value.importing) {
            _state.value = _state.value.copy(importing = false)
        }
    }

    /**
     * Imports [reference] as an audio source — music, voice-over, effects —
     * and lands it as one undoable history step: a new [AudioTrack] named
     * after the source holding a full-length clip starting at the
     * playhead. The same [VideoEditorState.importing] window and stale /
     * cancel guards as [addVideoSource] apply — a probe failure surfaces
     * through [VideoEditorState.error], never drops.
     */
    fun addAudioSource(reference: String) {
        importAudio { media.probeAudio(reference) }
    }

    /**
     * The A1b recording seam: captures a microphone take through
     * [VideoMediaAccess.recordAudio] and imports it like
     * [addAudioSource]. Hosts without a recorder fail explicitly through
     * [VideoEditorState.error] — an Unsupported error, never a silent
     * no-op.
     */
    fun addRecordedAudio() {
        importAudio { media.recordAudio() }
    }

    /** Shared guarded import window for every audio-producing seam. */
    private fun importAudio(produce: suspend () -> AudioInfo) {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting || _state.value.exporting ||
            _state.value.importing || _state.value.opening
        ) return
        pause()
        val generation = ++importGeneration
        _state.value = _state.value.copy(importing = true)
        importJob = sessionScope.launch {
            var failure: Throwable? = null
            try {
                val probed = withContext(workerDispatcher) { produce() }
                if (generation != importGeneration || closed) return@launch
                applyImportedAudio(probed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            if (generation == importGeneration && !closed) {
                failure?.let {
                    onError(it)
                    _state.value = _state.value.copy(
                        error = errorKindFor(it, VideoErrorKind.Open)
                    )
                }
                _state.value = _state.value.copy(importing = false)
            }
        }
    }

    /** Lands a probed audio source as one undoable track+clip step. */
    private fun applyImportedAudio(
        info: AudioInfo,
        at: EditorTime = _state.value.position,
        label: String = "Add audio"
    ) {
        val current = history?.current ?: return
        val clipTotal = current.tracks.sumOf { it.clips.size } +
            current.audioTracks.sumOf { it.clips.size }
        if (current.sources.size + current.audioSources.size >= MAX_SOURCES ||
            clipTotal >= MAX_CLIPS
        ) {
            val limit = IllegalStateException(
                "sequence is at the $MAX_SOURCES-source/$MAX_CLIPS-clip limit"
            )
            onError(limit)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return
        }
        val sourceId = nextSourceId(current)
        val clipId = nextClipId(current)
        // An empty lane the user created and selected takes the import
        // (unless the host's settings turn that off); otherwise the source
        // lands on a lane of its own.
        val emptyLane = if (!fillSelectedEmptyAudioTrack) null else current.audioTracks.firstOrNull {
            it.id == _state.value.selectedAudioTrackId && it.clips.isEmpty() && !it.locked
        }
        val trackId = emptyLane?.id ?: nextTrackId(current)
        val asset = info.asset.copy(id = sourceId)
        val clip = AudioClip(
            id = clipId,
            sourceId = sourceId,
            timelineStart = at,
            sourceRange = TimeRange(EditorTime.Zero, asset.duration)
        )
        val applied = applyEdit(label) { sequence ->
            val withSource = sequence.addAudioSource(asset)
            if (emptyLane != null) {
                withSource.insertAudioClip(emptyLane.id, clip)
            } else {
                withSource.addAudioTrack(
                    AudioTrack(
                        id = trackId,
                        name = info.displayName,
                        clips = persistentListOf(clip)
                    )
                )
            }
        }
        if (applied) {
            _state.value = _state.value.copy(
                audioSourceInfos =
                    _state.value.audioSourceInfos.put(sourceId, info)
            )
        }
    }

    /**
     * Refreshes [VideoEditorState.voiceSupport] — run when the voice
     * surface opens and after the host's permission prompt resolves.
     * No recorder port means [VoiceSupport.Unavailable], the honest
     * "not on this host" state, never a dead Record button.
     */
    fun refreshVoicePermission() {
        if (closed) return
        val recorder = voiceRecorder
        if (recorder == null) {
            _state.value = _state.value.copy(voiceSupport = VoiceSupport.Unavailable)
            return
        }
        sessionScope.launch {
            val support = try {
                when (recorder.permissionState()) {
                    MicPermission.Granted -> VoiceSupport.Ready
                    MicPermission.Requestable -> VoiceSupport.RequiresPermission
                    MicPermission.Denied -> VoiceSupport.Denied
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                VoiceSupport.Unavailable
            }
            if (!closed) {
                _state.value = _state.value.copy(
                    voiceSupport = support,
                    voiceInput = recorder.inputLabel
                )
            }
        }
    }

    /**
     * The host's permission prompt was refused — the surface shows the
     * Denied copy (settings remedy) instead of re-prompting blindly.
     */
    fun voicePermissionDenied() {
        if (closed) return
        _state.value = _state.value.copy(voiceSupport = VoiceSupport.Denied)
    }

    /**
     * Begins a voice-over take anchored at the current playhead: the
     * timeline keeps playing through the [countInMs] lead-in, capture
     * starts re-anchored to the marked position so the take aligns with
     * what the user heard, and the input level publishes at ~30 Hz.
     * [monitor] is the recorder's headphone-cue flag — the surface's
     * honest "plug in headphones" guidance, not speaker feedback.
     * Missing hardware, drafts in flight and concurrent session work
     * are explicit refusals, never silent.
     */
    fun beginVoiceRecording(
        countInMs: Long = DEFAULT_COUNT_IN_MS,
        monitor: Boolean = true
    ) {
        if (closed) return
        val recorder = voiceRecorder
        if (recorder == null) {
            val unsupported = UnsupportedVideoException(
                "voice-over recording is not available on this host"
            )
            onError(unsupported)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return
        }
        val h = history ?: return
        val current = _state.value
        if (h.isDrafting || current.exporting || current.importing ||
            current.opening || current.voiceRecording != null
        ) {
            return
        }
        val anchor = current.position
        val generation = ++voiceGeneration
        _state.value = current.copy(
            voiceRecording = VoiceRecordingState(
                phase = VoiceRecordingState.Phase.CountIn,
                anchor = anchor,
                countInMs = countInMs,
                monitor = monitor
            )
        )
        // The lead-in is live — the user hears the mix before the take.
        play()
        voiceJob = sessionScope.launch {
            var failure: Throwable? = null
            try {
                delay(countInMs)
                val capture = withContext(workerDispatcher) {
                    recorder.start(monitor)
                }
                activeCapture = capture
                if (generation != voiceGeneration || closed) {
                    activeCapture = null
                    capture.cancel()
                    throw CancellationException()
                }
                // Re-anchor — the playhead drifted during the lead-in.
                seek(anchor)
                play()
                _state.value = _state.value.copy(
                    voiceRecording = VoiceRecordingState(
                        phase = VoiceRecordingState.Phase.Recording,
                        anchor = anchor,
                        countInMs = countInMs,
                        monitor = monitor
                    )
                )
                while (true) {
                    delay(LEVEL_POLL_MS)
                    val rec = _state.value.voiceRecording
                    if (rec != null &&
                        rec.phase == VoiceRecordingState.Phase.Recording &&
                        generation == voiceGeneration
                    ) {
                        _state.value = _state.value.copy(
                            voiceRecording = rec.copy(level = capture.level())
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            if (generation == voiceGeneration && !closed && failure != null) {
                onError(failure)
                _state.value = _state.value.copy(
                    voiceRecording = null,
                    error = errorKindFor(failure, VideoErrorKind.Open)
                )
            }
        }
    }

    /**
     * Ends the take: the open capture finalizes its WAV on the worker
     * and the result lands as a new "Voice-over" lane at the recorded
     * anchor — one undoable history step, same as an import. Stop during
     * the lead-in is a cancel — nothing was captured.
     */
    fun stopVoiceRecording() {
        if (closed) return
        val rec = _state.value.voiceRecording ?: return
        if (rec.phase == VoiceRecordingState.Phase.CountIn) {
            cancelVoiceRecording()
            return
        }
        if (rec.phase != VoiceRecordingState.Phase.Recording) return
        val capture = activeCapture
        if (capture == null) {
            cancelVoiceRecording()
            return
        }
        val generation = ++voiceGeneration
        voiceJob?.cancel()
        _state.value = _state.value.copy(
            voiceRecording = rec.copy(phase = VoiceRecordingState.Phase.Finishing)
        )
        voiceJob = sessionScope.launch {
            var take: VoiceTake? = null
            var failure: Throwable? = null
            try {
                take = withContext(workerDispatcher) { capture.stop() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            activeCapture = null
            pause()
            if (generation == voiceGeneration && !closed) {
                if (failure != null) {
                    onError(failure)
                    _state.value = _state.value.copy(
                        error = errorKindFor(failure, VideoErrorKind.Open)
                    )
                } else if (take != null) {
                    val imported = AudioInfo(
                        asset = MediaAsset.Audio(
                            id = take.reference,
                            reference = take.reference,
                            duration = take.duration,
                            sampleRate = take.sampleRate,
                            channels = take.channels
                        ),
                        displayName = take.displayName
                    )
                    applyImportedAudio(
                        imported,
                        at = rec.anchor,
                        label = "Record voice-over"
                    )
                }
                _state.value = _state.value.copy(voiceRecording = null)
            }
        }
    }

    /**
     * Abandons the take — the temp file is deleted by [VoiceCapture.cancel]
     * and nothing enters history. Safe from any phase.
     */
    fun cancelVoiceRecording() {
        if (closed) return
        _state.value.voiceRecording ?: return
        voiceGeneration += 1L
        voiceJob?.cancel()
        val capture = activeCapture
        activeCapture = null
        _state.value = _state.value.copy(voiceRecording = null)
        pause()
        if (capture != null) {
            try {
                capture.cancel()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Selects an audio-lane clip — the Clip audio inspector's target —
     * and remembers the lane that owns it for the routing surface. An
     * unknown id is ignored; audio and video selections are independent.
     */
    fun selectAudioClip(id: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val owner = sequence.audioTracks.firstOrNull { track ->
            track.clips.any { it.id == id }
        } ?: return
        _state.value = _state.value.copy(
            selectedAudioClipId = id,
            selectedAudioTrackId = owner.id
        )
    }

    /** Selects an audio track — the Routing surface's target lane. */
    fun selectAudioTrack(id: String) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        if (sequence.audioTracks.none { it.id == id }) return
        _state.value = _state.value.copy(selectedAudioTrackId = id)
    }

    /**
     * Opens a live audio draft — the mixer/fade/routing counterpart of
     * [beginClipEdit], sharing the history's single draft slot (an audio
     * gesture, a trim and a recipe edit can never be open at once).
     * [updateAudioClipEdit]/[updateAudioTrackEdit]/[updateMasterGain]
     * re-point the draft; [commitAudioEdit] lands the whole gesture as
     * one undoable step, [cancelAudioEdit] restores the captured
     * sequence.
     */
    fun beginAudioEdit(label: String = "Adjust audio") {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting) return
        pause()
        thumbnailJob?.cancel()
        h.begin(label)
        audioEditOpen = true
        publishAfterEdit()
    }

    /** Re-points a clip's mix recipe inside the open audio draft. */
    fun updateAudioClipEdit(
        trackId: String,
        clipId: String,
        edit: AudioClipEdit
    ) {
        mutateAudioDraft {
            it.updateAudioClip(trackId, clipId) { c -> edit.applyTo(c) }
        }
    }

    /** Re-points a lane header inside the open audio draft. */
    fun updateAudioTrackEdit(trackId: String, edit: AudioTrackEdit) {
        mutateAudioDraft {
            it.updateAudioTrack(trackId) { t -> edit.applyTo(t) }
        }
    }

    /** Re-points the output gain inside the open audio draft. */
    fun updateMasterGain(gainDb: Float) {
        mutateAudioDraft { it.copy(masterGainDb = gainDb) }
    }

    /** Lands the open audio draft as one undoable step. */
    fun commitAudioEdit() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting || !audioEditOpen) return
        h.commit()
        audioEditOpen = false
        publishAfterEdit()
    }

    /** Restores the sequence captured at [beginAudioEdit]. */
    fun cancelAudioEdit() {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting || !audioEditOpen) return
        h.cancel()
        audioEditOpen = false
        publishAfterEdit()
    }

    /** One-shot clip recipe write — a toggle or a committed value, one step. */
    fun applyAudioClipEdit(
        label: String,
        trackId: String,
        clipId: String,
        edit: AudioClipEdit
    ) {
        if (closed) return
        cancelOwnedDraft()
        applyEdit(label) {
            it.updateAudioClip(trackId, clipId) { c -> edit.applyTo(c) }
        }
    }

    /** One-shot lane header write — mute/solo/lock, one undoable step. */
    fun applyAudioTrackEdit(
        label: String,
        trackId: String,
        edit: AudioTrackEdit
    ) {
        if (closed) return
        cancelOwnedDraft()
        applyEdit(label) {
            it.updateAudioTrack(trackId) { t -> edit.applyTo(t) }
        }
    }

    /** One-shot master-gain write for a typed value — a drag drafts instead. */
    fun setMasterGain(gainDb: Float) {
        if (closed) return
        cancelOwnedDraft()
        applyEdit("Adjust master gain") { it.copy(masterGainDb = gainDb) }
    }

    /** Toggles the output ceiling — the mixer's honest "Limiter" switch. */
    fun setLimiterEnabled(enabled: Boolean) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        if (sequence.limiterEnabled == enabled) return
        applyEdit(
            if (enabled) "Enable limiter" else "Disable limiter"
        ) {
            it.copy(limiterEnabled = enabled)
        }
    }

    /**
     * Toggles the virtual Main-lane input — the "Source sound" card's
     * mute, a document edit (it changes the mix), one undoable step.
     */
    fun setSourceSoundMuted(muted: Boolean) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        if (sequence.sourceSoundMuted == muted) return
        applyEdit(
            if (muted) "Mute source sound" else "Unmute source sound"
        ) {
            it.copy(sourceSoundMuted = muted)
        }
    }

    /**
     * Link/detach a video clip's embedded sound — detach flags it
     * `audioMuted` so the virtual input skips it (an extracted lane
     * carries the sound instead); link restores embedded playback. One
     * undoable step, no lane is created or removed here.
     */
    fun setAudioLink(trackId: String, clipId: String, linked: Boolean) {
        if (closed) return
        cancelOwnedDraft()
        val sequence = currentSequence() ?: return
        val clip = sequence.tracks.firstOrNull { it.id == trackId }
            ?.clips?.firstOrNull { it.id == clipId } ?: return
        if (clip.audioMuted == !linked) return
        applyEdit(if (linked) "Link audio" else "Detach audio") {
            it.updateClip(trackId, clipId) { c -> c.copy(audioMuted = !linked) }
        }
    }

    /**
     * Extracts the selected video clip's sound onto its own lane: probes
     * the source's audio stream, then lands one undoable step that adds
     * an [com.dot.gallery.editor.core.model.AudioTrack] named after the
     * source holding a clip mirroring the video clip's placement — and
     * flags the video clip `audioMuted`, so the virtual input never
     * doubles the extracted sound. Already-detached or audioless clips
     * are a no-op; a probe failure is an explicit Open error.
     */
    fun extractAudioFromSelectedVideo() {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting || _state.value.exporting ||
            _state.value.importing || _state.value.opening
        ) return
        val sequence = h.current
        val target = selectedTarget(sequence) ?: return
        val clip = sequence.tracks.first { it.id == target.first }
            .clips.first { it.id == target.second }
        if (clip.audioMuted) return
        val source = sequence.sources.first { it.id == clip.sourceId }
        pause()
        val generation = ++importGeneration
        _state.value = _state.value.copy(importing = true)
        importJob = sessionScope.launch {
            var failure: Throwable? = null
            try {
                val probed = withContext(workerDispatcher) {
                    media.probeAudio(source.reference)
                }
                if (generation != importGeneration || closed) return@launch
                applyExtractedAudio(probed, target.first, target.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            if (generation == importGeneration && !closed) {
                failure?.let {
                    onError(it)
                    _state.value = _state.value.copy(
                        error = errorKindFor(it, VideoErrorKind.Open)
                    )
                }
                _state.value = _state.value.copy(importing = false)
            }
        }
    }

    /** Lands the probed extraction as one undoable mute+lane step. */
    private fun applyExtractedAudio(
        info: AudioInfo,
        videoTrackId: String,
        videoClipId: String
    ) {
        val current = history?.current ?: return
        val clipTotal = current.tracks.sumOf { it.clips.size } +
            current.audioTracks.sumOf { it.clips.size }
        if (current.sources.size + current.audioSources.size >= MAX_SOURCES ||
            clipTotal >= MAX_CLIPS
        ) {
            val limit = IllegalStateException(
                "sequence is at the $MAX_SOURCES-source/$MAX_CLIPS-clip limit"
            )
            onError(limit)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return
        }
        val sourceId = nextSourceId(current)
        val audioClipId = nextClipId(current)
        val audioTrackId = nextTrackId(current)
        val applied = applyEdit("Extract audio") { sequence ->
            val videoClip = sequence.tracks.first { it.id == videoTrackId }
                .clips.first { it.id == videoClipId }
            sequence
                .updateClip(videoTrackId, videoClipId) {
                    it.copy(audioMuted = true)
                }
                .addAudioSource(info.asset.copy(id = sourceId))
                .addAudioTrack(
                    AudioTrack(
                        id = audioTrackId,
                        name = info.displayName,
                        clips = persistentListOf(
                            AudioClip(
                                id = audioClipId,
                                sourceId = sourceId,
                                timelineStart = videoClip.timelineStart,
                                sourceRange = videoClip.sourceRange
                            )
                        )
                    )
                )
        }
        if (applied) {
            _state.value = _state.value.copy(
                audioSourceInfos =
                    _state.value.audioSourceInfos.put(sourceId, info),
                selectedAudioClipId = audioClipId,
                selectedAudioTrackId = audioTrackId
            )
        }
    }

    /**
     * Applies one draft mutation — the audio counterpart of
     * [updateClipVisuals]. Only runs while an audio draft is open; a
     * model rejection leaves the prior draft state untouched.
     */
    private fun mutateAudioDraft(op: (VideoSequence) -> VideoSequence) {
        if (closed) return
        val h = history ?: return
        if (!h.isDrafting || !audioEditOpen) return
        val next = try {
            op(h.current)
        } catch (e: IllegalArgumentException) {
            onError(e)
            return
        }
        if (next == h.current) return
        h.update(next)
        publishAfterEdit()
    }

    /**
     * (Re)starts the waveform summarizer after a debounce — one source
     * at a time through `media.waveform`, cache-miss only, sharing the
     * [frameFetchMutex] so the session never runs two native decodes.
     * A decode failure reports through [onError] and leaves that lane's
     * placeholder; it is never an editor-level error.
     */
    private fun scheduleWaveforms() {
        if (closed) return
        waveformJob?.cancel()
        val generation = ++waveformGeneration
        waveformJob = sessionScope.launch {
            delay(THUMBNAIL_DEBOUNCE_MS)
            val sequence = currentSequence() ?: return@launch
            for (source in sequence.audioSources) {
                if (generation != waveformGeneration) return@launch
                if (waveformCache[source.reference] != null) continue
                val peaks = try {
                    frameFetchMutex.withLock {
                        withContext(workerDispatcher) {
                            media.waveform(source.reference, WAVEFORM_BUCKETS)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                    continue
                }
                waveformCache[source.reference] = peaks
                while (waveformCache.size > WAVEFORM_CACHE_LIMIT) {
                    val eldest = waveformCache.keys.firstOrNull() ?: break
                    waveformCache.remove(eldest)
                }
                if (generation != waveformGeneration) return@launch
                val current = currentSequence() ?: return@launch
                _state.value = _state.value.copy(
                    waveforms = waveformsFor(current)
                )
            }
        }
    }

    /** Cached peak summaries for [sequence]'s audio sources, by source id. */
    private fun waveformsFor(
        sequence: VideoSequence
    ): kotlinx.collections.immutable.PersistentMap<String, FloatArray> {
        val result = LinkedHashMap<String, FloatArray>(sequence.audioSources.size)
        for (source in sequence.audioSources) {
            waveformCache[source.reference]?.let { result[source.id] = it }
        }
        return result.toPersistentMap()
    }

    private fun applyImportedSource(info: VideoInfo, insertion: VideoInsertion) {
        val current = history?.current ?: return
        val clipTotal = current.tracks.sumOf { it.clips.size }
        if (current.sources.size >= MAX_SOURCES || clipTotal >= MAX_CLIPS) {
            val limit = IllegalStateException(
                "sequence is at the $MAX_SOURCES-source/$MAX_CLIPS-clip limit"
            )
            onError(limit)
            _state.value = _state.value.copy(error = VideoErrorKind.Unsupported)
            return
        }
        val sourceId = nextSourceId(current)
        val clipId = nextClipId(current)
        val splitId = nextClipId(current)
        val overlayTrackId = nextTrackId(current)
        val mainTrackId =
            current.mainTrack?.id ?: nextTrackId(current, reserved = overlayTrackId)
        val asset = info.asset.copy(id = sourceId)
        val duration = asset.duration
        val playhead = _state.value.position
        var landedClipId: String? = null
        var landedStart = playhead
        val applied = applyEdit("Add video") { sequence ->
            var next = sequence.addSource(asset)
            when (insertion) {
                VideoInsertion.End -> {
                    val main = next.mainTrack ?: VideoTrack(
                        mainTrackId, kind = VideoTrackKind.Main
                    ).also { next = next.addTrack(it) }
                    val start = main.clips.lastOrNull()
                        ?.timelineRange?.endExclusive ?: EditorTime.Zero
                    landedStart = start
                    next = next.insertClip(
                        main.id,
                        VideoClip(
                            id = clipId, sourceId = sourceId,
                            timelineStart = start,
                            sourceRange = TimeRange(EditorTime.Zero, duration)
                        ),
                        ripple = false
                    )
                }
                VideoInsertion.AtPlayhead -> {
                    val main = next.mainTrack ?: VideoTrack(
                        mainTrackId, kind = VideoTrackKind.Main
                    ).also { next = next.addTrack(it) }
                    val straddler = main.clips.firstOrNull {
                        playhead > it.timelineRange.start &&
                            playhead < it.timelineRange.endExclusive
                    }
                    val ripple = _state.value.rippleEnabled
                    if (straddler != null && ripple) {
                        next = next.splitClip(
                            main.id, straddler.id, playhead, splitId
                        )
                    }
                    next = next.insertClip(
                        main.id,
                        VideoClip(
                            id = clipId, sourceId = sourceId,
                            timelineStart = playhead,
                            sourceRange = TimeRange(EditorTime.Zero, duration)
                        ),
                        ripple = ripple
                    )
                }
                VideoInsertion.Overlay -> {
                    val end = playhead + duration
                    val host = next.tracks.firstOrNull { track ->
                        track.kind == VideoTrackKind.Overlay &&
                            track.clips.none {
                                playhead < it.timelineRange.endExclusive &&
                                    it.timelineRange.start < end
                            }
                    } ?: VideoTrack(overlayTrackId, kind = VideoTrackKind.Overlay)
                        .also { next = next.addTrack(it) }
                    next = next.insertClip(
                        host.id,
                        VideoClip(
                            id = clipId, sourceId = sourceId,
                            timelineStart = playhead,
                            sourceRange = TimeRange(EditorTime.Zero, duration)
                        ),
                        ripple = false
                    )
                }
            }
            landedClipId = clipId
            next
        }
        if (!applied) return
        val clipIdLanded = landedClipId ?: return
        _state.value = _state.value.copy(
            selectedClipId = clipIdLanded,
            sourceInfos = _state.value.sourceInfos.put(sourceId, info)
        )
        // Bring the imported clip under the playhead so the preview shows
        // what was added; an AtPlayhead/Overlay clip already sits under it
        // and publishAfterEdit refreshed the frame.
        if (insertion == VideoInsertion.End) {
            seek(landedStart)
        }
    }

    /**
     * Restores a persisted multi-source document: probes every referenced
     * source on the worker first, so missing or truncated media is an
     * explicit [VideoErrorKind.Open] failure and the opened sequence is
     * never silently flattened to the playable remainder. A fully readable
     * project lands as one undoable step on the clean history — dirty,
     * unless it reproduces the opened sequence exactly.
     */
    fun restoreProject(
        sequence: VideoSequence,
        selectedClipId: String?,
        position: EditorTime
    ) {
        if (closed) return
        val h = history ?: return
        if (h.isDrafting || _state.value.opening || _state.value.exporting) {
            return
        }
        restoreJob?.cancel()
        pendingRestore = VideoProjectSnapshot(sequence, selectedClipId, position)
        restoreJob = sessionScope.launch {
            val infos = LinkedHashMap<String, VideoInfo>(sequence.sources.size)
            val audioInfos =
                LinkedHashMap<String, AudioInfo>(sequence.audioSources.size)
            // Every missing or unreadable source is collected, never
            // dropped — the restore fails afterwards with the complete
            // list for the recovery surface.
            val missing = ArrayList<String>()
            for (source in sequence.sources) {
                try {
                    val probed = withContext(workerDispatcher) {
                        media.probe(source.reference)
                    }
                    val overruns = sequence.tracks.any { track ->
                        track.clips.any {
                            it.sourceId == source.id &&
                                it.sourceRange.endExclusive >
                                    probed.asset.duration
                        }
                    }
                    require(!overruns) {
                        "restored source ${source.reference} is shorter " +
                            "than the clips placed on it"
                    }
                    infos[source.id] = probed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    missing += source.reference
                }
            }
            for (source in sequence.audioSources) {
                try {
                    val probed = withContext(workerDispatcher) {
                        media.probeAudio(source.reference)
                    }
                    val overruns = sequence.audioTracks.any { track ->
                        track.clips.any {
                            it.sourceId == source.id &&
                                it.sourceRange.endExclusive >
                                    probed.asset.duration
                        }
                    }
                    require(!overruns) {
                        "restored audio source ${source.reference} is " +
                            "shorter than the clips placed on it"
                    }
                    audioInfos[source.id] = probed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    missing += source.reference
                }
            }
            if (closed) return@launch
            if (missing.isNotEmpty()) {
                onError(
                    IllegalArgumentException(
                        "restored project media is missing or unreadable: " +
                            missing.joinToString()
                    )
                )
                _state.value = _state.value.copy(
                    recovery = VideoRecovery(missing.toPersistentList())
                )
                return@launch
            }
            pause()
            val applied = applyEdit("Restore project") { sequence }
            if (applied || h.current == sequence) {
                val persistentInfos = infos.toPersistentMap()
                val primaryId = sequence.tracks
                    .firstOrNull { it.kind == VideoTrackKind.Main }
                    ?.clips?.firstOrNull()?.sourceId
                val currentRef = _state.value.info?.asset?.reference
                _state.value = _state.value.copy(
                    info = persistentInfos.values.firstOrNull {
                        it.asset.reference == currentRef
                    } ?: primaryId?.let { persistentInfos[it] }
                        ?: persistentInfos.values.firstOrNull(),
                    sourceInfos = persistentInfos,
                    audioSourceInfos = audioInfos.toPersistentMap(),
                    selectedClipId = selectedClipId?.takeIf { id ->
                        sequence.tracks.any { t ->
                            t.clips.any { it.id == id }
                        }
                    },
                    recovery = null
                )
                // The restore landed — nothing is left pending recovery.
                pendingRestore = null
                seek(position)
            }
        }
    }

    /** Re-probes every missing reference and replays the pending restore. */
    fun retryRestore() {
        val pending = pendingRestore ?: return
        restoreProject(pending.sequence, pending.selectedClipId, pending.position)
    }

    /**
     * Abandons the pending snapshot — the user's explicit choice to keep
     * the freshly opened source and lose the persisted project.
     */
    fun discardRestore() {
        pendingRestore = null
        _state.value = _state.value.copy(recovery = null)
    }

    /**
     * Replaces one missing [missing] reference with the host-picked
     * [replacement] in the pending snapshot and replays the restore.
     * Source ids are unchanged, so every clip pointing at the relinked
     * source keeps its placement; other missing references re-report.
     */
    fun relinkRestore(missing: String, replacement: String) {
        val pending = pendingRestore ?: return
        val sequence = pending.sequence.copy(
            sources = pending.sequence.sources.map { source ->
                if (source.reference == missing) {
                    source.copy(reference = replacement)
                } else {
                    source
                }
            }.toPersistentList(),
            audioSources = pending.sequence.audioSources.map { source ->
                if (source.reference == missing) {
                    source.copy(reference = replacement)
                } else {
                    source
                }
            }.toPersistentList()
        )
        restoreProject(sequence, pending.selectedClipId, pending.position)
    }

    /**
     * Republishes document + controls, re-normalizes the selection (an undo
     * may have removed the selected clip) and refreshes the frame at the
     * (clamped) position.
     */
    private fun publishAfterEdit() {
        val h = history ?: return
        val sequence = h.current
        val position = clampPosition(sequence, _state.value.position)
        val selection = _state.value.selectedClipId?.takeIf { id ->
            sequence.tracks.any { t -> t.clips.any { it.id == id } }
        }
        // An audio selection re-normalizes the same way — an undo or a
        // lane edit that removed the clip clears it; the track id follows
        // whichever lane still owns the clip.
        val audioClipSel = _state.value.selectedAudioClipId?.takeIf { id ->
            sequence.audioTracks.any { t -> t.clips.any { it.id == id } }
        }
        val audioTrackSel = _state.value.selectedAudioTrackId
            ?.takeIf { id -> sequence.audioTracks.any { it.id == id } }
            ?: audioClipSel?.let { id ->
                sequence.audioTracks.firstOrNull { t ->
                    t.clips.any { it.id == id }
                }?.id
            }
        _state.value = _state.value.copy(
            sequence = sequence,
            position = position,
            selectedClipId = selection
                ?: clipAtPlayheadOrFirst(sequence, position)?.id,
            selectedAudioClipId = audioClipSel,
            selectedAudioTrackId = audioTrackSel,
            waveforms = waveformsFor(sequence),
            canUndo = h.canUndo,
            canRedo = h.canRedo,
            isDirty = h.isDirty,
            isDrafting = h.isDrafting,
            // A draft keeps the tiles decoded before it — resampling mid-
            // gesture would chase a moving source range; commit/cancel
            // schedules the fresh pass.
            thumbnails = if (h.isDrafting) {
                _state.value.thumbnails
            } else {
                thumbnailsFor(sequence)
            }
        )
        refreshPreview(sequence, position)
        if (!h.isDrafting) {
            scheduleThumbnails()
            scheduleWaveforms()
        }
    }

    private fun refreshPreview(sequence: VideoSequence, at: EditorTime) {
        val generation = ++seekGeneration
        _state.value = _state.value.copy(seeking = true)
        seekRequests.trySend(SeekRequest(generation, sequence, at))
    }

    /**
     * (Re)starts the clip-tile sampler after a debounce. Callers cancel the
     * previous pass implicitly — a seek, play or export always lands first —
     * and a new schedule while scrubbing simply pushes the decode out until
     * input goes idle. One `media.frame` call is in flight at a time, sharing
     * [frameFetchMutex] with the interactive seek path so the whole session
     * never runs two native decodes.
     */
    private fun scheduleThumbnails() {
        if (closed) return
        thumbnailJob?.cancel()
        val generation = ++thumbnailGeneration
        thumbnailJob = sessionScope.launch {
            delay(THUMBNAIL_DEBOUNCE_MS)
            val sequence = currentSequence() ?: return@launch
            val clips = sequence.tracks.flatMap { it.clips }
            if (clips.isEmpty()) return@launch
            val sourcesById =
                sequence.sources.associateBy { it.id }
            // Past the clip bound only the leading clips plus the selection
            // are sampled — a 200-clip timeline does not decode 200 frames.
            val wanted = clips.take(THUMBNAIL_CLIP_LIMIT).toMutableList()
            val selectedId = _state.value.selectedClipId
            clips.firstOrNull { it.id == selectedId }
                ?.takeIf { wanted.none { c -> c.id == it.id } }
                ?.let { wanted.add(it) }
            for (clip in wanted) {
                val reference = sourcesById[clip.sourceId]?.reference
                    ?: continue
                for (sample in VideoThumbnail.sampleTimes(clip.sourceRange)) {
                    if (generation != thumbnailGeneration) return@launch
                    val key = thumbnailKey(reference, sample)
                    // get(), not containsKey — access-order marks it recently
                    // used so the LRU bound evicts the genuinely oldest tile.
                    if (thumbnailCache[key] != null) continue
                    val thumbnail = try {
                        val frame = frameFetchMutex.withLock {
                            withContext(workerDispatcher) {
                                media.frame(reference, sample, THUMBNAIL_MAX_EDGE)
                            }
                        }
                        VideoThumbnail(clip.id, sample, frame.pixels)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A tile failure reports through onError and simply
                        // leaves a placeholder — never an editor-level error.
                        onError(e)
                        continue
                    }
                    thumbnailCache[key] = thumbnail
                    while (thumbnailCache.size > THUMBNAIL_CACHE_LIMIT) {
                        val eldest = thumbnailCache.keys.firstOrNull() ?: break
                        thumbnailCache.remove(eldest)
                    }
                    if (generation != thumbnailGeneration) return@launch
                    val current = currentSequence() ?: return@launch
                    _state.value = _state.value.copy(
                        thumbnails = thumbnailsFor(current)
                    )
                }
            }
        }
    }

    /** Cached tiles for [sequence]'s current clips, in clip/sample order. */
    private fun thumbnailsFor(
        sequence: VideoSequence
    ): kotlinx.collections.immutable.PersistentList<VideoThumbnail> {
        val sourcesById = sequence.sources.associateBy { it.id }
        val result = mutableListOf<VideoThumbnail>()
        for (track in sequence.tracks) {
            for (clip in track.clips) {
                val reference = sourcesById[clip.sourceId]?.reference
                    ?: continue
                for (sample in VideoThumbnail.sampleTimes(clip.sourceRange)) {
                    thumbnailCache[thumbnailKey(reference, sample)]
                        ?.let { result.add(it.copy(clipId = clip.id)) }
                }
            }
        }
        return result.toPersistentList()
    }

    private fun thumbnailKey(reference: String, sourceTime: EditorTime) =
        "$reference|${sourceTime.ticks}"

    private fun errorKindFor(error: Throwable, fallback: VideoErrorKind) =
        when (error) {
            is UnsupportedVideoException -> VideoErrorKind.Unsupported
            else -> fallback
        }

    private companion object {
        const val PREVIEW_MAX_EDGE = 1024
        const val THUMBNAIL_MAX_EDGE = 160
        /** Decoded images bound — 8 clips x 4 filmstrip samples fill it. */
        const val THUMBNAIL_CACHE_LIMIT = 32
        const val THUMBNAIL_CLIP_LIMIT = 8
        const val THUMBNAIL_DEBOUNCE_MS = 180L
        /** Peak buckets per audio source — the lane can downsample freely. */
        const val WAVEFORM_BUCKETS = 240
        /** Bound on decoded summaries — one entry per source reference. */
        const val WAVEFORM_CACHE_LIMIT = 16
        /** Import/document bounds — ids stay mintable and restores bounded. */
        const val MAX_SOURCES = 64
        const val MAX_CLIPS = 64
        const val SOURCE_ID = "source-1"
        const val TRACK_ID = "track-1"
        const val SOURCE_ID_PREFIX = "source-"
        const val TRACK_ID_PREFIX = "track-"
        const val CLIP_ID_PREFIX = "clip-"
        /** The voice-over lead-in — three seconds of live mix before capture. */
        const val DEFAULT_COUNT_IN_MS = 3_000L
        /** Input-meter cadence during a take. */
        const val LEVEL_POLL_MS = 33L
        val OUTPUT_FRAME_RATE = FrameRate(30)
    }
}
