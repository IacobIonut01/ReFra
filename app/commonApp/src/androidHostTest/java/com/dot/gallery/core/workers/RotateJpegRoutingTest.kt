/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only real JPEGs take the lossless EXIF-orientation rotation path. */
class RotateJpegRoutingTest {

    @Test
    fun jpegMimeDetection() {
        assertTrue(isJpegMime("image/jpeg"))
        assertTrue(isJpegMime("IMAGE/JPG"))
        assertFalse(isJpegMime("image/heic"))
        assertFalse(isJpegMime("image/png"))
    }
}
