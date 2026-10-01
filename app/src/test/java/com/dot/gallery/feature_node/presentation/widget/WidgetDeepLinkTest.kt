/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.widget

import com.dot.gallery.feature_node.presentation.widget.data.WidgetData
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDeepLink
import com.dot.gallery.feature_node.presentation.widget.data.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDeepLinkTest {

    private fun widgetData(
        uris: List<String>,
        ids: List<Long?> = emptyList(),
    ) = WidgetData(widgetId = 1, type = WidgetType.SINGLE, mediaUris = uris, mediaIds = ids)

    // ── pairDeepLinkIds ──

    @Test
    fun pairDeepLinkIdsAlignsByIndexWhenSizesMatch() {
        val ids = WidgetDeepLink.pairDeepLinkIds(
            materialized = listOf(
                "content://media/external/images/media/10",
                "content://media/external/images/media/20",
            ),
            selected = listOf(
                Triple("content://media/external/images/media/10", 10L, true),
                Triple("content://media/external/images/media/20", 20L, true),
            ),
        )
        assertEquals(listOf(10L, 20L), ids)
    }

    @Test
    fun pairDeepLinkIdsFallsBackToUriMatchWhenMaterializationDroppedItems() {
        // Selected three items but the first failed to materialize — the URI
        // list is shorter, so index pairing would misalign every id.
        val ids = WidgetDeepLink.pairDeepLinkIds(
            materialized = listOf(
                "content://media/external/images/media/20",
                "content://media/external/images/media/30",
            ),
            selected = listOf(
                Triple("content://media/external/images/media/10", 10L, true),
                Triple("content://media/external/images/media/20", 20L, true),
                Triple("content://media/external/images/media/30", 30L, true),
            ),
        )
        assertEquals(listOf(20L, 30L), ids)
    }

    @Test
    fun pairDeepLinkIdsDropsIneligibleMedia() {
        // Vault (file://) and private-folder (SAF) picks are not in the
        // unified timeline — they must produce no deep-link id.
        val ids = WidgetDeepLink.pairDeepLinkIds(
            materialized = listOf(
                "content://com.dot.gallery.provider/share/vault_tmp.jpg",
                "content://com.android.providers.downloads.documents/document/42",
                "content://media/external/images/media/10",
            ),
            selected = listOf(
                Triple("file:///data/vault/enc_1", 55L, false),
                Triple("content://com.android.providers.downloads.documents/document/42", 4611686018427387905L, false),
                Triple("content://media/external/images/media/10", 10L, true),
            ),
        )
        assertEquals(listOf(null, null, 10L), ids)
    }

    @Test
    fun pairDeepLinkIdsUnmatchedUriYieldsNull() {
        val ids = WidgetDeepLink.pairDeepLinkIds(
            materialized = listOf("content://com.dot.gallery.provider/share/cloud_tmp.jpg"),
            selected = listOf(
                Triple("cloud://immich/9?cfg=1", -17L, true),
                Triple("content://media/external/images/media/10", 10L, true),
            ),
        )
        assertEquals(listOf(null), ids)
    }

    @Test
    fun pairDeepLinkIdsEmptyMaterializedYieldsEmpty() {
        assertEquals(
            emptyList<Long?>(),
            WidgetDeepLink.pairDeepLinkIds(
                materialized = emptyList(),
                selected = listOf(Triple("content://media/external/images/media/10", 10L, true)),
            )
        )
    }

    // ── mediaStoreIdOrNull ──

    @Test
    fun mediaStoreIdOrNullParsesMediaStoreUri() {
        assertEquals(
            10937L,
            WidgetDeepLink.mediaStoreIdOrNull("content://media/external/images/media/10937")
        )
        assertEquals(
            12L,
            WidgetDeepLink.mediaStoreIdOrNull("content://media/external_primary/images/media/12")
        )
    }

    @Test
    fun mediaStoreIdOrNullRejectsNonMediaUris() {
        assertNull(WidgetDeepLink.mediaStoreIdOrNull("content://com.dot.gallery.provider/share/x.jpg"))
        assertNull(WidgetDeepLink.mediaStoreIdOrNull("content://com.android.providers.downloads.documents/document/42"))
        assertNull(WidgetDeepLink.mediaStoreIdOrNull("cloud://immich/9?cfg=1"))
        assertNull(WidgetDeepLink.mediaStoreIdOrNull("file:///data/vault/enc_1"))
        assertNull(WidgetDeepLink.mediaStoreIdOrNull("content://media/not-a-number"))
    }

    // ── resolveDeepLinkId ──

    @Test
    fun resolveDeepLinkIdPrefersStoredId() {
        val data = widgetData(
            uris = listOf("content://media/external/images/media/10"),
            ids = listOf(42L),
        )
        assertEquals(42L, WidgetDeepLink.resolveDeepLinkId(data, 0))
    }

    @Test
    fun resolveDeepLinkIdFallsBackToUriParseForLegacyWidgets() {
        // Widget configured before mediaIds existed — stored JSON has no ids.
        val data = widgetData(uris = listOf("content://media/external/images/media/10"))
        assertEquals(10L, WidgetDeepLink.resolveDeepLinkId(data, 0))
    }

    @Test
    fun resolveDeepLinkIdReturnsNullForUnresolvableAndMissing() {
        val unresolvable = widgetData(
            uris = listOf("content://com.dot.gallery.provider/share/x.jpg"),
            ids = listOf(null),
        )
        assertNull(WidgetDeepLink.resolveDeepLinkId(unresolvable, 0))
        assertNull(WidgetDeepLink.resolveDeepLinkId(unresolvable, 7))
        assertNull(WidgetDeepLink.resolveDeepLinkId(null, 0))
    }

    @Test
    fun resolveDeepLinkIdNullStoredEntryFallsBackToUri() {
        val data = widgetData(
            uris = listOf("content://media/external/images/media/77"),
            ids = listOf(null),
        )
        assertEquals(77L, WidgetDeepLink.resolveDeepLinkId(data, 0))
    }
}
