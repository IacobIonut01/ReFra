package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature_node.presentation.util.PreviewHost

/**
 * A component showing a regex pattern example with description.
 * Used to help users understand regex patterns.
 */
@Composable
fun RegexExample(
    description: String,
    pattern: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pattern,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun RegexExamplePreview() {
    PreviewHost {
        RegexExample(
            description = "Match anything with \"Screenshots\" in its path:",
            pattern = ".*Screenshots.*"
        )
    }
}

@Preview(showBackground = true, name = "Contains Pattern")
@Composable
private fun RegexExampleContainsPreview() {
    PreviewHost {
        RegexExample(
            description = "Match everything inside the WhatsApp folder:",
            pattern = ".*/WhatsApp/.*"
        )
    }
}

@Preview(showBackground = true, name = "Complex Pattern")
@Composable
private fun RegexExampleComplexPreview() {
    PreviewHost {
        RegexExample(
            description = "Match everything under DCIM on internal storage:",
            pattern = "/storage/emulated/0/DCIM/.*"
        )
    }
}
