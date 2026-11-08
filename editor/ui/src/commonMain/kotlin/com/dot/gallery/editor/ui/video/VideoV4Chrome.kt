package com.dot.gallery.editor.ui.video

import androidx.compose.runtime.Composable
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_video_zoom_fit_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_zoom_value
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Which sheet the workspace shows over the studio, if any. */
internal enum class VideoPanel { Timeline, Tools, Layers, Media, Audio }

/** The timeline-zoom readout — Fit at 1×, otherwise one decimal. */
@Composable
internal fun videoZoomLabel(zoom: Float): String =
    if (zoom <= 1.001f) {
        stringResource(Res.string.editor_video_zoom_fit_label)
    } else {
        val tenths = (zoom * 10f).roundToInt()
        val text = if (tenths % 10 == 0) "${tenths / 10}"
        else "${tenths / 10}.${tenths % 10}"
        stringResource(Res.string.editor_video_zoom_value, text)
    }
