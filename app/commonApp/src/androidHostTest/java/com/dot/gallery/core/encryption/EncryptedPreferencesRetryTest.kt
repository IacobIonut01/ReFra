/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import javax.crypto.IllegalBlockSizeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test


class EncryptedPreferencesRetryTest {

    @Test
    fun aPrunedKeystoreOperationIsRetriedWithAFreshCipher() {
        var calls = 0
        val result = EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt") {
            calls++
            if (calls == 1) throw IllegalBlockSizeException()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun aKeystoreFailureThatKeepsHappeningStillThrows() {
        var calls = 0
        assertThrows(IllegalBlockSizeException::class.java) {
            EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt") {
                calls++
                throw IllegalBlockSizeException()
            }
        }
        assertEquals(EncryptedPreferencesSerializer.KEYSTORE_ATTEMPTS, calls)
    }
}
