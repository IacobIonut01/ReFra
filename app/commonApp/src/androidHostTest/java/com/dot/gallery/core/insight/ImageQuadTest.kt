/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_LINEAR
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_MATRIX
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_PRODUCT
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_QR
import com.dot.gallery.domain.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQuadTest {

    @Test
    fun fromPixelsNormalisesAndClamps() {
        val points = intArrayOf(9, 9, 0, 0, 200, 50, 400, 50, 400, 250, 200, 250)
        val quad = ImageQuad.fromPixels(points, offset = 4, width = 400, height = 200)
        assertEquals(0.5f, quad.x0, 1e-6f)
        assertEquals(0.25f, quad.y0, 1e-6f)
        assertEquals(1f, quad.x1, 1e-6f)
        // 250 / 200 is outside the frame and clamps to the edge.
        assertEquals(1f, quad.y2, 1e-6f)
    }

    @Test
    fun containsHandlesPerspectiveQuads() {
        // A skewed quad like a code photographed at an angle.
        val quad = ImageQuad(0.30f, 0.20f, 0.70f, 0.25f, 0.68f, 0.60f, 0.28f, 0.55f)
        assertTrue(quad.contains(0.5f, 0.4f))
        assertFalse(quad.contains(0.29f, 0.21f))
        assertFalse(quad.contains(0.9f, 0.4f))
        assertEquals(0.28f, quad.left, 1e-6f)
        assertEquals(0.60f, quad.bottom, 1e-6f)
    }

    @Test
    fun formatBitsMatchZxingCpp() {
        // Values from zxing-cpp's BarcodeFormat.h; the native reader takes this mask directly.
        assertEquals(1 shl 13, CodeFormat.QR_CODE.bit)
        assertEquals(1 shl 7, CodeFormat.DATA_MATRIX.bit)
        assertEquals(1 shl 9, CodeFormat.EAN_13.bit)
        assertEquals(1 shl 19, CodeFormat.DATA_BAR_LIMITED.bit)
        assertEquals(CodeFormat.entries.size, CodeFormat.entries.map { it.bit }.toSet().size)
        CodeFormat.entries.forEach { assertEquals(it, CodeFormat.fromBit(it.bit)) }
    }

    @Test
    fun groupsCoverEveryFormatExactlyOnce() {
        val grouped = CodeFormat.groups.values.flatten()
        assertEquals(CodeFormat.entries.size - 1, grouped.size)
        assertEquals(grouped.size, grouped.toSet().size)
        // DX film edge codes say nothing useful to a person and are not offered.
        assertFalse(CodeFormat.DX_FILM_EDGE in grouped)
    }

    @Test
    fun settingsGroupNamesMatchCodeFormatGroups() {
        assertEquals(
            setOf(CODE_SCAN_GROUP_QR, CODE_SCAN_GROUP_MATRIX, CODE_SCAN_GROUP_PRODUCT, CODE_SCAN_GROUP_LINEAR),
            CodeFormat.groups.keys,
        )
        assertEquals(CodeFormat.DEFAULT_GROUPS, SettingsKeys.codeScanFormats.default)
    }

    @Test
    fun maskForGroups() {
        val qr = CodeFormat.maskForGroups(setOf(CODE_SCAN_GROUP_QR))
        assertEquals(CodeFormat.QR_CODE.bit or CodeFormat.MICRO_QR_CODE.bit or CodeFormat.RMQR_CODE.bit, qr)
        assertEquals(0, CodeFormat.maskForGroups(emptySet()))
        assertEquals(0, CodeFormat.maskForGroups(setOf("unknown")))
    }

    @Test
    fun scanningIsOnAndAutomaticByDefault() {
        assertTrue(SettingsKeys.codeScanEnabled.default)
        assertEquals("auto", SettingsKeys.codeScanMode.default)
    }
}
