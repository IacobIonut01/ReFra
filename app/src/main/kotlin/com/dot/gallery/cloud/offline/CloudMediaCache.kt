/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.offline

import android.content.Context
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent on-disk byte cache for cloud media, shared by every image pipeline through the
 * [CloudCacheInterceptor]. Two tiers:
 *
 * - **auto** (`cacheDir/cloud_media_cache`): cache-on-view entries, LRU-evicted under the user's
 *   size budget. May be cleared by the OS under storage pressure.
 * - **pinned** (`filesDir/cloud_media_pinned`): explicitly downloaded for offline ("Available
 *   offline"). Never LRU-evicted and kept out of `cacheDir` so the OS won't reclaim it.
 *
 * Entries are content-addressed by [keyFor] (provider + account + remoteId + size), which is
 * immutable for a given asset variant — so a cache hit can be served even while online.
 */
@Singleton
class CloudMediaCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val autoDir: File by lazy { File(context.cacheDir, "cloud_media_cache").apply { mkdirs() } }
    private val pinnedDir: File by lazy { File(context.filesDir, "cloud_media_pinned").apply { mkdirs() } }

    /** Stable, immutable cache key for an asset variant. Delegates to the static [keyFor]. */
    fun keyFor(
        providerType: ProviderType,
        configId: Long,
        remoteId: String,
        size: String,
        type: String? = null
    ): String = Companion.keyFor(providerType, configId, remoteId, size, type)

    private fun autoFile(key: String) = File(autoDir, "$key.bin")
    private fun pinnedFile(key: String) = File(pinnedDir, "$key.bin")
    private fun autoTypeFile(key: String) = File(autoDir, "$key.type")
    private fun pinnedTypeFile(key: String) = File(pinnedDir, "$key.type")

    /** The stored MIME type for [key], if recorded — lets cache hits report the original type. */
    fun contentTypeFor(key: String): String? {
        pinnedTypeFile(key).takeIf { it.isFile }?.let {
            return runCatching { it.readText() }
                .onFailure { e -> printWarn("cloud.offline", "pinned content-type read failed: $e") }
                .getOrNull()?.ifBlank { null }
        }
        return autoTypeFile(key).takeIf { it.isFile }?.let {
            runCatching { it.readText() }
                .onFailure { e -> printWarn("cloud.offline", "auto content-type read failed: $e") }
                .getOrNull()?.ifBlank { null }
        }
    }

    /** Record the MIME type for an auto-tier entry (sidecar to the .bin). */
    fun writeAutoType(key: String, contentType: String?) {
        if (contentType.isNullOrBlank()) return
        runCatching { autoTypeFile(key).writeText(contentType) }
            .onFailure { printWarn("cloud.offline", "auto content-type write failed: $it") }
    }

    /** Returns a readable cache file for [key] (pinned tier first), or null on a miss. */
    fun get(key: String): File? {
        pinnedFile(key).takeIf { it.isFile && it.length() > 0 }?.let { return it }
        return autoFile(key).takeIf { it.isFile && it.length() > 0 }?.also {
            // Bump mtime so LRU treats a recently-served entry as fresh.
            runCatching { it.setLastModified(System.currentTimeMillis()) }
        }
    }

    fun isPinned(key: String): Boolean = pinnedFile(key).let { it.isFile && it.length() > 0 }

    /** Number of requested pinned variants that are present and non-empty. */
    fun pinnedVariantCount(refs: Collection<CacheAssetRef>, sizeLabels: Collection<String>): Int =
        refs.sumOf { ref ->
            sizeLabels.count { size ->
                isPinned(keyFor(ref.providerType, ref.configId, ref.remoteId, size))
            }
        }

    /** A unique temp file in the auto tier for write-through teeing. */
    fun newAutoTemp(key: String): File = File(autoDir, "$key.${System.nanoTime()}.tmp")

    /** Atomically promote a completed temp file into the auto tier. */
    fun promoteAuto(tmp: File, key: String) {
        val target = autoFile(key)
        when {
            target.exists() -> tmp.delete()
            tmp.renameTo(target) -> {}
            else -> tmp.delete()
        }
    }

    /** Write bytes straight into the pinned tier (used by the offline download worker). */
    fun storePinned(key: String, bytes: ByteArray, contentType: String? = null): Boolean = runCatching {
        if (bytes.isEmpty()) return false
        val tmp = File(pinnedDir, "$key.${System.nanoTime()}.tmp")
        tmp.writeBytes(bytes)
        val target = pinnedFile(key)
        when {
            target.isFile && target.length() > 0L -> tmp.delete()
            else -> {
                target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return false
                }
            }
        }
        if (!contentType.isNullOrBlank()) {
            runCatching { pinnedTypeFile(key).writeText(contentType) }
                .onFailure { printWarn("cloud.offline", "pinned content-type write failed: $it") }
        }
        target.isFile && target.length() > 0L
    }.getOrElse {
        printWarn("cloud.offline", "pinned cache write failed: $it")
        false
    }

    fun autoSizeBytes(): Long = autoDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    fun pinnedSizeBytes(): Long = pinnedDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    fun totalSizeBytes(): Long = autoSizeBytes() + pinnedSizeBytes()

    /** Every cache key (all size variants) that could exist for a single asset. */
    private fun keysForAsset(ref: CacheAssetRef): List<String> =
        MEDIA_SIZE_LABELS.map { keyFor(ref.providerType, ref.configId, ref.remoteId, it) }

    /**
     * Total on-disk bytes currently cached for [refs] across both tiers and every size variant.
     * Used to report per-account / per-album cache usage on the cache manager screen.
     */
    @Synchronized
    fun sizeForAssets(refs: Collection<CacheAssetRef>): Long {
        var total = 0L
        for (ref in refs) {
            for (key in keysForAsset(ref)) {
                pinnedFile(key).takeIf { it.isFile }?.let { total += it.length() }
                autoFile(key).takeIf { it.isFile }?.let { total += it.length() }
            }
        }
        return total
    }

    /**
     * Delete every cached variant (both tiers, plus the .type sidecars) for [refs].
     * Returns the number of bytes freed. Lets the user free the cache of one account
     * or one album without touching the rest.
     */
    @Synchronized
    fun clearForAssets(refs: Collection<CacheAssetRef>): Long {
        var freed = 0L
        for (ref in refs) {
            for (key in keysForAsset(ref)) {
                for (f in listOf(pinnedFile(key), autoFile(key), pinnedTypeFile(key), autoTypeFile(key))) {
                    if (f.isFile) {
                        freed += f.length()
                        f.delete()
                    }
                }
            }
        }
        return freed
    }

    /** Evict oldest auto entries (by last access) until under [budgetBytes]. Pinned tier untouched. */
    @Synchronized
    fun trimAuto(budgetBytes: Long) {
        runCatching {
            val files = autoDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
            var total = files.sumOf { it.length() }
            if (total <= budgetBytes) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= budgetBytes) return
                total -= f.length()
                f.delete()
            }
            printDebug("CloudMediaCache: trimmed auto cache to <= ${budgetBytes / (1024 * 1024)}MB")
        }
    }

    /** Clear cache-on-view entries; pinned offline content is kept. */
    @Synchronized
    fun clearAuto() {
        autoDir.listFiles()?.forEach { it.delete() }
    }

    /** Remove only pinned variants for [refs], preserving browsed auto-cache entries. */
    @Synchronized
    fun clearPinnedForAssets(refs: Collection<CacheAssetRef>): Long {
        var freed = 0L
        for (ref in refs) {
            for (key in keysForAsset(ref)) {
                for (file in listOf(pinnedFile(key), pinnedTypeFile(key))) {
                    if (file.isFile) {
                        freed += file.length()
                        file.delete()
                    }
                }
            }
        }
        return freed
    }

    /** Remove pinned entries (e.g. when all pins are cleared). */
    @Synchronized
    fun clearPinned() {
        pinnedDir.listFiles()?.forEach { it.delete() }
    }

    @Synchronized
    fun clearAll() {
        clearAuto()
        clearPinned()
    }

    companion object {
        /**
         * Request header carrying the [keyFor] cache key. Image fetch sites attach it; the
         * [CloudCacheInterceptor] consumes (and strips) it. Absent header == not cloud media,
         * so the interceptor passes the request straight through.
         */
        const val HEADER_KEY = "X-Cloud-Cache-Key"

        /**
         * Size variants stored per media asset — mirrors the labels used by the image fetch
         * sites ([CloudMediaFetcher]/[CloudGlideModelLoader]/[CloudImageSource]) and the offline
         * download worker. Used to enumerate every cache key belonging to a single asset.
         */
        private val MEDIA_SIZE_LABELS = listOf("thumbnail", "preview", "original")

        /** Stable, immutable cache key for an asset variant (provider + account + remoteId + size). */
        fun keyFor(
            providerType: ProviderType,
            configId: Long,
            remoteId: String,
            size: String,
            type: String? = null
        ): String {
            val raw = "${providerType.name}|$configId|$remoteId|$size|${type ?: ""}"
            return MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Identifies a single cached cloud asset (provider + account + remoteId), independent of size
 * variant. Passed to [CloudMediaCache.sizeForAssets] / [CloudMediaCache.clearForAssets] to scope
 * cache reporting and clearing to one account or one album.
 */
data class CacheAssetRef(
    val providerType: ProviderType,
    val configId: Long,
    val remoteId: String
)
