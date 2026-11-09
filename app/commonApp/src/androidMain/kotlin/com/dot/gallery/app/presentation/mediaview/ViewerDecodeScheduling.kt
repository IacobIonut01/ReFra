/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

/**
 * How many pages either side of the settled page get their low-res preview warmed. Previews of
 * local photos come from the platform thumbnail cache, so this is a few cheap reads that make a
 * swipe land on an image instead of an empty page (#1385).
 */
const val NEIGHBOUR_PREVIEW_RADIUS = 2

/**
 * Longest the HDR gain-map probe waits for the current page's full image before decoding anyway.
 * The probe is a second full decode; started together with the visible one, both share the
 * hardware decoder and the photo sharpens later. The cap keeps a failed or very slow load from
 * leaving the window in the wrong HDR mode.
 */
const val HDR_PROBE_MAX_WAIT_MS = 1_500L

/**
 * Pages to prefetch around [page], nearest first and forward before backward at equal distance
 * (the usual swipe direction), clipped to `0 until count`. [page] itself is excluded: the viewer
 * already requests it.
 */
fun neighbourPrefetchOrder(page: Int, count: Int, radius: Int = NEIGHBOUR_PREVIEW_RADIUS): List<Int> {
    if (count <= 0 || page !in 0 until count) return emptyList()
    return buildList {
        for (distance in 1..radius) {
            (page + distance).takeIf { it < count }?.let(::add)
            (page - distance).takeIf { it >= 0 }?.let(::add)
        }
    }
}
