package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.PreviewHost

/**
 * A step header component with a back button, title, subtitle, and optional trailing content.
 * Used at the top of each setup step.
 */
@Composable
fun StepHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            trailingContent?.invoke()
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun StepHeaderPreview() {
    PreviewHost {
        StepHeader(
            title = "Select Albums",
            subtitle = "Choose albums to hide",
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "With Trailing Content")
@Composable
private fun StepHeaderWithTrailingPreview() {
    PreviewHost {
        StepHeader(
            title = "Select Multiple",
            subtitle = "Tap albums to add them",
            onBack = {},
            trailingContent = {
                Text(
                    text = "3 selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

@Preview(showBackground = true, name = "Long Text")
@Composable
private fun StepHeaderLongTextPreview() {
    PreviewHost {
        StepHeader(
            title = "Regular Expression Pattern",
            subtitle = "Enter a regex pattern to match album names",
            onBack = {}
        )
    }
}
