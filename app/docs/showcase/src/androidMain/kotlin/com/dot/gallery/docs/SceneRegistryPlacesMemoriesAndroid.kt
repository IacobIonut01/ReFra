/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.runtime.Composable
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.docs.scenes.DocsSharedScopes
import com.dot.gallery.feature.classifier.ui.CategoriesActions
import com.dot.gallery.feature.classifier.ui.CategoriesContent
import com.dot.gallery.feature.classifier.ui.CategoriesState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.dot.gallery.app.presentation.location.ListLocationsContent
import com.dot.gallery.cloud.ui.memories.MemoriesPageContent
import com.dot.gallery.cloud.ui.memories.MemoryEntry
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import java.time.LocalDate

/**
 * The Library's Places list, Memories page and Categories page: the real
 * [ListLocationsContent], [MemoriesPageContent] and [CategoriesContent] over the bundled docs
 * photos, placed at example locations, dates and categories.
 */
internal val placesMemoriesScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "places-list",
        title = "Places",
        pathTrace = listOf("Library", "Locations", "Places"),
        settleMillis = 1000L,
    ) { DocsPlacesScene() },
    DocsScene(
        id = "memories-page",
        title = "Memories",
        pathTrace = listOf("Library", "Memories"),
        states = listOf("memories", "empty"),
        settleMillis = 1000L,
    ) { state -> DocsMemoriesScene(state) },
    DocsScene(
        id = "categories-browse",
        title = "Categories",
        pathTrace = listOf("Library", "Categories"),
        states = listOf("sorted", "sorting", "empty"),
        settleMillis = 1000L,
    ) { state -> DocsCategoriesScene(state) },
)

/** Example places for the docs photos; the coordinates are illustrative. */
private data class DocsPlace(val city: String, val country: String, val lat: Double, val lon: Double)

private val docsPlaces = listOf(
    DocsPlace("Moraine Lake", "Canada", 51.3217, -116.1860),
    DocsPlace("Moraine Lake", "Canada", 51.3224, -116.1852),
    DocsPlace("Kyoto", "Japan", 35.0116, 135.7681),
    DocsPlace("Lisbon", "Portugal", 38.7223, -9.1393),
)

@Composable
private fun DocsPlacesScene() {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val detail = rememberDocsMediaStoreImage(
        "drawable/docs_flower_detail.jpg", "Docs_flower_detail.jpg", ageDays = 1L,
    )
    val photos = listOf(lake, earth, flower, detail)
    val geoMedia = photos.mapIndexed { index, media ->
        val place = docsPlaces[index]
        GeoMedia(
            mediaId = media.id,
            latitude = place.lat,
            longitude = place.lon,
            locationCity = place.city,
            locationCountry = place.country,
            media = media,
        )
    }
    val locations = photos.mapIndexed { index, media ->
        val place = docsPlaces[index]
        LocationMedia(
            media = media,
            location = "${place.city}, ${place.country}",
            city = place.city,
            country = place.country,
        )
    }.distinctBy { it.location }
    DocsViewerProviders(photos) {
        ListLocationsContent(
            metadataState = remember { mutableStateOf(MediaMetadataState()) },
            locations = locations,
            geoMedia = geoMedia,
            onShowMap = {},
        )
    }
}

@Composable
private fun DocsMemoriesScene(state: String?) {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val detail = rememberDocsMediaStoreImage(
        "drawable/docs_flower_detail.jpg", "Docs_flower_detail.jpg", ageDays = 1L,
    )
    val photos = listOf(lake, earth, flower, detail)
    val today = remember { LocalDate.now() }
    val memories = if (state == "empty") emptyList() else listOf(
        MemoryEntry(
            key = "docs-on-this-day-2",
            isLocal = true,
            onThisDay = true,
            year = today.year - 2,
            assetCount = 3,
            media = listOf(lake, flower, detail),
            typeLabel = "",
            date = today.minusYears(2),
        ),
        MemoryEntry(
            key = "docs-on-this-day-5",
            isLocal = true,
            onThisDay = true,
            year = today.year - 5,
            assetCount = 2,
            media = listOf(earth, lake),
            typeLabel = "",
            date = today.minusYears(5),
        ),
        MemoryEntry(
            key = "docs-earlier-3",
            isLocal = true,
            onThisDay = false,
            year = today.year - 3,
            assetCount = 4,
            media = listOf(flower, detail, lake, earth),
            typeLabel = "",
            date = null,
        ),
        MemoryEntry(
            key = "docs-earlier-6",
            isLocal = true,
            onThisDay = false,
            year = today.year - 6,
            assetCount = 2,
            media = listOf(earth, flower),
            typeLabel = "",
            date = null,
        ),
    )
    DocsViewerProviders(photos) {
        MemoriesPageContent(
            memories = memories,
            isLoading = false,
            error = null,
            onOpenMemory = {},
            today = today,
        )
    }
}

@Composable
private fun DocsCategoriesScene(state: String?) {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val detail = rememberDocsMediaStoreImage(
        "drawable/docs_flower_detail.jpg", "Docs_flower_detail.jpg", ageDays = 1L,
    )
    val photos = listOf(lake, earth, flower, detail)
    fun category(id: Long, name: String, count: Int, cover: Long?, pinned: Boolean = false, mine: Boolean = false) =
        CategoryWithMediaCount(
            id = id,
            name = name,
            searchTerms = name.lowercase(),
            embedding = null,
            referenceImageIds = emptyList(),
            threshold = 0.25f,
            isUserCreated = mine,
            isPinned = pinned,
            createdAt = 0L,
            updatedAt = 0L,
            mediaCount = count,
            thumbnailMediaId = cover,
        )
    val categories = if (state == "empty") emptyList() else listOf(
        category(1L, "Mountains", 2, lake.id, pinned = true),
        category(2L, "Flowers", 2, flower.id, pinned = true, mine = true),
        category(3L, "Space", 1, earth.id),
        category(4L, "Close-ups", 1, detail.id, mine = true),
        category(5L, "Receipts", 0, null, mine = true),
    )
    val sorting = state == "sorting"
    DocsViewerProviders(photos) {
        DocsSharedScopes { sts, acs ->
            CategoriesContent(
                state = CategoriesState(
                    categoriesWithCount = categories,
                    distinctMediaCount = if (categories.isEmpty()) 0 else photos.size,
                    mediaState = DocsGalleryData.mediaStateFor(photos),
                    isScanning = sorting,
                    scanProgress = if (sorting) 0.4f else null,
                    scanProcessed = if (sorting) 2 else 0,
                    scanTotal = if (sorting) 4 else 0,
                    searchModelStatus = ModelStatus.READY,
                ),
                actions = CategoriesActions(
                    onOpenCategory = {},
                    onEditCategory = {},
                    onCreateCategory = {},
                    onOpenSettings = {},
                ),
                lastCellIndex = remember { mutableStateOf(1) },
                sharedTransitionScope = sts,
                animatedContentScope = acs,
            )
        }
    }
}
