/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.privatefolder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.ui.PrivateFolderSecuritySetupContent
import com.dot.gallery.feature.vault.ui.components.VaultPinMaxLength
import com.dot.gallery.feature.vault.ui.components.VaultSecretInput
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The private-folder lock picker keeps its three outcomes: device security by default, No lock,
 * and PIN / Pattern / Password through the shared create-and-confirm page.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class PrivateFolderSecuritySetupContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                GalleryTheme(ignoreUserPreference = true) {
                    PrivateFolderSecuritySetupContent(
                        onBack = { calls += "back" },
                        onNoneSelected = { calls += "none" },
                        onDeviceSecuritySelected = { calls += "device" },
                        onCustomSecretSet = { type, secret -> calls += "custom:$type:$secret" },
                    )
                }
            }
        }
    }

    @Test
    fun continueDefaultsToDeviceSecurity() {
        show()
        rule.onNodeWithText("Device security").assertIsSelected()
        rule.onNodeWithText("Continue").performClick()
        assertEquals(listOf("device"), calls)
    }

    @Test
    fun noLockCallsOnNoneSelected() {
        show()
        rule.onNodeWithText("No lock").performClick()
        rule.onNodeWithText("No lock").assertIsSelected()
        rule.onNodeWithText("Continue").performClick()
        assertEquals(listOf("none"), calls)
    }

    @Test
    fun pinCreatesAndConfirmsBeforeReportingTheSecret() {
        show()
        rule.onNodeWithText("PIN").performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Create a PIN").assertExists()

        repeat(2) {
            "123456".forEach { digit -> rule.onNodeWithText(digit.toString()).performClick() }
            rule.onNodeWithContentDescription("Confirm").performClick()
            rule.waitForIdle()
        }
        assertEquals(listOf("custom:${VaultAuthType.PIN}:123456"), calls)
    }

    @Test
    fun pinRowStatesTheSupportedRangeForThePrivateFolder() {
        show()
        rule.onNodeWithText("6 to 16 digits, just for the private folder").assertExists()
    }

    @Test
    fun pinLongerThanSixDigitsIsAcceptedUpToSixteen() {
        show()
        rule.onNodeWithText("PIN").performClick()
        rule.onNodeWithText("Continue").performClick()

        "12345678".forEach { rule.onNodeWithText(it.toString()).performClick() }
        // Past the minimum the helper counts against the maximum, never "8 of 6 digits".
        rule.onNodeWithText("8 of up to 16 digits").assertExists()

        // Eight more digits reach 16; the 17th press is ignored, as it was before 6.0.0.
        val sixteen = "1234567890123456"
        "901234567".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("16 of up to 16 digits").assertExists()
        rule.onNodeWithContentDescription("Confirm").performClick()
        rule.waitForIdle()

        // The confirm page starts empty.
        (sixteen + "7").forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithContentDescription("Confirm").performClick()
        assertEquals(listOf("custom:${VaultAuthType.PIN}:$sixteen"), calls)
    }

    @Test
    fun unlockAcceptsSixteenDigitPinsSetBefore600() {
        var submitted: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                GalleryTheme(ignoreUserPreference = true) {
                    Column(Modifier.fillMaxSize()) {
                        VaultSecretInput(
                            type = VaultAuthType.PIN,
                            errorMessage = null,
                            creating = false,
                            submitLabel = "",
                            onSubmit = { submitted = it },
                        )
                    }
                }
            }
        }
        val legacyPin = "9876543210987654"
        legacyPin.forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithContentDescription("Confirm").performClick()
        assertEquals(legacyPin, submitted)
        assertEquals(16, VaultPinMaxLength)
    }
}
