package com.dot.gallery.app.presentation.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: the editor's Undo and Redo disappeared whenever there was
 * nothing to undo or redo, so the top bar jumped around. They now always
 * show and are disabled instead.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class EditUndoRedoButtonsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun bothButtonsStayVisibleAndOnlyEnableWhenUsable() {
        var canUndo by mutableStateOf(false)
        var canRedo by mutableStateOf(false)
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                EditUndoRedoButtons(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    enabled = true,
                    onUndo = {},
                    onRedo = {}
                )
            }
        }

        rule.onNodeWithTag(TAG_EDIT_UNDO).assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag(TAG_EDIT_REDO).assertIsDisplayed().assertIsNotEnabled()

        canUndo = true
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_EDIT_UNDO).assertIsEnabled()
        rule.onNodeWithTag(TAG_EDIT_REDO).assertIsDisplayed().assertIsNotEnabled()

        canRedo = true
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_EDIT_REDO).assertIsEnabled()
    }

    @Test
    fun savingDisablesBothButStillShowsThem() {
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                EditUndoRedoButtons(
                    canUndo = true,
                    canRedo = true,
                    enabled = false,
                    onUndo = {},
                    onRedo = {}
                )
            }
        }
        rule.onNodeWithTag(TAG_EDIT_UNDO).assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag(TAG_EDIT_REDO).assertIsDisplayed().assertIsNotEnabled()
    }
}
