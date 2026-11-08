/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test: copied files were renamed to a short numeric name
 * because content:// URIs expose only the MediaStore row id via
 * Uri.getLastPathSegment. The media label from the work input must win over the
 * URI-derived fallback.
 */
class MediaCopyDisplayNameTest {

    @Test
    fun labelWinsOverUriSegment() {
        assertEquals(
            "IMG_20231015_142233.jpg",
            copyDisplayName(labelHint = "IMG_20231015_142233.jpg", uriSegmentFallback = "123456")
        )
    }

    @Test
    fun uriSegmentUsedWhenNoLabel() {
        assertEquals(
            "123456",
            copyDisplayName(labelHint = null, uriSegmentFallback = "123456")
        )
    }

    @Test
    fun genericFallbackWhenNothingAvailable() {
        assertEquals("media", copyDisplayName(labelHint = null, uriSegmentFallback = null))
    }
}
