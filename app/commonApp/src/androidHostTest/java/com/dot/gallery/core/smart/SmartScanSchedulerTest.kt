/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.data.entity.SmartScanTrigger
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the dead "Scan for people" button: a run whose WorkManager task
 * sits `Enqueued` — in retry-backoff or waiting on constraints — used to satisfy
 * the not-finished check, so every manual request merged into the stale run and
 * nothing ever executed. Manual triggers now replace the deferred task instead.
 */
class SmartScanSchedulerTest {

    @Test
    fun manualRequestReplacesDeferredTask() {
        assertTrue(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Enqueued))
    }

    @Test
    fun runningTaskIsLeftAlone() {
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Running))
    }

    @Test
    fun automaticRequestKeepsDeferredTask() {
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.AUTOMATIC, TaskState.Enqueued))
    }

    @Test
    fun recoveryRequestKeepsDeferredTask() {
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.RECOVERY, TaskState.Enqueued))
    }

    @Test
    fun terminalAndUnknownStatesAreNotReplaced() {
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Succeeded()))
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Failed()))
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Cancelled))
        assertFalse(shouldKickDeferredTask(SmartScanTrigger.MANUAL, TaskState.Unknown))
    }
}
