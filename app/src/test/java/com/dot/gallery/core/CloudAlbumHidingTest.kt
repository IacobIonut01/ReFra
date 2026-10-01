/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for hiding cloud albums from the unified timeline.
 *
 * Regression guard for the bug where hiding an SMB/cloud album recorded the ignore entry
 * (and removed the album tile) but its media stayed on the timeline: cloud media carries the
 * constant albumID -500, so IgnoredAlbum's id match can never hit it. The timeline filter
 * resolves hidden cloud albums through the per-album member sets instead.
 */
class CloudAlbumHidingTest {

    private fun album(
        remoteId: String,
        type: ProviderType = ProviderType.SMB,
        configId: Long = 3L,
        thumbnailAssetId: String? = "cover.jpg"
    ) = CloudAlbum(
        remoteId = remoteId,
        providerType = type,
        serverConfigId = configId,
        name = remoteId.substringAfterLast('/'),
        assetCount = 2,
        thumbnailAssetId = thumbnailAssetId
    )

    private fun member(type: ProviderType, configId: Long, remoteId: String) =
        CloudAlbumMemberId(type, configId, remoteId)

    private fun membersOf(
        type: ProviderType,
        configId: Long,
        albumRemoteId: String,
        vararg mediaRemoteIds: String
    ): Pair<CloudAlbumMemberId, Set<CloudAlbumMemberId>> =
        member(type, configId, albumRemoteId) to
            mediaRemoteIds.mapTo(HashSet()) { member(type, configId, it) }

    @Test
    fun cloudMemberKeyParsesSlashedRemoteIdAndConfig() {
        val key = cloudMemberKey("cloud://SMB/Download/sub/img.jpg?size=preview&cfg=3")!!
        assertEquals(CloudAlbumMemberId(ProviderType.SMB, 3L, "Download/sub/img.jpg"), key)
    }

    @Test
    fun cloudMemberKeyRejectsNonCloudAndMissingAccount() {
        assertNull(cloudMemberKey("content://media/external/images/1"))
        // No cfg param: no account context, can never be album-matched.
        assertNull(cloudMemberKey("cloud://SMB/Download/img.jpg?size=preview"))
    }

    @Test
    fun unsortedAlbumIdRoundTripsToProvider() {
        val id = unsortedCloudAlbumId(ProviderType.SMB)
        assertEquals(ProviderType.SMB, unsortedAlbumProviderType(id))
        assertNull(unsortedAlbumProviderType(42L))
    }

    @Test
    fun hidesMembersOfAlbumMatchedById() {
        val download = album("Download")
        val memberKeys = mapOf(
            membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg", "Download/sub/b.jpg"),
            membersOf(ProviderType.SMB, 3L, "Camera", "Camera/c.jpg")
        )
        val hidden = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.SMB, 3L, "Download"),
                    location = IgnoredAlbum.ALBUMS_AND_TIMELINE
                )
            ),
            cloudAlbums = listOf(download, album("Camera")),
            membersByAlbum = memberKeys
        )!!

        assertTrue(hidden(member(ProviderType.SMB, 3L, "Download/a.jpg")))
        assertTrue(hidden(member(ProviderType.SMB, 3L, "Download/sub/b.jpg")))
        assertFalse(hidden(member(ProviderType.SMB, 3L, "Camera/c.jpg")))
    }

    @Test
    fun albumOnlyLocationDoesNotHideFromTimeline() {
        val predicate = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.SMB, 3L, "Download"),
                    location = IgnoredAlbum.ALBUMS_ONLY
                )
            ),
            cloudAlbums = listOf(album("Download")),
            membersByAlbum = mapOf(membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"))
        )
        assertNull(predicate)
    }

    @Test
    fun timelineOnlyLocationHidesMembers() {
        val predicate = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.SMB, 3L, "Download"),
                    location = IgnoredAlbum.TIMELINE_ONLY
                )
            ),
            cloudAlbums = listOf(album("Download")),
            membersByAlbum = mapOf(membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"))
        )!!
        assertTrue(predicate(member(ProviderType.SMB, 3L, "Download/a.jpg")))
    }

    @Test
    fun sameAlbumRemoteIdOnTwoAccountsOnlyHidesTheHiddenOne() {
        // The account id is part of the album-id hash, so "Download" on cfg=3 and cfg=7
        // are different albums — hiding one must not touch the other.
        val members = mapOf(
            membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"),
            membersOf(ProviderType.SMB, 7L, "Download", "Download/a.jpg")
        )
        val hidden = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.SMB, 3L, "Download"),
                    location = IgnoredAlbum.ALBUMS_AND_TIMELINE
                )
            ),
            cloudAlbums = listOf(album("Download", configId = 3L), album("Download", configId = 7L)),
            membersByAlbum = members
        )!!
        assertTrue(hidden(member(ProviderType.SMB, 3L, "Download/a.jpg")))
        assertFalse(hidden(member(ProviderType.SMB, 7L, "Download/a.jpg")))
    }

    @Test
    fun albumIdsEntryAndWildcardAlsoMatch() {
        val albums = listOf(album("Download"), album("Camera"))
        val members = mapOf(
            membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"),
            membersOf(ProviderType.SMB, 3L, "Camera", "Camera/c.jpg")
        )
        // Combined entry listing the album under albumIds.
        val viaIds = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = 1L,
                    albumIds = listOf(cloudAlbumId(ProviderType.SMB, 3L, "Download")),
                    location = IgnoredAlbum.TIMELINE_ONLY
                )
            ),
            cloudAlbums = albums,
            membersByAlbum = members
        )!!
        assertTrue(viaIds(member(ProviderType.SMB, 3L, "Download/a.jpg")))
        assertFalse(viaIds(member(ProviderType.SMB, 3L, "Camera/c.jpg")))

        // Wildcard matching the cloud album's synthetic relativePath ("cloud/SMB").
        val viaWildcard = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(id = 2L, wildcard = "cloud/SMB", location = IgnoredAlbum.TIMELINE_ONLY)
            ),
            cloudAlbums = albums,
            membersByAlbum = members
        )!!
        assertTrue(viaWildcard(member(ProviderType.SMB, 3L, "Download/a.jpg")))
    }

    @Test
    fun hiddenUnsortedAlbumHidesOnlyNonMemberMediaOfThatProvider() {
        val members = mapOf(
            membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"),
            membersOf(ProviderType.IMMICH, 5L, "album-1", "asset-9")
        )
        val hidden = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = unsortedCloudAlbumId(ProviderType.SMB),
                    location = IgnoredAlbum.ALBUMS_AND_TIMELINE
                )
            ),
            cloudAlbums = listOf(album("Download")),
            membersByAlbum = members
        )!!
        // SMB media in an album stays; SMB media in no album is hidden.
        assertFalse(hidden(member(ProviderType.SMB, 3L, "Download/a.jpg")))
        assertTrue(hidden(member(ProviderType.SMB, 3L, "root-file.jpg")))
        // Other providers are untouched, even their unsorted media.
        assertFalse(hidden(member(ProviderType.IMMICH, 5L, "asset-9")))
        assertFalse(hidden(member(ProviderType.IMMICH, 5L, "orphan-asset")))
    }

    @Test
    fun mediaInHiddenAndVisibleAlbumIsStillHidden() {
        // Immich-style N:N membership: hiding one album removes the item even when it is
        // also a member of a visible album.
        val members = mapOf(
            membersOf(ProviderType.IMMICH, 5L, "hidden-album", "asset-1"),
            membersOf(ProviderType.IMMICH, 5L, "visible-album", "asset-1")
        )
        val hidden = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.IMMICH, 5L, "hidden-album"),
                    location = IgnoredAlbum.TIMELINE_ONLY
                )
            ),
            cloudAlbums = listOf(album("hidden-album", ProviderType.IMMICH, 5L), album("visible-album", ProviderType.IMMICH, 5L)),
            membersByAlbum = members
        )!!
        assertTrue(hidden(member(ProviderType.IMMICH, 5L, "asset-1")))
    }

    @Test
    fun nothingHiddenYieldsNullPredicate() {
        assertNull(
            hiddenCloudMediaPredicate(
                blacklistedAlbums = emptyList(),
                cloudAlbums = listOf(album("Download")),
                membersByAlbum = mapOf(membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"))
            )
        )
        assertNull(
            hiddenCloudMediaPredicate(
                blacklistedAlbums = listOf(
                    IgnoredAlbum(id = 123L, location = IgnoredAlbum.ALBUMS_AND_TIMELINE)
                ),
                cloudAlbums = listOf(album("Download")),
                membersByAlbum = mapOf(membersOf(ProviderType.SMB, 3L, "Download", "Download/a.jpg"))
            )
        )
    }

    @Test
    fun trashedMembersAreExcludedFromLiveAlbumListing() {
        // Immich's album endpoint returns trashed assets as members — the live
        // membership branch must drop them or trashed items reappear in albums.
        val entity = { id: String, trashed: Boolean ->
            CloudMediaEntity(
                remoteId = id,
                providerType = ProviderType.IMMICH,
                serverConfigId = 5L,
                trashed = trashed
            )
        }
        val kept = listOf(
            entity("live-1", false),
            entity("trashed-1", true),
            entity("live-2", false)
        ).excludingTrashedAlbumMembers()

        assertEquals(listOf("live-1", "live-2"), kept.map { it.remoteId })
    }

    @Test
    fun hiddenAlbumWithFailedMemberFetchHidesNothing() {
        val predicate = hiddenCloudMediaPredicate(
            blacklistedAlbums = listOf(
                IgnoredAlbum(
                    id = cloudAlbumId(ProviderType.SMB, 3L, "Download"),
                    location = IgnoredAlbum.TIMELINE_ONLY
                )
            ),
            cloudAlbums = listOf(album("Download")),
            membersByAlbum = emptyMap() // album fetch failed — no membership known
        )
        assertNull(predicate)
    }
}
