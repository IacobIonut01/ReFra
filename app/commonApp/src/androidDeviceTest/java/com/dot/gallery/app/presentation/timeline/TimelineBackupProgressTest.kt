/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.app.presentation.main.TimelineBackupProgress
import com.dot.gallery.app.presentation.main.TimelineBackupChipTag
import com.dot.gallery.app.presentation.main.MainScreenTopBarContent
import com.dot.gallery.app.presentation.main.TimelineBackupCollapseTag
import com.dot.gallery.core.presentation.components.GalleryHomeTopBarTag
import com.dot.gallery.core.presentation.components.rememberGalleryHeaderRevealState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.dot.gallery.app.presentation.main.TimelineBackupProgressTag
import com.dot.gallery.app.presentation.main.TimelineBackupTrackTag
import com.dot.gallery.cloud.ui.backup.UploadDetailsUiState
import com.dot.gallery.core.ui.components.PreviewHost
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TimelineBackupProgressTest {
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun activeBackupCanExpandAndCollapseWithoutNavigation() {
        val backup = UploadDetailsUiState(isWorkerRunning = true, totalItems = 10, completedItems = 4)
        rule.setContent {
            PreviewHost {
                val reveal = rememberGalleryHeaderRevealState()
                SideEffect { reveal.enabled = true }
                MainScreenTopBarContent(
                    backup = backup,
                    revealState = reveal,
                    onBrandClick = {},
                    onOpenBackupDetails = {},
                    menu = {},
                    filter = null,
                )
            }
        }

        rule.onNodeWithTag(TimelineBackupProgressTag).assertDoesNotExist()
        rule.onNodeWithTag(TimelineBackupChipTag).assertIsDisplayed().performClick()
        rule.onNodeWithTag(TimelineBackupProgressTag).assertIsDisplayed()
        rule.onNodeWithTag(TimelineBackupCollapseTag).performClick()
        rule.onNodeWithTag(TimelineBackupProgressTag).assertDoesNotExist()
    }

    @Test
    fun swipingTheHeaderDownRevealsProgressAndSwipingUpHidesIt() {
        val backup = UploadDetailsUiState(isWorkerRunning = true, totalItems = 10, completedItems = 4)
        rule.setContent {
            PreviewHost {
                val reveal = rememberGalleryHeaderRevealState()
                SideEffect { reveal.enabled = true }
                MainScreenTopBarContent(
                    backup = backup,
                    revealState = reveal,
                    onBrandClick = {},
                    onOpenBackupDetails = {},
                    menu = {},
                    filter = null,
                )
            }
        }

        rule.onNodeWithTag(GalleryHomeTopBarTag).performTouchInput { swipeDown(endY = centerY + 400f) }
        rule.onNodeWithTag(TimelineBackupProgressTag).assertIsDisplayed()
        rule.onNodeWithTag(GalleryHomeTopBarTag).performTouchInput { swipeUp(startY = bottom - 1f, endY = top) }
        rule.onNodeWithTag(TimelineBackupProgressTag).assertDoesNotExist()
    }

    @Test
    fun knownProgressIsAccessibleAndOpensDetails() {
        var openedDetails = false
        rule.setContent {
            PreviewHost {
                TimelineBackupProgress(
                    state = UploadDetailsUiState(totalItems = 10, completedItems = 4),
                    onClick = { openedDetails = true },
                )
            }
        }

        rule.onNodeWithTag(TimelineBackupTrackTag, useUnmergedTree = true)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))
        rule.onNodeWithTag(TimelineBackupProgressTag).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(openedDetails) }
    }

    @Test
    fun unknownTotalDoesNotPretendTheBackupIsComplete() {
        rule.setContent {
            PreviewHost {
                TimelineBackupProgress(state = UploadDetailsUiState(totalItems = 0), onClick = {})
            }
        }

        rule.onNodeWithTag(TimelineBackupTrackTag, useUnmergedTree = true)
            .assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }
}
