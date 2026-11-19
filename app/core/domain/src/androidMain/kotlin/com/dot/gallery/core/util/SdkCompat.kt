/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.os.Build
import android.os.Environment
import androidx.annotation.ChecksSdkIntAtLeast


/**
 * SDK compatibility helpers for features that differ between API 29 and 30+.
 */
object SdkCompat {

    /**
     * Whether the device supports MediaStore trash (IS_TRASHED, createTrashRequest).
     * Available on Android 11 (API 30) and above.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val supportsTrash: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports MediaStore favorites (IS_FAVORITE, createFavoriteRequest).
     * Available on Android 11 (API 30) and above.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val supportsFavorites: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports MediaStore intent-based requests
     * (createDeleteRequest, createTrashRequest, createFavoriteRequest, createWriteRequest).
     * Available on Android 11 (API 30) and above.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val supportsMediaStoreRequests: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether the device supports blur effects (Haze uses RenderEffect via GraphicsLayer).
     * Available on Android 12 (API 31) and above.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val supportsBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Whether the OS itself shows a visual confirmation when content is copied to the clipboard.
     * Available on Android 13 (API 33) and above, so the app should not show its own
     * "Copied to clipboard" toast to avoid duplicate notifications.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val showsClipboardConfirmation: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the device runs Android 10 (API 29) where scoped storage is opt-in
     * and IS_TRASHED/IS_FAVORITE columns don't exist.
     */
    val isApi29: Boolean
        get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

    /**
     * Whether the app has been granted MANAGE_EXTERNAL_STORAGE (All Files Access).
     * On API 30+ this enables direct file operations on external volumes (SD cards).
     * On API 29 and below, legacy storage grants full access by default.
     */
    val hasFullFileAccess: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else true

    /**
     * Whether the device is a Samsung device. Some Samsung builds black out a
     * video [android.view.SurfaceView] when the system bars are toggled (immersive
     * transitions), so the video-surface rebind workaround is enabled by default here.
     */
    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
