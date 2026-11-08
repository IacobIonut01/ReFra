/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.VideoFile
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.feature.viewer.InfoRow
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.feature.viewer.components.video.DolbyVisionPlayback
import com.dot.gallery.feature.viewer.components.video.VideoHdrInfo
import com.dot.gallery.feature.viewer.components.video.VideoHdrType
import com.dot.gallery.feature.viewer.components.video.dolbyVisionPlayback
import com.dot.gallery.app.presentation.util.formatSize
import com.dot.gallery.core.presentation.util.toBitrateString
import java.text.SimpleDateFormat
import java.util.Locale
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.uri.path
import com.dot.gallery.data.entity.imageMp
import com.dot.gallery.domain.date.formatMinSec
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.at_kbps
import com.dot.gallery.generated.resources.dimensions
import com.dot.gallery.generated.resources.duration
import com.dot.gallery.generated.resources.frame_rate
import com.dot.gallery.generated.resources.label
import com.dot.gallery.generated.resources.path
import com.dot.gallery.generated.resources.ppcm
import com.dot.gallery.generated.resources.ppi
import com.dot.gallery.generated.resources.resolution_with_mp
import com.dot.gallery.generated.resources.taken_on
import com.dot.gallery.generated.resources.type_size
import com.dot.gallery.generated.resources.video_format
import com.dot.gallery.generated.resources.video_format_dolby_vision
import com.dot.gallery.generated.resources.video_format_dolby_vision_compatible
import com.dot.gallery.generated.resources.video_format_dolby_vision_unsupported
import com.dot.gallery.generated.resources.video_format_hdr10
import com.dot.gallery.generated.resources.video_format_hlg
import com.dot.gallery.generated.resources.video_fps
import com.dot.gallery.core.util.getString


fun Media.retrieveMetadata(
    context: Context,
    exifDateFormat: String,
    mediaMetadata: MediaMetadata?,
    onLabelClick: () -> Unit,
    videoHdr: VideoHdrInfo = VideoHdrInfo.NONE
): List<InfoRow> {
    val info = mutableListOf<InfoRow>()

    // 1) Label (editable)
    info += InfoRow(
        icon = Icons.Outlined.Photo,
        trailingIcon = Icons.Outlined.Edit,
        label = context.getString(Res.string.label),
        onClick = onLabelClick,
        content = label
    )

    // 2) Full path
    info += InfoRow(
        icon = Icons.Outlined.FolderOpen,
        label = context.getString(Res.string.path),
        content = path.substringBeforeLast("/")
    )

    mediaMetadata?.let { md ->
        // 6) DateTimeOriginal
        md.dateTimeOriginal?.let {
            val formattedDate = try {
                val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val date = parser.parse(it)
                if (date != null) {
                    val formatter = SimpleDateFormat(exifDateFormat, Locale.getDefault())
                    formatter.format(date)
                } else it
            } catch (_: Exception) {
                it
            }
            info += InfoRow(
                icon = Icons.Outlined.CalendarToday,
                label = context.getString(Res.string.taken_on),
                content = formattedDate
            )
        }

        fun formatExposureTime(exposure: String?): String? {
            if (exposure == null) return null
            val fractionPart = exposure.removeSuffix(" sec")
            val parts = fractionPart.split("/")
            if (parts.size != 2) return exposure
            val numerator = parts[0].toDoubleOrNull() ?: return exposure
            val denominator = parts[1].toDoubleOrNull() ?: return exposure
            val value = numerator / denominator

            var bestNumerator = 1
            var bestDenominator = 1
            var minDiff = Double.MAX_VALUE

            for (den in 1..1000) {
                val num = (value * den).toInt()
                val diff = kotlin.math.abs(value - num.toDouble() / den)
                if (diff < minDiff) {
                    minDiff = diff
                    bestNumerator = num
                    bestDenominator = den
                }
            }
            return "${bestNumerator}/${bestDenominator} sec"
        }

        // 7) Camera + settings (merged with lens info)
        val cam = listOfNotNull(
            md.manufacturerName,
            md.modelName
        ).joinToString(" ")
        val camDetails = listOfNotNull(
            md.aperture,
            md.focalLength?.takeIf { it > 0 }?.let { fl ->
                if (fl == fl.toLong().toDouble()) "${fl.toLong()} mm" else "$fl mm"
            },
            formatExposureTime(md.exposureTime),
            md.iso?.let { "ISO $it" }
        ).joinToString(" • ")
        if (cam.isNotBlank()) {
            info += InfoRow(
                icon = Icons.Outlined.PhotoCamera,
                label = cam,
                content = camDetails
            )
        }

        // 8) Image dimensions & megapixels & Image resolution (PPI)
        if (md.imageWidth > 0 && md.imageHeight > 0) {
            var content = context.getString(
                Res.string.resolution_with_mp,
                md.imageWidth,
                md.imageHeight,
                md.imageMp
            )
            val resX = md.imageResolutionX
            val resY = md.imageResolutionY
            if (resX != null && resY != null) {
                val unit = when (md.resolutionUnit) {
                    2 -> context.getString(Res.string.ppi)
                    3 -> context.getString(Res.string.ppcm)
                    else -> ""
                }
                // Built here rather than from ppi_resolution_string: Compose resources keep
                // its XML quotes and do not expand the %1$.1f float placeholders.
                content += " • ${"%.1f".format(resX)} × ${"%.1f".format(resY)} $unit".trimEnd()
            }
            if (size > 0) {
                content += " • ${formatSize(size)}"
            }
            info += InfoRow(
                icon = Icons.Outlined.PhotoSizeSelectLarge,
                label = context.getString(Res.string.dimensions),
                content = content
            )
        } else {
            // If no dimensions, just show size if available
            if (size > 0) {
                info += InfoRow(
                    icon = Icons.Outlined.Info,
                    label = context.getString(Res.string.type_size),
                    content = formatSize(size)
                )
            }
        }

        // 9) Video fields
        if (isVideo) {
            md.durationMs?.takeIf { it > 0 }?.let {
                info += InfoRow(
                    icon = Icons.Outlined.VideoFile,
                    label = context.getString(Res.string.duration),
                    content = it.formatMinSec()
                )
            }
            if (md.videoWidth != null && md.videoHeight != null) {
                info += InfoRow(
                    icon = Icons.Outlined.VideoFile,
                    label = context.getString(Res.string.dimensions),
                    content = "${md.videoWidth} × ${md.videoHeight}"
                )
            }
            md.frameRate?.let {
                var content = context.getString(Res.string.video_fps, it)
                md.bitRate?.let {
                    content += context.getString(Res.string.at_kbps, it.toBitrateString())
                }
                info += InfoRow(
                    icon = Icons.Outlined.Info,
                    label = context.getString(Res.string.frame_rate),
                    content = content
                )
            }
        }

    }

    // Floor rows: a missing metadata row must never hide the file itself. Size and
    // duration already come from the MediaStore projection, so surface them even
    // when every parser failed.
    if (mediaMetadata == null && size > 0) {
        info += InfoRow(
            icon = Icons.Outlined.Info,
            label = context.getString(Res.string.type_size),
            content = formatSize(size)
        )
    }
    if (isVideo && (mediaMetadata?.durationMs ?: 0L) <= 0L) {
        duration.formatMinSec().takeIf { it.isNotBlank() }?.let {
            info += InfoRow(
                icon = Icons.Outlined.VideoFile,
                label = context.getString(Res.string.duration),
                content = it
            )
        }
    }

    // HDR classification of the playing track, published by the player. Kept outside
    // the metadata scope so the row still renders when no parsed metadata exists. For Dolby
    // Vision the label also reflects how the device actually renders it.
    if (isVideo && videoHdr.isHdr) {
        val formatLabel = when (videoHdr.type) {
            VideoHdrType.DOLBY_VISION -> when (
                dolbyVisionPlayback(
                    videoHdr,
                    HdrCapabilities.hasDolbyVisionDecoder(),
                    HdrCapabilities.hasDolbyVisionDisplay(context)
                )
            ) {
                DolbyVisionPlayback.BASE_LAYER ->
                    context.getString(Res.string.video_format_dolby_vision_compatible)
                DolbyVisionPlayback.UNSUPPORTED ->
                    context.getString(Res.string.video_format_dolby_vision_unsupported)
                else -> context.getString(Res.string.video_format_dolby_vision)
            }
            VideoHdrType.HDR10 -> context.getString(Res.string.video_format_hdr10)
            VideoHdrType.HLG -> context.getString(Res.string.video_format_hlg)
            VideoHdrType.NONE -> ""
        }
        info += InfoRow(
            icon = Icons.Outlined.VideoFile,
            label = context.getString(Res.string.video_format),
            content = buildString {
                append(formatLabel)
                videoHdr.codecs?.let { append(" • $it") }
            }
        )
    }

    return info
}
