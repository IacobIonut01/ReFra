/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.startup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dot.gallery.core.encryption.EncryptedPreferencesSerializer
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.smart.FaceClusterPhaseProcessor
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.library.CachedLibrarySnapshot
import com.dot.gallery.feature_node.presentation.library.LibrarySnapshot
import com.dot.gallery.feature_node.presentation.library.MAX_CACHED_LIBRARY_CATEGORIES
import com.dot.gallery.feature_node.presentation.library.isPersistableThumbnailUrl
import com.dot.gallery.feature_node.presentation.library.libraryPreviewWindow
import com.dot.gallery.feature_node.presentation.library.validateLibrarySnapshot
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

internal const val MAX_CACHED_MEDIA = 250
internal const val MAX_CACHED_ALBUMS = 512
internal const val MAX_PAYLOAD_CHARS = 1_048_576

internal const val PERMISSION_MASK_LEGACY_STORAGE = 1
internal const val PERMISSION_MASK_FULL_MEDIA = 3

@Serializable
internal data class StartupCacheStamp(
    val mediaVersion: String,
    val permissionMask: Int
)

@Serializable
internal data class CachedStartupAlbum(
    val id: Long,
    val label: String,
    val uri: String,
    val pathToThumbnail: String,
    val relativePath: String,
    val timestamp: Long,
    val count: Long,
    val size: Long,
    val storageVolume: String?
)

internal fun Album.toCachedStartupAlbum(): CachedStartupAlbum = CachedStartupAlbum(
    id = id,
    label = label,
    uri = uri.toString(),
    pathToThumbnail = pathToThumbnail,
    relativePath = relativePath,
    timestamp = timestamp,
    count = count,
    size = size,
    storageVolume = storageVolume
)

internal fun CachedStartupAlbum.toAlbum(): Album = Album(
    id = id,
    label = label,
    uri = Uri.parse(uri),
    pathToThumbnail = pathToThumbnail,
    relativePath = relativePath,
    timestamp = timestamp,
    count = count,
    size = size,
    storageVolume = storageVolume
)

internal fun isStampUsable(stored: StartupCacheStamp?, current: StartupCacheStamp?): Boolean =
    stored != null && stored == current

internal fun fullReadPermissionMask(
    sdkInt: Int,
    imagesGranted: Boolean,
    videosGranted: Boolean,
    legacyGranted: Boolean
): Int? = when {
    sdkInt < Build.VERSION_CODES.R -> null
    sdkInt >= Build.VERSION_CODES.TIRAMISU ->
        if (imagesGranted && videosGranted) PERMISSION_MASK_FULL_MEDIA else null
    else -> if (legacyGranted) PERMISSION_MASK_LEGACY_STORAGE else null
}

internal suspend fun readStartupCacheStamp(context: Context): StartupCacheStamp? {
    val permissionMask = fullReadPermissionMask(
        sdkInt = Build.VERSION.SDK_INT,
        imagesGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED,
        videosGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED,
        legacyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    ) ?: return null
    val mediaVersion = try {
        context.mediaStoreVersion
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        printWarning("StartupMediaCache: media store version unavailable (${t.javaClass.simpleName})")
        return null
    }
    return StartupCacheStamp(mediaVersion, permissionMask)
}

internal fun mediaStoreUriId(value: String): Long? = try {
    val uri = URI(value)
    if (uri.scheme != "content" || uri.authority != "media") null
    else uri.path.substringAfterLast('/').toLongOrNull()?.takeIf { it >= 0 }
} catch (_: URISyntaxException) {
    null
}

internal fun validateCachedMediaEntries(entries: List<Pair<Long, String>>): Boolean =
    entries.size <= MAX_CACHED_MEDIA &&
        entries.all { (id, uri) -> id >= 0 && mediaStoreUriId(uri) == id } &&
        entries.mapTo(HashSet()) { it.first }.size == entries.size

internal fun validateCachedMedia(media: List<Media.UriMedia>): Boolean =
    media.all { it.size >= 0 && it.trashed == 0 } &&
        validateCachedMediaEntries(media.map { it.id to it.uri.toString() })

internal fun validateCachedAlbums(albums: List<CachedStartupAlbum>): Boolean =
    albums.size <= MAX_CACHED_ALBUMS &&
        albums.all {
            it.count >= 0 && it.size >= 0 &&
                mediaStoreUriId(it.uri) != null && !it.relativePath.startsWith("cloud/")
        } &&
        albums.mapTo(HashSet()) { it.id }.size == albums.size

@Singleton
class StartupMediaCache internal constructor(
    private val context: Context,
    private val dataStoreFile: File,
    private val scope: CoroutineScope,
    private val stampProvider: suspend () -> StartupCacheStamp?
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        File(context.filesDir, "datastore/startup_media_cache.pb"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { readStartupCacheStamp(context) }
    )

    private val json = Json { ignoreUnknownKeys = true }

    internal val store by lazy {
        DataStoreFactory.create(
            serializer = EncryptedPreferencesSerializer(context),
            scope = scope,
            produceFile = { dataStoreFile }
        )
    }

    internal fun close() = scope.cancel()

    internal suspend fun currentStamp(): StartupCacheStamp? = stampProvider()

    private suspend fun <T> tracedRead(label: String, read: suspend () -> T?): T? {
        val span = StartupTracer.begin("Startup.cacheRead($label)")
        try {
            return read().also { result ->
                StartupTracer.trace(
                    if (result == null) "Startup.cacheMiss($label)" else "Startup.cacheHit($label)"
                ) { }
            }
        } finally {
            StartupTracer.end(span)
        }
    }

    internal suspend fun readMedia(): List<Media.UriMedia>? = tracedRead("media") {
        readEntry(
            payloadKey = MEDIA_PAYLOAD_KEY,
            stampKey = MEDIA_STAMP_KEY,
            label = "media",
            decode = {
                json.decodeFromString(ListSerializer(Media.UriMedia.serializer()), it)
            },
            validate = { media -> validateCachedMedia(media) }
        )
    }

    internal suspend fun readAlbums(): List<Album>? = tracedRead("albums") {
        readEntry(
            payloadKey = ALBUMS_PAYLOAD_KEY,
            stampKey = ALBUMS_STAMP_KEY,
            label = "albums",
            decode = {
                json.decodeFromString(ListSerializer(CachedStartupAlbum.serializer()), it)
            },
            validate = { dtos -> validateCachedAlbums(dtos) }
        )?.map { it.toAlbum() }
    }

    internal suspend fun writeMedia(stamp: StartupCacheStamp?, media: List<Media.UriMedia>) {
        if (stamp == null) return
        val bounded = media.take(MAX_CACHED_MEDIA)
        if (!validateCachedMedia(bounded)) return
        writeEntry(
            stamp = stamp,
            payloadKey = MEDIA_PAYLOAD_KEY,
            stampKey = MEDIA_STAMP_KEY,
            encodedPayload = json.encodeToString(
                ListSerializer(Media.UriMedia.serializer()), bounded
            ),
            label = "media"
        )
    }

    internal suspend fun writeAlbums(stamp: StartupCacheStamp?, albums: List<Album>) {
        if (stamp == null) return
        if (albums.size > MAX_CACHED_ALBUMS) {
            removeEntry(ALBUMS_PAYLOAD_KEY, ALBUMS_STAMP_KEY, "albums")
            return
        }
        val dtos = albums.map { it.toCachedStartupAlbum() }
        if (!validateCachedAlbums(dtos)) return
        writeEntry(
            stamp = stamp,
            payloadKey = ALBUMS_PAYLOAD_KEY,
            stampKey = ALBUMS_STAMP_KEY,
            encodedPayload = json.encodeToString(
                ListSerializer(CachedStartupAlbum.serializer()), dtos
            ),
            label = "albums"
        )
    }

    private val faceThumbDir: String =
        File(context.filesDir, FaceClusterPhaseProcessor.FACE_THUMBNAIL_DIRECTORY).absolutePath

    internal suspend fun readLibrary(privacyFingerprint: String): LibrarySnapshot? =
        tracedRead("library") {
            readEntry(
                payloadKey = LIBRARY_PAYLOAD_KEY,
                stampKey = LIBRARY_STAMP_KEY,
                label = "library",
                decode = { json.decodeFromString(CachedLibrarySnapshot.serializer(), it) },
                validate = {
                    it.version == 1 && it.privacyFingerprint == privacyFingerprint &&
                        validateLibrarySnapshot(it.snapshot, faceThumbDir)
                }
            )?.snapshot
        }

    internal suspend fun writeLibrary(
        stamp: StartupCacheStamp?,
        privacyFingerprint: String,
        snapshot: LibrarySnapshot
    ) {
        if (stamp == null) return
        val bounded = snapshot.copy(
            categories = snapshot.categories?.take(MAX_CACHED_LIBRARY_CATEGORIES),
            locations = snapshot.locations?.let { locations ->
                libraryPreviewWindow(locations, snapshot.viewport.locations) {
                    it.media.id.toString()
                }
            },
            cloud = snapshot.cloud.copy(
                isConnected = false,
                connectedCapabilities = emptySet(),
                people = libraryPreviewWindow(
                    snapshot.cloud.people,
                    snapshot.viewport.people
                ) { it.accountKey }.map { person ->
                    person.copy(
                        thumbnailUrl = person.thumbnailUrl?.takeIf {
                            isPersistableThumbnailUrl(it, person, faceThumbDir)
                        }
                    )
                }
            )
        )
        if (!validateLibrarySnapshot(bounded, faceThumbDir)) {
            printWarning("StartupMediaCache: library validation rejected write")
            return
        }
        writeEntry(
            stamp = stamp,
            payloadKey = LIBRARY_PAYLOAD_KEY,
            stampKey = LIBRARY_STAMP_KEY,
            encodedPayload = json.encodeToString(
                CachedLibrarySnapshot.serializer(),
                CachedLibrarySnapshot(
                    privacyFingerprint = privacyFingerprint,
                    snapshot = bounded
                )
            ),
            label = "library"
        )
    }

    private suspend fun <T> readEntry(
        payloadKey: Preferences.Key<String>,
        stampKey: Preferences.Key<String>,
        label: String,
        decode: (String) -> T,
        validate: (T) -> Boolean
    ): T? {
        val stampBefore = currentStamp() ?: return null
        val prefs = try {
            store.data.first()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            printWarning("StartupMediaCache: $label read failed (${t.javaClass.simpleName})")
            return null
        }
        val storedStamp = prefs[stampKey]?.let {
            try {
                json.decodeFromString(StartupCacheStamp.serializer(), it)
            } catch (e: Exception) {
                printWarning("StartupMediaCache: $label stamp decode failed (${e.javaClass.simpleName})")
                null
            }
        } ?: return null
        if (!isStampUsable(storedStamp, stampBefore)) return null
        val payload = prefs[payloadKey] ?: return null
        if (payload.length > MAX_PAYLOAD_CHARS) {
            printWarning("StartupMediaCache: $label payload oversized")
            return null
        }
        val decoded = try {
            decode(payload)
        } catch (e: Exception) {
            printWarning("StartupMediaCache: $label decode failed (${e.javaClass.simpleName})")
            return null
        }
        if (currentStamp() != stampBefore) return null
        if (!validate(decoded)) {
            printWarning("StartupMediaCache: $label failed validation")
            return null
        }
        return decoded
    }

    private suspend fun writeEntry(
        stamp: StartupCacheStamp,
        payloadKey: Preferences.Key<String>,
        stampKey: Preferences.Key<String>,
        encodedPayload: String,
        label: String
    ) {
        if (encodedPayload.length > MAX_PAYLOAD_CHARS) {
            printWarning("StartupMediaCache: $label payload oversized, skipping write")
            return
        }
        try {
            store.edit { prefs ->
                val now = currentStamp()
                if (now != stamp) {
                    printWarning("StartupMediaCache: $label stamp moved $stamp -> $now, skipping write")
                    return@edit
                }
                prefs[payloadKey] = encodedPayload
                prefs[stampKey] = json.encodeToString(StartupCacheStamp.serializer(), stamp)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            printWarning("StartupMediaCache: $label write failed (${t.javaClass.simpleName})")
        }
    }

    private suspend fun removeEntry(
        payloadKey: Preferences.Key<String>,
        stampKey: Preferences.Key<String>,
        label: String
    ) {
        try {
            store.edit { prefs ->
                prefs.remove(payloadKey)
                prefs.remove(stampKey)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            printWarning("StartupMediaCache: $label remove failed (${t.javaClass.simpleName})")
        }
    }

    private companion object {
        val MEDIA_PAYLOAD_KEY = stringPreferencesKey("startup_media")
        val MEDIA_STAMP_KEY = stringPreferencesKey("startup_media_stamp")
        val ALBUMS_PAYLOAD_KEY = stringPreferencesKey("startup_albums")
        val ALBUMS_STAMP_KEY = stringPreferencesKey("startup_albums_stamp")
        val LIBRARY_PAYLOAD_KEY = stringPreferencesKey("startup_library")
        val LIBRARY_STAMP_KEY = stringPreferencesKey("startup_library_stamp")
    }
}
