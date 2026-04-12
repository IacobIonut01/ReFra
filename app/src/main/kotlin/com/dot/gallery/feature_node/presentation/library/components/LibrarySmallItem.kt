package com.dot.gallery.feature_node.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation

@Composable
fun LibrarySmallItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector?,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = contentColor.copy(alpha = 0.1f),
    useIndicator: Boolean = false,
    indicatorCounter: Int = 0,
    contentDescription: String = title
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = contentColor
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        leadingContent = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    tint = contentColor,
                    contentDescription = contentDescription
                )
            }
        } else null,
        supportingContent = if (subtitle == null) null else {
            {
                Text(
                    modifier = Modifier.padding(vertical = 6.dp),
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        },
        trailingContent = {
            AnimatedVisibility(
                useIndicator && indicatorCounter > 0,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
                Text(
                    text = remember(indicatorCounter) {
                        indicatorCounter.coerceAtMost(99)
                            .toString() + if (indicatorCounter > 99) "+" else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    )
}