/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.cast.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R

@Composable
fun CastButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    followTheme: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (followTheme) {
            LocalContentColor.current
        } else {
            if (isConnected) Color.White else Color.White.copy(alpha = 0.8f)
        },
        label = "CastButtonTint"
    )

    val alpha = if (isConnecting) {
        val transition = rememberInfiniteTransition(label = "cast_connecting")
        val a by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cast_alpha"
        )
        a
    } else 1f

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
            contentDescription = stringResource(
                if (isConnected) R.string.cast_disconnect else R.string.cast_connect
            ),
            tint = tint
        )
    }
}
