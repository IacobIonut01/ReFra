package com.dot.gallery.editor.ui.video

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VoiceSupport
import com.dot.gallery.editor.ui.EditorSettingRow
import com.dot.gallery.editor.ui.EditorSettingsPage
import com.dot.gallery.editor.ui.EditorSettingsSection
import com.dot.gallery.editor.ui.EditorSettingsStore
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_settings_audio
import com.dot.gallery.editor.ui.generated.resources.editor_settings_export
import com.dot.gallery.editor.ui.generated.resources.editor_settings_export_footer
import com.dot.gallery.editor.ui.generated.resources.editor_settings_fill_tracks
import com.dot.gallery.editor.ui.generated.resources.editor_settings_fill_tracks_summary
import com.dot.gallery.editor.ui.generated.resources.editor_settings_mic_allowed
import com.dot.gallery.editor.ui.generated.resources.editor_settings_mic_ask
import com.dot.gallery.editor.ui.generated.resources.editor_settings_mic_denied
import com.dot.gallery.editor.ui.generated.resources.editor_settings_mic_unavailable
import com.dot.gallery.editor.ui.generated.resources.editor_settings_microphone
import com.dot.gallery.editor.ui.generated.resources.editor_settings_snap
import com.dot.gallery.editor.ui.generated.resources.editor_settings_snap_summary
import com.dot.gallery.editor.ui.generated.resources.editor_settings_timeline
import com.dot.gallery.editor.ui.generated.resources.editor_settings_video_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_aac
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_none
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_row
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_bitrate
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_bitrate_auto
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_codec_value
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_format
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_fps_constant
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_frame_rate
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_resolution
import com.dot.gallery.editor.ui.generated.resources.editor_video_ripple_editing
import com.dot.gallery.editor.ui.generated.resources.editor_video_ripple_summary
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal const val TAG_SETTINGS_SNAP = "videoEditor.settings.snap"
internal const val TAG_SETTINGS_RIPPLE = "videoEditor.settings.ripple"
internal const val TAG_SETTINGS_FILL_TRACKS = "videoEditor.settings.fillTracks"
internal const val TAG_SETTINGS_MIC = "videoEditor.settings.microphone"

/**
 * The Video editor settings page: the timeline's drag and insert
 * behaviour, the microphone grant, and what the exporter writes. Opened
 * from the More sheet.
 */
@Composable
internal fun VideoEditorSettingsPage(
    store: EditorSettingsStore,
    state: VideoEditorState,
    actions: VideoEditorActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = store.settings
    val sequence = state.sequence
    val fps = sequence?.frameRate?.let { (it.numerator.toDouble() / it.denominator).roundToInt() }
    val hasAudio = state.info?.hasAudio == true || state.sourceInfos.values.any { it.hasAudio } ||
        sequence?.audioTracks?.any { it.clips.isNotEmpty() } == true
    val mic = when (state.voiceSupport) {
        VoiceSupport.Ready -> Res.string.editor_settings_mic_allowed
        VoiceSupport.Denied -> Res.string.editor_settings_mic_denied
        VoiceSupport.Unavailable -> Res.string.editor_settings_mic_unavailable
        VoiceSupport.RequiresPermission, VoiceSupport.Unknown -> Res.string.editor_settings_mic_ask
    }
    val micAskable = state.voiceSupport == VoiceSupport.RequiresPermission ||
        state.voiceSupport == VoiceSupport.Unknown
    EditorSettingsPage(
        title = stringResource(Res.string.editor_settings_video_title),
        onBack = onBack,
        modifier = modifier,
        footer = stringResource(Res.string.editor_settings_export_footer),
        sections = listOf(
            EditorSettingsSection(
                stringResource(Res.string.editor_settings_timeline),
                listOf(
                    EditorSettingRow.Toggle(
                        Icons.Outlined.VerticalAlignCenter,
                        stringResource(Res.string.editor_settings_snap),
                        stringResource(Res.string.editor_settings_snap_summary),
                        TAG_SETTINGS_SNAP,
                        checked = settings.videoSnap,
                        onCheckedChange = { on -> store.update { it.copy(videoSnap = on) } }
                    ),
                    EditorSettingRow.Toggle(
                        Icons.Outlined.SwapHoriz,
                        stringResource(Res.string.editor_video_ripple_editing),
                        stringResource(Res.string.editor_video_ripple_summary),
                        TAG_SETTINGS_RIPPLE,
                        checked = settings.videoRipple,
                        onCheckedChange = { on ->
                            store.update { it.copy(videoRipple = on) }
                            actions.setRippleEnabled(on)
                        }
                    ),
                    EditorSettingRow.Toggle(
                        Icons.AutoMirrored.Outlined.PlaylistAdd,
                        stringResource(Res.string.editor_settings_fill_tracks),
                        stringResource(Res.string.editor_settings_fill_tracks_summary),
                        TAG_SETTINGS_FILL_TRACKS,
                        checked = settings.videoFillEmptyAudioTracks,
                        onCheckedChange = { on ->
                            store.update { it.copy(videoFillEmptyAudioTracks = on) }
                        }
                    )
                )
            ),
            EditorSettingsSection(
                stringResource(Res.string.editor_settings_audio),
                listOf(
                    EditorSettingRow.Action(
                        Icons.Outlined.Mic,
                        stringResource(Res.string.editor_settings_microphone), null,
                        TAG_SETTINGS_MIC,
                        value = stringResource(mic),
                        enabled = micAskable,
                        onClick = actions.requestMicrophone
                    )
                )
            ),
            EditorSettingsSection(
                stringResource(Res.string.editor_settings_export),
                listOfNotNull(
                    EditorSettingRow.Info(
                        Icons.Outlined.Movie,
                        stringResource(Res.string.editor_video_export_format),
                        "videoEditor.settings.format",
                        stringResource(Res.string.editor_video_export_codec_value)
                    ),
                    sequence?.let {
                        EditorSettingRow.Info(
                            Icons.Outlined.HighQuality,
                            stringResource(Res.string.editor_video_export_resolution),
                            "videoEditor.settings.resolution",
                            "${it.size.width} × ${it.size.height}"
                        )
                    },
                    fps?.let {
                        EditorSettingRow.Info(
                            Icons.Outlined.Timer,
                            stringResource(Res.string.editor_video_export_frame_rate),
                            "videoEditor.settings.frameRate",
                            stringResource(Res.string.editor_video_export_fps_constant, it)
                        )
                    },
                    EditorSettingRow.Info(
                        Icons.Outlined.Speaker,
                        stringResource(Res.string.editor_video_export_audio_row),
                        "videoEditor.settings.audio",
                        stringResource(
                            if (hasAudio) Res.string.editor_video_export_audio_aac
                            else Res.string.editor_video_export_audio_none
                        )
                    ),
                    EditorSettingRow.Info(
                        Icons.Outlined.Tune,
                        stringResource(Res.string.editor_video_export_bitrate),
                        "videoEditor.settings.bitrate",
                        stringResource(Res.string.editor_video_export_bitrate_auto)
                    )
                )
            )
        )
    )
}
