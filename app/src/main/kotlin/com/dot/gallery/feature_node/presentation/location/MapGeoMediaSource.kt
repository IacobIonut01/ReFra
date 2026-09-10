/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import com.dot.gallery.cloud.core.CloudMapMarker
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.MapCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.locationIdentityKey
import com.dot.gallery.feature_node.domain.util.isCloud
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal data class CloudMapAssetKey(
    val providerType: ProviderType,
    val configId: Long,
    val remoteId: String,
)

internal data class AccountCloudMapMarker(
    val configId: Long,
    val marker: CloudMapMarker,
) {
    val key: CloudMapAssetKey
        get() = CloudMapAssetKey(marker.providerType, configId, marker.assetId)
}

private data class MapProviderAccount(
    val configId: Long,
    val providerType: ProviderType,
    val provider: MapCapableProvider,
)

/**
 * Joins local EXIF state, account-scoped cloud cache rows and live map-marker APIs.
 * A cloud marker is only rendered when its exact account's cached media row is available, because
 * that row supplies the real viewer item and account-qualified [CloudMediaEntity.globalMediaId].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MapGeoMediaSource @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudServerConfigDao: CloudServerConfigDao,
) {
    private val activeConfigs = cloudServerConfigDao.getActive()

    private val activeCachedMedia = combine(activeConfigs, cloudMediaDao.getAll()) { configs, media ->
        val activeIds = configs.mapTo(HashSet()) { it.id }
        media.filter { it.serverConfigId in activeIds && !it.trashed && !it.archived }
    }

    private val liveMarkers: Flow<List<AccountCloudMapMarker>> = combine(
        activeConfigs,
        providerRegistry.connectionStates,
    ) { configs, states ->
        configs.mapNotNull { config ->
            val provider = providerRegistry.getByConfigId(config.id) as? MapCapableProvider
                ?: return@mapNotNull null
            val state = states[config.id]
            if (state != ConnectionState.CONNECTED && state != ConnectionState.SYNCING) {
                return@mapNotNull null
            }
            MapProviderAccount(config.id, config.providerType, provider)
        }
    }.distinctUntilChanged { old, new ->
        old.map { it.configId to it.provider } == new.map { it.configId to it.provider }
    }.flatMapLatest { accounts ->
        if (accounts.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val flows = accounts.map { account ->
            account.provider.getMapMarkers()
                .map { resource ->
                    resource.data.orEmpty()
                        .asSequence()
                        .filter { it.providerType == account.providerType }
                        .map { AccountCloudMapMarker(account.configId, it) }
                        .toList()
                }
                .catch { emit(emptyList()) }
        }
        combine(flows) { accountResults -> accountResults.flatMap { it } }
    }

    fun mergedGeoMedia(
        localGeoMedia: Flow<List<GeoMedia>>,
        timelineMedia: Flow<MediaState<Media.UriMedia>>,
    ): Flow<List<GeoMedia>> = combine(
        localGeoMedia,
        activeCachedMedia,
        liveMarkers,
        timelineMedia,
    ) { local, cached, markers, timeline ->
        val timelineById = timeline.media.associateBy { it.id }
        val backupOwnerByCloudId = buildMap {
            timeline.cloudBackups.forEach { (localId, copies) ->
                copies.forEach { put(it.id, localId) }
            }
        }
        val result = LinkedHashMap<Long, GeoMedia>()
        mergeAccountQualifiedGeoMedia(local, cached, markers).forEach { item ->
            val resolved = when (val ownerId = backupOwnerByCloudId[item.mediaId]) {
                null -> timelineById[item.mediaId]?.let { item.copy(media = it) }
                else -> timelineById[ownerId]?.let { item.copy(mediaId = ownerId, media = it) }
            } ?: return@forEach
            val existing = result[resolved.mediaId]
            val hasName = !resolved.locationCity.isNullOrBlank() || !resolved.locationCountry.isNullOrBlank()
            val existingHasName = existing != null &&
                    (!existing.locationCity.isNullOrBlank() || !existing.locationCountry.isNullOrBlank())
            if (existing == null || hasName && !existingHasName) result[resolved.mediaId] = resolved
        }
        result.values.sortedByDescending { it.media.definedTimestamp }
    }

    fun mergedLocations(
        localLocations: Flow<List<LocationMedia>>,
        geoMedia: Flow<List<GeoMedia>>,
        timelineMedia: Flow<MediaState<Media.UriMedia>>,
    ): Flow<List<LocationMedia>> = combine(
        localLocations,
        geoMedia,
        activeCachedMedia,
        timelineMedia,
    ) { local, geo, cached, timeline ->
        val timelineById = timeline.media.associateBy { it.id }
        val backupOwnerByCloudId = buildMap {
            timeline.cloudBackups.forEach { (localId, copies) ->
                copies.forEach { put(it.id, localId) }
            }
        }
        val backupLocations = cached.mapNotNull { entity ->
            val owner = backupOwnerByCloudId[entity.globalMediaId]?.let(timelineById::get)
                ?: return@mapNotNull null
            val city = entity.city?.trim()?.takeIf(String::isNotBlank)
            val country = entity.country?.trim()?.takeIf(String::isNotBlank)
            if (city == null && country == null) return@mapNotNull null
            LocationMedia(
                media = owner,
                location = locationLabel(city, country, entity.latitude, entity.longitude),
                city = city,
                country = country,
                latitude = entity.latitude,
                longitude = entity.longitude,
            )
        }
        buildActionableLocations(
            localLocations = backupLocations + local.filter { it.media.id in timelineById },
            geoMedia = geo,
            cachedCloudMedia = cached.filter { it.globalMediaId in timelineById },
        )
    }
}

internal fun mergeAccountQualifiedGeoMedia(
    localGeoMedia: List<GeoMedia>,
    cachedCloudMedia: List<CloudMediaEntity>,
    liveMarkers: List<AccountCloudMapMarker>,
): List<GeoMedia> {
    val result = LinkedHashMap<Long, GeoMedia>()
    localGeoMedia.asSequence()
        .filterNot { it.media.isCloud }
        .forEach { result[it.mediaId] = it }

    val cachedByKey: Map<CloudMapAssetKey, CloudMediaEntity> = cachedCloudMedia.associateBy { entity ->
        CloudMapAssetKey(entity.providerType, entity.serverConfigId, entity.remoteId)
    }
    cachedCloudMedia.forEach { entity ->
        val latitude = entity.latitude
        val longitude = entity.longitude
        if (latitude != null && longitude != null && validCoordinates(latitude, longitude)) {
            result[entity.globalMediaId] = entity.toGeoMedia(latitude, longitude)
        }
    }
    liveMarkers.forEach { accountMarker ->
        val marker = accountMarker.marker
        if (!validCoordinates(marker.latitude, marker.longitude)) return@forEach
        val entity = cachedByKey[accountMarker.key] ?: return@forEach
        result[entity.globalMediaId] = entity.toGeoMedia(
            latitude = marker.latitude,
            longitude = marker.longitude,
            city = marker.city?.takeIf(String::isNotBlank) ?: entity.city,
            country = marker.country?.takeIf(String::isNotBlank) ?: entity.country,
        )
    }
    return result.values.sortedByDescending { it.media.definedTimestamp }
}

internal fun buildActionableLocations(
    localLocations: List<LocationMedia>,
    geoMedia: List<GeoMedia>,
    cachedCloudMedia: List<CloudMediaEntity>,
): List<LocationMedia> {
    val locationByMediaId = LinkedHashMap<Long, LocationMedia>()

    fun addMedia(item: LocationMedia) {
        val existing = locationByMediaId[item.media.id]
        val hasName = !item.city.isNullOrBlank() || !item.country.isNullOrBlank()
        val existingHasName = existing != null &&
                (!existing.city.isNullOrBlank() || !existing.country.isNullOrBlank())
        if (existing == null || hasName || !existingHasName) {
            locationByMediaId[item.media.id] = item
        }
    }

    cachedCloudMedia.forEach { entity ->
        val city = entity.city?.trim()?.takeIf(String::isNotBlank)
        val country = entity.country?.trim()?.takeIf(String::isNotBlank)
        if (city == null && country == null) return@forEach
        addMedia(
            LocationMedia(
                media = entity.toUriMedia(),
                location = locationLabel(city, country, entity.latitude, entity.longitude),
                city = city,
                country = country,
                latitude = entity.latitude,
                longitude = entity.longitude,
            )
        )
    }
    localLocations.forEach { item ->
        val city = item.city?.trim()?.takeIf(String::isNotBlank)
        val country = item.country?.trim()?.takeIf(String::isNotBlank)
        addMedia(
            item.copy(
                location = locationLabel(city, country, item.latitude, item.longitude),
                city = city,
                country = country,
            )
        )
    }
    geoMedia.forEach { item ->
        val city = item.locationCity?.trim()?.takeIf(String::isNotBlank)
        val country = item.locationCountry?.trim()?.takeIf(String::isNotBlank)
        addMedia(
            LocationMedia(
                media = item.media,
                location = locationLabel(city, country, item.latitude, item.longitude),
                city = city,
                country = country,
                latitude = item.latitude,
                longitude = item.longitude,
            )
        )
    }

    val newestByLocation = LinkedHashMap<String, LocationMedia>()
    locationByMediaId.values.forEach { item ->
        val key = item.locationIdentityKey()
        val existing = newestByLocation[key]
        if (existing == null || item.media.definedTimestamp > existing.media.definedTimestamp) {
            newestByLocation[key] = item
        }
    }
    return newestByLocation.values.sortedBy { it.location.lowercase(Locale.ROOT) }
}

private fun CloudMediaEntity.toGeoMedia(
    latitude: Double,
    longitude: Double,
    city: String? = this.city,
    country: String? = this.country,
): GeoMedia = GeoMedia(
    mediaId = globalMediaId,
    latitude = latitude,
    longitude = longitude,
    locationCity = city,
    locationCountry = country,
    media = toUriMedia(),
)

private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun locationLabel(
    city: String?,
    country: String?,
    latitude: Double?,
    longitude: Double?,
): String {
    val named = listOfNotNull(
        city?.trim()?.takeIf(String::isNotBlank),
        country?.trim()?.takeIf(String::isNotBlank),
    ).joinToString(", ")
    if (named.isNotBlank()) return named
    if (latitude != null && longitude != null) {
        return String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)
    }
    return "Unknown location"
}
