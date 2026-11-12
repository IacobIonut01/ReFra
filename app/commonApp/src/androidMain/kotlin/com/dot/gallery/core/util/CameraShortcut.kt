/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.dot.gallery.core.logging.printError

/** Opens the camera app in photo mode, as the Timeline Camera shortcut does. */
private fun stillCameraIntent() = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

/** Whether any installed app opens [MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA]. */
fun Context.hasStillCamera(): Boolean = stillCameraIntent().resolveActivity(packageManager) != null

fun Context.launchStillCamera() {
    try {
        startActivity(stillCameraIntent())
    } catch (e: ActivityNotFoundException) {
        printError("app.camera", "No app handles the still camera intent", throwable = e)
    } catch (e: SecurityException) {
        printError("app.camera", "The camera app refused the still camera intent", throwable = e)
    }
}
