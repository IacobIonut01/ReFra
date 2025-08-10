package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler

@Composable
fun NavigationBackButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = stringResource(R.string.back_cd),
    forcedAction: (() -> Unit)? = null
) {
    val eventHandler = LocalEventHandler.current
    IconButton(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = CircleShape
            ),
        onClick = {
            forcedAction?.invoke() ?: eventHandler.navigateUpAction()
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}