/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedReadTest {

    /** Hands out at most three bytes per read and fails on the API 33 bulk readers. */
    private class Api29Stream(bytes: ByteArray) : InputStream() {
        private val source = ByteArrayInputStream(bytes)
        override fun read(): Int = source.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, minOf(len, 3))
        override fun readNBytes(len: Int): ByteArray = throw AssertionError("readNBytes needs API 33")
        override fun readNBytes(b: ByteArray, off: Int, len: Int): Int = throw AssertionError("readNBytes needs API 33")
        override fun readAllBytes(): ByteArray = throw AssertionError("readAllBytes needs API 33")
    }

    private val data = ByteArray(10) { it.toByte() }

    @Test
    fun readsTheWholeStreamWhenItIsShorterThanTheLimit() {
        assertArrayEquals(data, Api29Stream(data).readAtMost(64))
    }

    @Test
    fun stopsAtTheLimitAndLeavesTheRestUnread() {
        val stream = Api29Stream(data)
        assertArrayEquals(data.copyOf(7), stream.readAtMost(7))
        assertEquals(7, stream.read())
    }

    @Test
    fun readsExactlyTheLimit() {
        assertArrayEquals(data, Api29Stream(data).readAtMost(data.size))
    }

    @Test
    fun zeroLimitReadsNothing() {
        assertArrayEquals(ByteArray(0), Api29Stream(data).readAtMost(0))
    }
}
