/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import com.dot.gallery.docs.scenes.DocsAlbumsScene
import com.dot.gallery.docs.scenes.DocsCollectionGridScene
import com.dot.gallery.docs.scenes.DocsFavoritesScene
import com.dot.gallery.docs.scenes.DocsHelpScene
import com.dot.gallery.docs.scenes.DocsLibraryScene
import com.dot.gallery.docs.scenes.DocsNavigationBarScene
import com.dot.gallery.docs.scenes.DocsSearchScene
import com.dot.gallery.docs.scenes.DocsTimelineFiltersScene
import com.dot.gallery.docs.scenes.DocsTimelineScene

/** Browse surfaces: timeline, albums, library, navigation and search. */
val browseScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "browse-timeline",
        title = "Photo timeline",
        pathTrace = listOf("Timeline"),
        states = listOf("grid", "mosaic", "selected"),
        handlesContentPadding = true,
    ) { state -> DocsTimelineScene(state) },
    DocsScene(
        id = "browse-albums",
        title = "Albums",
        pathTrace = listOf("Albums"),
        states = listOf("folders", "pinned", "collections"),
        handlesContentPadding = true,
    ) { state -> DocsAlbumsScene(state) },
    DocsScene(
        id = "browse-library",
        title = "Library",
        pathTrace = listOf("Library"),
        states = listOf("overview", "editing"),
        handlesContentPadding = true,
    ) { state -> DocsLibraryScene(state) },
    DocsScene(
        id = "navigation-bar",
        title = "Main navigation bar",
        pathTrace = listOf("Home", "Bottom bar"),
        states = listOf("floating", "classic"),
        presentation = "component",
        widthDp = 412,
        heightDp = 128,
    ) { state -> DocsNavigationBarScene(state) },
    DocsScene(
        id = "timeline-filters",
        title = "Timeline sort and filter",
        pathTrace = listOf("Timeline", "Sort and filter"),
        states = listOf("open"),
        captureWindow = true,
    ) { state -> DocsTimelineFiltersScene(state) },
    DocsScene(
        id = "browse-favorites",
        title = "Favorites",
        pathTrace = listOf("Library", "Favorites"),
        states = listOf("filled", "scrolled", "empty"),
    ) { state -> DocsFavoritesScene(state) },
    DocsScene(
        id = "collection-grid",
        title = "Collection",
        pathTrace = listOf("Albums", "Collections", "Holiday 2025"),
        states = listOf("filled", "scrolled", "empty"),
    ) { state -> DocsCollectionGridScene(state) },
    DocsScene(
        id = "search-library",
        title = "Search",
        pathTrace = listOf("Timeline", "Search"),
        states = listOf("suggestions", "results", "empty"),
    ) { state -> DocsSearchScene(state) },
    DocsScene(
        id = "help-browser",
        title = "In-app help",
        pathTrace = listOf("Settings", "Help"),
        states = listOf("default", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { DocsHelpScene(it) },
)
