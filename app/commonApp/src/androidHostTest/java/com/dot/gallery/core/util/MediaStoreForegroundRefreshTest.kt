/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import com.dot.gallery.core.util.ext.MediaStoreForegroundRefresh
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreForegroundRefreshTest {

    private class Rig {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        var version = "v1"
        var requeryCount = 0
        val refresh = MediaStoreForegroundRefresh(
            readVersion = { version },
            requery = { requeryCount++ },
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun foregroundWithoutBackgroundDoesNotRequery() = runTest {
        val rig = Rig()
        rig.scope.advanceUntilIdle()
        rig.scope.launch { rig.refresh.onForeground() }
        rig.scope.advanceUntilIdle()
        assertEquals(0, rig.requeryCount)
    }

    @Test
    fun unchangedVersionDoesNotRequery() = runTest {
        val rig = Rig()
        rig.scope.launch { rig.refresh.onBackground() }
        rig.scope.advanceUntilIdle()
        rig.scope.launch { rig.refresh.onForeground() }
        rig.scope.advanceUntilIdle()
        assertEquals(0, rig.requeryCount)
    }

    @Test
    fun changedVersionRequeriesOnce() = runTest {
        val rig = Rig()
        rig.scope.launch { rig.refresh.onBackground() }
        rig.scope.advanceUntilIdle()
        rig.version = "v2"
        rig.scope.launch { rig.refresh.onForeground() }
        rig.scope.advanceUntilIdle()
        assertEquals(1, rig.requeryCount)
        rig.scope.launch { rig.refresh.onForeground() }
        rig.scope.advanceUntilIdle()
        assertEquals(1, rig.requeryCount)
    }

    @Test
    fun throwingVersionReadDoesNotRequeryOrCrash() = runTest {
        val rig = Rig()
        var fail = false
        val refresh = MediaStoreForegroundRefresh(
            readVersion = { if (fail) throw IllegalStateException("gone") else "v1" },
            requery = { rig.requeryCount++ },
            ioDispatcher = rig.dispatcher,
        )
        rig.scope.launch { refresh.onBackground() }
        rig.scope.advanceUntilIdle()
        fail = true
        rig.scope.launch { refresh.onForeground() }
        rig.scope.advanceUntilIdle()
        assertEquals(0, rig.requeryCount)
    }
}
