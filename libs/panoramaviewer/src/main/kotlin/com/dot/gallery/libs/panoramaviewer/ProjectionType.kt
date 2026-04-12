/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import androidx.compose.runtime.Stable

/**
 * Determines how a panoramic image is projected onto 3D geometry for rendering.
 *
 * The projection type affects both the shape of the geometry the image is mapped onto
 * and the camera movement constraints applied during user interaction.
 *
 * @see PanoramaViewer
 */
@Stable
enum class ProjectionType {

    /**
     * Full 360° × 180° equirectangular photosphere rendered on the inside of a sphere.
     *
     * Use this for images captured with a 360° camera or photosphere mode, where the
     * image covers the full spherical field of view. The user can look in any direction
     * including straight up and down.
     *
     * Expected image aspect ratio: 2:1 for full equirectangular coverage.
     */
    SPHERE,

    /**
     * Wide panorama rendered on the inside of a cylinder.
     *
     * Use this for standard panoramic images that cover a wide horizontal field of view
     * but a limited vertical range. The cylinder arc and height are automatically computed
     * from the image's aspect ratio.
     *
     * The user's vertical look angle is restricted to ±45° and horizontal panning is
     * clamped to the image boundaries for partial-arc panoramas.
     */
    CYLINDER
}
