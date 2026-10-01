/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * On API 29+ MediaProvider serves shared storage reads with the GPS EXIF byte ranges
 * zeroed unless the caller holds ACCESS_MEDIA_LOCATION and asks for the original file.
 * The unredacted descriptor is only worth requesting when all three conditions hold.
 */
internal fun shouldRequestOriginalMetadata(
    sdkInt: Int,
    permissionGranted: Boolean,
    isMediaStoreUri: Boolean
): Boolean = sdkInt >= Build.VERSION_CODES.Q && permissionGranted && isMediaStoreUri

internal fun <T> openOriginalOrFallback(
    requestOriginal: Boolean,
    openOriginal: () -> T?,
    openFallback: () -> T?
): T? {
    if (requestOriginal) runCatching(openOriginal).getOrNull()?.let { return it }
    return runCatching(openFallback).getOrNull()
}

/**
 * Opens [uri] for metadata reads. When ACCESS_MEDIA_LOCATION is granted and [uri] is
 * served by MediaProvider (API 29+), requests the unredacted original so GPS EXIF
 * survives; falls back to a regular descriptor on any failure. Every EXIF read path
 * (isolated parse, in-process fallback, frame-source materialization) goes through
 * this so the redaction policy stays in one place.
 */
fun Context.openUnredactedFileDescriptor(uri: Uri): ParcelFileDescriptor? {
    val permissionGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val isMediaStoreUri = uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY
    return openOriginalOrFallback(
        requestOriginal = shouldRequestOriginalMetadata(
            Build.VERSION.SDK_INT,
            permissionGranted,
            isMediaStoreUri
        ),
        openOriginal = {
            contentResolver.openFileDescriptor(MediaStore.setRequireOriginal(uri), "r")
        },
        openFallback = { contentResolver.openFileDescriptor(uri, "r") }
    )
}
