/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.image

import org.junit.Assert.assertEquals
import org.junit.Test


/**
 * [cloudCacheSizeFor] collapses thumbnail/preview requests that resolve to the
 * original URL onto the shared `"original"` disk-cache entry.
 */
class CloudCacheSizeForTest {

    @Test
    fun originalUrlCollapsesToOriginalSize() {
        val original = "https://dav.example/files/Photos/a.jpg"
        assertEquals("original", cloudCacheSizeFor(original, original, "thumbnail"))
        assertEquals("original", cloudCacheSizeFor(original, original, "preview"))
        assertEquals("original", cloudCacheSizeFor(original, original, "original"))
    }

    @Test
    fun distinctUrlKeepsRequestedSize() {
        val original = "https://dav.example/files/Photos/a.jpg"
        val preview = "https://dav.example/index.php/core/preview?fileId=1"
        assertEquals("thumbnail", cloudCacheSizeFor(preview, original, "thumbnail"))
        assertEquals("preview", cloudCacheSizeFor(preview, original, "preview"))
    }

    @Test
    fun blankUrlsKeepRequestedSize() {
        assertEquals("preview", cloudCacheSizeFor("", "https://x/y", "preview"))
        assertEquals("preview", cloudCacheSizeFor("https://x/y", "", "preview"))
        assertEquals("preview", cloudCacheSizeFor("", "", "preview"))
    }
}
