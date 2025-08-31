/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.EncryptedRegionDecoder
import com.dot.gallery.core.presentation.components.util.LocalBatteryStatus
import com.dot.gallery.core.presentation.components.util.ProvideBatteryStatus
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.asSubsamplingImage
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.zoomimage.GlideZoomAsyncImage
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.glide.ExperimentalGlideComposeApi
import com.github.panpf.zoomimage.rememberGlideZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlideComposeApi::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class
)
@Stable
@Composable
fun <T: Media> BoxScope.ZoomablePagerImage(
    modifier: Modifier = Modifier,
    media: T,
    uiEnabled: Boolean,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val feedbackManager = rememberFeedbackManager()
    var isRotating by rememberSaveable { mutableStateOf(false) }
    var currentRotation by rememberSaveable { mutableIntStateOf(0) }
    val rotationAnimation by animateFloatAsState(
        targetValue = if (isRotating) 90f else 0f,
        label = "rotationAnimation"
    )
    ProvideBatteryStatus {
        val allowBlur by Settings.Misc.rememberAllowBlur()
        val isPowerSavingMode = LocalBatteryStatus.current.isPowerSavingMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && allowBlur && !isPowerSavingMode) {
            val blurAlpha by animateFloatAsState(
                animationSpec = tween(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                targetValue = if (uiEnabled) 0.7f else 0f,
                label = "blurAlpha"
            )
            GlideImage(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(blurAlpha)
                    .blur(100.dp),
                model = media.getUri(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                requestBuilderTransform = {
                    it.override(600)
                        .thumbnail(it.clone().sizeMultiplier(0.1f))
                }
            )
        }
    }
    val zoomState = rememberGlideZoomState()
    val scope = rememberCoroutineScope()

    if (media.isEncrypted) {
        val painter = rememberAsyncImagePainter(
            request = ComposableImageRequest(media.getUri().toString()) {
                crossfade(durationMillis = 200)
                setExtra(
                    key = "mediaKeyPreviewEnc",
                    value = media.idLessKey,
                )
                setExtra("realMimeType", media.mimeType)
            },
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )
        val context = LocalContext.current
        val keychainHolder = remember {
            KeychainHolder(context)
        }
        LaunchedEffect(zoomState.subsampling) {
            zoomState.subsampling.setRegionDecoders(listOf(EncryptedRegionDecoder.Factory(keychainHolder)))
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        ZoomImage(
            zoomState = zoomState,
            painter = painter,
            modifier = Modifier
                .fillMaxSize()
                .swipe(
                    onSwipeDown = onSwipeDown
                )
                .graphicsLayer {
                    rotationZ = if (isRotating) rotationAnimation else 0f
                }.then(modifier),
            onTap = { onItemClick() },
            onLongPress = {
                scope.launch {
                    isRotating = true
                    feedbackManager.vibrate()
                    currentRotation += 90
                    delay(350)
                    zoomState.zoomable.rotate(currentRotation)
                    isRotating = false
                }
            },
            alignment = Alignment.Center,
            contentDescription = media.label
        )
    } else {
        GlideZoomAsyncImage(
            zoomState = zoomState,
            model = media.getUri(),
            modifier = Modifier
                .fillMaxSize()
                .swipe(
                    onSwipeDown = onSwipeDown
                )
                .graphicsLayer {
                    rotationZ = if (isRotating) rotationAnimation else 0f
                }
                .then(modifier),
            onTap = { onItemClick() },
            onLongPress = {
                scope.launch {
                    isRotating = true
                    feedbackManager.vibrate()
                    currentRotation += 90
                    delay(350)
                    zoomState.zoomable.rotate(currentRotation)
                    isRotating = false
                }
            },
            alignment = Alignment.Center,
            contentDescription = media.label
        )
    }
}


