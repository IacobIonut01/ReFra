package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoErrorKind
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_export_cancelling
import com.dot.gallery.editor.ui.generated.resources.editor_open_export
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_aac
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_none
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_audio_row
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_bitrate
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_bitrate_auto
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_codec_value
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_failed_body
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_failed_sheet
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_format
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_fps_constant
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_frame_rate
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_hide
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_new_copy
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_output
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_size
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_stopped
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_video
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_writing
import com.dot.gallery.editor.ui.generated.resources.editor_video_exporting_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_return_to_editor
import com.dot.gallery.editor.ui.generated.resources.editor_video_saved_movies
import com.dot.gallery.editor.ui.generated.resources.editor_video_saved_title
import com.dot.gallery.editor.ui.generated.resources.editor_video_try_again
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_EXPORT_SHEET = "videoEditor.exportSheet"
internal const val TAG_VIDEO_EXPORT_CONFIRM = "videoEditor.export.confirm"
internal const val TAG_VIDEO_EXPORT_HIDE = "videoEditor.export.hide"
internal const val TAG_VIDEO_EXPORT_SHEET_CANCEL = "videoEditor.export.sheetCancel"
internal const val TAG_VIDEO_EXPORT_RETRY = "videoEditor.export.retry"
internal const val TAG_VIDEO_EXPORT_RETURN = "videoEditor.export.return"
internal const val TAG_VIDEO_EXPORT_DONE = "videoEditor.export.done"
internal const val TAG_VIDEO_EXPORT_ROW_PREFIX = "videoEditor.export.row."

/** Which face of the export sheet is showing. */
internal enum class VideoExportStage { Options, Progress, Done, Failed }

/** One read-only fact of the output contract the exporter honours. */
internal data class VideoExportFact(val key: String, val label: String, val value: String)

/**
 * The output facts — exactly what the current H.264/AAC MP4 exporter
 * writes: the sequence canvas size, a constant frame rate, automatic
 * bit rate, stereo 48 kHz AAC when any sound reaches the mix, and a new
 * copy beside the untouched source. Nothing here is a choice the encoder
 * cannot honour, so the rows are facts rather than pickers.
 */
@Composable
internal fun videoExportFacts(state: VideoEditorState): List<VideoExportFact> {
    val sequence = state.sequence ?: return emptyList()
    val fps = (sequence.frameRate.numerator.toDouble() /
        sequence.frameRate.denominator).roundToInt()
    val sourceSound = !sequence.sourceSoundMuted &&
        (state.info?.hasAudio == true ||
            state.sourceInfos.values.any { it.hasAudio })
    val hasAudio = sourceSound || sequence.audioTracks.any { it.clips.isNotEmpty() }
    return listOf(
        VideoExportFact(
            "resolution",
            stringResource(Res.string.editor_video_export_size),
            "${sequence.size.width} × ${sequence.size.height}"
        ),
        VideoExportFact(
            "frameRate",
            stringResource(Res.string.editor_video_export_frame_rate),
            stringResource(Res.string.editor_video_export_fps_constant, fps)
        ),
        VideoExportFact(
            "codec",
            stringResource(Res.string.editor_video_export_format),
            stringResource(Res.string.editor_video_export_codec_value)
        ),
        VideoExportFact(
            "bitrate",
            stringResource(Res.string.editor_video_export_bitrate),
            stringResource(Res.string.editor_video_export_bitrate_auto)
        ),
        VideoExportFact(
            "audio",
            stringResource(Res.string.editor_video_export_audio_row),
            stringResource(
                if (hasAudio) Res.string.editor_video_export_audio_aac
                else Res.string.editor_video_export_audio_none
            )
        ),
        VideoExportFact(
            "destination",
            stringResource(Res.string.editor_video_export_output),
            stringResource(Res.string.editor_video_export_new_copy)
        )
    )
}

/**
 * The face for one export run. [baselineReference] is the receipt that
 * existed before this run started, so an older saved copy never reads as
 * this run's success; a run that ended with neither a new receipt nor an
 * export error was cancelled and returns to the options.
 */
internal fun videoExportStage(
    state: VideoEditorState,
    started: Boolean,
    baselineReference: String? = null
): VideoExportStage = when {
    state.exporting -> VideoExportStage.Progress
    !started -> VideoExportStage.Options
    state.error == VideoErrorKind.Export -> VideoExportStage.Failed
    state.exported != null &&
        state.exported?.reference != baselineReference -> VideoExportStage.Done
    else -> VideoExportStage.Options
}

/**
 * The export surface body — the options face (facts + Export video) or
 * the live progress/done/failed faces of one export run. Also the
 * registry content of the Deliver › Export tool on wide inspectors. Inside
 * [VideoExportSheet] it hands the sheet the title of the current face.
 */
@Composable
internal fun VideoExportBody(
    state: VideoEditorState,
    actions: VideoEditorActions,
    stage: VideoExportStage,
    onExport: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExport: ((VideoExportResult) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") showTitle: Boolean = true
) {
    val enabled = (state.sequence?.duration?.ticks ?: 0L) > 0L &&
        !state.exporting && !state.opening && !state.isDrafting
    val titleHost = LocalVideoExportTitle.current
    if (titleHost != null) {
        val title = when (stage) {
            VideoExportStage.Options -> stringResource(Res.string.editor_video_export_video)
            VideoExportStage.Progress -> stringResource(
                if (state.exportCancelling) Res.string.editor_export_cancelling
                else Res.string.editor_video_exporting_title
            )
            VideoExportStage.Done -> stringResource(Res.string.editor_video_saved_title)
            VideoExportStage.Failed -> stringResource(Res.string.editor_video_export_failed_sheet)
        }
        SideEffect { titleHost.title = title }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (titleHost == null) Modifier.videoSurfaceInset() else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (stage) {
            VideoExportStage.Options -> {
                val facts = videoExportFacts(state)
                Column(verticalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
                    facts.forEachIndexed { index, fact ->
                        ExportFactRow(
                            fact = fact,
                            shape = exportFactShape(index, facts.size),
                            modifier = Modifier.testTag(TAG_VIDEO_EXPORT_ROW_PREFIX + fact.key)
                        )
                    }
                }
                VideoPrimaryAction(
                    label = stringResource(Res.string.editor_video_export_video),
                    icon = VideoGlyphs.Movie,
                    onClick = onExport,
                    enabled = enabled,
                    height = 64.dp,
                    textSize = 18.sp,
                    modifier = Modifier.testTag(TAG_VIDEO_EXPORT_CONFIRM)
                )
            }
            VideoExportStage.Progress -> {
                val fraction = (state.exportProgress ?: 0f).coerceIn(0f, 1f)
                Text(
                    text = "${(fraction * 100f).roundToInt()}%",
                    fontSize = 64.sp,
                    lineHeight = 72.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                VideoWavyProgress(
                    fraction = fraction,
                    playing = !state.exportCancelling,
                    strokeWidth = 6.dp,
                    amplitude = 4.dp,
                    height = 20.dp
                )
                ExportBody(stringResource(Res.string.editor_video_export_writing))
                ExportActions {
                    VideoTextAction(
                        label = stringResource(Res.string.editor_video_export_hide),
                        onClick = onHide,
                        modifier = Modifier.testTag(TAG_VIDEO_EXPORT_HIDE)
                    )
                    VideoTextAction(
                        label = stringResource(Res.string.editor_cancel_export),
                        onClick = actions.cancelExport,
                        tonal = true,
                        enabled = !state.exportCancelling,
                        modifier = Modifier.testTag(TAG_VIDEO_EXPORT_SHEET_CANCEL)
                    )
                }
            }
            VideoExportStage.Done -> {
                val result = state.exported
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(VideoCookieShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            VideoGlyphs.Check, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        val name = result?.reference?.substringAfterLast('/')
                            ?.takeIf { it.isNotEmpty() }
                        if (name != null) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ExportBody(stringResource(Res.string.editor_video_saved_movies))
                    }
                }
                ExportActions {
                    VideoTextAction(
                        label = stringResource(Res.string.editor_done),
                        onClick = onHide,
                        modifier = Modifier.testTag(TAG_VIDEO_EXPORT_DONE)
                    )
                    if (onOpenExport != null && result != null) {
                        VideoTextAction(
                            label = stringResource(Res.string.editor_open_export),
                            onClick = { onOpenExport(result) },
                            filled = true,
                            modifier = Modifier.testTag(TAG_VIDEO_EXPORT_OPEN)
                        )
                    }
                }
            }
            VideoExportStage.Failed -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.editor_video_export_stopped),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(Res.string.editor_video_export_failed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                ExportActions {
                    VideoTextAction(
                        label = stringResource(Res.string.editor_video_return_to_editor),
                        onClick = {
                            actions.clearError()
                            onHide()
                        },
                        modifier = Modifier.testTag(TAG_VIDEO_EXPORT_RETURN)
                    )
                    VideoTextAction(
                        label = stringResource(Res.string.editor_video_try_again),
                        onClick = {
                            actions.clearError()
                            onExport()
                        },
                        filled = true,
                        enabled = enabled,
                        modifier = Modifier.testTag(TAG_VIDEO_EXPORT_RETRY)
                    )
                }
            }
        }
    }
}

/** First and last fact rows round the group's ends; the rest join squarely. */
private fun exportFactShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 20.dp else VideoStudio.JoinCorner
    val bottom = if (index == count - 1) 20.dp else VideoStudio.JoinCorner
    return RoundedCornerShape(top, top, bottom, bottom)
}

@Composable
private fun ExportFactRow(
    fact: VideoExportFact,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = fact.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = fact.value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ExportActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun ExportBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Where the sheet's title lives, so the body can name the face it shows. */
@Stable
private class VideoExportTitleHost {
    var title by mutableStateOf("")
}

private val LocalVideoExportTitle = staticCompositionLocalOf<VideoExportTitleHost?> { null }

/**
 * The export sheet host — the studio sheet: a bottom sheet on compact
 * windows, a centred 28 dp card elsewhere. The hosted [VideoExportBody]
 * publishes the title of its current face.
 */
@Composable
internal fun VideoExportSheet(
    compact: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val titleHost = remember { VideoExportTitleHost() }
    VideoStudioSheet(
        title = titleHost.title,
        phone = compact,
        onDismiss = onDismiss,
        tag = TAG_VIDEO_EXPORT_SHEET,
        width = 440.dp
    ) {
        CompositionLocalProvider(LocalVideoExportTitle provides titleHost) {
            content()
        }
    }
}
