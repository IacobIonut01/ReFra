/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.dot.gallery.R

fun Context.launchMap(lat: Double, lang: Double) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = "geo:0,0?q=$lat,$lang(${getString(R.string.media_location)})".toUri()
            }
        )
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = "https://www.google.com/maps/search/?api=1&query=$lat,$lang".toUri()
                }
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_map_app), Toast.LENGTH_SHORT).show()
        }
    }
}