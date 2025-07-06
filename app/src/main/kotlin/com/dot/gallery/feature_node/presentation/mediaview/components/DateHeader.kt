package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.MediaDateCaption

@Composable
fun DateHeader(
    modifier: Modifier = Modifier,
    mediaDateCaption: MediaDateCaption
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ).toSpanStyle()
            ) {
                appendLine(mediaDateCaption.date)
            }
            /*mediaDateCaption.deviceInfo?.let { deviceInfo ->
                withStyle(
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ).toSpanStyle()
                ) {
                    appendLine(deviceInfo)
                }
            }*/
            withStyle(
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ).toSpanStyle()
            ) {
                append(
                    mediaDateCaption.description.ifEmpty {
                        stringResource(R.string.image_add_description)
                    }
                )
            }
        },
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 8.dp)
    )
}