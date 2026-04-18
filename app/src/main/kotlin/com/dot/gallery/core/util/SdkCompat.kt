/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.os.Build

/**
 * SDK compatibility helpers for features that differ between API 29 and 30+.
 */
object SdkCompat {

    /**
     * Whether the device supports MediaStore trash (IS_TRASHED, createTrashRequest).
     * Available on Android 11 (API 30) and above.
     */
    val supportsTrash: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports MediaStore favorites (IS_FAVORITE, createFavoriteRequest).
     * Available on Android 11 (API 30) and above.
     */
    val supportsFavorites: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports MediaStore intent-based requests
     * (createDeleteRequest, createTrashRequest, createFavoriteRequest, createWriteRequest).
     * Available on Android 11 (API 30) and above.
     */
    val supportsMediaStoreRequests: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports blur effects (Haze uses RenderEffect via GraphicsLayer).
     * Available on Android 12 (API 31) and above.
     */
    val supportsBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Whether the device runs Android 10 (API 29) where scoped storage is opt-in
     * and IS_TRASHED/IS_FAVORITE columns don't exist.
     */
    val isApi29: Boolean
        get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
}
