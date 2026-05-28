/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.annotation.SuppressLint
import android.content.Context
import com.dot.gallery.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Global access point for the [IsolatedImageDecoder] singleton used by
 * Glide and Sketch decoders. Initialized by [com.dot.gallery.GalleryApp].
 *
 * Decoders call [isEnabled] to check the user's sandboxed-decode preference
 * and [decoder] to obtain the shared instance.
 */
@SuppressLint("StaticFieldLeak")
object SandboxedDecoderHolder {

    @Volatile
    var decoder: IsolatedImageDecoder? = null
        private set

    @Volatile
    private var cachedEnabled: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(decoder: IsolatedImageDecoder, context: Context) {
        this.decoder = decoder
        // Observe the preference in a background coroutine instead of
        // runBlocking on every decode thread — eliminates 50-200ms blocking
        // per image decode.
        scope.launch {
            Settings.Security.getSandboxedDecode(context).collectLatest { enabled ->
                cachedEnabled = enabled
            }
        }
    }

    /**
     * Check if sandboxed decoding is enabled via the cached preference value.
     * The value is kept up to date by a background coroutine started in [init].
     */
    fun isEnabled(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        return cachedEnabled
    }
}
