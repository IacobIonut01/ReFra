/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.vault

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.feature.vault.domain.model.VaultGateAction
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.ui.components.SelectVaultSheet
import com.dot.gallery.feature.vault.ui.components.VaultDeviceAuthBridge
import com.dot.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The selector sheet must keep an "Unlock vault" placeholder with a working Unlock
 * retry whenever the device-credential prompt fails or is cancelled, instead of
 * hiding itself with no way back in.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class SelectVaultSheetGateTest {

    @get:Rule
    val rule = createComposeRule()

    private fun showSheet(
        resolveGateAction: suspend () -> VaultGateAction,
        outcomeState: MutableState<Boolean?>,
        onAuthenticate: () -> Unit,
    ): Pair<AppBottomSheetState, CoroutineScope> {
        lateinit var sheetState: AppBottomSheetState
        lateinit var scope: CoroutineScope
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                GalleryTheme(ignoreUserPreference = true) {
                    scope = rememberCoroutineScope()
                    sheetState = rememberAppBottomSheetState()
                    SelectVaultSheet(
                        state = sheetState,
                        vaultState = VaultState(
                            vaults = listOf(Vault(name = "Trips")),
                            isLoading = false
                        ),
                        resolveGateAction = resolveGateAction,
                        verifyGateSecret = { VerifyResult.Failed(0) },
                        deviceAuth = VaultDeviceAuthBridge(
                            isSupported = true,
                            authenticateAction = onAuthenticate,
                            cancelAction = {},
                            outcomeState = outcomeState
                        ),
                        onVaultSelected = {},
                    )
                }
            }
        }
        return sheetState to scope
    }

    @Test
    fun deviceChallengeWithoutPromptOutcomeKeepsUnlockRetry() {
        var authCalls = 0
        val outcomeState = mutableStateOf<Boolean?>(null)
        val (sheetState, scope) = showSheet(
            resolveGateAction = { VaultGateAction.ChallengeDevice },
            outcomeState = outcomeState,
            onAuthenticate = { authCalls++ },
        )
        rule.runOnUiThread { scope.launch { sheetState.show() } }
        rule.waitUntil("prompt requested", 5_000) { authCalls == 1 }

        rule.onNodeWithText("Unlock vault").assertExists()
        rule.onNodeWithText("Unlock").assertExists()

        rule.onNodeWithText("Unlock").performClick()
        rule.waitUntil("second prompt requested", 5_000) { authCalls == 2 }
        rule.onNodeWithText("Unlock vault").assertExists()
        assertTrue(sheetState.isVisible)
    }

    @Test
    fun deviceChallengeFailureKeepsSheetAndSuccessRevealsVaults() {
        var authCalls = 0
        val outcomeState = mutableStateOf<Boolean?>(null)
        val (sheetState, scope) = showSheet(
            resolveGateAction = { VaultGateAction.ChallengeDevice },
            outcomeState = outcomeState,
            onAuthenticate = { authCalls++ },
        )
        rule.runOnUiThread { scope.launch { sheetState.show() } }
        rule.waitUntil("prompt requested", 5_000) { authCalls == 1 }

        // A cancelled/failed prompt must not dismiss the sheet.
        rule.runOnUiThread { outcomeState.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Unlock vault").assertExists()
        rule.onNodeWithText("Unlock").assertExists()
        assertTrue(sheetState.isVisible)

        // Unlock retries the prompt.
        rule.onNodeWithText("Unlock").performClick()
        rule.waitUntil("second prompt requested", 5_000) { authCalls == 2 }

        // A successful prompt reveals the vault list.
        rule.runOnUiThread { outcomeState.value = true }
        rule.waitUntil("vault list revealed", 5_000) {
            rule.onAllNodesWithText("Trips").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Select a vault").assertExists()
        rule.onNodeWithText("Trips").assertExists()
    }

    @Test
    fun denyHidesSheet() {
        val outcomeState = mutableStateOf<Boolean?>(null)
        val (sheetState, scope) = showSheet(
            resolveGateAction = { VaultGateAction.Deny },
            outcomeState = outcomeState,
            onAuthenticate = {},
        )
        rule.runOnUiThread { scope.launch { sheetState.show() } }
        rule.waitUntil("sheet hidden", 5_000) { !sheetState.isVisible }
    }
}
