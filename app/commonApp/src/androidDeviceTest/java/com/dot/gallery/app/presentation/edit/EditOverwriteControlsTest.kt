package com.dot.gallery.app.presentation.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: Overwrite was hidden in the top bar's overflow menu and
 * replaced the photo at once. It is now a button beside Save copy that
 * only asks; the overwrite happens on the dialog's confirm.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class EditOverwriteControlsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun overwriteAsksBeforeReplacingTheOriginal() {
        var overwrites = 0
        var asking by mutableStateOf(false)
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                EditOverwriteButton(enabled = true, onClick = { asking = true })
                if (asking) {
                    OverwriteConfirmDialog(
                        onConfirm = {
                            asking = false
                            overwrites++
                        },
                        onDismiss = { asking = false }
                    )
                }
            }
        }

        rule.onNodeWithTag(TAG_EDIT_OVERWRITE).assertIsDisplayed().performClick()
        assertEquals(0, overwrites)
        // The dialog says the original is backed up.
        rule.onNodeWithText("Revert to Original", substring = true).assertIsDisplayed()

        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertEquals(0, overwrites)

        rule.onNodeWithTag(TAG_EDIT_OVERWRITE).performClick()
        rule.onNodeWithTag(TAG_EDIT_OVERWRITE_CONFIRM).performClick()
        rule.waitForIdle()
        assertEquals(1, overwrites)
    }
}
