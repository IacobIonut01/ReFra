package com.dot.gallery.feature_node.presentation.edit.components.editor

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.presentation.edit.components.core.SupportiveLazyLayout
import com.dot.gallery.feature_node.presentation.util.getEditImageCapableApps
import com.dot.gallery.feature_node.presentation.util.launchEditImageIntent
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding

@Composable
fun ExternalEditor(
    currentUri: Uri?,
    isSupportingPanel: Boolean
) {
    val context = LocalContext.current
    val editApps = remember(context, context::getEditImageCapableApps)
    val padding = remember(isSupportingPanel) {
        if (isSupportingPanel) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp, vertical = 2.dp)
    }
    AnimatedVisibility(
        visible = currentUri != null,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        SupportiveLazyLayout(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .then(
                    if (isSupportingPanel) Modifier
                        .safeSystemGesturesPadding(onlyRight = true)
                        .clipToBounds()
                        .clip(RoundedCornerShape(16.dp))
                    else Modifier
                ),
            contentPadding = padding,
            isSupportingPanel = isSupportingPanel
        ) {
            items(
                items = editApps
            ) { app ->
                val icon = remember(app) {
                    try {
                        app.loadIcon(context.packageManager).toBitmap().asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                AnimatedVisibility(
                    visible = icon != null,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    EditApp(
                        bitmap = icon!!,
                        title = app.loadLabel(context.packageManager).toString(),
                        horizontal = isSupportingPanel
                    ) {
                        context.launchEditImageIntent(
                            app.activityInfo.packageName,
                            currentUri!!
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditApp(
    bitmap: ImageBitmap,
    title: String,
    enabled: Boolean = true,
    horizontal: Boolean = false,
    onItemLongClick: (() -> Unit)? = null,
    onItemClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    val tintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    val modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .defaultMinSize(
            minWidth = 90.dp,
            minHeight = 80.dp
        )
        .combinedClickable(
            enabled = enabled,
            onLongClick = onItemLongClick,
            onClick = onItemClick
        )
        .padding(vertical = 16.dp)
    if (horizontal) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(12.dp)
                )
                .then(modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                modifier = Modifier
                    .padding(16.dp)
                    .size(28.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
                color = tintColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(24.dp))
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .height(32.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
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
}

@Composable
private fun EditApp(
    bitmap: ImageBitmap,
    title: String,
    onItemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .defaultMinSize(
                minWidth = 90.dp,
                minHeight = 80.dp
            )
            .clickable(onClick = onItemClick)
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = title,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 12.dp)
                .height(32.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = title,
            modifier = Modifier,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}