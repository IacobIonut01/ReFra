package com.dot.gallery.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_transparent_export_png
import com.dot.gallery.editor.ui.generated.resources.editor_transparent_export_subtitle
import com.dot.gallery.editor.ui.generated.resources.editor_transparent_export_title
import com.dot.gallery.editor.ui.generated.resources.editor_transparent_export_webp
import org.jetbrains.compose.resources.stringResource

internal const val TAG_TRANSPARENT_EXPORT_SHEET = "editor.transparentExport"

/**
 * Asked before saving a transparent result (a shaped crop or a removed
 * background) whose save would otherwise land in a container without alpha,
 * such as a JPEG source's own: PNG keeps it lossless, WebP keeps it smaller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoTransparentExportSheet(
    allowWebp: Boolean,
    onSelect: (PhotoExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .testTag(TAG_TRANSPARENT_EXPORT_SHEET),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.editor_transparent_export_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.editor_transparent_export_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val rows = buildList {
                add(PhotoExportFormat.PNG to Res.string.editor_transparent_export_png)
                if (allowWebp) {
                    add(PhotoExportFormat.WEBP to Res.string.editor_transparent_export_webp)
                }
            }
            rows.forEach { (format, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onSelect(format) }
                        .testTag("$TAG_TRANSPARENT_EXPORT_SHEET.${format.name}")
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RetouchSymbols.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
