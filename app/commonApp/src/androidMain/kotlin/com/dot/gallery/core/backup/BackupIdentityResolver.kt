/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.core.backup.ConfigBackupManager.ImportIssue
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.UriMedia

/** Exports device ids as portable refs. */
internal class MediaRefIndex(
    localMedia: List<UriMedia>,
    cloudMedia: List<CloudMediaEntity>
) {
    private val byId = HashMap<Long, MediaRef>(localMedia.size + cloudMedia.size)
    private val byUri = HashMap<String, MediaRef>(localMedia.size)

    init {
        localMedia.forEach { media ->
            val ref = MediaRef.Local(
                relativePath = media.relativePath,
                label = media.label,
                size = media.size,
                timestamp = media.timestamp,
                albumLabel = media.albumLabel,
                volume = volumeOf(media.path)
            )
            byId[media.id] = ref
            byUri[media.uri.toString()] = ref
        }
        cloudMedia.forEach { media ->
            byId[media.globalMediaId] = MediaRef.Cloud(
                providerType = media.providerType.name,
                sourceAccountId = backupSourceAccountId(
                    media.providerType.name,
                    media.serverConfigId
                ),
                remoteId = media.remoteId,
                label = media.label
            )
        }
    }

    fun ref(id: Long?): MediaRef? = id?.let(byId::get)

    fun ref(uri: String): MediaRef? = byUri[uri]
}

/** Exports album ids as portable refs. */
internal class AlbumRefIndex(albums: List<Album>) {
    private val byId = HashMap<Long, AlbumRef>(albums.size)

    init {
        albums.forEach { album ->
            byId[album.id] = album.toRef()
        }
    }

    fun ref(id: Long?): AlbumRef? = id?.let(byId::get)
}

internal fun Album.toRef(): AlbumRef {
    val cloud = cloudIdentity
    return if (cloud != null) {
        AlbumRef.Cloud(
            providerType = cloud.providerType.name,
            sourceAccountId = backupSourceAccountId(
                cloud.providerType.name,
                cloud.serverConfigId
            ),
            remoteAlbumId = cloud.remoteId,
            label = label
        )
    } else {
        AlbumRef.Local(
            label = label,
            relativePath = relativePath,
            volume = volume,
            bucketId = id
        )
    }
}

/**
 * Resolves portable refs back to destination ids. Built once per restore so every section shares
 * the same maps and the same ambiguity reporting.
 */
internal class BackupIdentityResolver(
    localMedia: List<UriMedia>,
    albums: List<Album>,
    private val accountMappings: BackupAccountMappings,
    private val issues: MutableList<ImportIssue>,
    private val maxIssues: Int = 200
) {
    private val localByVolumePath = HashMap<String, UriMedia>(localMedia.size)
    private val localByRelativePath = HashMap<String, MutableList<UriMedia>>()
    private val localByLabelSize = HashMap<String, MutableList<UriMedia>>()
    private val albumByVolumeKey = HashMap<String, Album>(albums.size)
    private val albumByKey = HashMap<String, Album>(albums.size)
    private val albumByLabel = HashMap<String, MutableList<Album>>()
    private val albumById = HashMap<Long, Album>(albums.size)

    init {
        localMedia.forEach { media ->
            val relativeKey = "${media.relativePath}/${media.label}"
            localByVolumePath["${volumeOf(media.path)}\u0000$relativeKey"] = media
            localByRelativePath.getOrPut(relativeKey) { mutableListOf() }.add(media)
            localByLabelSize.getOrPut(media.labelSizeKey()) { mutableListOf() }.add(media)
        }
        albums.forEach { album ->
            albumById[album.id] = album
            albumByVolumeKey["${album.volume}\u0000${album.label}\u0000${album.relativePath}"] = album
            albumByKey["${album.label}\u0000${album.relativePath}"] = album
            albumByLabel.getOrPut(album.label) { mutableListOf() }.add(album)
        }
    }

    fun resolveMedia(ref: MediaRef, section: BackupSection): Long? = when (ref) {
        is MediaRef.Local -> resolveLocalMedia(ref, section)
        is MediaRef.Cloud -> resolveCloudMediaId(ref, section)
    }

    fun resolveAlbum(ref: AlbumRef, section: BackupSection): Long? = when (ref) {
        is AlbumRef.Local -> resolveLocalAlbum(ref, section)
        is AlbumRef.Cloud -> resolveCloudAlbumId(ref, section)
    }

    /** Resolves a cloud ref even when the item is not in the local cache yet (feature rows). */
    fun resolveCloudMediaId(ref: MediaRef.Cloud, section: BackupSection): Long? {
        val provider = runCatching { ProviderType.valueOf(ref.providerType) }.getOrNull()
        if (provider == null) {
            issue(section, ref.remoteId, "Unsupported provider ${ref.providerType}")
            return null
        }
        val destinationConfig = accountMappings.resolveDestination(
            ref.sourceAccountId,
            ref.providerType
        )
        if (destinationConfig == null) {
            issue(section, ref.remoteId, "No destination account for ${ref.providerType}")
            return null
        }
        return cloudMediaId(provider, destinationConfig, ref.remoteId)
    }

    /** Destination config id for a backup-local account id, or null when it cannot be mapped. */
    fun resolveCloudAccount(sourceAccountId: String, providerType: String): Long? =
        accountMappings.resolveDestination(sourceAccountId, providerType)

    fun resolveCloudAlbumId(ref: AlbumRef.Cloud, section: BackupSection): Long? {
        val provider = runCatching { ProviderType.valueOf(ref.providerType) }.getOrNull()
        if (provider == null) {
            issue(section, ref.remoteAlbumId, "Unsupported provider ${ref.providerType}")
            return null
        }
        val destinationConfig = accountMappings.resolveDestination(
            ref.sourceAccountId,
            ref.providerType
        )
        if (destinationConfig == null) {
            issue(section, ref.remoteAlbumId, "No destination account for ${ref.providerType}")
            return null
        }
        return cloudAlbumId(provider, destinationConfig, ref.remoteAlbumId)
    }

    private fun resolveLocalMedia(ref: MediaRef.Local, section: BackupSection): Long? {
        val relativeKey = "${ref.relativePath}/${ref.label}"
        localByVolumePath["${ref.volume}\u0000$relativeKey"]?.let { return it.id }
        localByRelativePath[relativeKey]?.takeIf { it.size == 1 }?.first()?.let { return it.id }

        val candidates = localByLabelSize["${ref.label}\u0000${ref.size}"]
        if (candidates.isNullOrEmpty()) {
            issue(section, ref.label, "Media not found on this device")
            return null
        }
        if (candidates.size == 1) return candidates.first().id
        candidates.firstOrNull { it.timestamp == ref.timestamp }?.let { return it.id }
        issue(section, ref.label, "Ambiguous media match (${candidates.size} candidates)")
        return null
    }

    private fun resolveLocalAlbum(ref: AlbumRef.Local, section: BackupSection): Long? {
        val volume = ref.volume
        if (!volume.isNullOrBlank()) {
            albumByVolumeKey["$volume\u0000${ref.label}\u0000${ref.relativePath}"]
                ?.let { return it.id }
        }
        albumByKey["${ref.label}\u0000${ref.relativePath}"]?.let { return it.id }
        val candidates = albumByLabel[ref.label]
        when {
            candidates.isNullOrEmpty() -> {
                issue(section, ref.label, "Album not found on this device")
                return null
            }
            candidates.size == 1 -> return candidates.first().id
            else -> {
                val byVolume = volume?.let { v -> candidates.filter { it.volume == v } }
                if (byVolume?.size == 1) return byVolume.first().id
                // A bucket id is only safe on the same install and only as a last resort.
                albumById[ref.bucketId]?.takeIf { it.label == ref.label }?.let { return it.id }
                issue(section, ref.label, "Ambiguous album match (${candidates.size} candidates)")
                return null
            }
        }
    }

    internal fun issue(section: BackupSection, item: String, message: String) {
        if (issues.size >= maxIssues) return
        issues += ImportIssue(section, item, message)
    }
}

internal fun UriMedia.localKey(): String = "$relativePath/$label"

internal fun UriMedia.labelSizeKey(): String = "$label\u0000$size"

/** Extracts the storage volume from an absolute media path (`/storage/<volume>/...`). */
internal fun volumeOf(path: String): String {
    val marker = "/storage/"
    val start = path.indexOf(marker)
    if (start < 0) return ""
    val rest = path.substring(start + marker.length)
    return rest.substringBefore('/')
}
