/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings

import com.dot.gallery.feature.settings.ui.formatBytes
import com.dot.gallery.feature.settings.ui.isPrivateFolderOnInternalStorage
import com.dot.gallery.feature.settings.ui.privateFolderDisplayPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateFolderPathTest {

    private val internal =
        "content://com.android.externalstorage.documents/tree/primary%3APictures%2FPrivate"
    private val sdCard =
        "content://com.android.externalstorage.documents/tree/1234-5678%3ASD%20Card%2FHidden%20Photos"

    @Test
    fun displayPathDropsTheVolumePrefix() {
        assertEquals("Pictures/Private", privateFolderDisplayPath(internal))
        assertEquals("SD Card/Hidden Photos", privateFolderDisplayPath(sdCard))
    }

    @Test
    fun primaryVolumeIsInternalStorage() {
        assertTrue(isPrivateFolderOnInternalStorage(internal))
        assertFalse(isPrivateFolderOnInternalStorage(sdCard))
    }

    @Test
    fun sizesReachGigabytes() {
        assertEquals("1.8 GB", formatBytes(1_932_735_283L))
        assertEquals("312.0 MB", formatBytes(312L * 1_048_576L))
    }
}
