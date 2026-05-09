package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.Media

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val baseColor by animateColorAsState(
        targetValue = if (followTheme) LocalContentColor.current else Color.White,
        label = "MediaViewButtonBaseColor"
    )

    val tintColor by animateColorAsState(
        baseColor.copy(alpha = alpha),
        label = "MediaViewButtonTintColor"
    )
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            4.dp
        ),
        tooltip = {
            PlainTooltip {
                Text(text = title)
            }
        },
        state = tooltipState
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        currentMedia?.let {
                            onItemClick.invoke(it)
                        }
                    },
                    onLongClick = if (onItemLongClick != null) {
                        {
                            currentMedia?.let {
                                onItemLongClick.invoke(it)
                            }
                        }
                    } else null,
                    onClickLabel = title
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = imageVector,
                colorFilter = ColorFilter.tint(tintColor),
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}