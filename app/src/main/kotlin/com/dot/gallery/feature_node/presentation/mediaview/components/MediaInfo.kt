/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.InfoRow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.formatMinSec
import com.dot.gallery.feature_node.presentation.util.formatSize
import com.dot.gallery.feature_node.presentation.util.toBitrateString
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MediaInfoRow(
    modifier: Modifier = Modifier,
    label: String,
    content: String,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val clipboardManager: Clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium)
            .combinedClickable(
                onClick = { onClick?.let { it() } },
                onLongClick = {
                    if (onLongClick != null) onLongClick()
                    else {
                        scope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(
                                    ClipData(
                                        ClipDescription("text/plain", arrayOf("text/plain")),
                                        ClipData.Item(AnnotatedString(content))
                                    )
                                )
                            )
                        }
                    }
                }
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        headlineContent = {
            Text(
                text = label,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(text = content)
        },
        trailingContent = trailingContent
    )
}

fun Media.retrieveMetadata(
    context: Context,
    exifDateFormat: String,
    mediaMetadata: MediaMetadata?,
    onLabelClick: () -> Unit
): List<InfoRow> {
    val info = mutableListOf<InfoRow>()

    // 1) Label (editable)
    info += InfoRow(
        icon = Icons.Outlined.Photo,
        trailingIcon = Icons.Outlined.Edit,
        label = context.getString(R.string.label),
        onClick = onLabelClick,
        content = label
    )

    // 2) Full path
    info += InfoRow(
        icon = Icons.Outlined.Info,
        label = context.getString(R.string.path),
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
                label = context.getString(R.string.taken_on),
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

        // 7) Camera + settings
        val cam = listOfNotNull(
            md.manufacturerName,
            md.modelName
        ).joinToString(" ")
        val camDetails = listOfNotNull(
            md.aperture,
            formatExposureTime(md.exposureTime),
            md.iso?.let { "ISO $it" }
        ).joinToString(" • ")
        if (cam.isNotBlank()) {
            info += InfoRow(
                icon = Icons.Outlined.Camera,
                label = cam,
                content = camDetails
            )
        }

        // 8) Image dimensions & megapixels & Image resolution (PPI)
        if (md.imageWidth > 0 && md.imageHeight > 0) {
            var content = context.getString(
                R.string.resolution_with_mp,
                md.imageWidth,
                md.imageHeight,
                md.imageMp
            )
            if (md.imageResolutionX != null && md.imageResolutionY != null) {
                val unit = when (md.resolutionUnit) {
                    2 -> context.getString(R.string.ppi)
                    3 -> context.getString(R.string.ppcm)
                    else -> ""
                }
                content += context.getString(
                    R.string.ppi_resolution_string,
                    md.imageResolutionX,
                    md.imageResolutionY,
                    unit
                )
            }
            if (size > 0) {
                content += " • ${formatSize(size)}"
            }
            info += InfoRow(
                icon = Icons.Outlined.ImageSearch,
                label = context.getString(R.string.dimensions),
                content = content
            )
        } else {
            // If no dimensions, just show size if available
            if (size > 0) {
                info += InfoRow(
                    icon = Icons.Outlined.Info,
                    label = context.getString(R.string.type_size),
                    content = formatSize(size)
                )
            }
        }

        // 9) Video fields
        if (isVideo) {
            md.durationMs?.takeIf { it > 0 }?.let {
                info += InfoRow(
                    icon = Icons.Outlined.VideoFile,
                    label = context.getString(R.string.duration),
                    content = it.formatMinSec()
                )
            }
            if (md.videoWidth != null && md.videoHeight != null) {
                info += InfoRow(
                    icon = Icons.Outlined.VideoFile,
                    label = context.getString(R.string.dimensions),
                    content = "${md.videoWidth} × ${md.videoHeight}"
                )
            }
            md.frameRate?.let {
                var content = context.getString(R.string.video_fps, it)
                md.bitRate?.let {
                    content += context.getString(R.string.at_kbps, it.toBitrateString())
                }
                info += InfoRow(
                    icon = Icons.Outlined.Info,
                    label = context.getString(R.string.frame_rate),
                    content = content
                )
            }
        }

    }

    return info
}