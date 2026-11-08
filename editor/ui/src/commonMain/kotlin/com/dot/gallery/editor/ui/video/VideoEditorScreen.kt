package com.dot.gallery.editor.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.dot.gallery.editor.ui.LocalEditorSettingsStore
import com.dot.gallery.editor.ui.rememberEditorSettingsStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoInsertion

/**
 * The session-bound video editor screen: collects
 * [VideoEditorSession.state] and hands [VideoEditorContent] a stable,
 * memoized [VideoEditorActions] built from the session's methods.
 * [onClose] means the user finished (saved or discarded); [onRetryOpen]
 * re-triggers the host's open pipeline after an open failure.
 * [onOpenExport] adds an "Open" affordance to the saved-copy strip — pass
 * null on hosts that cannot open the exported file. [onRequestAddMedia]
 * is the host picker seam for adding another video source at a given
 * [VideoInsertion] point — the host resolves picked URIs back through
 * [VideoEditorSession.addVideoSource]; null disables the affordance.
 *
 * Callers wrap this in their theme (GalleryTheme on Android,
 * [com.dot.gallery.editor.ui.EditorTheme] standalone). The session must be
 * opened and closed by the host.
 *
 * [closeRequest] is a host-driven close signal (a desktop window's X, for
 * example): incrementing it runs the same draft/export/dirty close flow as
 * the Back button, so nothing closes without consent.
 */
@Composable
fun VideoEditorScreen(
    session: VideoEditorSession,
    onClose: () -> Unit,
    onRetryOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExport: ((VideoExportResult) -> Unit)? = null,
    onRequestAddMedia: ((VideoInsertion) -> Unit)? = null,
    onRequestAddAudio: (() -> Unit)? = null,
    onRequestMicrophone: (() -> Unit)? = null,
    onRequestRelink: ((String) -> Unit)? = null,
    closeRequest: Int = 0
) {
    val state by session.state.collectAsState()
    val actions = remember(
        session, onClose, onRetryOpen, onRequestAddMedia, onRequestAddAudio,
        onRequestMicrophone, onRequestRelink
    ) {
        VideoEditorActions(
            selectClip = session::selectClip,
            seek = session::seek,
            play = session::play,
            pause = session::pause,
            setMuted = session::setMuted,
            trimSelected = session::trimSelected,
            beginTrim = session::beginTrim,
            updateTrim = session::updateTrim,
            commitTrim = session::commitTrim,
            cancelTrim = session::cancelTrim,
            split = session::splitAtPlayhead,
            delete = session::deleteSelected,
            duplicate = session::duplicateSelected,
            moveSelected = session::moveSelected,
            setTrackVisible = session::setTrackVisible,
            setTrackLocked = session::setTrackLocked,
            addVideoTrack = session::addVideoTrack,
            addAudioTrack = session::addAudioTrack,
            moveTrack = session::moveTrack,
            moveAudioTrack = session::moveAudioTrack,
            removeTrack = session::removeTrack,
            removeAudioTrack = session::removeAudioTrack,
            moveVideoClip = session::moveVideoClip,
            moveAudioClip = session::moveAudioClip,
            setRippleEnabled = session::setRippleEnabled,
            beginClipEdit = session::beginClipEdit,
            updateClipVisuals = session::updateClipVisuals,
            commitClipEdit = session::commitClipEdit,
            cancelClipEdit = session::cancelClipEdit,
            applyClipVisuals = session::applyClipVisuals,
            cancelImport = session::cancelImport,
            undo = session::undo,
            redo = session::redo,
            // Encoding shares the decoder path with playback — the UI
            // pauses before asking so a live preview never contends with
            // the encoder (the session also pauses defensively).
            saveCopy = { session.pause(); session.exportCopy() },
            cancelExport = session::cancelExport,
            clearError = session::clearError,
            retryOpen = onRetryOpen,
            close = onClose,
            requestAddMedia = onRequestAddMedia ?: {},
            mediaPickerAvailable = onRequestAddMedia != null,
            requestAddAudio = onRequestAddAudio ?: {},
            selectAudioClip = session::selectAudioClip,
            selectAudioTrack = session::selectAudioTrack,
            beginAudioEdit = session::beginAudioEdit,
            updateAudioClipEdit = session::updateAudioClipEdit,
            updateAudioTrackEdit = session::updateAudioTrackEdit,
            updateMasterGain = session::updateMasterGain,
            commitAudioEdit = session::commitAudioEdit,
            cancelAudioEdit = session::cancelAudioEdit,
            applyAudioClipEdit = session::applyAudioClipEdit,
            applyAudioTrackEdit = session::applyAudioTrackEdit,
            setMasterGain = session::setMasterGain,
            setLimiterEnabled = session::setLimiterEnabled,
            setSourceSoundMuted = session::setSourceSoundMuted,
            setAudioLink = session::setAudioLink,
            extractAudio = session::extractAudioFromSelectedVideo,
            refreshVoicePermission = session::refreshVoicePermission,
            requestMicrophone = onRequestMicrophone ?: {},
            beginVoiceRecording = session::beginVoiceRecording,
            stopVoiceRecording = session::stopVoiceRecording,
            cancelVoiceRecording = session::cancelVoiceRecording,
            retryRecovery = session::retryRestore,
            discardRecovery = session::discardRestore,
            requestRelink = onRequestRelink ?: {}
        )
    }
    // Gesture-intent capture (timeline scrub) reads the session's live
    // state rather than the collected one — a just-issued play()/pause()
    // can lag a recomposition frame behind the pointer down.
    // Settings that live on the session. The fill rule follows the setting
    // live. Ripple is applied each time an open completes (opening resets
    // the session state); after that the timeline's own Ripple toggle
    // writes back, so it becomes the next session's default too.
    val settingsStore = rememberEditorSettingsStore()
    val settings = settingsStore.settings
    SideEffect { session.fillSelectedEmptyAudioTrack = settings.videoFillEmptyAudioTracks }
    LaunchedEffect(session, settingsStore) {
        var applied = false
        session.state.collect { s ->
            when {
                s.opening || s.sequence == null -> applied = false
                !applied -> {
                    applied = true
                    session.setRippleEnabled(settingsStore.settings.videoRipple)
                }
                else -> settingsStore.update { it.copy(videoRipple = s.rippleEnabled) }
            }
        }
    }
    CompositionLocalProvider(
        LocalVideoLiveState provides { session.state.value },
        LocalEditorSettingsStore provides settingsStore
    ) {
        com.dot.gallery.editor.ui.VideoWorkspaceTheme {
            VideoEditorContent(
                state = state,
                actions = actions,
                modifier = modifier,
                onOpenExport = onOpenExport,
                closeRequest = closeRequest
            )
        }
    }
}
