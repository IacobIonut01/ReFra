/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.net.Uri
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.decoder.EncryptedPanoramaImageLoader
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import com.dot.gallery.libs.panoramaviewer.CameraState
import com.dot.gallery.libs.panoramaviewer.PanoramaImageLoader
import com.dot.gallery.libs.panoramaviewer.PanoramaViewer
import com.dot.gallery.libs.panoramaviewer.ProjectionType
import dev.chrisbanes.haze.hazeSource

@Stable
@Composable
fun <T : Media> PanoramaImageViewer(
    media: T,
    isPhotosphere: Boolean,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit = {},
    currentVault: Vault? = null
) {
    val projectionType = if (isPhotosphere) ProjectionType.SPHERE else ProjectionType.CYLINDER

    val context = LocalContext.current

    // Build an encrypted loader when viewing vault media
    val imageLoader: PanoramaImageLoader? = remember(media.id, currentVault) {
        if (currentVault != null) {
            val keychainHolder = KeychainHolder(context)
            val encryptedFile = media.getUri().toFile()
            EncryptedPanoramaImageLoader(keychainHolder, encryptedFile)
        } else null
    }

    val imageUri: Uri = remember(media.id, currentVault) {
        if (currentVault != null) Uri.EMPTY else media.getUri()
    }

    var cameraState by remember { mutableStateOf(CameraState()) }
    var glViewRef by remember { mutableStateOf<View?>(null) }
    val allowBlur by rememberAllowBlur()
    val hazeState = LocalHazeState.current
    val panoramaCapture by rememberSurfaceCapture(
        view = glViewRef,
        enabled = allowBlur,
        captureWidth = 64
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Captured GL content as hazeSource — drawn BEFORE the PanoramaViewer so the
        // GLSurfaceView's transparent hole punches through it on the window surface
        // (user sees sharp GL), while haze captures from this Image's own RenderNode.
        panoramaCapture?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
            )
        }

        PanoramaViewer(
            imageUri = imageUri,
            projectionType = projectionType,
            gyroscopeEnabled = isPhotosphere,
            imageLoader = imageLoader,
            onTap = onItemClick,
            onCameraChanged = { cameraState = it },
            onViewCreated = { glViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // Compass indicator
        PanoramaCompass(
            cameraState = cameraState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun PanoramaCompass(
    cameraState: CameraState,
    modifier: Modifier = Modifier
) {
    val arcDeg = cameraState.arcDegrees
    val totalH = if (cameraState.projectionType == ProjectionType.SPHERE) 360f else arcDeg
    val fovFraction = (cameraState.fov / totalH).coerceIn(0.01f, 1f)

    // Normalize yaw to [0, totalH] range
    val yawNorm = if (cameraState.projectionType == ProjectionType.SPHERE) {
        ((cameraState.yaw % 360f + 360f) % 360f)
    } else {
        cameraState.yaw + totalH / 2f
    }
    val yawFraction by animateFloatAsState(
        targetValue = (yawNorm / totalH).coerceIn(0f, 1f),
        animationSpec = tween(50),
        label = "compassYaw"
    )

    // Pitch for sphere: show vertical indicator
    val pitchFraction by animateFloatAsState(
        targetValue = ((90f - cameraState.pitch) / 180f).coerceIn(0f, 1f),
        animationSpec = tween(50),
        label = "compassPitch"
    )

    val ringColor = Color.White.copy(alpha = 0.5f)
    val fovColor = Color.White.copy(alpha = 0.85f)
    val dotColor = Color.White

    Canvas(modifier = modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.width / 2f - 4.dp.toPx()

        // Outer ring
        drawCircle(
            color = ringColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // FOV arc — shows what portion of the panorama is visible
        val sweepAngle = fovFraction * 360f
        val startAngle = yawFraction * 360f - sweepAngle / 2f - 90f
        drawArc(
            color = fovColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        if (cameraState.projectionType == ProjectionType.SPHERE) {
            // Vertical pitch indicator — small dot showing vertical position
            val dotY = cy - radius + pitchFraction * radius * 2f
            drawCircle(
                color = dotColor,
                radius = 3.dp.toPx(),
                center = Offset(cx, dotY.coerceIn(cy - radius, cy + radius))
            )
        }

        // Center dot
        val dotAngle = Math.toRadians((yawFraction * 360f - 90f).toDouble())
        val dotX = cx + radius * kotlin.math.cos(dotAngle).toFloat()
        val dotYOnRing = cy + radius * kotlin.math.sin(dotAngle).toFloat()
        drawCircle(
            color = dotColor,
            radius = 3.dp.toPx(),
            center = Offset(dotX, dotYOnRing)
        )
    }
}
