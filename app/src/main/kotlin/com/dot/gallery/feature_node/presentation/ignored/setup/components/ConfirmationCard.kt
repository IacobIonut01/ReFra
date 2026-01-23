package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.PreviewHost

/**
 * A card component showing confirmation information with icon, title, and value.
 * Used in the confirmation step to summarize user selections.
 */
@Composable
fun ConfirmationCard(
    title: String,
    value: String,
    icon: ImageVector,
    extra: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            extra?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun ConfirmationCardLocationPreview() {
    PreviewHost {
        ConfirmationCard(
            title = "Hide From",
            value = "Albums only",
            icon = Icons.Outlined.FilterNone
        )
    }
}

@Preview(showBackground = true, name = "Single Album")
@Composable
private fun ConfirmationCardSingleAlbumPreview() {
    PreviewHost {
        ConfirmationCard(
            title = "Album",
            value = "Camera",
            icon = Icons.Outlined.PhotoAlbum
        )
    }
}

@Preview(showBackground = true, name = "Multiple Albums")
@Composable
private fun ConfirmationCardMultiplePreview() {
    PreviewHost {
        ConfirmationCard(
            title = "Albums",
            value = "3 albums selected",
            icon = Icons.Outlined.FolderCopy
        )
    }
}

@Preview(showBackground = true, name = "With Extra")
@Composable
private fun ConfirmationCardWithExtraPreview() {
    PreviewHost {
        ConfirmationCard(
            title = "Matched Albums",
            value = "Screenshots\nCamera\nDownloads\nWhatsApp Images\nTelegram",
            icon = Icons.Outlined.Checklist,
            extra = "+5 more"
        )
    }
}

@Preview(showBackground = true, name = "Regex")
@Composable
private fun ConfirmationCardRegexPreview() {
    PreviewHost {
        ConfirmationCard(
            title = "Pattern",
            value = "^Screenshot.*",
            icon = Icons.Outlined.FilterNone
        )
    }
}
