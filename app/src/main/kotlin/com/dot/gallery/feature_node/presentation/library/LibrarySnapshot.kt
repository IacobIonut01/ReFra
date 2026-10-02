/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library

import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.core.startup.mediaStoreUriId
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.net.URISyntaxException

internal const val MAX_CACHED_LIBRARY_LOCATIONS = 64
internal const val MAX_CACHED_LIBRARY_PEOPLE = 64
internal const val MAX_CACHED_LIBRARY_CATEGORIES = 5
internal const val MAX_CACHED_SCROLL_OFFSET = 100_000

@Serializable
data class LibraryCategoryPreview(
    val id: Long,
    val name: String,
    val mediaCount: Int,
    val thumbnailMedia: Media.UriMedia? = null
)

@Serializable
data class LibraryGeoPreview(
    val media: Media.UriMedia,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class LibraryScrollPosition(
    val key: String? = null,
    val index: Int = 0,
    val offset: Int = 0
)

@Serializable
data class LibraryViewport(
    val grid: LibraryScrollPosition = LibraryScrollPosition(),
    val locations: LibraryScrollPosition = LibraryScrollPosition(),
    val people: LibraryScrollPosition = LibraryScrollPosition(),
    val categories: LibraryScrollPosition = LibraryScrollPosition(),
    val configuration: String = "",
    val statusBarTop: Int? = null,
    val navigationBarBottom: Int? = null
)

@Serializable
data class LibrarySnapshot(
    val categories: List<LibraryCategoryPreview>? = null,
    val categoryCount: Int = 0,
    val locations: List<LocationMedia>? = null,
    val locationCount: Int = 0,
    val latestGeo: LibraryGeoPreview? = null,
    val peopleCount: Int = 0,
    val peopleCountsByAccount: Map<Long, Int> = emptyMap(),
    val sharedLinkCountsByAccount: Map<Long, Int> = emptyMap(),
    val cloud: CloudLibraryState = CloudLibraryState(),
    val indicators: LibraryIndicatorState = LibraryIndicatorState(),
    val viewport: LibraryViewport = LibraryViewport()
)

@Serializable
internal data class CachedLibrarySnapshot(
    val version: Int = 1,
    val privacyFingerprint: String,
    val snapshot: LibrarySnapshot
)

internal fun restoredLibraryIndex(keys: List<String>, position: LibraryScrollPosition): Int =
    keys.indexOf(position.key).takeIf { it >= 0 }
        ?: position.index.coerceIn(0, (keys.size - 1).coerceAtLeast(0))

internal fun <T> libraryPreviewWindow(
    items: List<T>,
    position: LibraryScrollPosition,
    key: (T) -> String
): List<T> {
    if (items.size <= 64) return items
    val anchor = restoredLibraryIndex(items.map(key), position)
    val start = (anchor - 16).coerceIn(0, items.size - 64)
    return items.subList(start, start + 64).toList()
}

internal fun LibraryScrollPosition.isMeasuredPosition(): Boolean =
    key != null || index != 0 || offset != 0

internal fun mergeLibraryViewport(
    current: LibraryViewport,
    incoming: LibraryViewport
): LibraryViewport {
    val configChanged = incoming.configuration.isNotBlank() &&
        incoming.configuration != current.configuration
    fun retained(row: LibraryScrollPosition): LibraryScrollPosition =
        if (configChanged) row.copy(offset = 0) else row
    return LibraryViewport(
        grid = if (incoming.grid.isMeasuredPosition()) incoming.grid else retained(current.grid),
        locations = if (incoming.locations.isMeasuredPosition()) {
            incoming.locations
        } else retained(current.locations),
        people = if (incoming.people.isMeasuredPosition()) {
            incoming.people
        } else retained(current.people),
        categories = if (incoming.categories.isMeasuredPosition()) {
            incoming.categories
        } else retained(current.categories),
        configuration = incoming.configuration.ifBlank { current.configuration },
        statusBarTop = incoming.statusBarTop ?: current.statusBarTop.takeUnless { configChanged },
        navigationBarBottom = incoming.navigationBarBottom
            ?: current.navigationBarBottom.takeUnless { configChanged }
    )
}

internal fun initialLibraryInset(
    current: Int,
    cached: Int?,
    sameConfiguration: Boolean,
    visible: Boolean?
): Int = if (current == 0 && sameConfiguration && visible != false) cached ?: 0 else current

private fun LibraryScrollPosition.isValidPosition(): Boolean =
    index >= 0 && offset in 0..MAX_CACHED_SCROLL_OFFSET

private fun validCoordinates(latitude: Double?, longitude: Double?): Boolean =
    (latitude == null || (latitude.isFinite() && latitude in -90.0..90.0)) &&
        (longitude == null || (longitude.isFinite() && longitude in -180.0..180.0))

internal fun validateCachedLibraryMedia(media: Media.UriMedia): Boolean {
    if (media.size < 0 || media.trashed != 0) return false
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

internal fun isPersistableThumbnailUrl(
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

internal fun validateLibrarySnapshot(
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
        if (categories.any {
                it.thumbnailMedia != null && !validateCachedLibraryMedia(it.thumbnailMedia)
            }
        ) return false
    }
    snapshot.locations?.let { locations ->
        if (locations.size > MAX_CACHED_LIBRARY_LOCATIONS) return false
        if (snapshot.locationCount < locations.size) return false
        if (locations.any { !validCoordinates(it.latitude, it.longitude) }) return false
        val keys = locations.map { it.media.id.toString() }
        if (keys.toSet().size != keys.size) return false
        if (locations.any { it.media !is Media.UriMedia }) return false
        if (locations.any {
                !validateCachedLibraryMedia(it.media as Media.UriMedia)
            }
        ) return false
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
    if (cloud.people.any {
            it.thumbnailUrl != null &&
                !isPersistableThumbnailUrl(it.thumbnailUrl, it, faceThumbDir)
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
