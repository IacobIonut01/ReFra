package com.dot.gallery.feature_node.presentation.edit.components.adjustment

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding
import com.dot.gallery.feature_node.presentation.util.sentenceCase

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    horizontal: Boolean = false,
    onLongItemClick: (() -> Unit?)? = null,
    onItemClick: () -> Unit
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColorHigh = MaterialTheme.colorScheme.surfaceContainerHigh

    val onTertiaryColor = MaterialTheme.colorScheme.onTertiary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        label = "alpha"
    )

    val tintColor by animateColorAsState(
        (if (selected) onTertiaryColor else onSurfaceColor).copy(alpha = alpha),
        label = "tintColor"
    )
    val containerColor by animateColorAsState(
        (if (selected) tertiaryColor else surfaceColorHigh).copy(alpha = alpha),
        label = "containerColor"
    )

    val mModifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .defaultMinSize(
            minWidth = 90.dp,
            minHeight = 80.dp
        )
    if (horizontal) {
        Row(
            modifier = mModifier
                .safeSystemGesturesPadding(onlyRight = true)
                .clip(CircleShape)
                .fillMaxWidth()
                .background(
                    color = containerColor,
                    shape = CircleShape
                )
                .combinedClickable(
                    enabled = enabled,
                    onLongClick = { onLongItemClick?.invoke() },
                    onClick = onItemClick
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                imageVector = icon,
                colorFilter = ColorFilter.tint(tintColor),
                contentDescription = title,
                modifier = Modifier
                    .padding(12.dp)
                    .size(28.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = title.sentenceCase().replace("_", " "),
                modifier = Modifier,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
                color = tintColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(24.dp))
        }
    } else {
        Column(
            modifier = mModifier
                .combinedClickable(
                    enabled = enabled,
                    onLongClick = { onLongItemClick?.invoke() },
                    onClick = onItemClick
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.sentenceCase().replace("_", " "),
                modifier = Modifier,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Image(
                imageVector = icon,
                colorFilter = ColorFilter.tint(tintColor),
                contentDescription = title,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        color = containerColor,
                        shape = CircleShape
                    )
                    .padding(20.dp)
                    .size(28.dp)
            )
        }
    }
}