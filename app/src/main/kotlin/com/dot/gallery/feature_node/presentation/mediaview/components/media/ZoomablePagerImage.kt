/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.dot.gallery.feature_node.domain.util.isApng
import com.dot.gallery.feature_node.domain.util.isAvif
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.PainterState
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.rememberSketchZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun <T : Media> BlurredMediaBackground(
    media: T,
    uiEnabled: Boolean,
) {
    ProvideBatteryStatus {
        val allowBlur by Settings.Misc.rememberAllowBlur()
        val isPowerSavingMode = LocalBatteryStatus.current.isPowerSavingMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && allowBlur && !isPowerSavingMode) {
            val isEncrypted = remember(media) {
                media.isEncrypted
            }
            val blurAlpha by animateFloatAsState(
                animationSpec = tween(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                targetValue = if (uiEnabled) 0.7f else 0f,
                label = "blurAlpha"
            )
            AsyncImage(
                request = ComposableImageRequest(media.getUri().toString()) {
                    resize(width = 600, height = 600, precision = Precision.LESS_PIXELS)
                    crossfade(false)

                    if (isEncrypted) {
                        setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
                        setExtra("realMimeType", media.mimeType)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(blurAlpha)
                    .blur(100.dp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Stable
@Composable
fun <T : Media> ZoomablePagerImage(
    modifier: Modifier = Modifier,
    media: T,
    rotationDisabled: Boolean,
    onImageRotated: (newRotation: Int) -> Unit,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val feedbackManager = rememberFeedbackManager()
    var isRotating by rememberSaveable(media) { mutableStateOf(false) }
    var currentRotation by rememberSaveable(media) { mutableIntStateOf(0) }
    val rotationAnimation by animateFloatAsState(
        targetValue = if (isRotating) 90f else 0f,
        label = "rotationAnimation"
    )
    val zoomState = rememberSketchZoomState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val mediaUri = remember(media) {
        media.getUri().toString()
    }
    val isEncrypted = remember(media) {
        media.isEncrypted
    }
    val isAnimated = remember(media) {
        media.isApng || (media.isAvif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }

    // Fast low-res preview painter, shown until full image loads
    val previewPainter = rememberAsyncImagePainter(
        request = ComposableImageRequest(mediaUri) {
            resize(width = 600, height = 600, precision = Precision.LESS_PIXELS)
            crossfade(false)
            if (isEncrypted) {
                setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
                setExtra("realMimeType", media.mimeType)
            }
        },
        contentScale = ContentScale.Fit
    )

    // Full-res painter with state tracking
    val fullImageState = rememberAsyncImageState()
    val fullPainter = rememberAsyncImagePainter(
        request = ComposableImageRequest(mediaUri) {
            if (isEncrypted || isAnimated) {
                crossfade(durationMillis = 200)
            }
            if (isEncrypted) {
                setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
                setExtra("realMimeType", media.mimeType)
            }
        },
        state = fullImageState,
        contentScale = ContentScale.Fit
    )

    val isFullImageLoaded by rememberedDerivedState(media) {
        fullImageState.painterState is PainterState.Success
    }
    val activePainter = remember(isFullImageLoaded) {
        if (isFullImageLoaded) fullPainter else previewPainter
    }

    if (isEncrypted) {
        val keychainHolder = remember { KeychainHolder(context) }
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
        LaunchedEffect(zoomState.subsampling, media) {
            zoomState.subsampling.setRegionDecoders(
                listOf(
                    EncryptedRegionDecoder.Factory(
                        keychainHolder
                    )
                )
            )
        }
    } else if (!isAnimated) {
        LaunchedEffect(media, isFullImageLoaded, zoomState.subsampling) {
            zoomState.setSubsamplingImage(media.asSubsamplingImage(context))
        }
    }

    ZoomImage(
        zoomState = zoomState,
        painter = activePainter,
        modifier = Modifier
            .fillMaxSize()
            .swipe(onSwipeDown = onSwipeDown)
            .graphicsLayer {
                rotationZ = if (isRotating) rotationAnimation else 0f
            }
            .then(modifier),
        onTap = { onItemClick() },
        onLongPress = {
            if (!rotationDisabled) {
                scope.launch {
                    isRotating = true
                    feedbackManager.vibrate()
                    currentRotation += 90
                    onImageRotated(currentRotation)
                    delay(350)
                    zoomState.zoomable.rotate(currentRotation)
                    isRotating = false
                }
            }
        },
        alignment = Alignment.Center,
        contentDescription = media.label,
        scrollBar = null
    )
}


