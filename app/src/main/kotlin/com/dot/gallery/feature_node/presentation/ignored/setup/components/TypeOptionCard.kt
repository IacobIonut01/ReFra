package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.PreviewHost

/**
 * A selectable card for choosing the type of ignored album (single, multiple, or regex).
 * Shows an icon and label with visual feedback for selection state.
 */
@Composable
fun TypeOptionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).padding(8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun TypeOptionCardUnselectedPreview() {
    PreviewHost {
        TypeOptionCard(
            icon = Icons.Outlined.PhotoAlbum,
            title = "Single",
            isSelected = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TypeOptionCardSelectedPreview() {
    PreviewHost {
        TypeOptionCard(
            icon = Icons.Outlined.FolderCopy,
            title = "Multiple",
            isSelected = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Long Label")
@Composable
private fun TypeOptionCardLongLabelPreview() {
    PreviewHost {
        TypeOptionCard(
            icon = Icons.Outlined.FolderCopy,
            title = "Regular Expression",
            isSelected = false,
            onClick = {}
        )
    }
}
