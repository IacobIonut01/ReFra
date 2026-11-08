/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.exif

import com.dot.gallery.feature.exif.CloudCopyUiState
import com.dot.gallery.feature.exif.CopyWorkState
import com.dot.gallery.feature.exif.LocalCopyUiState
import com.dot.gallery.feature.exif.copySheetOperationVisible
import com.dot.gallery.feature.exif.isTerminalCopyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy sheet used to open on every launch whenever any `MediaCopyWorker` work was still queued
 * in WorkManager, such as a copy interrupted by closing the app or one stuck after a failure. It
 * now follows only transfers the sheet itself started.
 */
class CopySheetVisibilityTest {

    @Test
    fun freshSheetStaysClosedRegardlessOfBackgroundWork() {
        // A new view model has no tracked transfer, whatever WorkManager still holds.
        assertFalse(copySheetOperationVisible(LocalCopyUiState(), CloudCopyUiState()))
    }

    @Test
    fun startedLocalCopyOpensSheetUntilResultIsDismissed() {
        val running = LocalCopyUiState(workIds = listOf("MediaCopyWorker_1_1_x"), total = 1, active = true)
        assertTrue(copySheetOperationVisible(running, CloudCopyUiState()))

        val failed = running.copy(active = false, finished = true, succeeded = false)
        assertTrue(copySheetOperationVisible(failed, CloudCopyUiState()))

        // Dismissing clears the result back to the empty state, which closes the sheet.
        assertFalse(copySheetOperationVisible(LocalCopyUiState(), CloudCopyUiState()))
    }

    @Test
    fun startedCloudCopyOpensSheet() {
        assertTrue(copySheetOperationVisible(LocalCopyUiState(), CloudCopyUiState(active = true)))
        assertTrue(copySheetOperationVisible(LocalCopyUiState(), CloudCopyUiState(finished = true)))
    }

    @Test
    fun restoredCloudWorkIsOnlyResumedWhileRunning() {
        assertTrue(CopyWorkState.SUCCEEDED.isTerminalCopyState())
        assertTrue(CopyWorkState.FAILED.isTerminalCopyState())
        assertTrue(CopyWorkState.CANCELLED.isTerminalCopyState())
        assertFalse(CopyWorkState.RUNNING.isTerminalCopyState())
        assertFalse(CopyWorkState.ENQUEUED.isTerminalCopyState())
        assertFalse(CopyWorkState.BLOCKED.isTerminalCopyState())
    }
}
