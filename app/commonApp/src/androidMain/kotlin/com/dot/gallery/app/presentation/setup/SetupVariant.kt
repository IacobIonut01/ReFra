/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.setup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.feature.setup.ui.SetupPage
import com.dot.gallery.feature.setup.ui.SetupPermission

/**
 * What this installed APK can do, as far as the setup wizard cares: the permissions its merged
 * manifest declares (offline builds strip the network ones), the SDK level, whether all-files
 * access is enabled for the build, and whether the model pack ships inside the APK.
 *
 * [pages] and [permissions] are derived from these facts only, so the wizard never lists a page
 * or permission row the variant can't use.
 */
internal data class SetupVariant(
    val declaredPermissions: Set<String>,
    val sdkInt: Int,
    val allowAllFilesAccess: Boolean,
    val hasRemoteProvider: Boolean,
    val modelsBundled: Boolean,
) {
    val hasInternet: Boolean get() = INTERNET in declaredPermissions

    val pages: List<SetupPage>
        get() = buildList {
            add(SetupPage.WELCOME)
            add(SetupPage.PERMISSIONS)
            add(SetupPage.LOOKS)
            if (hasInternet && hasRemoteProvider) add(SetupPage.CLOUD)
            // WithML shows its bundled models as ready; NoML needs the network to download them.
            if (modelsBundled || hasInternet) add(SetupPage.AI_MODELS)
            add(SetupPage.TIPS)
        }

    /** Permission rows in display order. Photos and videos share the one required media grant. */
    val permissions: List<SetupPermission>
        get() = buildList {
            add(SetupPermission.IMAGES)
            add(SetupPermission.VIDEO)
            if (sdkInt >= Build.VERSION_CODES.Q && declares(ACCESS_MEDIA_LOCATION)) {
                add(SetupPermission.MEDIA_LOCATION)
            }
            if (hasInternet) add(SetupPermission.INTERNET)
            // Local network access only matters for network features, which need INTERNET too.
            if (sdkInt >= SDK_LOCAL_NETWORK && hasInternet && declares(ACCESS_LOCAL_NETWORK)) {
                add(SetupPermission.LOCAL_NETWORK)
            }
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU && declares(POST_NOTIFICATIONS)) {
                add(SetupPermission.NOTIFICATIONS)
            }
            if (sdkInt >= Build.VERSION_CODES.S && declares(MANAGE_MEDIA)) {
                add(SetupPermission.MANAGE_MEDIA)
            }
            if (sdkInt >= Build.VERSION_CODES.S && allowAllFilesAccess && declares(MANAGE_EXTERNAL_STORAGE)) {
                add(SetupPermission.MANAGE_FILES)
            }
        }

    private fun declares(permission: String) = permission in declaredPermissions

    companion object {
        const val INTERNET = "android.permission.INTERNET"
        const val ACCESS_MEDIA_LOCATION = "android.permission.ACCESS_MEDIA_LOCATION"
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        const val MANAGE_MEDIA = "android.permission.MANAGE_MEDIA"
        const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

        /** Android 17, where direct LAN connections need a runtime grant. */
        const val SDK_LOCAL_NETWORK = 37
    }
}

/** The permissions this package's merged manifest requests. */
internal fun Context.declaredPermissions(): Set<String> = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }
    info.requestedPermissions?.toSet().orEmpty()
}.getOrElse {
    printWarn("app.setup", "Could not read requested permissions", mapOf("error" to it.toString()))
    emptySet()
}
