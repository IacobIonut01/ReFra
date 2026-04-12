/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

/**
 * Snapshot of the panorama viewer's camera state, emitted via
 * [PanoramaViewer]'s `onCameraChanged` callback.
 *
 * This data class is intended for building overlay UI such as a compass indicator
 * or debugging HUD. All angles are in degrees.
 *
 * @property yaw Horizontal rotation in degrees.
 *   - [ProjectionType.SPHERE]: wraps freely around 360°.
 *   - [ProjectionType.CYLINDER]: clamped to the image arc boundaries.
 * @property pitch Vertical rotation in degrees.
 *   Positive values look up, negative values look down.
 *   Clamped to ±90° for spheres, ±45° for cylinders.
 * @property fov Current field of view in degrees (zoomed via pinch gesture).
 *   Range: 30°–110°.
 * @property arcDegrees The horizontal angular span of the panorama geometry.
 *   360° for full spheres and full-wrap cylinders; less for partial panoramas.
 * @property projectionType The active projection mode.
 *
 * @see PanoramaViewer
 */
data class CameraState(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val fov: Float = PanoramaRenderer.DEFAULT_FOV,
    val arcDegrees: Float = 360f,
    val projectionType: ProjectionType = ProjectionType.SPHERE
)
