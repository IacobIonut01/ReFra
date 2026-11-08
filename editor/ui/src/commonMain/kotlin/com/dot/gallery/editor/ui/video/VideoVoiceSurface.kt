package com.dot.gallery.editor.ui.video

import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VoiceRecordingState
import com.dot.gallery.editor.render.video.VoiceSupport
import com.dot.gallery.editor.ui.NumericEntryDialog
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_audio_seconds
import com.dot.gallery.editor.ui.generated.resources.editor_mic_denied
import com.dot.gallery.editor.ui.generated.resources.editor_mic_not_now
import com.dot.gallery.editor.ui.generated.resources.editor_mic_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_mic_allow
import com.dot.gallery.editor.ui.generated.resources.editor_video_mic_body
import com.dot.gallery.editor.ui.generated.resources.editor_video_mic_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_voice_recording_hint
import com.dot.gallery.editor.ui.generated.resources.editor_video_voice_starts_at
import com.dot.gallery.editor.ui.generated.resources.editor_voice_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_voice_checking
import com.dot.gallery.editor.ui.generated.resources.editor_voice_countin
import com.dot.gallery.editor.ui.generated.resources.editor_voice_counting
import com.dot.gallery.editor.ui.generated.resources.editor_voice_finishing
import com.dot.gallery.editor.ui.generated.resources.editor_voice_input
import com.dot.gallery.editor.ui.generated.resources.editor_voice_level
import com.dot.gallery.editor.ui.generated.resources.editor_voice_monitor
import com.dot.gallery.editor.ui.generated.resources.editor_voice_monitor_sub
import com.dot.gallery.editor.ui.generated.resources.editor_voice_record
import com.dot.gallery.editor.ui.generated.resources.editor_voice_stop
import com.dot.gallery.editor.ui.generated.resources.editor_voice_unavailable
import com.dot.gallery.editor.ui.trimTrailingZeros
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VOICE_INPUT = "videoEditor.voice.input"
internal const val TAG_VOICE_COUNTIN = "videoEditor.voice.countIn"
internal const val TAG_VOICE_MONITOR = "videoEditor.voice.monitor"
internal const val TAG_VOICE_RECORD = "videoEditor.voice.record"
internal const val TAG_VOICE_STOP = "videoEditor.voice.stop"
internal const val TAG_VOICE_CANCEL = "videoEditor.voice.cancel"
internal const val TAG_VOICE_LEVEL = "videoEditor.voice.level"
internal const val TAG_VOICE_STATUS = "videoEditor.voice.status"
internal const val TAG_MIC_CONTINUE = "videoEditor.mic.continue"
internal const val TAG_MIC_NOT_NOW = "videoEditor.mic.notNow"
internal const val TAG_MIC_CARD = "videoEditor.mic.card"

private const val VOICE_LEVEL_BARS = 28

/**
 * The Voice-over panel. On entry it re-queries mic support through the
 * session and then paints exactly one of:
 * - the permission card — rationale, Allow (the host's real runtime
 *   prompt), Not now — when the platform needs asking, with the honest
 *   "enable it in system settings" copy after a refusal;
 * - the recorder — the level strip, the take timer and the record button
 *   that morphs from cookie to squircle, over the Input, Count-in and
 *   Monitor settings — when the mic is ready;
 * - the unavailable note — when the host has no recorder at all.
 *
 * Stop lands the take as a real "Voice-over" lane; Cancel deletes it.
 */
@Composable
internal fun VideoVoiceSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val workspace = LocalVideoWorkspace.current
    val latestActions by rememberUpdatedState(actions)
    var countInSeconds by rememberSaveable { mutableStateOf(3f) }
    var monitor by rememberSaveable { mutableStateOf(true) }
    var countInDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { latestActions.refreshVoicePermission() }

    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val recording = state.voiceRecording
        val support = state.voiceSupport
        when {
            recording != null || support == VoiceSupport.Ready -> {
                VoiceRecorderBlock(
                    recording = recording,
                    positionText = formatVideoTimecode(state.position),
                    canStart = !state.opening && !state.isDrafting &&
                        !state.importing && !state.exporting,
                    onStart = {
                        actions.beginVoiceRecording((countInSeconds * 1000f).toLong(), monitor)
                    },
                    actions = actions
                )
                if (recording == null) {
                    VideoV4Row(
                        label = stringResource(Res.string.editor_voice_input),
                        value = state.voiceInput
                            ?: stringResource(Res.string.editor_mic_title),
                        modifier = Modifier.testTag(TAG_VOICE_INPUT)
                    )
                    VideoV4Row(
                        label = stringResource(Res.string.editor_voice_countin),
                        value = stringResource(
                            Res.string.editor_audio_seconds,
                            trimTrailingZeros(countInSeconds)
                        ),
                        enabled = !state.opening,
                        onClick = { countInDialog = true },
                        modifier = Modifier.testTag(TAG_VOICE_COUNTIN)
                    )
                    VideoStudioSwitchRow(
                        label = stringResource(Res.string.editor_voice_monitor),
                        supporting = stringResource(Res.string.editor_voice_monitor_sub),
                        checked = monitor,
                        enabled = !state.opening,
                        tag = TAG_VOICE_MONITOR,
                        onCheckedChange = { monitor = it }
                    )
                }
            }
            support == VoiceSupport.Unknown -> VoiceNote(
                stringResource(Res.string.editor_voice_checking)
            )
            support == VoiceSupport.Unavailable -> VoiceNote(
                stringResource(Res.string.editor_voice_unavailable)
            )
            else -> MicPermissionCard(
                denied = support == VoiceSupport.Denied,
                onContinue = { actions.requestMicrophone() },
                onNotNow = workspace?.closeTool?.let { close -> { close() } }
            )
        }
    }

    if (countInDialog) {
        NumericEntryDialog(
            label = stringResource(Res.string.editor_voice_countin),
            initialValue = countInSeconds,
            range = 0f..10f,
            onConfirm = {
                countInSeconds = it
                countInDialog = false
            },
            onDismiss = { countInDialog = false }
        )
    }
}

/** The permission card on the tertiary container — rationale, Not now, Allow. */
@Composable
private fun MicPermissionCard(
    denied: Boolean,
    onContinue: () -> Unit,
    onNotNow: (() -> Unit)?
) {
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .testTag(TAG_MIC_CARD)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.editor_video_mic_title),
            style = MaterialTheme.typography.titleMedium,
            color = ink
        )
        Text(
            text = stringResource(
                if (denied) Res.string.editor_mic_denied else Res.string.editor_video_mic_body
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = ink
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (onNotNow != null) {
                MicCardButton(
                    label = stringResource(Res.string.editor_mic_not_now),
                    container = Color.Transparent,
                    ink = ink,
                    tag = TAG_MIC_NOT_NOW,
                    onClick = onNotNow
                )
            }
            MicCardButton(
                label = stringResource(Res.string.editor_video_mic_allow),
                container = ink,
                ink = MaterialTheme.colorScheme.tertiaryContainer,
                tag = TAG_MIC_CONTINUE,
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun MicCardButton(
    label: String,
    container: Color,
    ink: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 20.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

/**
 * The level strip, take timer, record button and hint. The bars are the
 * polled input level of this take, newest last; before a take they rest
 * flat. The timer counts real elapsed time from the moment recording began.
 */
@Composable
private fun VoiceRecorderBlock(
    recording: VoiceRecordingState?,
    positionText: String,
    canStart: Boolean,
    onStart: () -> Unit,
    actions: VideoEditorActions
) {
    val phase = recording?.phase
    val capturing = phase == VoiceRecordingState.Phase.Recording
    val levels = remember { mutableStateListOf<Float>() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(capturing) {
        if (capturing) {
            levels.clear()
            val start = TimeSource.Monotonic.markNow()
            // Tenths are all the timer shows, so it never needs every frame.
            while (true) {
                elapsedMs = start.elapsedNow().inWholeMilliseconds
                delay(100)
            }
        }
    }
    val level = recording?.level ?: 0f
    LaunchedEffect(capturing, level) {
        if (capturing) {
            levels.add(level.coerceIn(0f, 1f))
            while (levels.size > VOICE_LEVEL_BARS) levels.removeAt(0)
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (recording != null) Modifier.testTag(TAG_VOICE_STATUS) else Modifier)
    ) {
        val levelLabel = stringResource(Res.string.editor_voice_level)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .semantics { contentDescription = levelLabel }
                .then(if (capturing) Modifier.testTag(TAG_VOICE_LEVEL) else Modifier)
                .padding(horizontal = 8.dp)
        ) {
            repeat(VOICE_LEVEL_BARS) { index ->
                val value = levels.getOrElse(index - (VOICE_LEVEL_BARS - levels.size)) { 0f }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((0.1f + 0.8f * value.pow(0.6f)).coerceIn(0.1f, 0.9f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (value > 0.8f) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
        Text(
            text = formatTakeTime(elapsedMs),
            fontSize = 32.sp,
            lineHeight = 40.sp,
            maxLines = 1
        )
        VoiceRecordButton(
            active = recording != null,
            label = stringResource(
                if (capturing) Res.string.editor_voice_stop else if (recording != null) {
                    Res.string.editor_voice_cancel
                } else {
                    Res.string.editor_voice_record
                }
            ),
            enabled = when (phase) {
                null -> canStart
                VoiceRecordingState.Phase.Finishing -> false
                else -> true
            },
            tag = if (capturing) TAG_VOICE_STOP else TAG_VOICE_RECORD,
            onClick = when (phase) {
                null -> onStart
                VoiceRecordingState.Phase.Recording -> actions.stopVoiceRecording
                else -> actions.cancelVoiceRecording
            }
        )
        Text(
            text = when (phase) {
                null -> stringResource(Res.string.editor_video_voice_starts_at, positionText)
                VoiceRecordingState.Phase.CountIn -> stringResource(
                    Res.string.editor_voice_counting,
                    trimTrailingZeros(ceil(recording.countInMs / 1000f))
                )
                VoiceRecordingState.Phase.Recording ->
                    stringResource(Res.string.editor_video_voice_recording_hint)
                VoiceRecordingState.Phase.Finishing ->
                    stringResource(Res.string.editor_voice_finishing)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (recording != null) {
            VideoTextAction(
                label = stringResource(Res.string.editor_voice_cancel),
                onClick = actions.cancelVoiceRecording,
                enabled = phase != VoiceRecordingState.Phase.Finishing,
                modifier = Modifier.testTag(TAG_VOICE_CANCEL)
            )
        }
    }
}

/** The 80 dp record control: a primary cookie, an error squircle in a take. */
@Composable
private fun VoiceRecordButton(
    active: Boolean,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val progress by animateFloatAsState(
        if (active) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "recordMorph"
    )
    val container by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.outlineVariant
            active -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "recordContainer"
    )
    val shape = remember(progress) { VideoMorphShape(progress) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(shape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag)
    ) {
        Icon(
            imageVector = if (active) VideoGlyphs.Stop else VideoGlyphs.Mic,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onError
            else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

/** "00:07.4" — minutes, seconds and tenths of the running take. */
private fun formatTakeTime(ms: Long): String {
    val tenths = ms / 100L
    val minutes = (tenths / 600L).toString().padStart(2, '0')
    val seconds = ((tenths / 10L) % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds.${tenths % 10L}"
}

/** The single-line note states — checking, unavailable. */
@Composable
private fun VoiceNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
