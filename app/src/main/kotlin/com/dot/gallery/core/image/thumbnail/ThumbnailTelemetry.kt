/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.os.Trace
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarn
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 1 (#1076): low-overhead thumbnail telemetry.
 *
 * Records only bounded counters/histograms — never retains bitmaps, media, drawables, or
 * Activity references — so it is safe to keep attached to every grid thumbnail request.
 * Entirely no-op in release builds (gated on [enabled]); staging/debug builds accumulate the
 * data behind atomics and expose a concise [dump] for logcat and instrumentation tests.
 *
 * What it explains (per the phase-1 exit gate): whether delay is dominated by cache miss vs
 * fetch/decode (via [DataSource] distribution) and how the visible-thumbnail latency
 * distribution looks (via the bucketed histogram + slow-request diagnostics at 750ms/2s/5s).
 */
object ThumbnailTelemetry {

    /** Staging/debug only; release is a hard no-op so there is zero runtime cost shipped. */
    val enabled: Boolean = BuildConfig.BUILD_TYPE != "release"

    private const val TAG = "decode.thumb"

    // Slow-request diagnostic thresholds mirror the issue's acceptance gates.
    private const val SLOW_CHEAP_MS = 750L
    private const val SLOW_REFINED_MS = 2_000L
    private const val SLOW_HARD_MS = 5_000L

    // Upper edges (ms) for the latency histogram. Last bucket is an implicit +inf overflow.
    private val BUCKET_EDGES_MS = longArrayOf(50, 100, 250, 500, 750, 1_000, 2_000, 5_000)

    private val started = AtomicLong(0)
    private val succeeded = AtomicLong(0)
    private val failed = AtomicLong(0)
    private val slow750 = AtomicLong(0)
    private val slow2s = AtomicLong(0)
    private val slow5s = AtomicLong(0)

    // Cache-source distribution for successes. Indexed by DataSource.ordinal (bounded, 5 values).
    private val cacheSourceCounts = AtomicIntegerArray(DataSource.values().size)

    // Latency histogram (BUCKET_EDGES_MS.size + 1 buckets, last is overflow).
    private val histogram = AtomicIntegerArray(BUCKET_EDGES_MS.size + 1)
    private val totalLatencyMs = AtomicLong(0)

    /**
     * A [RequestListener] that times a single Glide request and folds the result into the
     * bounded counters. Returns null in release so the request builder skips the listener.
     *
     * [surface] is a short static tag (e.g. "timeline", "album") used only for slow-request
     * logging — never stored, so it cannot leak. [tier] distinguishes MOTION vs REFINED once
     * the scroll-aware scheduler lands; today all grid loads pass the default.
     */
    fun listener(surface: String, tier: String = "grid"): RequestListener<Drawable>? {
        if (!enabled) return null
        val startedAtMs = SystemClock.elapsedRealtime()
        started.incrementAndGet()
        // Async trace section so Perfetto shows the queue-wait + fetch + decode span per request.
        val cookie = (startedAtMs.toInt() xor surface.hashCode())
        Trace.beginAsyncSection("thumb:$tier:$surface", cookie)
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Trace.endAsyncSection("thumb:$tier:$surface", cookie)
                failed.incrementAndGet()
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any?,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                Trace.endAsyncSection("thumb:$tier:$surface", cookie)
                val elapsed = SystemClock.elapsedRealtime() - startedAtMs
                recordSuccess(elapsed, dataSource, surface, tier)
                return false
            }
        }
    }

    private fun recordSuccess(elapsedMs: Long, dataSource: DataSource, surface: String, tier: String) {
        succeeded.incrementAndGet()
        totalLatencyMs.addAndGet(elapsedMs)
        cacheSourceCounts.incrementAndGet(dataSource.ordinal)
        histogram.incrementAndGet(bucketIndex(elapsedMs))
        when {
            elapsedMs >= SLOW_HARD_MS -> {
                slow5s.incrementAndGet()
                printWarn(TAG, "SLOW>5s ${elapsedMs}ms tier=$tier surface=$surface src=$dataSource")
            }
            elapsedMs >= SLOW_REFINED_MS -> {
                slow2s.incrementAndGet()
                printWarn(TAG, "SLOW>2s ${elapsedMs}ms tier=$tier surface=$surface src=$dataSource")
            }
            elapsedMs >= SLOW_CHEAP_MS -> {
                slow750.incrementAndGet()
                printDebug(TAG, "slow>750ms ${elapsedMs}ms tier=$tier surface=$surface src=$dataSource")
            }
        }
    }

    private fun bucketIndex(ms: Long): Int {
        for (i in BUCKET_EDGES_MS.indices) {
            if (ms <= BUCKET_EDGES_MS[i]) return i
        }
        return BUCKET_EDGES_MS.size
    }

    /** Reset all counters — used at the start of a benchmark/measurement window. */
    fun reset() {
        if (!enabled) return
        started.set(0); succeeded.set(0); failed.set(0)
        slow750.set(0); slow2s.set(0); slow5s.set(0)
        totalLatencyMs.set(0)
        for (i in 0 until cacheSourceCounts.length()) cacheSourceCounts.set(i, 0)
        for (i in 0 until histogram.length()) histogram.set(i, 0)
    }

    /** Concise, allocation-light snapshot suitable for logcat and instrumentation assertions. */
    fun dump(): String {
        if (!enabled) return "ThumbnailTelemetry disabled (release)"
        val ok = succeeded.get()
        val avg = if (ok > 0) totalLatencyMs.get() / ok else 0
        val cache = buildString {
            DataSource.values().forEachIndexed { i, ds ->
                val c = cacheSourceCounts.get(i)
                if (c > 0) append("${ds.name}=$c ")
            }
        }.trim()
        val hist = buildString {
            for (i in BUCKET_EDGES_MS.indices) {
                val c = histogram.get(i)
                if (c > 0) append("<=${BUCKET_EDGES_MS[i]}ms:$c ")
            }
            val overflow = histogram.get(BUCKET_EDGES_MS.size)
            if (overflow > 0) append(">${BUCKET_EDGES_MS.last()}ms:$overflow")
        }.trim()
        return "Thumbnails: started=${started.get()} ok=$ok failed=${failed.get()} " +
                "avg=${avg}ms slow[>750=${slow750.get()} >2s=${slow2s.get()} >5s=${slow5s.get()}] " +
                "cache[$cache] hist[$hist]"
    }

    /** Emit [dump] to logcat under [TAG]; call after a scroll/soak window. */
    fun logDump() {
        if (!enabled) return
        printInfo(TAG, dump())
    }
}
