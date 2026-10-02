/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library

import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isCompleteForLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySnapshotTest {

    private fun person(
        id: String,
        configId: Long = 11L,
        type: ProviderType = ProviderType.IMMICH,
        thumb: String? = null
    ) = PersonInfo(
        id = id,
        name = "person-$id",
        providerType = type,
        serverConfigId = configId,
        thumbnailUrl = thumb,
        assetCount = 3
    )

    private fun localPerson(id: String, thumb: String? = null) = person(
        id = id,
        configId = LOCAL_PEOPLE_CONFIG_ID,
        type = ProviderType.LOCAL_PEOPLE,
        thumb = thumb
    )


    @Test
    fun restoredIndexResolvesSavedKey() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(
            2,
            restoredLibraryIndex(keys, LibraryScrollPosition(key = "c", index = 0, offset = 9))
        )
    }

    @Test
    fun restoredIndexFallsBackToClampedIndexWhenKeyMissing() {
        val keys = listOf("a", "b")
        assertEquals(
            1,
            restoredLibraryIndex(keys, LibraryScrollPosition(key = "gone", index = 7))
        )
        assertEquals(
            1,
            restoredLibraryIndex(keys, LibraryScrollPosition(index = 1))
        )
    }

    @Test
    fun restoredIndexClampsIntoEmptyList() {
        assertEquals(
            0,
            restoredLibraryIndex(emptyList(), LibraryScrollPosition(key = "k", index = 3))
        )
    }


    @Test
    fun previewWindowPassesShortListsThrough() {
        val items = (1..40).toList()
        val out = libraryPreviewWindow(items, LibraryScrollPosition()) { it.toString() }
        assertEquals(items, out)
    }

    @Test
    fun previewWindowAnchorsAroundSavedKey() {
        val items = (0..999).toList()
        val position = LibraryScrollPosition(key = "555", index = 555)
        val out = libraryPreviewWindow(items, position) { it.toString() }
        assertEquals(64, out.size)
        assertTrue(555 in out)
        assertEquals(539, out.first())
        assertEquals(602, out.last())
    }

    @Test
    fun previewWindowClampsAtStartAndEnd() {
        val items = (0..99).toList()
        val nearStart = libraryPreviewWindow(
            items, LibraryScrollPosition(key = "2", index = 2)
        ) { it.toString() }
        assertEquals(0, nearStart.first())
        assertEquals(63, nearStart.last())
        val nearEnd = libraryPreviewWindow(
            items, LibraryScrollPosition(key = "97", index = 97)
        ) { it.toString() }
        assertEquals(36, nearEnd.first())
        assertEquals(99, nearEnd.last())
    }

    @Test
    fun previewWindowFallsBackToIndexWhenKeyMissing() {
        val items = (0..199).toList()
        val out = libraryPreviewWindow(
            items, LibraryScrollPosition(key = "missing", index = 150)
        ) { it.toString() }
        assertEquals(64, out.size)
        assertTrue(150 in out)
    }


    @Test
    fun mergeKeepsUnmeasuredRowsAndTakesMeasuredRows() {
        val current = LibraryViewport(
            grid = LibraryScrollPosition("g", 5, 40),
            locations = LibraryScrollPosition("l", 3, 12),
            configuration = "cfg-a"
        )
        val incoming = LibraryViewport(
            grid = LibraryScrollPosition("g2", 9, 20),
            configuration = "cfg-a"
        )
        val merged = mergeLibraryViewport(current, incoming)
        assertEquals(LibraryScrollPosition("g2", 9, 20), merged.grid)
        assertEquals(LibraryScrollPosition("l", 3, 12), merged.locations)
        assertEquals("cfg-a", merged.configuration)
    }

    @Test
    fun mergeResetsRetainedOffsetsOnConfigurationChange() {
        val current = LibraryViewport(
            grid = LibraryScrollPosition("g", 5, 40),
            people = LibraryScrollPosition("p", 2, 33),
            configuration = "cfg-a"
        )
        val merged = mergeLibraryViewport(current, LibraryViewport(configuration = "cfg-b"))
        assertEquals(0, merged.grid.offset)
        assertEquals(5, merged.grid.index)
        assertEquals(0, merged.people.offset)
        assertEquals(2, merged.people.index)
        assertEquals("cfg-b", merged.configuration)
    }

    @Test
    fun mergeKeepsCurrentConfigurationWhenIncomingBlank() {
        val current = LibraryViewport(configuration = "cfg-a")
        val merged = mergeLibraryViewport(current, LibraryViewport())
        assertEquals("cfg-a", merged.configuration)
    }


    @Test
    fun completePredicateRejectsLoadingPartialAndErrorStates() {
        assertFalse(
            MediaState<com.dot.gallery.feature_node.domain.model.Media.UriMedia>()
                .isCompleteForLibrary()
        )
        assertFalse(
            MediaState<com.dot.gallery.feature_node.domain.model.Media.UriMedia>(
                isLoading = false, isPartial = true
            ).isCompleteForLibrary()
        )
        assertFalse(
            MediaState<com.dot.gallery.feature_node.domain.model.Media.UriMedia>(
                isLoading = false, error = "boom"
            ).isCompleteForLibrary()
        )
        assertTrue(
            MediaState<com.dot.gallery.feature_node.domain.model.Media.UriMedia>(
                isLoading = false
            ).isCompleteForLibrary()
        )
    }


    private val remote = person("p1", configId = 11L)

    @Test
    fun contentThumbnailRequiresAMediaStoreId() {
        assertTrue(
            isPersistableThumbnailUrl(
                "content://media/external/images/media/42", remote
            )
        )
        assertFalse(
            isPersistableThumbnailUrl(
                "content://com.other.provider/doc/1", remote
            )
        )
        assertFalse(
            isPersistableThumbnailUrl(
                "content://media/external/images/media/notanum", remote
            )
        )
    }

    @Test
    fun fileThumbnailMustLiveInsideTheFaceThumbRoot() {
        val root = "/data/app/files/face_thumbs"
        val local = localPerson("lp")
        assertTrue(
            isPersistableThumbnailUrl(
                "file://$root/ab/face.jpg", local, root
            )
        )
        assertFalse(isPersistableThumbnailUrl("file:///sdcard/a.jpg", local, root))
        assertFalse(
            isPersistableThumbnailUrl("file://$root/../other/x.jpg", local, root)
        )
        assertFalse(
            isPersistableThumbnailUrl("file://$root/a.jpg?q=1", local, root)
        )
        assertFalse(isPersistableThumbnailUrl("file://$root/a.jpg", local, null))
        assertFalse(isPersistableThumbnailUrl("file://$root/a.jpg", remote, root))
    }

    @Test
    fun cloudThumbnailRequiresMatchingAccountAndCleanQuery() {
        val url = "cloud://IMMICH/asset-1?cfg=11&size=thumbnail"
        assertTrue(isPersistableThumbnailUrl(url, remote))
        assertFalse(isPersistableThumbnailUrl("cloud://IMMICH/asset-1?cfg=12", remote))
        assertFalse(isPersistableThumbnailUrl("cloud://IMMICH/asset-1", remote))
        assertFalse(
            isPersistableThumbnailUrl("cloud://WEBDAV/asset-1?cfg=11", remote)
        )
        assertFalse(
            isPersistableThumbnailUrl("$url&token=abc", remote)
        )
        assertFalse(
            isPersistableThumbnailUrl(
                "cloud://IMMICH/asset-1?cfg=11&cfg=11", remote
            )
        )
        assertFalse(
            isPersistableThumbnailUrl(
                "cloud://IMMICH/asset-1?cfg=11&type=person&fileId=f1", localPerson("x")
            )
        )
    }

    @Test
    fun httpAndUnknownSchemesAreNeverPersisted() {
        assertFalse(isPersistableThumbnailUrl("https://example.com/t.jpg", remote))
        assertFalse(isPersistableThumbnailUrl("http://example.com/t.jpg", remote))
        assertFalse(isPersistableThumbnailUrl("ftp://x/t.jpg", remote))
        assertFalse(isPersistableThumbnailUrl("content://media/a/1#frag", remote))
    }


    @Test
    fun emptySnapshotIsValid() {
        assertTrue(validateLibrarySnapshot(LibrarySnapshot()))
    }

    @Test
    fun peopleOnlySnapshotValidates() {
        val snapshot = LibrarySnapshot(
            peopleCount = 7,
            peopleCountsByAccount = mapOf(11L to 5, LOCAL_PEOPLE_CONFIG_ID to 2),
            sharedLinkCountsByAccount = mapOf(11L to 3),
            cloud = CloudLibraryState(
                people = listOf(person("a"), localPerson("b")),
                sharedLinkCount = 3
            ),
            indicators = LibraryIndicatorState(trashCount = 2, favoriteCount = 1),
            viewport = LibraryViewport(
                grid = LibraryScrollPosition("k", 4, 12)
            )
        )
        assertTrue(validateLibrarySnapshot(snapshot))
    }

    @Test
    fun negativeCountsAreRejected() {
        assertFalse(
            validateLibrarySnapshot(LibrarySnapshot(peopleCount = -1))
        )
        assertFalse(
            validateLibrarySnapshot(LibrarySnapshot(locationCount = -1))
        )
        assertFalse(
            validateLibrarySnapshot(LibrarySnapshot(categoryCount = -1))
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    indicators = LibraryIndicatorState(trashCount = -1)
                )
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    indicators = LibraryIndicatorState(favoriteCount = -1)
                )
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(peopleCountsByAccount = mapOf(11L to -2))
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(sharedLinkCountsByAccount = mapOf(11L to -1))
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(cloud = CloudLibraryState(archivedCount = -1))
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(cloud = CloudLibraryState(hiddenPeopleCount = -1))
            )
        )
    }

    @Test
    fun rowCountsCannotBeBelowStoredListSizes() {
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    peopleCount = 1,
                    cloud = CloudLibraryState(
                        people = listOf(person("a"), person("b"))
                    )
                )
            )
        )
    }

    @Test
    fun persistedConnectivityIsRejected() {
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(cloud = CloudLibraryState(isConnected = true))
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    cloud = CloudLibraryState(
                        connectedCapabilities = setOf(ProviderCapability.PEOPLE)
                    )
                )
            )
        )
    }

    @Test
    fun oversizedOrDuplicatePeopleListsAreRejected() {
        val many = (0 until MAX_CACHED_LIBRARY_PEOPLE + 1).map { person("p$it") }
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(peopleCount = many.size, cloud = CloudLibraryState(people = many))
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    peopleCount = 2,
                    cloud = CloudLibraryState(people = listOf(person("a"), person("a")))
                )
            )
        )
    }

    @Test
    fun personIdentityMustMatchItsAccountPartition() {
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    peopleCount = 1,
                    cloud = CloudLibraryState(
                        people = listOf(
                            person("a", configId = LOCAL_PEOPLE_CONFIG_ID)
                        )
                    )
                )
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    peopleCount = 1,
                    cloud = CloudLibraryState(
                        people = listOf(
                            PersonInfo(
                                id = "a", name = "a",
                                providerType = ProviderType.LOCAL_PEOPLE,
                                serverConfigId = 11L
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun unsafeThumbnailUrlFailsValidation() {
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    peopleCount = 1,
                    cloud = CloudLibraryState(
                        people = listOf(person("a", thumb = "https://x/t.jpg"))
                    )
                )
            )
        )
    }

    @Test
    fun cachedInsetsBridgeOnlyUnsettledMatchingWindowGeometry() {
        assertEquals(156, initialLibraryInset(0, 156, true, null))
        assertEquals(156, initialLibraryInset(0, 156, true, true))
        assertEquals(180, initialLibraryInset(180, 156, true, true))
        assertEquals(0, initialLibraryInset(0, 156, true, false))
        assertEquals(0, initialLibraryInset(0, 156, false, null))
        assertEquals(0, initialLibraryInset(0, null, true, null))
    }

    @Test
    fun viewportGeometrySurvivesPartialUpdatesButNotConfigurationChanges() {
        val current = LibraryViewport(
            configuration = "portrait", statusBarTop = 156, navigationBarBottom = 72
        )
        val unchanged = mergeLibraryViewport(current, LibraryViewport(grid = LibraryScrollPosition("g")))
        assertEquals(156, unchanged.statusBarTop)
        assertEquals(72, unchanged.navigationBarBottom)
        val rotated = mergeLibraryViewport(current, LibraryViewport(configuration = "landscape"))
        assertNull(rotated.statusBarTop)
        assertNull(rotated.navigationBarBottom)
        val hidden = mergeLibraryViewport(current, LibraryViewport(statusBarTop = 0, navigationBarBottom = 0))
        assertEquals(0, hidden.statusBarTop)
        assertEquals(0, hidden.navigationBarBottom)
        assertFalse(validateLibrarySnapshot(LibrarySnapshot(viewport = current.copy(statusBarTop = -1))))
    }

    @Test
    fun viewportPositionsAreBoundsChecked() {
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    viewport = LibraryViewport(grid = LibraryScrollPosition(index = -1))
                )
            )
        )
        assertFalse(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    viewport = LibraryViewport(
                        people = LibraryScrollPosition(
                            index = 0,
                            offset = MAX_CACHED_SCROLL_OFFSET + 1
                        )
                    )
                )
            )
        )
        assertTrue(
            validateLibrarySnapshot(
                LibrarySnapshot(
                    viewport = LibraryViewport(
                        categories = LibraryScrollPosition(
                            "c", 3, MAX_CACHED_SCROLL_OFFSET
                        )
                    )
                )
            )
        )
    }
}
