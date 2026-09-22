/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.UploadTargetResolver
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isFavorite
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal const val FREE_UP_SPACE_DELETE_BATCH_SIZE = 2_000

/** Sentinel cutoff meaning "never remove based on age". */
internal const val FREE_UP_SPACE_NEVER_CUTOFF = -1

internal const val FREE_UP_SPACE_DEFAULT_INTERVAL_DAYS = 1

internal val KEEP_FAVORITES_KEY = booleanPreferencesKey("cloud_free_space_keep_favorites")
internal val CUTOFF_DAYS_KEY = intPreferencesKey("cloud_free_space_cutoff_days")
internal val AUTO_ENABLED_KEY = booleanPreferencesKey("cloud_free_space_auto_enabled")
internal val AUTO_INTERVAL_DAYS_KEY = intPreferencesKey("cloud_free_space_auto_interval_days")

internal fun <T> freeUpSpaceDeletionBatch(items: List<T>): List<T> =
    items.take(FREE_UP_SPACE_DELETE_BATCH_SIZE)

internal fun verifiedLocalRevisionMatches(
    mediaId: Long,
    currentHash: String?,
    verifiedHashes: Map<Long, String>
): Boolean = currentHash != null && verifiedHashes[mediaId] == currentHash

/** Outcome of one verification pass over the local library. */
data class FreeUpSpaceScanResult(
    /** Local, non-cloud items that passed the age/favorite filters. */
    val totalLocal: Int,
    /** Items whose SHA-1 was verified present on every configured destination. */
    val verified: List<Media.UriMedia>,
    /** mediaId -> the SHA-1 that was verified remotely. */
    val verifiedHashes: Map<Long, String>
)

/**
 * The scan -> verify -> recheck pipeline behind Free Up Space, shared by the
 * interactive screen ([com.dot.gallery.cloud.ui.space.FreeUpSpaceViewModel]) and
 * the scheduled [FreeUpSpaceWorker]. A local copy is only removed after its
 * SHA-1 was verified present on *every* upload destination configured for its
 * album, so a partial or failed backup can never cause data loss.
 */
@Singleton
class FreeUpSpaceEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val registry: ProviderRegistry,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val configDao: CloudServerConfigDao
) {
    private companion object {
        private const val MEDIA_QUERY_TIMEOUT_MS = 10_000L
    }

    /**
     * Loads the complete local media list, computes each candidate's SHA-1 and
     * verifies it against every enabled destination. Returns null when the media
     * store could not be queried.
     */
    suspend fun scan(
        cutoffDays: Int,
        keepFavorites: Boolean,
        onScanned: (scannedCount: Int) -> Unit = {}
    ): FreeUpSpaceScanResult? = withContext(Dispatchers.IO) {
        val allMedia = loadCompleteMedia() ?: return@withContext null
        val cutoffMs = System.currentTimeMillis() - (cutoffDays.toLong() * 86_400_000L)
        val candidates = allMedia
            .filter { it.uri.scheme != "cloud" && it.definedTimestamp * 1000L < cutoffMs }
            .let { items ->
                if (keepFavorites) items.filterNot { it.isFavorite }
                else items
            }
        val preferencesByAlbum = uploadPrefDao.getEnabledList().groupBy { it.albumId }
        val configsById = configDao.getAll().first().associateBy { it.id }
        val hashCache = mutableMapOf<Long, String?>()
        val verifiedHashes = mutableMapOf<Long, String>()
        val verified = candidates.filterIndexed { index, media ->
            val checksum = hashCache.getOrPut(media.id) { computeSha1(media) }
            val destinations = preferencesByAlbum[media.albumID].orEmpty()
            val presentEverywhere = checksum != null && destinations.isNotEmpty() &&
                    destinations.all { preference ->
                        val provider = registry.getByConfigId(preference.serverConfigId)
                                as? SyncCapableProvider ?: return@all false
                        verifyRemoteContent(
                            provider,
                            media,
                            UploadTargetResolver.resolve(
                                configsById[preference.serverConfigId],
                                preference,
                                media
                            ),
                            checksum
                        )
                    }
            if (presentEverywhere) verifiedHashes[media.id] = checksum
            onScanned(index + 1)
            presentEverywhere
        }
        FreeUpSpaceScanResult(
            totalLocal = candidates.size,
            verified = verified,
            verifiedHashes = verifiedHashes
        )
    }

    /**
     * Full re-verification of one deletion batch: reloads the media store and
     * re-checks age, favorite, local-hash and remote presence for each item.
     * Used by the interactive flow where arbitrary time may pass between the
     * scan and the user's confirm tap. Returns null when the media store could
     * not be queried.
     */
    suspend fun reverifyForDeletion(
        candidates: List<Media.UriMedia>,
        cutoffDays: Int,
        keepFavorites: Boolean,
        verifiedHashes: Map<Long, String>
    ): List<Media.UriMedia>? = withContext(Dispatchers.IO) {
        val currentById = loadCompleteMedia()?.associateBy { media -> media.id }
            ?: return@withContext null
        val preferencesByAlbum = uploadPrefDao.getEnabledList().groupBy { it.albumId }
        val configsById = configDao.getAll().first().associateBy { it.id }
        val cutoffMs = System.currentTimeMillis() - (cutoffDays.toLong() * 86_400_000L)
        candidates.mapNotNull { candidate ->
            val media = currentById[candidate.id] ?: return@mapNotNull null
            if (media.uri.scheme == "cloud" || media.definedTimestamp * 1000L >= cutoffMs) {
                return@mapNotNull null
            }
            if (keepFavorites && media.isFavorite) return@mapNotNull null
            val checksum = verifiedHashes[media.id] ?: return@mapNotNull null
            if (!verifiedLocalRevisionMatches(media.id, computeSha1(media), verifiedHashes)) {
                return@mapNotNull null
            }
            val destinations = preferencesByAlbum[media.albumID].orEmpty()
            media.takeIf {
                destinations.isNotEmpty() && destinations.all { preference ->
                    val provider = registry.getByConfigId(preference.serverConfigId)
                            as? SyncCapableProvider ?: return@all false
                    verifyRemoteContent(
                        provider,
                        media,
                        UploadTargetResolver.resolve(
                            configsById[preference.serverConfigId],
                            preference,
                            media
                        ),
                        checksum
                    )
                }
            }
        }
    }

    /**
     * Local-only recheck used by the scheduled worker: confirms each item still
     * exists, still passes the age/favorite filters and still hashes to the
     * verified SHA-1 — without repeating the remote round-trips the scan just
     * performed. Returns null when the media store could not be queried.
     */
    suspend fun currentlyDeletable(
        candidates: List<Media.UriMedia>,
        cutoffDays: Int,
        keepFavorites: Boolean,
        verifiedHashes: Map<Long, String>
    ): List<Media.UriMedia>? = withContext(Dispatchers.IO) {
        val currentById = loadCompleteMedia()?.associateBy { media -> media.id }
            ?: return@withContext null
        val cutoffMs = System.currentTimeMillis() - (cutoffDays.toLong() * 86_400_000L)
        candidates.mapNotNull { candidate ->
            val media = currentById[candidate.id] ?: return@mapNotNull null
            if (media.uri.scheme == "cloud" || media.definedTimestamp * 1000L >= cutoffMs) {
                return@mapNotNull null
            }
            if (keepFavorites && media.isFavorite) return@mapNotNull null
            if (!verifiedLocalRevisionMatches(media.id, computeSha1(media), verifiedHashes)) {
                return@mapNotNull null
            }
            media
        }
    }

    suspend fun loadCompleteMedia(): List<Media.UriMedia>? = try {
        withTimeoutOrNull(MEDIA_QUERY_TIMEOUT_MS) {
            (repository.getCompleteMedia().first() as? Resource.Success)?.data
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun verifyRemoteContent(
        provider: SyncCapableProvider,
        media: Media,
        targetPath: String?,
        checksum: String
    ): Boolean = try {
        provider.verifyRemoteContent(media, targetPath, checksum).getOrDefault(false)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    fun computeSha1(media: Media): String? {
        return try {
            context.contentResolver.openInputStream(media.getUri())?.use { input ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) { null }
    }
}
