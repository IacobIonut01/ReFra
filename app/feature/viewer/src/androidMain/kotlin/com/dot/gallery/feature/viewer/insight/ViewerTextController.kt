/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.ocr.model.OcrSwitchPolicy
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.settings.OCR_MODE_ON_REQUEST
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** The viewer's text controller, or null while text recognition is off or has no ready pack. */
val LocalTextInsightController = staticCompositionLocalOf<InsightController<Media>?> { null }

/** Text is read this long after the pager settles: after codes (120 ms) have had their turn. */
const val TEXT_SETTLE_DELAY_MS = 420L

/**
 * Creates the text controller for one viewer screen. With the switch off, no pack ready, or no OCR
 * runtime (previews), this returns null and no model is loaded. One job at a time, cancelled on every
 * page change, results cached in memory only (vault photos are never written anywhere).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberViewerTextController(runtime: OcrRuntime?): InsightController<Media>? {
    runtime ?: return null
    val enabled by rememberPreference(SettingsKeys.ocrEnabled)
    val enabledPacks by rememberPreference(SettingsKeys.ocrPacks)
    val links by rememberPreference(SettingsKeys.ocrExternalModelUris)
    val context = LocalContext.current
    // Verifying installed and linked models hashes files: never on the main thread.
    LaunchedEffect(runtime, links) { withContext(Dispatchers.IO) { runtime.store.setLinks(links) } }
    val states by runtime.store.states.collectAsState()
    val ready = states.filterValues { it.isReady }.keys
    val active = OcrSwitchPolicy.activePacks(enabledPacks, ready)
    if (!enabled || active.isEmpty()) return null
    val latestPacks by rememberUpdatedState(active)
    val controller = remember(context, runtime) {
        val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
        InsightController(
            engine = ViewerTextEngine(context, runtime) { latestPacks },
            dispatcher = dispatcher,
            settleDelayMs = TEXT_SETTLE_DELAY_MS,
            thoroughDelayMs = 0L,
            onError = { printError("insight.ocr", "Text recognition failed", it) },
        )
    }
    DisposableEffect(controller) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) releaseSessions(runtime)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = releaseSessions(runtime)
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            controller.dispose()
        }
    }
    return controller
}

private fun releaseSessions(runtime: OcrRuntime) {
    // Called on low memory from the main thread. A running inference holds the session lock for its
    // whole run, so the sessions are closed once it finishes instead of blocking here.
    runtime.engine.releaseSessionsAsync()
}

/** Whether text is read only when the person taps Read text. */
@Composable
fun rememberOcrOnRequest(): Boolean {
    val mode by rememberPreference(SettingsKeys.ocrMode)
    return mode == OCR_MODE_ON_REQUEST
}
