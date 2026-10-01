/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget.data

/**
 * Widget tap deep-link helpers. Pure Kotlin (no android.net.Uri / Media
 * parameters) so the pairing and fallback logic run in plain JVM tests.
 *
 * A widget tap opens the picked photo by delivering [EXTRA_WIDGET_MEDIA_ID]
 * to MainActivity, which routes it into the media viewer. Only media that
 * lives in the unified timeline (local MediaStore + cached cloud rows) can be
 * targeted — vault and private-folder picks carry no id and fall back to a
 * plain app open.
 */
object WidgetDeepLink {

    const val EXTRA_WIDGET_MEDIA_ID = "com.dot.gallery.extra.WIDGET_MEDIA_ID"

    /**
     * Pairs the materialized share URIs back to the picked media.
     *
     * `materializeToShareableUris` preserves order but drops failed items, so
     * `materialized.size` can be smaller than `selected.size`. When the sizes
     * are equal the index pairing is authoritative; otherwise each
     * materialized URI is matched by string equality against the media's own
     * uri (local/private picks pass through [resolveShareableUri] untouched;
     * vault/cloud picks become FileProvider temp URIs that match nothing).
     *
     * `selected[i]` is `(media.getUri().toString(), media.id, timelineEligible)`
     * where `timelineEligible` is `media.isLocalContent || media.isCloud`.
     */
    fun pairDeepLinkIds(
        materialized: List<String>,
        selected: List<Triple<String, Long, Boolean>>,
    ): List<Long?> {
        if (materialized.isEmpty()) return emptyList()
        val aligned = materialized.size == selected.size
        return materialized.mapIndexed { index, uri ->
            val match = when {
                aligned -> selected.getOrNull(index)
                else -> selected.firstOrNull { it.first == uri }
            }
            match?.takeIf { it.third }?.second
        }
    }

    /**
     * MediaStore row id parsed from a `content://media/…/<id>` string, else
     * null. Legacy fallback for widgets configured before [WidgetData.mediaIds]
     * existed — SAF, FileProvider and `cloud://` URIs deliberately return null
     * so a stale or foreign id can never open the wrong photo.
     */
    fun mediaStoreIdOrNull(uriString: String): Long? =
        uriString.takeIf { it.startsWith("content://media") }
            ?.substringAfterLast('/')
            ?.toLongOrNull()

    /**
     * The deep-link id for widget cell [index]: the stored id wins, the stored
     * `content://media` URI parse is the fallback. Null means "plain app open".
     */
    fun resolveDeepLinkId(data: WidgetData?, index: Int): Long? =
        data?.mediaIds?.getOrNull(index)
            ?: data?.mediaUris?.getOrNull(index)?.let(::mediaStoreIdOrNull)
}
