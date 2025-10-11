package com.dot.gallery.core.memory

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ByteArrayPool @Inject constructor() {
    private val buckets = ConcurrentHashMap<Int, ArrayDeque<ByteArray>>()
    private val sizes = intArrayOf(8 * 1024, 16 * 1024, 32 * 1024, 64 * 1024)
    private val maxPerBucket = 8

    fun borrow(minSize: Int): ByteArray {
        val bucketSize = sizes.firstOrNull { it >= minSize } ?: minSize
        val deque = buckets.getOrPut(bucketSize) { ArrayDeque<ByteArray>() }
        synchronized(deque) {
            val existing = if (deque.isEmpty()) null else deque.removeFirst()
            return existing ?: ByteArray(bucketSize)
        }
    }

    fun recycle(buffer: ByteArray) {
        val bucketSize = sizes.firstOrNull { it == buffer.size } ?: return
    val deque = buckets.getOrPut(bucketSize) { ArrayDeque<ByteArray>() }
        synchronized(deque) {
            if (deque.size < maxPerBucket) deque.addLast(buffer)
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ByteArrayPoolEntryPoint {
    fun pool(): ByteArrayPool
}
