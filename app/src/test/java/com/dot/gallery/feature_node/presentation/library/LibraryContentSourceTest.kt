/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library

import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.capabilities.ShareLinkCapableProvider
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.core.Resource
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.startup.PERMISSION_MASK_FULL_MEDIA
import com.dot.gallery.core.startup.StartupCacheStamp
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryContentSourceTest {

    private fun person(
        id: String,
        configId: Long,
        type: ProviderType = ProviderType.IMMICH
    ) = PersonInfo(
        id = id,
        name = "person-$id",
        providerType = type,
        serverConfigId = configId,
        assetCount = 2
    )

    private fun localPerson(id: String) = person(
        id = id,
        configId = LOCAL_PEOPLE_CONFIG_ID,
        type = ProviderType.LOCAL_PEOPLE
    )

    private fun config(id: Long, type: ProviderType = ProviderType.IMMICH) =
        CloudServerConfigEntity(
            id = id,
            providerType = type,
            serverUrl = "https://srv-$id",
            username = "u$id"
        )

    private fun link(id: String, configId: Long, type: ProviderType = ProviderType.IMMICH) =
        SharedLinkInfo(
            id = id,
            key = "key-$id",
            providerType = type,
            serverConfigId = configId
        )

    private open class FakeProvider(
        override val providerType: ProviderType,
        override var isAvailable: Boolean = true
    ) : PeopleCapableProvider, ShareLinkCapableProvider {
        override val displayName: String = providerType.displayName
        override val capabilities: Set<ProviderCapability> =
            setOf(ProviderCapability.PEOPLE, ProviderCapability.SHARE_MANAGE)

        val people = MutableSharedFlow<Resource<List<PersonInfo>>>(replay = 1)
        val links = MutableSharedFlow<Resource<List<SharedLinkInfo>>>(replay = 1)
        var peopleCollections = 0
        var activePeopleCollectors = 0

        override fun getPeople(): Flow<Resource<List<PersonInfo>>> =
            people
                .onStart { peopleCollections++; activePeopleCollectors++ }
                .onCompletion { activePeopleCollectors-- }

        override fun getPersonMedia(personId: String) =
            flowOf<Resource<List<Media>>>(Resource.Success(emptyList()))
        override fun getPersonThumbnailUrl(personId: String): String? = null
        override suspend fun updatePersonName(personId: String, name: String) =
            Result.success(Unit)
        override suspend fun updatePersonBirthDate(personId: String, birthDate: String) =
            Result.success(Unit)

        override suspend fun createShareLink(
            assetIds: List<String>,
            expiresAt: Long?
        ) = Result.success("https://link")
        override fun getSharedLinks() = links
        override suspend fun deleteSharedLink(linkId: String) = Result.success(Unit)
        override suspend fun updateSharedLink(linkId: String, updates: Map<String, Any>) =
            Result.success(Unit)
    }

    private class StubbornProvider(
        type: ProviderType
    ) : FakeProvider(type) {
        var postCancelResult: Resource<List<PersonInfo>>? = null

        override fun getPeople(): Flow<Resource<List<PersonInfo>>> = flow {
            try {
                people
                    .onStart { peopleCollections++; activePeopleCollectors++ }
                    .onCompletion { activePeopleCollectors-- }
                    .collect { emit(it) }
            } finally {
                withContext(NonCancellable) {
                    postCancelResult?.let { emit(it) }
                }
            }
        }
    }

    private class Harness(
        testScope: TestScope,
        cached: LibrarySnapshot? = null,
        configs: List<CloudServerConfigEntity> = emptyList(),
        connections: Map<Long, ConnectionState> = emptyMap(),
        val providers: Map<Long, FakeProvider> = emptyMap(),
        lockedFlow: Flow<List<LockedAlbum>>? = null,
        initialGrantMask: Int? = PERMISSION_MASK_FULL_MEDIA,
        initialMediaAccess: Boolean = true
    ) {
        val workDispatcher = StandardTestDispatcher(testScope.testScheduler)
        val scope = CoroutineScope(SupervisorJob() + workDispatcher)
        val ignoredAlbums = MutableStateFlow<List<IgnoredAlbum>>(emptyList())
        val activeConfigs = MutableSharedFlow<List<CloudServerConfigEntity>>(
            replay = 1,
            extraBufferCapacity = 4
        )
        val timeline = MutableStateFlow(MediaState<Media.UriMedia>(isLoading = false))
        val localGeo = MutableStateFlow<List<GeoMedia>>(emptyList())
        val localLocations = MutableStateFlow<List<LocationMedia>>(emptyList())
        val trash = MutableStateFlow(MediaState<Media.UriMedia>(isLoading = false))
        val favorites = MutableStateFlow(MediaState<Media.UriMedia>(isLoading = false))
        val categories = MutableStateFlow<List<CategoryMedia>?>(emptyList())
        val categoryCount = MutableStateFlow(0)
        val connStates = MutableStateFlow(connections)
        val invalidation = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val faceDetect = MutableStateFlow(ModelStatus.READY)
        val hiddenCount = MutableStateFlow(0)
        val linkFlows = mutableMapOf<Long, MutableSharedFlow<Resource<List<SharedLinkInfo>>>>()

        var grantMask = initialGrantMask
        var hasAccess = initialMediaAccess
        var stampValue: StartupCacheStamp? = StartupCacheStamp("v1", PERMISSION_MASK_FULL_MEDIA)
        var cacheSnapshot = cached
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var policyGate: CompletableDeferred<Unit>? = null
        var stampGate: CompletableDeferred<Unit>? = null
        var stampFailure: Throwable? = null
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Triple<StartupCacheStamp?, String, LibrarySnapshot>>()

        val source: LibraryContentSource

        init {
            activeConfigs.tryEmit(configs)
            val inputs = LibraryContentInputs(
                lockedAlbums = lockedFlow ?: flow {
                    policyGate?.await()
                    emit(emptyList())
                },
                ignoredAlbums = ignoredAlbums,
                activeConfigs = activeConfigs,
                timelineMedia = timeline,
                localGeoMedia = localGeo,
                localLocations = localLocations,
                trashMedia = trash,
                favoritesMedia = favorites,
                categories = categories,
                categoryCount = categoryCount,
                connectionStates = connStates,
                peopleInvalidation = invalidation,
                hiddenPeopleCount = hiddenCount,
                faceDetectStatus = faceDetect,
                providerByConfigId = { providers[it] },
                sharedLinks = { _, cfg ->
                    linkFlows.getOrPut(cfg) {
                        MutableSharedFlow(replay = 1)
                    }
                },
                cachedCloudCounts = { 0 to 0 },
                supportsTrash = true,
                supportsFavorites = true,
                hasMediaAccess = { hasAccess },
                fullReadPermissionMask = { grantMask },
                mergedGeo = { geo, _ -> geo },
                mergedLocations = { locs, _, _ -> locs },
                readCache = { fingerprint ->
                    reads += fingerprint
                    readGate?.await()
                    cacheSnapshot
                },
                writeCache = { stamp, fingerprint, snapshot ->
                    writeGate?.await()
                    writes += Triple(stamp, fingerprint, snapshot)
                },
                currentStamp = {
                    stampGate?.await()
                    stampFailure?.let { throw it }
                    stampValue
                },
            )
            source = LibraryContentSource(scope, workDispatcher, inputs) {}
        }

        fun close() = scope.cancel()
    }

    private fun harness(
        testScope: TestScope,
        cached: LibrarySnapshot? = null,
        configs: List<CloudServerConfigEntity> = emptyList(),
        connections: Map<Long, ConnectionState> = emptyMap(),
        providers: Map<Long, FakeProvider> = emptyMap(),
        lockedFlow: Flow<List<LockedAlbum>>? = null,
        initialGrantMask: Int? = PERMISSION_MASK_FULL_MEDIA,
        initialMediaAccess: Boolean = true
    ) = Harness(
        testScope, cached, configs, connections, providers, lockedFlow,
        initialGrantMask, initialMediaAccess
    )

    private suspend fun Harness.restoreVisibleDrawn() {
        source.restore()
        source.onVisible()
        source.onContentDrawn()
    }

    @Test
    fun cachedPeopleSurviveOfflineRefreshAndKeepTrueCount() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 12,
            peopleCountsByAccount = mapOf(11L to 7, 22L to 5),
            sharedLinkCountsByAccount = mapOf(11L to 2, 22L to 1),
            cloud = CloudLibraryState(
                people = listOf(person("a", 11), person("b", 22)),
                sharedLinkCount = 3
            )
        )
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11), config(22)),
            connections = mapOf(
                11L to ConnectionState.DISCONNECTED,
                22L to ConnectionState.DISCONNECTED
            ),
            providers = mapOf(11L to FakeProvider(ProviderType.IMMICH), 22L to FakeProvider(ProviderType.IMMICH))
        )
        h.source.restore()
        assertEquals(cached, h.source.state.value)
        h.source.onVisible(); h.source.onContentDrawn(); runCurrent()
        assertEquals(cached.cloud.people, h.source.state.value.cloud.people)
        assertEquals(cached.peopleCount, h.source.state.value.peopleCount)
        assertEquals(
            cached.peopleCountsByAccount,
            h.source.state.value.peopleCountsByAccount
        )
        assertEquals(
            cached.sharedLinkCountsByAccount,
            h.source.state.value.sharedLinkCountsByAccount
        )
        assertEquals(cached.cloud.sharedLinkCount, h.source.state.value.cloud.sharedLinkCount)
        assertEquals(0, h.providers.getValue(11L).peopleCollections)
        h.close()
    }

    @Test
    fun successEmptyFromOneAccountClearsOnlyThatPartition() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 12,
            peopleCountsByAccount = mapOf(11L to 7, 22L to 5),
            cloud = CloudLibraryState(
                people = listOf(person("a", 11), person("b", 22))
            )
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val p22 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11), config(22)),
            connections = mapOf(
                11L to ConnectionState.CONNECTED,
                22L to ConnectionState.CONNECTED
            ),
            providers = mapOf(11L to p11, 22L to p22)
        )
        h.restoreVisibleDrawn(); runCurrent()
        p22.people.emit(Resource.Success(emptyList()))
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(mapOf(11L to 7, 22L to 0), h.source.state.value.peopleCountsByAccount)
        assertEquals(7, h.source.state.value.peopleCount)
        h.close()
    }

    @Test
    fun providerErrorRetainsTheAccountPartition() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 7,
            peopleCountsByAccount = mapOf(11L to 7),
            cloud = CloudLibraryState(people = listOf(person("a", 11)))
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        p11.people.emit(Resource.Error("offline"))
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(7, h.source.state.value.peopleCount)
        h.close()
    }

    @Test
    fun hiddenPeopleCountKeepsThePeopleSectionAlive() = runTest {
        // #1262: hiding the last visible person must not remove the People
        // section — the hidden count alone keeps the header reachable.
        val h = harness(this)
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(0, h.source.state.value.cloud.hiddenPeopleCount)
        assertTrue(h.source.state.value.cloud.people.isEmpty())
        h.hiddenCount.value = 2
        runCurrent()
        val cloud = h.source.state.value.cloud
        assertEquals(2, cloud.hiddenPeopleCount)
        assertTrue(cloud.people.isEmpty())
        assertTrue(cloud.hasPeople)
        h.close()
    }

    @Test
    fun hiddenPeopleCountDropsWhenMediaAccessIsLost() = runTest {
        val h = harness(this)
        h.restoreVisibleDrawn(); runCurrent()
        h.hiddenCount.value = 2
        runCurrent()
        assertEquals(2, h.source.state.value.cloud.hiddenPeopleCount)
        h.source.onHidden(); runCurrent()
        h.hasAccess = false
        h.grantMask = null
        h.source.onVisible(); runCurrent()
        assertEquals(0, h.source.state.value.cloud.hiddenPeopleCount)
        h.close()
    }

    @Test
    fun restoredHiddenCountSurvivesAColdStart() = runTest {
        val cached = LibrarySnapshot(
            cloud = CloudLibraryState(hiddenPeopleCount = 3, hasPeople = true)
        )
        val h = harness(this, cached = cached)
        h.hiddenCount.value = 3 // the DAO flow is the authority once live starts
        h.source.restore()
        assertEquals(cached, h.source.state.value)
        h.source.onVisible(); h.source.onContentDrawn(); runCurrent()
        val cloud = h.source.state.value.cloud
        assertEquals(3, cloud.hiddenPeopleCount)
        assertTrue(cloud.hasPeople)
        h.close()
    }

    @Test
    fun foreignPersonInfoFromAProviderIsDropped() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        p11.people.emit(
            Resource.Success(
                listOf(person("a", 11), person("foreign", 99), person("wrong", 11, ProviderType.WEBDAV))
            )
        )
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(mapOf(11L to 1), h.source.state.value.peopleCountsByAccount)
        h.close()
    }

    @Test
    fun removedAccountPrunesPartitionAndCountTogether() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 12,
            peopleCountsByAccount = mapOf(11L to 7, 22L to 5),
            sharedLinkCountsByAccount = mapOf(11L to 2, 22L to 1),
            cloud = CloudLibraryState(
                people = listOf(person("a", 11), person("b", 22)),
                sharedLinkCount = 3
            )
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11), config(22)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11, 22L to FakeProvider(ProviderType.IMMICH))
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.activeConfigs.emit(listOf(config(11)))
        runCurrent()
        assertTrue(h.source.state.value.cloud.people.isEmpty())
        p11.people.emit(Resource.Success(listOf(person("a", 11), person("a2", 11))))
        h.linkFlows.getValue(11L).emit(
            Resource.Success(listOf(link("l1", 11), link("l2", 11)))
        )
        runCurrent()
        assertEquals(
            listOf(person("a", 11), person("a2", 11)),
            h.source.state.value.cloud.people
        )
        assertEquals(mapOf(11L to 2), h.source.state.value.peopleCountsByAccount)
        assertEquals(2, h.source.state.value.peopleCount)
        assertEquals(mapOf(11L to 2), h.source.state.value.sharedLinkCountsByAccount)
        assertEquals(2, h.source.state.value.cloud.sharedLinkCount)
        h.close()
    }

    @Test
    fun perAccountSharedLinkCountsArePublished() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val p22 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11), config(22)),
            connections = mapOf(
                11L to ConnectionState.CONNECTED,
                22L to ConnectionState.CONNECTED
            ),
            providers = mapOf(11L to p11, 22L to p22)
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.linkFlows.getValue(11L).emit(
            Resource.Success(listOf(link("l1", 11), link("l2", 11)))
        )
        runCurrent()
        assertEquals(mapOf(11L to 2), h.source.state.value.sharedLinkCountsByAccount)
        assertEquals(2, h.source.state.value.cloud.sharedLinkCount)
        h.close()
    }

    @Test
    fun liveDoesNotStartBeforeFirstContentIsDrawn() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.source.restore()
        h.source.onVisible()
        runCurrent()
        assertEquals(0, p11.peopleCollections)
        h.source.onContentDrawn()
        runCurrent()
        assertEquals(1, p11.peopleCollections)
        h.close()
    }

    @Test
    fun hiddenGraceCancelsLiveWorkAfterFiveSeconds() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(1, p11.activePeopleCollectors)
        h.source.onHidden()
        advanceTimeBy(4_999)
        assertEquals(1, p11.activePeopleCollectors)
        advanceTimeBy(2)
        assertEquals(0, p11.activePeopleCollectors)
        h.close()
    }

    @Test
    fun reentryWithinTheGracePeriodKeepsLiveWork() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.source.onHidden()
        advanceTimeBy(3_000)
        h.source.onVisible()
        advanceTimeBy(3_000)
        assertEquals(1, p11.activePeopleCollectors)
        h.close()
    }

    @Test
    fun reentryDuringSuspendedPersistDoesNotCancelLive() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.writeGate = CompletableDeferred()
        h.source.onHidden()
        advanceTimeBy(400)
        h.source.onVisible()
        h.writeGate!!.complete(Unit)
        advanceTimeBy(5_100)
        assertEquals(1, p11.activePeopleCollectors)
        assertTrue(h.writes.isNotEmpty())
        h.close()
    }

    @Test
    fun policyReadFailureClearsAndRecoversThroughTheObserver() = runTest {
        var attempts = 0
        val locked = flow<List<LockedAlbum>> {
            attempts++
            if (attempts <= 1) throw IOException("db")
            emit(emptyList())
        }
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11),
            lockedFlow = locked
        )
        h.source.restore()
        runCurrent()
        assertEquals(LibrarySnapshot(), h.source.state.value)
        h.source.onVisible()
        h.source.onContentDrawn()
        runCurrent()
        p11.people.emit(Resource.Success(listOf(person("a", 11))))
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        h.close()
    }

    @Test
    fun fullMaskDowngradeInvalidatesMemoryAndForbidsDisk() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 3,
            peopleCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11)))
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(1, h.reads.size)
        h.source.onHidden(); runCurrent()
        h.writes.clear()
        h.grantMask = null
        h.source.onVisible(); runCurrent()
        assertEquals(1, h.reads.size)
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        p11.people.emit(Resource.Success(listOf(person("a", 11), person("a2", 11))))
        runCurrent()
        assertEquals(
            listOf(person("a", 11), person("a2", 11)),
            h.source.state.value.cloud.people
        )
        assertEquals(mapOf(11L to 2), h.source.state.value.peopleCountsByAccount)
        h.source.onHidden()
        advanceTimeBy(500)
        assertTrue(h.writes.isEmpty())
        h.close()
    }

    @Test
    fun stampChangeBeforeWriteSkipsTheWrite() = runTest {
        val h = harness(this, configs = listOf(config(11)))
        h.restoreVisibleDrawn(); runCurrent()
        h.source.updateViewport(
            LibraryViewport(grid = LibraryScrollPosition("k", 4, 10), configuration = "sig")
        )
        h.stampValue = StartupCacheStamp("v2", PERMISSION_MASK_FULL_MEDIA)
        advanceTimeBy(500)
        assertTrue(h.writes.isEmpty())
        h.source.updateViewport(
            LibraryViewport(grid = LibraryScrollPosition("k", 5, 10), configuration = "sig")
        )
        advanceTimeBy(500)
        assertTrue(h.writes.isEmpty())
        h.close()
    }

    @Test
    fun canceledRestoreCanBeRetried() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 3,
            peopleCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11)))
        )
        val h = harness(this, cached = cached, configs = listOf(config(11)))
        h.readGate = CompletableDeferred()
        val job = launch { h.source.restore() }
        runCurrent()
        job.cancel()
        h.readGate = null
        h.source.restore()
        assertEquals(cached, h.source.state.value)
        h.close()
    }

    @Test
    fun newerViewportSurvivesASuspendedCacheRead() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 3,
            peopleCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11))),
            viewport = LibraryViewport(
                grid = LibraryScrollPosition("old", 1, 5),
                configuration = "sig-old"
            )
        )
        val h = harness(this, cached = cached, configs = listOf(config(11)))
        h.readGate = CompletableDeferred()
        val job = launch { h.source.restore() }
        runCurrent()
        val newer = LibraryViewport(
            grid = LibraryScrollPosition("new", 9, 30),
            configuration = "sig-new"
        )
        h.source.updateViewport(newer)
        h.readGate!!.complete(Unit)
        job.join()
        assertEquals(newer.grid, h.source.state.value.viewport.grid)
        assertEquals("sig-new", h.source.state.value.viewport.configuration)
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        h.close()
    }

    @Test
    fun policyChangeWhileCacheReadIsSuspendedDiscardsTheSnapshot() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 3,
            peopleCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11)))
        )
        val h = harness(this, cached = cached, configs = listOf(config(11)))
        h.readGate = CompletableDeferred()
        val job = launch { h.source.restore() }
        runCurrent()
        h.activeConfigs.emit(listOf(config(11), config(22)))
        h.readGate!!.complete(Unit)
        job.join()
        assertTrue(h.source.state.value.cloud.people.isEmpty())
        h.close()
    }

    @Test
    fun localPeopleStayWhileTheModelIsUnavailable() = runTest {
        val localProvider = FakeProvider(ProviderType.LOCAL_PEOPLE, isAvailable = false)
        val cached = LibrarySnapshot(
            peopleCount = 4,
            peopleCountsByAccount = mapOf(LOCAL_PEOPLE_CONFIG_ID to 4),
            cloud = CloudLibraryState(people = listOf(localPerson("l1"), localPerson("l2")))
        )
        val h = harness(
            this,
            cached = cached,
            providers = mapOf(LOCAL_PEOPLE_CONFIG_ID to localProvider)
        )
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(
            listOf(localPerson("l1"), localPerson("l2")),
            h.source.state.value.cloud.people
        )
        localProvider.isAvailable = true
        h.invalidation.emit(Unit)
        runCurrent()
        localProvider.people.emit(Resource.Success(listOf(localPerson("l1"))))
        runCurrent()
        assertEquals(listOf(localPerson("l1")), h.source.state.value.cloud.people)
        assertEquals(1, h.source.state.value.peopleCount)
        h.close()
    }

    @Test
    fun missingPolicyBlocksLiveAndPersistence() = runTest {
        val h = harness(
            this,
            configs = listOf(config(11)),
            lockedFlow = flow { throw IOException("db gone") }
        )
        h.source.restore()
        h.source.onVisible()
        h.source.onContentDrawn()
        runCurrent()
        assertEquals(LibrarySnapshot(), h.source.state.value)
        h.source.onHidden()
        advanceTimeBy(5_500)
        assertTrue(h.writes.isEmpty())
        h.close()
    }

    @Test
    fun policyObserverFailureWhileHiddenClearsContentAndRecoversOnRetry() = runTest {
        val failSignal = MutableStateFlow<Throwable?>(null)
        val locked = flow<List<LockedAlbum>> {
            failSignal.collect { failure ->
                if (failure != null) throw failure
                emit(emptyList())
            }
        }
        val cached = LibrarySnapshot(
            peopleCount = 3,
            peopleCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11)))
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11),
            lockedFlow = locked
        )
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(1, p11.activePeopleCollectors)
        h.source.onHidden(); runCurrent()

        failSignal.value = IOException("db")
        runCurrent()
        assertEquals(LibrarySnapshot(), h.source.state.value)
        advanceTimeBy(5_500)
        assertEquals(0, p11.activePeopleCollectors)
        assertEquals(LibrarySnapshot(), h.source.state.value)

        failSignal.value = null
        advanceTimeBy(1_100)
        runCurrent()
        h.source.onVisible(); runCurrent()
        p11.people.emit(Resource.Success(listOf(person("b", 11))))
        runCurrent()
        assertEquals(listOf(person("b", 11)), h.source.state.value.cloud.people)
        h.close()
    }

    @Test
    fun mediaStoreStampChangeOnVisibleDropsLocalMemory() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 4,
            peopleCountsByAccount = mapOf(LOCAL_PEOPLE_CONFIG_ID to 4),
            cloud = CloudLibraryState(people = listOf(localPerson("l1")))
        )
        val h = harness(this, cached = cached)
        h.restoreVisibleDrawn(); runCurrent()
        assertEquals(listOf(localPerson("l1")), h.source.state.value.cloud.people)
        h.source.onHidden(); runCurrent()
        h.stampValue = StartupCacheStamp("v2", PERMISSION_MASK_FULL_MEDIA)
        h.source.onVisible(); runCurrent()
        assertTrue(h.source.state.value.cloud.people.isEmpty())
        assertTrue(h.source.state.value.peopleCountsByAccount.isEmpty())
        h.close()
    }

    @Test
    fun grantDowngradeClearsLocalMemoryBeforePolicyReadCompletes() = runTest {
        val cached = LibrarySnapshot(
            peopleCount = 7,
            peopleCountsByAccount = mapOf(11L to 3, LOCAL_PEOPLE_CONFIG_ID to 4),
            cloud = CloudLibraryState(people = listOf(person("a", 11), localPerson("l1"))),
            indicators = LibraryIndicatorState(trashCount = 2, favoriteCount = 1)
        )
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.source.onHidden(); runCurrent()
        h.policyGate = CompletableDeferred()
        h.grantMask = null
        h.source.onVisible()
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(
            mapOf(11L to 3),
            h.source.state.value.peopleCountsByAccount
        )
        assertEquals(LibraryIndicatorState(), h.source.state.value.indicators)
        h.policyGate!!.complete(Unit)
        runCurrent()
        p11.people.emit(Resource.Success(listOf(person("a", 11), person("a2", 11))))
        runCurrent()
        assertEquals(
            listOf(person("a", 11), person("a2", 11)),
            h.source.state.value.cloud.people
        )
        assertTrue(h.source.state.value.peopleCountsByAccount
            .containsKey(LOCAL_PEOPLE_CONFIG_ID).not())
        h.close()
    }

    @Test
    fun deniedGrantsDoNotReadmitLocalPeopleFromAnAvailableProvider() = runTest {
        val localProvider = FakeProvider(ProviderType.LOCAL_PEOPLE)
        val cached = LibrarySnapshot(
            peopleCount = 5,
            peopleCountsByAccount = mapOf(11L to 2, LOCAL_PEOPLE_CONFIG_ID to 3),
            cloud = CloudLibraryState(people = listOf(person("a", 11), localPerson("l1")))
        )
        val h = harness(
            this,
            cached = cached,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(
                11L to FakeProvider(ProviderType.IMMICH),
                LOCAL_PEOPLE_CONFIG_ID to localProvider
            )
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.source.onHidden(); runCurrent()
        h.hasAccess = false
        h.grantMask = null
        h.source.onVisible(); runCurrent()
        localProvider.people.emit(
            Resource.Success(listOf(localPerson("l1"), localPerson("l2")))
        )
        runCurrent()
        assertEquals(listOf(person("a", 11)), h.source.state.value.cloud.people)
        assertEquals(
            mapOf(11L to 2),
            h.source.state.value.peopleCountsByAccount
        )
        h.close()
    }

    @Test
    fun policyChangeDuringSuspendedPersistAbortsTheWrite() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.restoreVisibleDrawn(); runCurrent()
        p11.people.emit(Resource.Success(listOf(person("a", 11))))
        runCurrent()
        h.source.updateViewport(
            LibraryViewport(grid = LibraryScrollPosition("k", 4, 10), configuration = "sig")
        )
        h.policyGate = CompletableDeferred()
        advanceTimeBy(500)
        h.activeConfigs.emit(listOf(config(11), config(22)))
        runCurrent()
        h.policyGate!!.complete(Unit)
        advanceTimeBy(500)
        runCurrent()
        val oldFingerprint =
            libraryPrivacyFingerprint(emptyList(), emptyList(), listOf(config(11)))
        assertTrue(h.writes.none { it.second == oldFingerprint })
        h.close()
    }

    @Test
    fun failedStampReadDoesNotStopLaterPersistence() = runTest {
        val h = harness(this, configs = listOf(config(11)))
        h.restoreVisibleDrawn(); runCurrent()
        h.stampFailure = IOException("stamp")
        h.source.updateViewport(
            LibraryViewport(grid = LibraryScrollPosition("a", 1, 5), configuration = "s")
        )
        advanceTimeBy(500)
        assertTrue(h.writes.isEmpty())
        h.stampFailure = null
        h.source.updateViewport(
            LibraryViewport(grid = LibraryScrollPosition("b", 2, 5), configuration = "s")
        )
        advanceTimeBy(500)
        assertEquals(1, h.writes.size)
        assertEquals(StartupCacheStamp("v1", PERMISSION_MASK_FULL_MEDIA), h.writes[0].first)
        h.close()
    }

    @Test
    fun concurrentVisibleAndDrawnStartsExactlyOneLiveGeneration() = runTest {
        val p11 = FakeProvider(ProviderType.IMMICH)
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to p11)
        )
        h.stampValue = null
        h.source.restore()
        h.stampGate = CompletableDeferred()
        h.source.onVisible()
        h.source.onContentDrawn()
        runCurrent()
        h.stampGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, p11.peopleCollections)
        assertEquals(1, p11.activePeopleCollectors)
        h.close()
    }

    @Test
    fun delayedOldGenerationProviderResultCannotMutatePartitions() = runTest {
        val stubborn = StubbornProvider(ProviderType.IMMICH)
        stubborn.postCancelResult = Resource.Success(listOf(person("stale", 11)))
        val h = harness(
            this,
            configs = listOf(config(11)),
            connections = mapOf(11L to ConnectionState.CONNECTED),
            providers = mapOf(11L to stubborn)
        )
        h.restoreVisibleDrawn(); runCurrent()
        h.activeConfigs.emit(listOf(config(11), config(22)))
        runCurrent()
        assertTrue(h.source.state.value.cloud.people.isEmpty())
        assertTrue(h.source.state.value.peopleCountsByAccount.isEmpty())
        h.close()
    }
}
