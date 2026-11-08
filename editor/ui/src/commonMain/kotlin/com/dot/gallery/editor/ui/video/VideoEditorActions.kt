package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.AudioClipEdit
import com.dot.gallery.editor.render.video.AudioTrackEdit
import com.dot.gallery.editor.render.video.VideoClipVisuals
import com.dot.gallery.editor.render.video.VideoInsertion

/**
 * Everything [VideoEditorContent] can ask of a session, as plain callbacks —
 * the content composable never sees the session, a Context or any I/O.
 * [VideoEditorScreen] fills this from a live
 * [com.dot.gallery.editor.render.video.VideoEditorSession]; tests and
 * previews fill it with recorders.
 */
data class VideoEditorActions(
    val selectClip: (String) -> Unit,
    val seek: (EditorTime) -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val setMuted: (Boolean) -> Unit,
    val trimSelected: (TimeRange) -> Unit,
    /** Live trim draft seam — grips/slider drive one undoable gesture. */
    val beginTrim: () -> Unit,
    val updateTrim: (TimeRange) -> Unit,
    val commitTrim: () -> Unit,
    val cancelTrim: () -> Unit,
    val split: () -> Unit,
    val delete: () -> Unit,
    /** Duplicates the selected clip after its original; selects the copy. */
    val duplicate: () -> Unit,
    val moveSelected: (Int) -> Unit,
    /** Track header toggles — both land as one undoable step each. */
    val setTrackVisible: (String, Boolean) -> Unit = { _, _ -> },
    val setTrackLocked: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * Lane structure — create, reorder (delta −1 up / +1 down) and delete
     * lanes. Each call is one undoable step; Main is never moved or
     * deleted.
     */
    val addVideoTrack: () -> Unit = {},
    val addAudioTrack: (name: String) -> Unit = {},
    val moveTrack: (trackId: String, delta: Int) -> Unit = { _, _ -> },
    val moveAudioTrack: (trackId: String, delta: Int) -> Unit = { _, _ -> },
    val removeTrack: (trackId: String) -> Unit = {},
    val removeAudioTrack: (trackId: String) -> Unit = {},
    /**
     * Clip drops from the timeline drag. A null audio target lands the
     * clip on a new lane called `newTrackName`; a refused placement leaves
     * the document untouched.
     */
    val moveVideoClip: (clipId: String, toTrackId: String, start: EditorTime) -> Unit =
        { _, _, _ -> },
    val moveAudioClip: (
        clipId: String,
        toTrackId: String?,
        start: EditorTime,
        newTrackName: String
    ) -> Unit = { _, _, _, _ -> },
    /** Arrange preference: ripple insert shifts Main clips, off anchors them. */
    val setRippleEnabled: (Boolean) -> Unit = {},
    /**
     * The visual-recipe draft seam — transform/content-mode/opacity/
     * blend/adjustments on the *selected* clip, the clip counterpart of
     * the trim draft: one gesture is one undo step, cancel restores the
     * captured sequence. [applyClipVisuals] is the one-shot form for
     * discrete picks. Both take the history label.
     */
    val beginClipEdit: (String) -> Unit = {},
    val updateClipVisuals: (VideoClipVisuals) -> Unit = {},
    val commitClipEdit: () -> Unit = {},
    val cancelClipEdit: () -> Unit = {},
    val applyClipVisuals: (String, VideoClipVisuals) -> Unit = { _, _ -> },
    /** Cancels an in-flight media import; a landed import is history. */
    val cancelImport: () -> Unit = {},
    val undo: () -> Unit,
    val redo: () -> Unit,
    val saveCopy: () -> Unit,
    val cancelExport: () -> Unit,
    val clearError: () -> Unit,
    val retryOpen: () -> Unit,
    val close: () -> Unit,
    /**
     * Host picker seam for adding another video source — the content asks
     * for media at an [VideoInsertion] point and the host (system document
     * picker, file dialog) resolves URIs back into the session. The V1b
     * dock owns the visible affordance; hosts that cannot pick media leave
     * the default no-op.
     */
    val requestAddMedia: (VideoInsertion) -> Unit = {},
    /**
     * Whether the host wired a media picker — without one the Media
     * destination and the empty state's Add media stay disabled instead
     * of pretending to open a picker.
     */
    val mediaPickerAvailable: Boolean = true,

    /**
     * Host picker seam for adding an audio source — the content asks for
     * an audio file and the host (document picker, file dialog) resolves
     * the URI back through `session.addAudioSource`. Hosts that cannot
     * pick audio leave the default no-op.
     */
    val requestAddAudio: () -> Unit = {},

    /** Audio-lane selection — the inspector/routing surfaces' targets. */
    val selectAudioClip: (String) -> Unit = {},
    val selectAudioTrack: (String) -> Unit = {},

    /**
     * The audio-recipe draft seam — gain/pan/fades/routing on lanes and
     * the master fader, one gesture is one undoable step. The update
     * calls carry explicit (track, clip) targets because a mixer can
     * touch several lanes inside one drag.
     */
    val beginAudioEdit: (String) -> Unit = {},
    val updateAudioClipEdit: (String, String, AudioClipEdit) -> Unit =
        { _, _, _ -> },
    val updateAudioTrackEdit: (String, AudioTrackEdit) -> Unit = { _, _ -> },
    val updateMasterGain: (Float) -> Unit = {},
    val commitAudioEdit: () -> Unit = {},
    val cancelAudioEdit: () -> Unit = {},

    /** One-shot audio writes — toggles and committed values, one step. */
    val applyAudioClipEdit: (String, String, String, AudioClipEdit) -> Unit =
        { _, _, _, _ -> },
    val applyAudioTrackEdit: (String, String, AudioTrackEdit) -> Unit =
        { _, _, _ -> },
    val setMasterGain: (Float) -> Unit = {},
    val setLimiterEnabled: (Boolean) -> Unit = {},
    val setSourceSoundMuted: (Boolean) -> Unit = {},
    /** Link/detach a video clip's embedded sound — (trackId, clipId). */
    val setAudioLink: (String, String, Boolean) -> Unit = { _, _, _ -> },
    /** Extracts the selected video clip's sound onto a new audio lane. */
    val extractAudio: () -> Unit = {},

    /**
     * The voice-over seam: the surface refreshes capability on entry,
     * [requestMicrophone] is the host's runtime-permission prompt seam
     * (desktop hosts satisfy it in-process), and the begin/stop/cancel
     * trio drives the take lifecycle. Begin carries (countInMs, monitor).
     */
    val refreshVoicePermission: () -> Unit = {},
    val requestMicrophone: () -> Unit = {},
    val beginVoiceRecording: (Long, Boolean) -> Unit = { _, _ -> },
    val stopVoiceRecording: () -> Unit = {},
    val cancelVoiceRecording: () -> Unit = {},

    /**
     * The blocked-restore seam: [retryRecovery] re-probes every missing
     * source, [discardRecovery] abandons the pending snapshot and keeps
     * the freshly opened source, and [requestRelink] asks the host to
     * pick a replacement for one missing reference — the host feeds it
     * back to
     * [com.dot.gallery.editor.render.video.VideoEditorSession.relinkRestore].
     * No-op defaults keep previews/tests compilable.
     */
    val retryRecovery: () -> Unit = {},
    val discardRecovery: () -> Unit = {},
    val requestRelink: (String) -> Unit = {}
)
