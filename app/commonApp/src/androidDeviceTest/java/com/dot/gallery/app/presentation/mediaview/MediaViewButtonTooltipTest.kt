/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.viewer.MediaViewButton
import com.dot.gallery.ui.theme.GalleryTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the viewer action bar staying composed (faded to
 * alpha 0) while the chrome is hidden: a disabled [MediaViewButton] must not
 * answer long-press with its tooltip nor fire its click.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class MediaViewButtonTooltipTest {

    @get:Rule
    val rule = createComposeRule()

    private val clicks = AtomicInteger(0)

    private fun setButton(enabled: Boolean) {
        clicks.set(0)
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                MediaViewButton(
                    currentMedia = media(),
                    imageVector = Icons.Outlined.FavoriteBorder,
                    title = "Favorite",
                    enabled = enabled,
                    onItemClick = { clicks.incrementAndGet() }
                )
            }
        }
    }

    @Test
    fun disabledButtonShowsNoTooltipOnLongPress() {
        setButton(enabled = false)

        rule.onNodeWithContentDescription("Favorite")
            .performTouchInput { longClick() }
        rule.waitForIdle()

        // The button only carries the label as a contentDescription; a shown
        // PlainTooltip would add a real Text node with the same string.
        rule.onAllNodesWithText("Favorite", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(0, clicks.get())
    }

    @Test
    fun disabledButtonDoesNotFireClick() {
        setButton(enabled = false)

        rule.onNodeWithContentDescription("Favorite")
            .performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(0, clicks.get())
    }

    @Test
    fun enabledButtonShowsTooltipOnLongPress() {
        setButton(enabled = true)

        rule.onNodeWithContentDescription("Favorite")
            .performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, clicks.get())

        rule.onNodeWithContentDescription("Favorite")
            .performTouchInput { longClick() }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Favorite", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Favorite", useUnmergedTree = true).assertCountEquals(1)
    }

    private fun media() = UriMedia(
        id = 1L,
        label = "photo.jpg",
        uri = Uri.parse("content://media/external/images/media/1"),
        path = "/storage/emulated/0/DCIM/photo.jpg",
        relativePath = "DCIM/",
        albumID = 10L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L,
        fullDate = "2023-11-14",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_024L
    )
}
