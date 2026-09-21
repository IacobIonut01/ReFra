/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSearchProvidersTest {

    private fun target(
        packageName: String,
        label: String = packageName,
    ) = VisualSearchTarget(
        packageName = packageName,
        activityName = "$packageName.ShareActivity",
        label = label,
        known = KnownVisualSearchProvider.forPackage(packageName),
    )

    @Test
    fun resolveReturnsNullWhenNothingIsInstalled() {
        assertNull(resolveVisualSearchTarget(emptyList(), ""))
        assertNull(resolveVisualSearchTarget(emptyList(), "com.google.ar.lens"))
    }

    @Test
    fun storedPackageWinsWhenStillInstalled() {
        val targets = listOf(
            target("com.google.ar.lens"),
            target("com.microsoft.bing"),
        )

        val resolved = resolveVisualSearchTarget(targets, "com.microsoft.bing")

        assertEquals("com.microsoft.bing", resolved?.packageName)
    }

    @Test
    fun missingStoredPackageFallsBackToFirstTarget() {
        val targets = listOf(
            target("com.google.ar.lens"),
            target("com.microsoft.bing"),
        )

        val resolved = resolveVisualSearchTarget(targets, "com.uninstalled.app")

        assertEquals("com.google.ar.lens", resolved?.packageName)
    }

    @Test
    fun autoResolvesToFirstTarget() {
        val targets = listOf(
            target("com.microsoft.bing"),
            target("com.unknown.app"),
        )

        val resolved = resolveVisualSearchTarget(
            targets,
            com.dot.gallery.core.Settings.Misc.VISUAL_SEARCH_PROVIDER_AUTO,
        )

        assertEquals("com.microsoft.bing", resolved?.packageName)
    }

    @Test
    fun knownProvidersExposeBrandNameAsDisplayName() {
        assertEquals("Google Lens", target("com.google.ar.lens", "Lens").displayName)
        assertEquals("Some App", target("com.unknown.app", "Some App").displayName)
    }

    @Test
    fun onlyLensKeepsBrandColorsUntinted() {
        assertFalse(target("com.google.ar.lens").tintIcon)
        assertTrue(target("com.microsoft.bing").tintIcon)
        assertTrue(target("com.unknown.app").tintIcon)
    }

    @Test
    fun exoticMimesNeedConversionButSafeMimesDoNot() {
        assertTrue(needsVisualSearchConvert("image/x-canon-cr2", isImage = true))
        assertTrue(needsVisualSearchConvert("image/tiff", isImage = true))
        assertTrue(needsVisualSearchConvert("image/vnd.adobe.photoshop", isImage = true))
        assertTrue(needsVisualSearchConvert("image/jxl", isImage = true))
        assertTrue(needsVisualSearchConvert(null, isImage = true))

        assertFalse(needsVisualSearchConvert("image/jpeg", isImage = true))
        assertFalse(needsVisualSearchConvert("image/png", isImage = true))
        assertFalse(needsVisualSearchConvert("image/webp", isImage = true))
        assertFalse(needsVisualSearchConvert("image/heic", isImage = true))
        assertFalse(needsVisualSearchConvert("image/avif", isImage = true))
        assertFalse(needsVisualSearchConvert("image/gif", isImage = true))

        // Videos never convert — the extracted frame is already JPEG.
        assertFalse(needsVisualSearchConvert("video/mp4", isImage = false))
        assertFalse(needsVisualSearchConvert(null, isImage = false))
    }
}
