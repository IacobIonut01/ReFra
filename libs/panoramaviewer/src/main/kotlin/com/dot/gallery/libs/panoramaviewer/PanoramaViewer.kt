/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.net.Uri
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A Jetpack Compose panorama / photosphere viewer.
 *
 * This is the **main public entry-point** of the library. Drop it into any Composable
 * hierarchy to render a panoramic or photosphere image with touch-based rotation,
 * pinch-to-zoom, inertia, and optional gyroscope input.
 *
 * ## Architecture
 *
 * ```
 * PanoramaViewer (Compose)
 *   └─ PanoramaGLSurfaceView (Android View)
 *        ├─ PanoramaRenderer (GL thread — draws the geometry)
 *        └─ PanoramaImageLoader (background thread — decodes image regions)
 * ```
 *
 * ## Image loading strategy
 *
 * The full-resolution image is never loaded into memory. Instead:
 *
 * 1. A **tiny background texture** (≈32 px) is loaded first and stretched fullscreen
 *    behind the geometry to provide a blurred backdrop.
 * 2. A **low-resolution base texture** (≈1024 px) is decoded and mapped onto
 *    the geometry surface immediately.
 * 3. A **high-resolution detail texture** (up to 4096 px) covering only the visible
 *    viewport is decoded on demand and overlaid on the base texture. The detail
 *    region is refreshed as the user pans or zooms.
 *
 * ## Standard usage (content URI)
 *
 * ```kotlin
 * PanoramaViewer(
 *     imageUri       = mediaUri,
 *     projectionType = ProjectionType.CYLINDER,
 *     onTap          = { toggleUi() },
 *     onCameraChanged = { state -> updateCompass(state) },
 *     modifier       = Modifier.fillMaxSize()
 * )
 * ```
 *
 * ## Custom loader usage (e.g. encrypted vault)
 *
 * ```kotlin
 * val loader = remember(media) { MyEncryptedLoader(keychainHolder, file) }
 * PanoramaViewer(
 *     imageUri       = Uri.EMPTY,   // ignored when imageLoader is set
 *     projectionType = ProjectionType.SPHERE,
 *     imageLoader    = loader,
 *     modifier       = Modifier.fillMaxSize()
 * )
 * ```
 *
 * @param imageUri        Content URI of the equirectangular or panoramic image.
 *                         Ignored when [imageLoader] is provided.
 * @param projectionType  The projection geometry to use.
 * @param modifier        [Modifier] applied to the underlying [AndroidView].
 * @param gyroscopeEnabled Whether to use the device gyroscope for rotation
 *                         (recommended for photospheres).
 * @param imageLoader     Optional custom [PanoramaImageLoader] for loading image data.
 *                         When provided, this is used instead of the default
 *                         `ContentResolver`-based loader. Useful for encrypted vault
 *                         media, network images, or custom formats.
 * @param onTap           Optional callback invoked on a single-tap gesture
 *                         (e.g. to toggle surrounding UI chrome).
 * @param onCameraChanged Optional callback invoked whenever the camera state changes
 *                         (yaw, pitch, fov). Useful for compass overlays.
 * @param onViewCreated    Optional callback invoked with the underlying [View] reference.
 *                         Useful for PixelCopy-based blur capture of the GL surface.
 *
 * @see ProjectionType
 * @see CameraState
 * @see PanoramaImageLoader
 * @see PanoramaLog
 */
@Composable
fun PanoramaViewer(
    imageUri: Uri,
    projectionType: ProjectionType,
    modifier: Modifier = Modifier,
    gyroscopeEnabled: Boolean = false,
    imageLoader: PanoramaImageLoader? = null,
    onTap: (() -> Unit)? = null,
    onCameraChanged: ((CameraState) -> Unit)? = null,
    onViewCreated: ((View) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val glView = remember(projectionType, gyroscopeEnabled) {
        PanoramaGLSurfaceView(context, projectionType, gyroscopeEnabled)
    }

    LaunchedEffect(glView) {
        onViewCreated?.invoke(glView)
    }

    // Wire tap and camera callbacks
    LaunchedEffect(onTap) {
        glView.onTapListener = onTap
    }
    LaunchedEffect(onCameraChanged) {
        glView.onCameraChangedListener = onCameraChanged
    }

    // Load image source — custom loader takes priority over URI
    LaunchedEffect(imageLoader, imageUri) {
        if (imageLoader != null) {
            glView.setImageLoader(imageLoader)
        } else {
            glView.setImageSource(context.contentResolver, imageUri)
        }
    }

    // Lifecycle management for GL context + sensors
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
            glView.release()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier
    )
}
