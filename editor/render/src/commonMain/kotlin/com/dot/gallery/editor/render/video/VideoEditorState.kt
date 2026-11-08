package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.SrgbPixels
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * The whole UI-facing snapshot of a video editing session. `position` is a
 * timeline instant; `preview` is the latest decoded frame for it. `muted` is
 * a preview preference — it never alters the sequence or the export.
 * `buffering` marks a started playback run that has not produced its first
 * frame yet. `thumbnails` holds up to four decoded filmstrip tiles per clip
 * — the [VideoThumbnail.sampleTimes] instants — arriving incrementally as
 * the bounded sampler decodes them (empty while it runs or when a clip has
 * none yet). `exported` keeps the last
 * *published* result: a later failed or cancelled export never erases it.
 */
data class VideoEditorState(
    val opening: Boolean = false,
    val info: VideoInfo? = null,
    /** Probed facts per sequence source id — [info] stays the primary source's. */
    val sourceInfos: PersistentMap<String, VideoInfo> = persistentMapOf(),
    /** Probed facts per sequence audio-source id — the audio lanes' inputs. */
    val audioSourceInfos: PersistentMap<String, AudioInfo> = persistentMapOf(),
    val sequence: VideoSequence? = null,
    /** A source import is probing/decoding before its one history step lands. */
    val importing: Boolean = false,
    val selectedClipId: String? = null,
    /** The selected audio-lane clip — independent of the video selection. */
    val selectedAudioClipId: String? = null,
    /** The selected audio track — the routing surface's target. */
    val selectedAudioTrackId: String? = null,
    /** Mic capability for the voice-over surface — refreshed on entry. */
    val voiceSupport: VoiceSupport = VoiceSupport.Unknown,
    /** The capture input's display label — the surface's Input row. */
    val voiceInput: String? = null,
    /** A live voice-over take — count-in, recording or finalizing. */
    val voiceRecording: VoiceRecordingState? = null,
    val position: EditorTime = EditorTime.Zero,
    val preview: SrgbPixels? = null,
    /** Peak summaries per audio-source id — the A-lane waveforms. */
    val waveforms: PersistentMap<String, FloatArray> = persistentMapOf(),
    /** The playback mix meter — zero while stopped or before the first block. */
    val levels: VideoLevels = VideoLevels(),
    val playing: Boolean = false,
    val seeking: Boolean = false,
    val buffering: Boolean = false,
    val muted: Boolean = false,
    val thumbnails: PersistentList<VideoThumbnail> = persistentListOf(),
    val exporting: Boolean = false,
    val exportCancelling: Boolean = false,
    val exportProgress: Float? = null,
    /** A live trim gesture owns the sequence — drafts are never persisted. */
    val isDrafting: Boolean = false,
    /**
     * Whether Main-lane inserts shift later clips right (packed timeline)
     * or keep them anchored — an arrange preference, never an edit.
     */
    val rippleEnabled: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isDirty: Boolean = false,
    val error: VideoErrorKind? = null,
    /**
     * A project restore is blocked on missing media — the snapshot is
     * held pending and the recovery surface offers Retry/Discard/Relink;
     * the freshly opened source stays untouched behind it.
     */
    val recovery: VideoRecovery? = null,
    val exported: VideoExportResult? = null
)
