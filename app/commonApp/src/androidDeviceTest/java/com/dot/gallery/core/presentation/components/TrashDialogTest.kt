/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.fixture.DemoFixtures
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.trash.TrashDialogAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TrashDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * With the trash confirmation disabled, showing the dialog auto-confirms —
     * and must do so on every show, not just the first. The confirmed latch
     * used to stay set because its reset lived only inside the rendered-sheet
     * branch, so a second delete silently did nothing.
     */
    @Test
    fun noConfirmationTrashFiresConfirmOnEveryShow() {
        val store = MapSettingsStore().apply {
            seed(SettingsKeys.trashConfirmationEnabled to false)
        }
        val items: List<Media> = DemoFixtures.media(nowSec = 1_700_000_000L).take(2)
        var confirmedCount by mutableIntStateOf(0)
        lateinit var sheetState: AppBottomSheetState
        lateinit var dialogScope: CoroutineScope

        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                dialogScope = rememberCoroutineScope()
                sheetState = rememberAppBottomSheetState()
                TrashDialog(
                    appBottomSheetState = sheetState,
                    data = items,
                    action = TrashDialogAction.TRASH,
                    supportsCloudTrash = { false },
                    onConfirm = { confirmed -> confirmedCount += confirmed.size }
                )
            }
        }

        // First show → auto-confirm fires and the sheet hides itself.
        rule.runOnUiThread { dialogScope.launch { sheetState.show() } }
        rule.waitUntil("first confirm fired", 5_000) { confirmedCount == items.size }
        rule.waitUntil("sheet hidden after first confirm", 5_000) { !sheetState.isVisible }

        // Second show must fire again instead of wedging on the stale latch.
        rule.runOnUiThread { dialogScope.launch { sheetState.show() } }
        rule.waitUntil("second confirm fired", 5_000) { confirmedCount == items.size * 2 }
        rule.waitUntil("sheet hidden after second confirm", 5_000) { !sheetState.isVisible }
        assertEquals(items.size * 2, confirmedCount)
    }
}
