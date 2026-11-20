/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import com.dot.gallery.core.logging.printWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Remembers the MediaStore version seen when the app went to the background and
 * asks [requery] to re-run live queries when the app returns and the version moved.
 */
class MediaStoreForegroundRefresh(
    private val readVersion: () -> String,
    private val requery: () -> Unit = MediaStoreRequery::request,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var versionWhenHidden: String? = null

    suspend fun onBackground() = mutex.withLock {
        versionWhenHidden = currentVersion()
    }

    suspend fun onForeground() = mutex.withLock {
        val hidden = versionWhenHidden ?: return@withLock
        versionWhenHidden = null
        val now = currentVersion() ?: return@withLock
        if (now != hidden) requery()
    }

    private suspend fun currentVersion(): String? = try {
        withContext(ioDispatcher) { readVersion() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        printWarn(
            "media.refresh",
            "MediaStore version unavailable",
            ctx = mapOf("error" to e.javaClass.simpleName),
        )
        null
    }
}
