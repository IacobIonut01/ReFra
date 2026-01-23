package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@Composable
fun OptionItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionItemDefaultPreview() {
    PreviewHost {
        OptionItem(
            icon = Icons.Outlined.Settings,
            title = "Settings",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionItemEditPreview() {
    PreviewHost {
        OptionItem(
            icon = Icons.Outlined.Edit,
            title = "Edit",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionItemDeletePreview() {
    PreviewHost {
        OptionItem(
            icon = Icons.Outlined.Delete,
            title = "Delete",
            tint = MaterialTheme.colorScheme.error,
            onClick = {}
        )
    }
}
