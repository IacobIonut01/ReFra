package com.dot.gallery.core.startup

import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.presentation.util.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.app.presentation.timeline.TimelineScreen
import com.dot.gallery.feature.library.LibraryContentSource
import com.dot.gallery.core.logging.printWarning



internal const val PREFILL_TIMEOUT_MS: Long = 120L

internal suspend fun boundedPrefill(
    route: String,
    selectedRoute: String,
    timeoutMillis: Long = PREFILL_TIMEOUT_MS,
    onError: (Throwable) -> Unit = {},
    cached: suspend () -> Boolean,
    awaitReady: suspend () -> Unit
) {
    if (route != selectedRoute) return
    try {
        withTimeoutOrNull(timeoutMillis) {
            if (cached()) awaitReady()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(e)
    }
}

@Singleton
class StartupPrefill @Inject constructor(
    private val cache: StartupMediaCache,
    private val distributor: MediaDistributor,
    private val libraryContentSource: LibraryContentSource
) {

    suspend fun prepare(route: String) {
        val span = StartupTracer.begin("Startup.prefill($route)")
        val onError: (Throwable) -> Unit = {
            printWarning("StartupPrefill failed (${it.javaClass.simpleName})")
        }
        try {
            withContext(Dispatchers.IO) {
                if (route == Screen.TimelineScreen() || route == Screen.LibraryScreen()) {
                    libraryContentSource.restore()
                }
                boundedPrefill(
                    route = Screen.TimelineScreen(),
                    selectedRoute = route,
                    onError = onError,
                    cached = { cache.readMedia() != null },
                    awaitReady = {
                        distributor.timelineMediaFlow.first { !it.isLoading }
                    }
                )
                boundedPrefill(
                    route = Screen.LibraryScreen(),
                    selectedRoute = route,
                    onError = onError,
                    cached = { cache.readAlbums() != null },
                    awaitReady = {
                        distributor.albumsFlow.first { !it.isLoading }
                    }
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(e)
        } finally {
            StartupTracer.end(span)
        }
    }

}
