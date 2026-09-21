/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.MediaCapabilityProvider
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.capabilities.ShareLinkCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.util.hasMediaAccess
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.startup.StartupCacheStamp
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.fullReadPermissionMask
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isCompleteForLibrary
import com.dot.gallery.feature_node.domain.model.shouldIgnore
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.location.MapGeoMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val HIDDEN_GRACE_MS = 5_000L
private const val PERSIST_DEBOUNCE_MS = 400L
private const val POLICY_RETRY_MS = 1_000L

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun libraryPrivacyFingerprint(
    lockedAlbums: List<LockedAlbum>,
    ignoredAlbums: List<IgnoredAlbum>,
    activeConfigs: List<CloudServerConfigEntity>
): String {
    val payload = buildJsonObject {
        putJsonArray("locked") {
            lockedAlbums.map { it.id }.sorted().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("ignored") {
            ignoredAlbums.sortedBy { it.id }.forEach { album ->
                add(buildJsonObject {
                    put("id", album.id)
                    put("label", album.label)
                    put("wildcard", album.wildcard)
                    putJsonArray("albumIds") {
                        album.albumIds.sorted().forEach { add(JsonPrimitive(it)) }
                    }
                    put("location", album.location)
                    putJsonArray("matchedAlbums") {
                        album.matchedAlbums.sorted().forEach { add(JsonPrimitive(it)) }
                    }
                })
            }
        }
        putJsonArray("accounts") {
            activeConfigs.sortedBy { it.id }.forEach { config ->
                add(buildJsonObject {
                    put("id", config.id)
                    put("providerType", config.providerType.name)
                    put("serverUrl", config.serverUrl)
                    put("username", config.username)
                    put("credentials", sha256Hex(buildJsonObject {
                        put("apiKey", config.apiKey)
                        put("encryptedPassword", config.encryptedPassword)
                    }.toString()))
                })
            }
        }
    }
    return sha256Hex(payload.toString())
}

internal class LibraryContentInputs(
    val lockedAlbums: Flow<List<LockedAlbum>>,
    val ignoredAlbums: Flow<List<IgnoredAlbum>>,
    val activeConfigs: Flow<List<CloudServerConfigEntity>>,
    val timelineMedia: Flow<MediaState<Media.UriMedia>>,
    val localGeoMedia: Flow<List<GeoMedia>>,
    val localLocations: Flow<List<LocationMedia>>,
    val trashMedia: Flow<MediaState<Media.UriMedia>>,
    val favoritesMedia: Flow<MediaState<Media.UriMedia>>,
    val categories: Flow<List<CategoryMedia>?>,
    val categoryCount: Flow<Int>,
    val connectionStates: Flow<Map<Long, ConnectionState>>,
    val peopleInvalidation: Flow<Unit>,
    val faceDetectStatus: Flow<ModelStatus>,
    val providerByConfigId: (Long) -> MediaCapabilityProvider?,
    val sharedLinks: (ProviderType, Long) -> Flow<Resource<List<SharedLinkInfo>>>,
    val cachedCloudCounts: suspend () -> Pair<Int, Int>,
    val supportsTrash: Boolean,
    val supportsFavorites: Boolean,
    val hasMediaAccess: () -> Boolean,
    val fullReadPermissionMask: () -> Int?,
    val mergedGeo: (
        Flow<List<GeoMedia>>,
        Flow<MediaState<Media.UriMedia>>
    ) -> Flow<List<GeoMedia>>,
    val mergedLocations: (
        Flow<List<LocationMedia>>,
        Flow<List<GeoMedia>>,
        Flow<MediaState<Media.UriMedia>>
    ) -> Flow<List<LocationMedia>>,
    val readCache: suspend (String) -> LibrarySnapshot?,
    val writeCache: suspend (StartupCacheStamp?, String, LibrarySnapshot) -> Unit,
    val currentStamp: suspend () -> StartupCacheStamp?,
)

@Singleton
class LibraryContentSource internal constructor(
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher,
    private val inputs: LibraryContentInputs,
    private val trace: (String) -> Unit,
) {

    @Inject
    constructor(
        repository: MediaRepository,
        distributor: MediaDistributor,
        mapGeoMediaSource: MapGeoMediaSource,
        libraryCategorySource: LibraryCategorySource,
        cache: StartupMediaCache,
        cloudRepository: CloudRepository,
        providerRegistry: ProviderRegistry,
        cloudMediaDao: CloudMediaDao,
        cloudServerConfigDao: CloudServerConfigDao,
        modelManager: ModelManager,
        @ApplicationContext context: Context,
    ) : this(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        workDispatcher = Dispatchers.Default,
        inputs = LibraryContentInputs(
            lockedAlbums = repository.getLockedAlbums(),
            ignoredAlbums = repository.getBlacklistedAlbums(),
            activeConfigs = cloudServerConfigDao.getActive(),
            timelineMedia = distributor.timelineMediaFlow.filter { it.isCompleteForLibrary() },
            localGeoMedia = distributor.geoMediaFlow,
            localLocations = distributor.locationsMediaFlow,
            trashMedia = distributor.trashMediaFlow,
            favoritesMedia = distributor.favoritesMediaFlow,
            categories = libraryCategorySource.categories,
            categoryCount = repository.getCategoryCount(),
            connectionStates = providerRegistry.connectionStates,
            peopleInvalidation = cloudRepository.peopleInvalidation,
            faceDetectStatus = modelManager.status(ModelGroup.FACE_DETECT),
            providerByConfigId = providerRegistry::getByConfigId,
            sharedLinks = { type, configId -> cloudRepository.getSharedLinks(type, configId) },
            cachedCloudCounts = { cloudMediaDao.countCached() to cloudMediaDao.countArchived() },
            supportsTrash = SdkCompat.supportsTrash,
            supportsFavorites = SdkCompat.supportsFavorites,
            hasMediaAccess = { context.hasMediaAccess() },
            fullReadPermissionMask = {
                fullReadPermissionMask(
                    sdkInt = Build.VERSION.SDK_INT,
                    imagesGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED,
                    videosGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED,
                    legacyGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            },
            mergedGeo = mapGeoMediaSource::mergedGeoMedia,
            mergedLocations = mapGeoMediaSource::mergedLocations,
            readCache = cache::readLibrary,
            writeCache = cache::writeLibrary,
            currentStamp = cache::currentStamp,
        ),
        trace = { label -> StartupTracer.trace(label) {} }
    )

    private val _state = MutableStateFlow(LibrarySnapshot())
    val state: StateFlow<LibrarySnapshot> = _state.asStateFlow()

    private val restoreMutex = Mutex()
    private val policyFlow = MutableStateFlow<Policy?>(null)
    private val peoplePartitions = MutableStateFlow<Map<Long, List<PersonInfo>>>(emptyMap())
    private val peopleCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val sharedLinkCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val persistRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val liveStartMutex = Mutex()

    private var restored = false
    private var liveEmitted = false
    private var contentDrawn = false
    private var generation = 0
    private var policyFingerprint: String? = null
    private var generationStamp: StartupCacheStamp? = null
    private var grantMask: Int? = null
    private var liveJob: Job? = null
    private var hideJob: Job? = null
    private var observersJob: Job? = null
    private var visibleCount = 0

    private data class Policy(
        val lockedIds: Set<Long>,
        val ignored: List<IgnoredAlbum>,
        val accounts: Map<Long, CloudServerConfigEntity>,
        val fingerprint: String,
        val grantMask: Int?,
        val hasMediaAccess: Boolean,
    ) {
        fun allowsMedia(media: Media): Boolean {
            if (media.albumID in lockedIds) return false
            if (ignored.any { it.shouldIgnore(media, -1L) }) return false
            if (media is Media.UriMedia) {
                val cloud = try {
                    CloudUri.parse(media.uri.toString())
                } catch (_: Exception) {
                    null
                }
                if (cloud != null) {
                    return accounts[cloud.configId]?.providerType == cloud.providerType
                }
            }
            return hasMediaAccess
        }

        fun allowsPerson(person: PersonInfo): Boolean = when {
            person.providerType == ProviderType.LOCAL_PEOPLE ->
                hasMediaAccess && person.serverConfigId == LOCAL_PEOPLE_CONFIG_ID
            else ->
                accounts[person.serverConfigId]?.providerType == person.providerType
        }
    }

    private suspend fun readPolicy(): Policy {
        val locked = inputs.lockedAlbums.first()
        val ignored = inputs.ignoredAlbums.first()
        val accounts = inputs.activeConfigs.first()
        return Policy(
            lockedIds = locked.mapTo(HashSet()) { it.id },
            ignored = ignored,
            accounts = accounts.associateBy { it.id },
            fingerprint = libraryPrivacyFingerprint(locked, ignored, accounts),
            grantMask = inputs.fullReadPermissionMask(),
            hasMediaAccess = inputs.hasMediaAccess(),
        )
    }

    private suspend fun readPolicyOrNull(): Policy? = try {
        withContext(workDispatcher) { readPolicy() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun readStampOrNull(): StartupCacheStamp? = try {
        withContext(workDispatcher) { inputs.currentStamp() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun <T> onScope(block: suspend CoroutineScope.() -> T): T =
        withContext(scope.coroutineContext.minusKey(Job), block)

    suspend fun restore() {
        restoreMutex.withLock {
            if (restored) return
            val policy = readPolicyOrNull()
                ?: return onScope {
                    clearForMissingPolicy()
                    ensureObservers()
                }
            val stamp = if (policy.grantMask != null) readStampOrNull() else null
            val viewportAtStart = _state.value.viewport
            val gen = generation
            val cached = if (policy.grantMask != null) {
                try {
                    withContext(workDispatcher) { inputs.readCache(policy.fingerprint) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else null
            val current = readPolicyOrNull()
                ?: return onScope {
                    clearForMissingPolicy()
                    ensureObservers()
                }
            val currentStamp = if (current.grantMask != null) readStampOrNull() else null
            onScope {
                if (gen != generation) return@onScope
                val publish = if (
                    cached != null && current.fingerprint == policy.fingerprint &&
                    currentStamp == stamp
                ) applyPolicy(cached, current) else null
                if (publish != null) {
                    peoplePartitions.value = publish.cloud.people.groupBy { it.serverConfigId }
                    peopleCounts.value = publish.peopleCountsByAccount
                    sharedLinkCounts.value = publish.sharedLinkCountsByAccount
                }
                _state.update { cur ->
                    if (liveEmitted) cur
                    else (publish ?: LibrarySnapshot()).copy(
                        viewport = if (cur.viewport != viewportAtStart) {
                            cur.viewport
                        } else publish?.viewport ?: cur.viewport
                    )
                }
                policyFingerprint = current.fingerprint
                grantMask = current.grantMask
                policyFlow.value = current
                generationStamp = currentStamp
                restored = true
                val s = _state.value
                trace(
                    "Library.initialState(cached=${publish != null}," +
                        "locations=${s.locations?.size ?: 0}," +
                        "people=${s.peopleCount},categories=${s.categories?.size ?: 0}," +
                        "grid=${s.viewport.grid})"
                )
                if (publish != null) trace("Library.snapshotRestored")
                ensureObservers()
            }
        }
        scope.launch { maybeStartLive() }
    }

    fun onVisible() {
        scope.launch {
            hideJob?.cancel()
            hideJob = null
            if (++visibleCount != 1) return@launch
            val mask = inputs.fullReadPermissionMask()
            val mediaAccess = inputs.hasMediaAccess()
            if (mask != grantMask || !mediaAccess) {
                dropLocalContent()
                grantMask = mask
                policyFlow.value = policyFlow.value?.copy(
                    grantMask = mask,
                    hasMediaAccess = mediaAccess
                )
            }
            val policy = readPolicyOrNull()
            val stamp = if (policy?.grantMask != null) readStampOrNull() else null
            when {
                policy == null -> clearForMissingPolicy()
                policy.fingerprint != policyFingerprint || policy.grantMask != grantMask ||
                    (generationStamp != null && generationStamp != stamp) ->
                    invalidateContent(policy)
                else -> policyFlow.value = policy
            }
            restore()
            maybeStartLive()
        }
    }

    fun onHidden() {
        scope.launch {
            if (visibleCount > 0) visibleCount--
            if (visibleCount != 0) return@launch
            scope.launch { persistState() }
            hideJob = scope.launch {
                delay(HIDDEN_GRACE_MS)
                if (visibleCount == 0) {
                    liveJob?.cancel()
                    liveJob = null
                }
            }
        }
    }

    fun onContentDrawn() {
        scope.launch {
            contentDrawn = true
            restore()
            maybeStartLive()
        }
    }

    fun updateViewport(viewport: LibraryViewport) {
        _state.update { cur -> cur.copy(viewport = mergeLibraryViewport(cur.viewport, viewport)) }
        trace("Library.viewportMerged(grid=${_state.value.viewport.grid})")
        persistRequests.tryEmit(Unit)
    }

    private fun applyPolicy(snapshot: LibrarySnapshot, policy: Policy): LibrarySnapshot {
        val locations = snapshot.locations?.filter { policy.allowsMedia(it.media) }
        val people = snapshot.cloud.people.filter(policy::allowsPerson)
        val peopleCounts = snapshot.peopleCountsByAccount.filterKeys {
            if (it == LOCAL_PEOPLE_CONFIG_ID) {
                policy.hasMediaAccess
            } else it in policy.accounts
        }
        val linkCounts = snapshot.sharedLinkCountsByAccount.filterKeys { it in policy.accounts }
        return snapshot.copy(
            locations = locations,
            locationCount = if (locations?.size == snapshot.locations?.size) {
                snapshot.locationCount
            } else locations?.size ?: 0,
            latestGeo = snapshot.latestGeo?.takeIf { policy.allowsMedia(it.media) },
            indicators = if (policy.hasMediaAccess) {
                snapshot.indicators
            } else LibraryIndicatorState(),
            peopleCountsByAccount = peopleCounts,
            sharedLinkCountsByAccount = linkCounts,
            peopleCount = peopleCounts.values.sum().coerceAtLeast(people.size),
            categories = snapshot.categories?.map { category ->
                val thumbnail = category.thumbnailMedia
                if (thumbnail != null && !policy.allowsMedia(thumbnail)) {
                    category.copy(thumbnailMedia = null)
                } else category
            },
            cloud = snapshot.cloud.copy(
                people = people,
                sharedLinkCount = linkCounts.values.sum(),
            ),
        )
    }

    private fun invalidateContent(newPolicy: Policy?) {
        generation++
        policyFingerprint = newPolicy?.fingerprint
        grantMask = newPolicy?.grantMask
        generationStamp = null
        policyFlow.value = newPolicy
        peoplePartitions.value = emptyMap()
        peopleCounts.value = emptyMap()
        sharedLinkCounts.value = emptyMap()
        liveJob?.cancel()
        liveJob = null
        _state.update { LibrarySnapshot(viewport = it.viewport) }
        persistRequests.tryEmit(Unit)
        if (newPolicy != null) {
            scope.launch {
                restore()
                maybeStartLive()
            }
        }
    }

    private fun clearForMissingPolicy() = invalidateContent(null)

    private fun dropLocalContent() {
        generation++
        generationStamp = null
        grantMask = null
        liveJob?.cancel()
        liveJob = null
        peoplePartitions.update { it - LOCAL_PEOPLE_CONFIG_ID }
        peopleCounts.update { it - LOCAL_PEOPLE_CONFIG_ID }
        _state.update { cur ->
            fun cloudBacked(media: Media): Boolean = media is Media.UriMedia && try {
                CloudUri.parse(media.uri.toString()) != null
            } catch (_: Exception) {
                false
            }
            val people = cur.cloud.people.filter {
                it.serverConfigId != LOCAL_PEOPLE_CONFIG_ID
            }
            val locations = cur.locations?.filter { cloudBacked(it.media) }
            cur.copy(
                locations = locations,
                locationCount = locations?.size ?: 0,
                latestGeo = cur.latestGeo?.takeIf { cloudBacked(it.media) },
                categories = cur.categories?.map { category ->
                    val thumbnail = category.thumbnailMedia
                    if (thumbnail != null && !cloudBacked(thumbnail)) {
                        category.copy(thumbnailMedia = null)
                    } else category
                },
                indicators = LibraryIndicatorState(),
                peopleCountsByAccount = cur.peopleCountsByAccount - LOCAL_PEOPLE_CONFIG_ID,
                peopleCount = cur.peopleCountsByAccount.entries
                    .filter { it.key != LOCAL_PEOPLE_CONFIG_ID }.sumOf { it.value }
                    .coerceAtLeast(people.size),
                cloud = cur.cloud.copy(people = people)
            )
        }
        persistRequests.tryEmit(Unit)
    }

    private fun ensureObservers() {
        if (observersJob != null) return
        observersJob = scope.launch {
            supervisorScope {
                launch {
                    while (isActive) {
                        try {
                            combine(
                                inputs.lockedAlbums,
                                inputs.ignoredAlbums,
                                inputs.activeConfigs
                            ) { locked, ignored, accounts ->
                                libraryPrivacyFingerprint(locked, ignored, accounts) to
                                    Policy(
                                        lockedIds = locked.mapTo(HashSet()) { it.id },
                                        ignored = ignored,
                                        accounts = accounts.associateBy { it.id },
                                        fingerprint = "",
                                        grantMask = inputs.fullReadPermissionMask(),
                                        hasMediaAccess = inputs.hasMediaAccess(),
                                    )
                            }.collect { (fingerprint, policy) ->
                                if (fingerprint != policyFingerprint ||
                                    policy.grantMask != grantMask ||
                                    policy.hasMediaAccess != policyFlow.value?.hasMediaAccess
                                ) {
                                    invalidateContent(policy.copy(fingerprint = fingerprint))
                                }
                            }
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            clearForMissingPolicy()
                            trace("Library.policyObserverFailed(${e.javaClass.simpleName})")
                            delay(POLICY_RETRY_MS)
                        }
                    }
                }
                launch {
                    persistRequests.collectLatest {
                        delay(PERSIST_DEBOUNCE_MS)
                        persistState()
                    }
                }
            }
        }
    }

    private suspend fun persistState() {
        val snapshot = _state.value
        if (snapshot == LibrarySnapshot()) return
        val gen = generation
        val stamp = generationStamp ?: return
        val fingerprint = policyFingerprint ?: return
        val policy = readPolicyOrNull() ?: return
        if (policy.fingerprint != fingerprint || policy.grantMask != grantMask) {
            onScope { invalidateContent(policy) }
            return
        }
        val now = readStampOrNull()
        if (gen != generation || fingerprint != policyFingerprint || now != stamp) {
            trace("Library.persistSkipped(gen=$gen!=$generation,fp=${fingerprint != policyFingerprint},stamp=$now!=$stamp)")
            return
        }
        trace("Library.persisting(grid=${snapshot.viewport.grid})")
        withContext(workDispatcher) {
            try {
                inputs.writeCache(stamp, fingerprint, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun maybeStartLive() = liveStartMutex.withLock {
        if (liveJob != null || !restored || !contentDrawn || visibleCount <= 0) {
            return@withLock
        }
        val policy = policyFlow.value ?: return@withLock
        val gen = generation
        val stamp = if (generationStamp == null && policy.grantMask != null) {
            readStampOrNull()
        } else generationStamp
        if (gen != generation || policyFlow.value != policy ||
            visibleCount <= 0 || liveJob != null
        ) {
            return@withLock
        }
        generationStamp = stamp
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                supervisorScope {
                    launch { collectCategories(gen) }
                    launch { collectLocations(this@supervisorScope, gen) }
                    launch { collectIndicators(gen) }
                    launch { collectCloud(gen) }
                    launch { collectPeople(gen) }
                    launch { collectSharedLinks(gen) }
                }
            } finally {
                if (liveJob === coroutineContext[Job]) liveJob = null
            }
        }
        liveJob = job
        job.start()
    }

    private inline fun publishIfCurrent(
        gen: Int,
        crossinline transform: (LibrarySnapshot) -> LibrarySnapshot
    ) {
        if (gen != generation) return
        val policy = policyFlow.value ?: return
        _state.update { cur ->
            if (gen == generation) applyPolicy(transform(cur), policy) else cur
        }
        liveEmitted = true
        trace("Library.snapshotUpdated")
        persistRequests.tryEmit(Unit)
    }

    // New category system - top categories for library display with thumbnails
    private suspend fun collectCategories(gen: Int) {
        try {
            // Total count of categories with media (for the "See all" indicator)
            combine(
                inputs.categories.filterNotNull(),
                inputs.categoryCount
            ) { items, count ->
                items.map {
                    LibraryCategoryPreview(
                        id = it.category.id,
                        name = it.category.name,
                        mediaCount = it.category.mediaCount,
                        thumbnailMedia = it.thumbnailMedia
                    )
                } to count
            }.collect { (previews, count) ->
                publishIfCurrent(gen) { cur ->
                    cur.copy(categories = previews, categoryCount = count)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private suspend fun collectLocations(liveScope: CoroutineScope, gen: Int) {
        try {
            val geo = inputs.mergedGeo(inputs.localGeoMedia, inputs.timelineMedia)
                .shareIn(liveScope, SharingStarted.Eagerly, replay = 1)
            val locations = inputs.mergedLocations(inputs.localLocations, geo, inputs.timelineMedia)
            combine(locations, geo, policyFlow.filterNotNull()) { locs, geoList, policy ->
                val allowed = locs.filter { policy.allowsMedia(it.media) }
                allowed to geoList.firstOrNull { policy.allowsMedia(it.media) }
            }.collect { (allowed, firstGeo) ->
                publishIfCurrent(gen) { cur ->
                    cur.copy(
                        locations = allowed,
                        locationCount = allowed.size,
                        latestGeo = firstGeo?.let {
                            LibraryGeoPreview(it.media, it.latitude, it.longitude)
                        }
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private suspend fun collectIndicators(gen: Int) {
        try {
            combine(
                if (inputs.supportsTrash) inputs.trashMedia
                else flowOf(MediaState<Media.UriMedia>(isLoading = false)),
                if (inputs.supportsFavorites) inputs.favoritesMedia
                else flowOf(MediaState<Media.UriMedia>(isLoading = false)),
            ) { trashed, favorites ->
                if (trashed.isCompleteForLibrary() && favorites.isCompleteForLibrary()) {
                    LibraryIndicatorState(
                        trashCount = trashed.media.size,
                        favoriteCount = favorites.media.size
                    )
                } else null
            }.collect { indicators ->
                if (indicators != null) {
                    publishIfCurrent(gen) { it.copy(indicators = indicators) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    // Configuration, advertised capabilities and connectivity are separate facts. In
    // particular, disconnecting an account must not make its supported features
    // disappear.
    private suspend fun collectCloud(gen: Int) {
        try {
            combine(
                inputs.activeConfigs,
                inputs.connectionStates
            ) { configs, states -> configs to states }.collectLatest { (configs, states) ->
                val counts = try {
                    withContext(workDispatcher) { inputs.cachedCloudCounts() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val providers = configs.mapNotNull { inputs.providerByConfigId(it.id) }
                // getRemoteProviders() intentionally returns only currently available
                // providers. Library feature availability instead comes from every
                // registered/configured remote provider.
                val allResolved = configs.all { inputs.providerByConfigId(it.id) != null }
                val configuredCaps = if (allResolved) {
                    providers.flatMapTo(LinkedHashSet()) { it.capabilities }
                } else null
                val connectedCaps = providers.asSequence()
                    .filter { it.isAvailable }
                    .flatMap { it.capabilities.asSequence() }
                    .toSet()
                val isConnected = configs.any {
                    states[it.id] == ConnectionState.CONNECTED ||
                        states[it.id] == ConnectionState.SYNCING
                }
                publishIfCurrent(gen) { cur ->
                    val cloud = cur.cloud
                    val availability = configuredCaps?.let {
                        resolveCloudLibraryAvailability(
                            hasConfiguredAccounts = configs.isNotEmpty(),
                            configuredCapabilities = it,
                            isConnected = isConnected,
                        )
                    }
                    cur.copy(
                        cloud = cloud.copy(
                            hasCloud = configs.isNotEmpty(),
                            isConnected = isConnected,
                            connectedCapabilities = connectedCaps,
                            hasArchive = availability?.hasArchive ?: cloud.hasArchive,
                            hasMemories = availability?.hasMemories ?: cloud.hasMemories,
                            hasShareLink = availability?.hasShareLink ?: cloud.hasShareLink,
                            hasPeople = (availability?.hasPeople ?: cloud.hasPeople) ||
                                cloud.people.isNotEmpty(),
                            hasMap = availability?.hasMap ?: cloud.hasMap,
                            hasCachedMedia = counts?.let { it.first > 0 || it.second > 0 }
                                ?: cloud.hasCachedMedia,
                            archivedCount = counts?.second ?: cloud.archivedCount,
                            totalCloudCount = counts?.first ?: cloud.totalCloudCount,
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    // Always-on people collector: combines local (on-device) and cloud people providers, so
    // on-device Person grouping surfaces in the Library even when no cloud account exists.
    private suspend fun collectPeople(gen: Int) {
        try {
            // Re-fetch people when a name changes
            combine(
                inputs.activeConfigs,
                inputs.connectionStates,
                inputs.faceDetectStatus,
                inputs.peopleInvalidation.onStart { emit(Unit) },
            ) { configs, states, _, _ -> configs to states }.collectLatest { (configs, states) ->
                val accountIds = configs.map { it.id } + LOCAL_PEOPLE_CONFIG_ID
                if (gen == generation) {
                    peoplePartitions.update { it.filterKeys(accountIds::contains) }
                    peopleCounts.update { it.filterKeys(accountIds::contains) }
                    publishPeople(gen)
                }
                supervisorScope {
                    accountIds.forEach { accountId ->
                        val provider =
                            inputs.providerByConfigId(accountId) as? PeopleCapableProvider
                        if (provider == null || !provider.isAvailable) {
                            // The local provider goes unavailable when its face model is
                            // deleted — drop its stale partition so the People section hides
                            // immediately instead of after a restart (issue #1229).
                            if (accountId == LOCAL_PEOPLE_CONFIG_ID &&
                                (accountId in peoplePartitions.value ||
                                    accountId in peopleCounts.value)
                            ) {
                                peoplePartitions.update { it - accountId }
                                peopleCounts.update { it - accountId }
                                publishPeople(gen)
                            }
                            return@forEach
                        }
                        if (accountId != LOCAL_PEOPLE_CONFIG_ID &&
                            states[accountId] != ConnectionState.CONNECTED &&
                            states[accountId] != ConnectionState.SYNCING
                        ) return@forEach
                        val config = configs.firstOrNull { it.id == accountId }
                        launch {
                            try {
                                provider.getPeople().collect { resource ->
                                    if (resource is Resource.Success && gen == generation) {
                                        val list = resource.data.orEmpty().filter { p ->
                                            if (accountId == LOCAL_PEOPLE_CONFIG_ID) {
                                                p.providerType == ProviderType.LOCAL_PEOPLE &&
                                                    p.serverConfigId == LOCAL_PEOPLE_CONFIG_ID
                                            } else {
                                                p.serverConfigId == accountId &&
                                                    p.providerType == config?.providerType
                                            }
                                        }
                                        peoplePartitions.update { it + (accountId to list) }
                                        peopleCounts.update { it + (accountId to list.size) }
                                        publishPeople(gen)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private fun publishPeople(gen: Int) {
        publishIfCurrent(gen) { cur ->
            val people = peoplePartitions.value.values.flatten()
            cur.copy(
                peopleCountsByAccount = peopleCounts.value,
                peopleCount = peopleCounts.value.values.sum().coerceAtLeast(people.size),
                cloud = cur.cloud.copy(
                    people = people,
                    hasPeople = cur.cloud.hasPeople || people.isNotEmpty()
                )
            )
        }
    }

    private suspend fun collectSharedLinks(gen: Int) {
        try {
            combine(
                inputs.activeConfigs,
                inputs.connectionStates
            ) { configs, states -> configs to states }.collectLatest { (configs, states) ->
                val accountIds = configs.mapTo(HashSet()) { it.id }
                if (gen == generation) {
                    sharedLinkCounts.update { it.filterKeys(accountIds::contains) }
                    publishSharedLinks(gen)
                }
                supervisorScope {
                    configs.forEach { config ->
                        val provider = inputs.providerByConfigId(config.id)
                        if (provider !is ShareLinkCapableProvider || !provider.isAvailable ||
                            ProviderCapability.SHARE_MANAGE !in provider.capabilities ||
                            (states[config.id] != ConnectionState.CONNECTED &&
                                states[config.id] != ConnectionState.SYNCING)
                        ) return@forEach
                        launch {
                            try {
                                inputs.sharedLinks(config.providerType, config.id)
                                    .collect { resource ->
                                        if (resource is Resource.Success && gen == generation) {
                                            sharedLinkCounts.update {
                                                it + (config.id to resource.data.orEmpty().size)
                                            }
                                            publishSharedLinks(gen)
                                        }
                                    }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private fun publishSharedLinks(gen: Int) {
        publishIfCurrent(gen) { cur ->
            cur.copy(
                sharedLinkCountsByAccount = sharedLinkCounts.value,
                cloud = cur.cloud.copy(sharedLinkCount = sharedLinkCounts.value.values.sum())
            )
        }
    }
}
