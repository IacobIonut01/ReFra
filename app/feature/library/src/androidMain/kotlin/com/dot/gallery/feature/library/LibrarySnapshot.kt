/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library

import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.core.startup.mediaStoreUriId
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.MAX_SHORTCUT_PREVIEW_MEDIA
import com.dot.gallery.feature.library.ui.LibraryScrollPosition
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID


const val MAX_CACHED_LIBRARY_LOCATIONS = 64
const val MAX_CACHED_LIBRARY_PEOPLE = 64
const val MAX_CACHED_LIBRARY_CATEGORIES = 5
const val MAX_CACHED_SCROLL_OFFSET = 100_000

@Serializable
data class CachedLibrarySnapshot(
    val version: Int = 1,
    val privacyFingerprint: String,
    val snapshot: LibrarySnapshot
)

private fun LibraryScrollPosition.isValidPosition(): Boolean =
    index >= 0 && offset in 0..MAX_CACHED_SCROLL_OFFSET

private fun validCoordinates(latitude: Double?, longitude: Double?): Boolean =
    (latitude == null || (latitude.isFinite() && latitude in -90.0..90.0)) &&
        (longitude == null || (longitude.isFinite() && longitude in -180.0..180.0))

fun validateCachedLibraryMedia(media: UriMedia, trashed: Boolean = false): Boolean {
    if (media.size < 0 || (media.trashed != 0) != trashed) return false
    val uri = try {
        media.uri.toString()
    } catch (_: Exception) {
        return false
    }
    mediaStoreUriId(uri)?.let { return it == media.id }
    val cloud = CloudUri.parse(uri) ?: return false
    if (cloud.configId <= 0) return false
    return media.id == cloudMediaId(cloud.providerType, cloud.configId, cloud.remoteId)
}

private val ALLOWED_CLOUD_THUMB_QUERY_KEYS = setOf("size", "type", "fileId", "cfg")

fun isPersistableThumbnailUrl(
    url: String,
    person: PersonInfo,
    faceThumbDir: String? = null,
): Boolean {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return false
    }
    if (uri.userInfo != null || uri.fragment != null) return false
    return when (uri.scheme?.lowercase()) {
        "content" -> mediaStoreUriId(url) != null
        "file" -> {
            if (person.providerType != ProviderType.LOCAL_PEOPLE || uri.query != null) {
                return false
            }
            val root = faceThumbDir ?: return false
            val path = uri.path ?: return false
            val child = try {
                File(path).canonicalPath
            } catch (_: Exception) {
                return false
            }
            val parent = try {
                File(root).canonicalPath
            } catch (_: Exception) {
                return false
            }
            child.startsWith("$parent/")
        }
        CloudUri.SCHEME -> {
            val cloud = CloudUri.parse(url) ?: return false
            if (cloud.configId <= 0 || cloud.configId != person.serverConfigId) return false
            if (cloud.providerType != person.providerType) return false
            val raw = uri.rawQuery ?: return false
            val keys = raw.split('&').map { it.substringBefore('=') }
            "cfg" in keys && keys.size == keys.toSet().size &&
                keys.all { it in ALLOWED_CLOUD_THUMB_QUERY_KEYS }
        }
        else -> false
    }
}

private fun validPersonIdentity(person: PersonInfo): Boolean =
    (person.providerType == ProviderType.LOCAL_PEOPLE) ==
        (person.serverConfigId == LOCAL_PEOPLE_CONFIG_ID)

fun validateLibrarySnapshot(
    snapshot: LibrarySnapshot,
    faceThumbDir: String? = null
): Boolean {
    if (snapshot.categoryCount < 0 || snapshot.locationCount < 0 || snapshot.peopleCount < 0) {
        return false
    }
    if (snapshot.indicators.trashCount < 0 || snapshot.indicators.favoriteCount < 0) {
        return false
    }
    if (snapshot.peopleCountsByAccount.values.any { it < 0 } ||
        snapshot.sharedLinkCountsByAccount.values.any { it < 0 }
    ) return false
    snapshot.categories?.let { categories ->
        if (categories.size > MAX_CACHED_LIBRARY_CATEGORIES) return false
        if (categories.any { it.mediaCount < 0 }) return false
        if (snapshot.categoryCount < categories.size) return false
        val keys = categories.map { "category_${it.id}" }
        if (keys.toSet().size != keys.size) return false
        if (categories.any { preview ->
                preview.thumbnailMedia?.let { !validateCachedLibraryMedia(it) } == true
            }
        ) return false
    }
    snapshot.locations?.let { locations ->
        if (locations.size > MAX_CACHED_LIBRARY_LOCATIONS) return false
        if (snapshot.locationCount < locations.size) return false
        if (locations.any { !validCoordinates(it.latitude, it.longitude) }) return false
        val keys = locations.map { it.media.id.toString() }
        if (keys.toSet().size != keys.size) return false
        if (locations.any { it.media !is UriMedia }) return false
        if (locations.any {
                !validateCachedLibraryMedia(it.media as UriMedia)
            }
        ) return false
    }
    snapshot.shortcutPreviews.let { previews ->
        val favorites = previews.latestFavorites + previews.oldestFavorites
        if (listOf(previews.latestFavorites, previews.oldestFavorites, previews.recentTrash)
                .any { it.size > MAX_SHORTCUT_PREVIEW_MEDIA }
        ) return false
        if (favorites.any { !validateCachedLibraryMedia(it) }) return false
        if (previews.recentTrash.any { !validateCachedLibraryMedia(it, trashed = true) }) return false
        if (previews.nextTrashExpiry?.let { it < 0 } == true) return false
    }
    snapshot.latestGeo?.let { geo ->
        if (!validCoordinates(geo.latitude, geo.longitude)) return false
        if (!validateCachedLibraryMedia(geo.media)) return false
    }
    val cloud = snapshot.cloud
    if (cloud.isConnected || cloud.connectedCapabilities.isNotEmpty()) return false
    if (cloud.people.size > MAX_CACHED_LIBRARY_PEOPLE) return false
    if (cloud.people.any { it.assetCount < 0 }) return false
    if (snapshot.peopleCount < cloud.people.size) return false
    if (cloud.people.any { !validPersonIdentity(it) }) return false
    val peopleKeys = cloud.people.map { it.accountKey }
    if (peopleKeys.toSet().size != peopleKeys.size) return false
    if (cloud.people.any { person ->
            person.thumbnailUrl?.let { !isPersistableThumbnailUrl(it, person, faceThumbDir) } == true
        }
    ) return false
    if (cloud.archivedCount < 0 || cloud.sharedLinkCount < 0 || cloud.totalCloudCount < 0 ||
        cloud.hiddenPeopleCount < 0
    ) {
        return false
    }
    val viewport = snapshot.viewport
    return viewport.grid.isValidPosition() && viewport.locations.isValidPosition() &&
        viewport.people.isValidPosition() && viewport.categories.isValidPosition() &&
        (viewport.statusBarTop == null || viewport.statusBarTop in 0..MAX_CACHED_SCROLL_OFFSET) &&
        (viewport.navigationBarBottom == null || viewport.navigationBarBottom in 0..MAX_CACHED_SCROLL_OFFSET)
}
