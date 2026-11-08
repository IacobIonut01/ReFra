/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image


enum class CloudSubsamplingMode {
    NONE,
    PLATFORM,
    JXL,
    CUSTOM,
    HEIF
}

fun resolveCloudSubsamplingMode(
    isJxl: Boolean,
    hasCustomRegionDecoder: Boolean,
    isHeif: Boolean,
    isSpecialFormat: Boolean,
    isAnimated: Boolean,
    isAnimatedRaster: Boolean,
    isAvif: Boolean = false,
): CloudSubsamplingMode = when {
    isJxl -> CloudSubsamplingMode.JXL
    hasCustomRegionDecoder -> CloudSubsamplingMode.CUSTOM
    // Still AVIF uses the same ISO-BMFF region decoder as HEIC. A sequence stays on the
    // animated painter, so it must not take this branch.
    (isHeif || isAvif) && !isAnimated -> CloudSubsamplingMode.HEIF
    isSpecialFormat -> CloudSubsamplingMode.NONE
    !isAnimated && !isAnimatedRaster -> CloudSubsamplingMode.PLATFORM
    else -> CloudSubsamplingMode.NONE
}

fun shouldLoadCloudOriginal(
    isSelected: Boolean,
    subsamplingMode: CloudSubsamplingMode
): Boolean = isSelected && subsamplingMode != CloudSubsamplingMode.NONE
