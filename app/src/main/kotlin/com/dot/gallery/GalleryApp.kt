/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dot.gallery.core.decoder.supportHeifDecoder
import com.dot.gallery.core.decoder.supportJxlDecoder
import com.dot.gallery.core.decoder.supportVaultDecoder
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.decode.supportAnimatedHeif
import com.github.panpf.sketch.decode.supportAnimatedWebp
import com.github.panpf.sketch.decode.supportSvg
import com.github.panpf.sketch.decode.supportVideoFrame
import com.github.panpf.sketch.request.supportPauseLoadWhenScrolling
import com.github.panpf.sketch.request.supportSaveCellularTraffic
import com.github.panpf.sketch.util.appCacheDirectory
import dagger.hilt.android.HiltAndroidApp
import okio.FileSystem
import javax.inject.Inject

@HiltAndroidApp
class GalleryApp : Application(), SingletonSketch.Factory, Configuration.Provider {

    override fun createSketch(context: PlatformContext): Sketch = Sketch.Builder(this).apply {
        components {
            supportSaveCellularTraffic()
            supportPauseLoadWhenScrolling()
            supportSvg()
            supportVideoFrame()
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

        resultCache(diskCache)
        downloadCache(diskCache)
    }.build()

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}