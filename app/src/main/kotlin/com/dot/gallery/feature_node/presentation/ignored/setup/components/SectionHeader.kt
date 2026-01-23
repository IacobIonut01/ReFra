package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    PreviewHost {
        SectionHeader(
            title = "Location",
            subtitle = "Choose where to hide the album"
        )
    }
}

@Preview(showBackground = true, name = "Long Text")
@Composable
private fun SectionHeaderLongTextPreview() {
    PreviewHost {
        SectionHeader(
            title = "Selection Type",
            subtitle = "Choose how you want to select albums - single, multiple, or using regex patterns"
        )
    }
}
