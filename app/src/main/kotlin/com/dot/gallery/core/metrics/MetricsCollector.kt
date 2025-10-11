package com.dot.gallery.core.metrics

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsCollector @Inject constructor() {
    private val decryptInvocations = AtomicLong(0)
    private val decryptCoalescedWaiters = AtomicLong(0)
    private val lruHits = AtomicLong(0)
    private val lruMisses = AtomicLong(0)
    private val sidecarReads = AtomicLong(0)
    private val sidecarWrites = AtomicLong(0)

    fun incDecryptInvocation() = decryptInvocations.incrementAndGet()
    fun incDecryptWaiters(count: Int) { if (count > 0) decryptCoalescedWaiters.addAndGet(count.toLong()) }
    fun incLruHit() = lruHits.incrementAndGet()
    fun incLruMiss() = lruMisses.incrementAndGet()
    fun incSidecarRead() = sidecarReads.incrementAndGet()
    fun incSidecarWrite() = sidecarWrites.incrementAndGet()

    fun snapshot(): Snapshot = Snapshot(
        decryptInvocations.get(),
        decryptCoalescedWaiters.get(),
        lruHits.get(),
        lruMisses.get(),
        sidecarReads.get(),
        sidecarWrites.get()
    )

    data class Snapshot(
        val decryptInvocations: Long,
        val decryptCoalescedWaiters: Long,
        val lruHits: Long,
        val lruMisses: Long,
        val sidecarReads: Long,
        val sidecarWrites: Long
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MetricsCollectorEntryPoint {
    fun metrics(): MetricsCollector
}
