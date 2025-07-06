package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.Media

@Composable
fun <T : Media> MediaViewButton(
    currentMedia: T?,
    imageVector: ImageVector,
    title: String,
    enabled: Boolean = true,
    followTheme: Boolean = false,
    onItemLongClick: ((T) -> Unit)? = null,
    onItemClick: (T) -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val tintColor by animateColorAsState(
        if (followTheme) onSurfaceColor.copy(alpha = alpha)
        else Color.White.copy(alpha = alpha)
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .defaultMinSize(
                minWidth = 90.dp
            )
            .height(84.dp)
            .combinedClickable(
                enabled = enabled,
                onLongClick = {
                    currentMedia?.let {
                        onItemLongClick?.invoke(it)
                    }
                },
                onClick = {
                    currentMedia?.let {
                        onItemClick.invoke(it)
                    }
                }
            )
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(tintColor),
            contentDescription = title,
            modifier = Modifier
                .height(32.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = title,
            modifier = Modifier,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            color = tintColor,
            textAlign = TextAlign.Center,
        )
    }
}