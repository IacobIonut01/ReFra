package com.dot.gallery.benchmark

import android.util.Log

data class BenchmarkResult(
    val name: String,
    val iterations: Int,
    val avgTimeMs: Double,
    val minTimeMs: Double,
    val maxTimeMs: Double
) {
    override fun toString(): String =
        "[$name] avg=${f(avgTimeMs)}ms  min=${f(minTimeMs)}ms  max=${f(maxTimeMs)}ms  ($iterations iterations)"

    private fun f(v: Double) = String.format("%.3f", v)
}

object BenchmarkUtils {
    const val TAG = "GalleryBenchmark"

    inline fun benchmark(
        name: String,
        warmup: Int = 3,
        iterations: Int = 10,
        crossinline block: () -> Unit
    ): BenchmarkResult {
        // Warmup
        repeat(warmup) { block() }
        System.gc()

        val times = DoubleArray(iterations)
        repeat(iterations) { i ->
            val start = System.nanoTime()
            block()
            times[i] = (System.nanoTime() - start) / 1_000_000.0
        }

        return BenchmarkResult(name, iterations, times.average(), times.min(), times.max()).also {
            Log.d(TAG, it.toString())
        }
    }

    suspend inline fun benchmarkSuspend(
        name: String,
        warmup: Int = 3,
        iterations: Int = 10,
        crossinline block: suspend () -> Unit
    ): BenchmarkResult {
        repeat(warmup) { block() }
        System.gc()

        val times = DoubleArray(iterations)
        repeat(iterations) { i ->
            val start = System.nanoTime()
            block()
            times[i] = (System.nanoTime() - start) / 1_000_000.0
        }

        return BenchmarkResult(name, iterations, times.average(), times.min(), times.max()).also {
            Log.d(TAG, it.toString())
        }
    }

    fun logSection(title: String) {
        Log.d(TAG, "")
        Log.d(TAG, "================================================================")
        Log.d(TAG, "  $title")
        Log.d(TAG, "================================================================")
    }

    fun logComparison(label: String, current: BenchmarkResult, optimized: BenchmarkResult) {
        val speedup = if (optimized.avgTimeMs > 0) current.avgTimeMs / optimized.avgTimeMs else 0.0
        val savedMs = current.avgTimeMs - optimized.avgTimeMs
        Log.d(TAG, "  >> $label: ${String.format("%.1f", speedup)}x faster " +
                "(saved ${String.format("%.3f", savedMs)}ms per call)")
    }

    fun logInfo(msg: String) = Log.d(TAG, "  [info] $msg")

    fun logSummaryHeader() {
        Log.d(TAG, "")
        Log.d(TAG, "================================================================")
        Log.d(TAG, "  BENCHMARK SUMMARY")
        Log.d(TAG, "================================================================")
    }
}
