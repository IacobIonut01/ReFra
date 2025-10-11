package com.dot.gallery.core.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks decryption progress per media id/path during session.
 * For initial implementation (full decrypt upfront) we emit 0 -> 100 instantly; future
 * streaming block decrypt will update progressively.
 */
object DecryptionProgressRegistry {
    private val progressMap = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    fun flowFor(key: String): StateFlow<Int> = progressMap.getOrPut(key) { MutableStateFlow(0) }

    fun update(key: String, percent: Int) {
        progressMap[key]?.value = percent.coerceIn(0, 100)
    }

    fun reset(key: String) { progressMap[key]?.value = 0 }
}
