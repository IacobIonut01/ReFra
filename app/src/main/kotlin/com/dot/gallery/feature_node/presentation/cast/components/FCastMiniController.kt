/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.cast.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * Slim casting status banner showing the connected device name with a stop button.
 * Supports blur matching the app bar style. Positioned in the top area below the date header.
 */
@Composable
fun CastStatusBanner(
    deviceName: String,
    onStop: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allowBlur by rememberAllowBlur()
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
    val backgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = surfaceContainer,
                shape = CircleShape
            )
        } else Modifier
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(backgroundModifier)
            .hazeEffectScaled(
                state = LocalHazeState.current,
                style = HazeMaterials.ultraThin(
                    containerColor = surfaceContainer
                )
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CastConnected,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.cast_casting_to, deviceName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cast_stop),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
