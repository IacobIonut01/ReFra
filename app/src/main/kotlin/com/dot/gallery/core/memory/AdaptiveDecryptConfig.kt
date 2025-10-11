package com.dot.gallery.core.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamically adjusts the in-memory decrypt threshold for small media based on memory pressure.
 * High = more items stay in RAM for speed; under pressure we lower to reduce heap retention.
 */
@Singleton
class AdaptiveDecryptConfig @Inject constructor(app: Application) : ComponentCallbacks2 {

    private val currentThreshold = AtomicInteger(DEFAULT_THRESHOLD_BYTES)
    private val floor = MIN_THRESHOLD_BYTES
    private val ceiling = MAX_THRESHOLD_BYTES

    init { app.registerComponentCallbacks(this) }

    fun threshold(): Int = currentThreshold.get()

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> decrease()
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> increase() // app in background, we can pre-size a bit
            else -> { /* No-op*/ }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() { decrease(force = true) }

    private fun decrease(force: Boolean = false) {
        var cur: Int
        do {
            cur = currentThreshold.get()
            val dec = if (force) cur / 2 else (cur * 0.75).toInt()
            val next = dec.coerceAtLeast(floor)
            if (next == cur) return
        } while (!currentThreshold.compareAndSet(cur, dec.coerceAtLeast(floor)))
    }

    private fun increase() {
        var cur: Int
        do {
            cur = currentThreshold.get()
            val inc = (cur * 1.15).toInt().coerceAtMost(ceiling)
            if (inc == cur) return
        } while (!currentThreshold.compareAndSet(cur, inc))
    }

    companion object {
        private const val DEFAULT_THRESHOLD_BYTES = 2 * 1024 * 1024 // 2MB
        private const val MIN_THRESHOLD_BYTES = 512 * 1024          // 512KB
        private const val MAX_THRESHOLD_BYTES = 4 * 1024 * 1024     // 4MB
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdaptiveDecryptConfigEntryPoint {
    fun adaptiveConfig(): AdaptiveDecryptConfig
}
