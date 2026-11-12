/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.components.PreviewHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SheetNameInputFocusTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * The name sheets must grab focus as soon as their content composes so the IME
     * opens together with the sheet animation ([RequestSheetFieldFocus]).
     */
    @Test
    fun nameFieldIsFocusedWhenSheetContentComposes() {
        rule.setContent {
            PreviewHost {
                ExpressiveSheetColumn {
                    SheetNameInputContent(
                        title = "New collection",
                        value = "",
                        onValueChange = {},
                        label = "Name",
                        confirmText = "Create",
                        dismissText = "Cancel",
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }

        rule.onNode(hasSetTextAction()).assertIsFocused()
    }
}
