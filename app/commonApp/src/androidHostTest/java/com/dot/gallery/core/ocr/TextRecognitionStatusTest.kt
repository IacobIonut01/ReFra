/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.feature.settings.ui.TextRecognitionState
import com.dot.gallery.feature.settings.ui.TextRecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TextRecognitionStatusTest {
    private fun state(canEnable: Boolean, enabled: Boolean) = TextRecognitionState(
        canEnable = canEnable,
        enabled = enabled,
        onRequest = false,
        indexLibrary = true,
        installedPackIds = if (canEnable) listOf("latin") else emptyList(),
        modelBytes = 0L,
        indexBytes = 0L,
        indexedPhotos = 0,
    )

    @Test
    fun withoutAReadyPackItAsksForLanguagesEvenIfTheSettingIsStillOn() {
        // The stored switch can lag behind a removed pack for a frame; the page must not offer Turn off.
        assertEquals(TextRecognitionStatus.NEEDS_PACK, TextRecognitionStatus.of(state(canEnable = false, enabled = true)))
        assertEquals(TextRecognitionStatus.NEEDS_PACK, TextRecognitionStatus.of(state(canEnable = false, enabled = false)))
    }

    @Test
    fun withAReadyPackItFollowsTheSetting() {
        assertEquals(TextRecognitionStatus.OFF, TextRecognitionStatus.of(state(canEnable = true, enabled = false)))
        assertEquals(TextRecognitionStatus.ON, TextRecognitionStatus.of(state(canEnable = true, enabled = true)))
    }
}
