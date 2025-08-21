/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.decoder.supportHeifDecoder
import com.dot.gallery.core.decoder.supportJxlDecoder
import com.dot.gallery.core.decoder.supportVaultDecoder
import com.dot.gallery.core.decoder.supportVideoFrame2
import com.dot.gallery.core.workers.MetadataCollectionWorker
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.decode.supportAnimatedHeif
import com.github.panpf.sketch.decode.supportAnimatedWebp
import com.github.panpf.sketch.decode.supportSvg
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.request.saveCellularTraffic
import com.github.panpf.sketch.request.supportPauseLoadWhenScrolling
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.util.appCacheDirectory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okio.FileSystem
import javax.inject.Inject

@HiltAndroidApp
class GalleryApp : Application(), SingletonSketch.Factory, Configuration.Provider {

    override fun createSketch(context: PlatformContext): Sketch = Sketch.Builder(this).apply {
        components {
            supportPauseLoadWhenScrolling()
            supportSvg()
            supportVideoFrame2()
            supportAnimatedWebp()
            supportAnimatedHeif()
            supportHeifDecoder()
            supportJxlDecoder()
            supportVaultDecoder()
        }
        val diskCache = DiskCache.Builder(context, FileSystem.SYSTEM)
            .directory(context.appCacheDirectory())
            .maxSize(150 * 1024 * 1024).build()

        memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.70)
                .build()
        }

        decodeParallelismLimited(maxOf(2, Runtime.getRuntime().availableProcessors().coerceAtMost(6)))

        resultCache(diskCache)
        downloadCache(diskCache)

        globalImageOptions(
            ImageOptions {
                crossfade(false)
                precision(Precision.LESS_PIXELS)
                saveCellularTraffic(false)
            }
        )
    }.build()

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var eventHandler: EventHandler

    @Inject
    lateinit var repository: MediaRepository

    @Inject
    lateinit var mediaDistributor: MediaDistributor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        workManager.enqueueUniqueWork(
            uniqueWorkName = "MetadataCollection",
            existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            request = OneTimeWorkRequestBuilder<MetadataCollectionWorker>()
                .build()
        )
        appScope.launch {
            eventHandler.updaterFlow.collectLatest { event ->
                when (event) {
                    UIEvent.UpdateDatabase -> {
                        delay(1000L)
                        repository.updateInternalDatabase()
                    }
                    UIEvent.NavigationUpEvent -> eventHandler.navigateUpAction()
                    is UIEvent.NavigationRouteEvent -> eventHandler.navigateAction(event.route)
                    is UIEvent.ToggleNavigationBarEvent -> eventHandler.toggleNavigationBarAction(event.isVisible)
                    is UIEvent.SetFollowThemeEvent -> eventHandler.setFollowThemeAction(event.followTheme)
                }
            }
        }
    }

}