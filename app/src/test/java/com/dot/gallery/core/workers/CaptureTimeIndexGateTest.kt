/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The remote byte range used to resolve capture times for cloud media (#1277)
 * must respect the same network policy as other cloud transfers: never while
 * effectively offline, and on metered networks only for accounts that did not
 * restrict transfers to unmetered.
 */
class CaptureTimeIndexGateTest {

    @Test
    fun offlineBlocksFetch() {
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = true, unmetered = true, accountWifiOnly = false))
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = true, unmetered = false, accountWifiOnly = false))
    }

    @Test
    fun meteredHonoursAccountWifiOnly() {
        assertFalse(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = false, accountWifiOnly = true))
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = false, accountWifiOnly = false))
    }

    @Test
    fun unmeteredAlwaysAllowed() {
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = true, accountWifiOnly = true))
        assertTrue(remoteCaptureFetchAllowed(effectiveOffline = false, unmetered = true, accountWifiOnly = false))
    }
}
