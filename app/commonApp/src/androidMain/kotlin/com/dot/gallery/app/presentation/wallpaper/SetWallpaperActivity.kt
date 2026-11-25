/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.wallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.util.getString
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.set_wallpaper_error

/**
 * Redirects a caller-delegated image URI to the platform wallpaper cropper.
 *
 * The incoming URI is attacker-controlled because this activity is exported. Never forward
 * ReFra's own FileProvider URIs for external callers and never emit an unrestricted implicit
 * crop intent: either one would let the caller use ReFra as a confused deputy and redeem the
 * outgoing read grant in a third-party receiver.
 */
class SetWallpaperActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.data
        if (imageUri != null) {
            val hasInternalToken = WallpaperLaunchTokens.consume(
                uri = imageUri.toString(),
                token = intent.getStringExtra(WallpaperLaunchTokens.EXTRA_TOKEN),
            )
            val callerIsSelf = hasInternalToken || (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    launchedFromUid == Process.myUid()
                )
            val callerAccess = if (callerIsSelf) {
                CallerUriAccess.GRANTED
            } else {
                callerReadAccess(imageUri)
            }
            val canForward = WallpaperUriPolicy.shouldForward(
                scheme = imageUri.scheme,
                authority = imageUri.authority,
                ownProviderAuthority = BuildConfig.CONTENT_AUTHORITY,
                callerIsSelf = callerIsSelf,
                callerAccess = callerAccess,
                incomingHasReadGrant = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            )
            if (canForward) {
                forwardToWallpaperCropper(imageUri)
            } else {
                printWarn(
                    TAG,
                    "Rejected an unsafe wallpaper URI",
                    mapOf(
                        "scheme" to imageUri.scheme.orEmpty(),
                        "ownProvider" to (imageUri.authority?.equals(
                            BuildConfig.CONTENT_AUTHORITY,
                            ignoreCase = true,
                        ) == true).toString(),
                        "callerIsSelf" to callerIsSelf.toString(),
                    ),
                )
            }
        }
        finish()
    }

    private fun callerReadAccess(imageUri: Uri): CallerUriAccess {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return CallerUriAccess.UNKNOWN
        }
        return try {
            val componentCaller = caller ?: return CallerUriAccess.DENIED
            when (
                componentCaller.checkContentUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            ) {
                PackageManager.PERMISSION_GRANTED -> CallerUriAccess.GRANTED
                else -> CallerUriAccess.DENIED
            }
        } catch (e: RuntimeException) {
            printError(TAG, "Could not verify wallpaper caller URI access", e)
            CallerUriAccess.DENIED
        }
    }

    private fun forwardToWallpaperCropper(imageUri: Uri) {
        try {
            val launchIntent = WallpaperManager.getInstance(this)
                .getCropAndSetWallpaperIntent(imageUri)
                .apply {
                    putExtra("mimeType", "image/*")
                }
            if (launchIntent.`package`.isNullOrEmpty() && launchIntent.component == null) {
                printWarn(TAG, "Rejected an unrestricted wallpaper crop intent")
                showWallpaperError()
                return
            }
            startActivity(launchIntent)
        } catch (e: IllegalArgumentException) {
            printError(TAG, "Could not create the wallpaper crop intent", e)
            showWallpaperError()
        } catch (e: ActivityNotFoundException) {
            printError(TAG, "No wallpaper cropper is available", e)
            showWallpaperError()
        } catch (e: SecurityException) {
            printError(TAG, "The wallpaper cropper rejected the image grant", e)
            showWallpaperError()
        }
    }

    private fun showWallpaperError() {
        Toast.makeText(this, getString(Res.string.set_wallpaper_error), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "app.wallpaper"
    }
}
