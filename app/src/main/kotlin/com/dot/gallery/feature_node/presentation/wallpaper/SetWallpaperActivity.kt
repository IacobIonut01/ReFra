/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.wallpaper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dot.gallery.R

/**
 * This is a dummy activity that redirects the Intent action ATTACH_DATA
 * to the system's CROP_AND_SET_WALLPAPER
 */
class SetWallpaperActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Image uri received via intent
        val imageUri = intent.data

        if (imageUri != null) {
            // Launch the system CROP_AND_SET_WALLPAPER app
            val launchIntent = Intent("android.service.wallpaper.CROP_AND_SET_WALLPAPER").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(imageUri, "image/*")
                putExtra("mimeType", "image/*")
                // Make sure uri permission is granted
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(launchIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.set_wallpaper_error), Toast.LENGTH_SHORT).show()
            }
        }
        // Just finish the activity after launching the intent
        // as there's no need of its existence
        finish()
    }

}