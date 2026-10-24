/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import com.dot.gallery.domain.date.DateGrouper
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.domain.date.isRelativeDateLabel
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.TimelineDateSource
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.uri.parseUri

/**
 * Deterministic in-memory media library for docs previews. Every row resolves
 * to a bundled photograph ([com.dot.gallery.docs.DocsMediaImageRenderer]) —
 * see ASSETS.md for the licensed sources. Timestamps anchor to
 * [DOCS_DEMO_EPOCH_SECONDS] so captured frames do not drift between runs.
 */
object DocsGalleryData {

    private const val DAY = 86_400L

    const val ALBUM_CAMERA = 1L
    const val ALBUM_SCREENSHOTS = 2L
    const val ALBUM_DOWNLOAD = 3L
    const val COLLECTION_TRIP = 90L

    /** Drawables bundled under `composeResources/drawable/`. */
    const val DRAWABLE_LAKE = "lake"
    const val DRAWABLE_EARTH = "earth"
    const val DRAWABLE_FLOWER = "flower"
    const val DRAWABLE_FLOWER_DETAIL = "flower_detail"

    private data class Row(
        val id: Long,
        val label: String,
        val albumID: Long,
        val albumLabel: String,
        val ageDays: Long,
        val favorite: Boolean,
        val drawable: String,
        val sizeKb: Long,
    )

    private val rows = listOf(
        Row(1, "Lake_01.jpg", ALBUM_CAMERA, "Camera", 0, true, DRAWABLE_LAKE, 2_480),
        Row(2, "Lake_02.jpg", ALBUM_CAMERA, "Camera", 0, false, DRAWABLE_LAKE, 2_310),
        Row(3, "Flower_01.jpg", ALBUM_CAMERA, "Camera", 0, true, DRAWABLE_FLOWER, 1_920),
        Row(4, "Flower_02.jpg", ALBUM_CAMERA, "Camera", 1, false, DRAWABLE_FLOWER_DETAIL, 1_640),
        Row(5, "Earth_01.jpg", ALBUM_CAMERA, "Camera", 2, false, DRAWABLE_EARTH, 3_220),
        Row(6, "Flower_BURST001.jpg", ALBUM_CAMERA, "Camera", 3, false, DRAWABLE_FLOWER, 1_880),
        Row(7, "Flower_BURST002.jpg", ALBUM_CAMERA, "Camera", 3, false, DRAWABLE_FLOWER_DETAIL, 1_860),
        Row(8, "Saved_lake.png", ALBUM_SCREENSHOTS, "Screenshots", 1, false, DRAWABLE_LAKE, 640),
        Row(9, "Saved_flower.png", ALBUM_SCREENSHOTS, "Screenshots", 2, false, DRAWABLE_FLOWER, 610),
        Row(10, "Lake_wallpaper.jpg", ALBUM_DOWNLOAD, "Download", 7, true, DRAWABLE_LAKE, 2_150),
        Row(11, "Earth_wallpaper.jpg", ALBUM_DOWNLOAD, "Download", 30, false, DRAWABLE_EARTH, 2_940),
        Row(12, "Flower_wallpaper.jpg", ALBUM_DOWNLOAD, "Download", 365, false, DRAWABLE_FLOWER_DETAIL, 1_780),
    )

    /** Media rows keyed by label so the image renderer can map uri/path → drawable. */
    val drawableKeyByLabel: Map<String, String> = rows.associate { it.label to it.drawable }

    fun mediaItems(): List<UriMedia> = rows.map { row ->
        val taken = DOCS_DEMO_EPOCH_SECONDS - row.ageDays * DAY
        UriMedia(
            id = row.id,
            label = row.label,
            uri = parseUri("fixture://docs/${row.label}"),
            path = "/demo/${row.albumLabel}/${row.label}",
            relativePath = "demo/${row.albumLabel}/",
            albumID = row.albumID,
            albumLabel = row.albumLabel,
            timestamp = taken,
            takenTimestamp = taken,
            fullDate = taken.getDate("EEE, MMM d, yyyy"),
            mimeType = if (row.label.endsWith(".png")) "image/png" else "image/jpeg",
            favorite = if (row.favorite) 1 else 0,
            trashed = 0,
            size = row.sizeKb * 1_024L,
        )
    }

    /**
     * One [Album] per distinct `albumID` in [media], first-seen order. For the
     * built-in rows this yields exactly Camera, Screenshots, Download; for
     * other inputs (e.g. MediaStore-backed fixtures) it adapts instead of
     * crashing on an empty bucket.
     */
    fun albums(media: List<UriMedia> = mediaItems()): List<Album> =
        media.groupBy { it.albumID }.map { (id, items) ->
            val cover = items.first()
            Album(
                id = id,
                label = cover.albumLabel,
                uri = cover.uri,
                pathToThumbnail = cover.path,
                relativePath = cover.relativePath,
                timestamp = cover.timestamp,
                count = items.size.toLong(),
                size = items.sumOf { it.size },
            )
        }

    /** "Holiday 2025" — the collection the collection-grid and albums scenes share. */
    val tripMediaIds: Set<Long> = setOf(1L, 3L, 10L)
    val tripCollection = Collection(
        id = COLLECTION_TRIP,
        label = "Holiday 2025",
        coverMediaId = 1L,
    )

    fun collections(media: List<UriMedia> = mediaItems()): List<CollectionWithCount> {
        val items = media.filter { it.id in tripMediaIds }
        return listOf(
            CollectionWithCount(
                collection = tripCollection,
                mediaCount = items.size,
                thumbnailMediaId = tripCollection.coverMediaId,
                totalSize = items.sumOf { it.size },
            )
        )
    }

    fun vaults(): List<Vault> = listOf(
        Vault(name = "Documents"),
        Vault(name = "Travel"),
    )

    /** One album rule (Download, timeline-scoped) + one path-regex rule. */
    fun ignoredAlbums(): List<IgnoredAlbum> = listOf(
        IgnoredAlbum(
            id = ALBUM_DOWNLOAD,
            label = "Download",
            albumIds = listOf(ALBUM_DOWNLOAD),
            location = IgnoredAlbum.TIMELINE_ONLY,
            matchedAlbums = listOf("Download"),
        ),
        IgnoredAlbum(
            id = -1L,
            wildcard = ".*Screenshots.*",
            location = IgnoredAlbum.ALBUMS_ONLY,
            matchedAlbums = listOf("Screenshots"),
        ),
    )

    /** [MediaState] with real typed headers for the given items. */
    fun <T : com.dot.gallery.domain.model.Media> mediaStateFor(items: List<T>): MediaState<T> =
        buildMappedMediaState(items)

    /**
     * Builds the same [MediaState] shape `mapMediaToItem` emits for the
     * ungrouped, non-similar path — real [MediaItem.Header]/[MediaItem.MediaViewItem]
     * rows classified by the shared [DateGrouper]. Synchronous by design: docs
     * fixtures must be populated before the first frame on wasmJs, where the
     * suspend variant's `Dispatchers.Default` hop cannot run from composition.
     */
    private fun <T : com.dot.gallery.domain.model.Media> buildMappedMediaState(
        data: List<T>,
        albumId: Long = -1L,
    ): MediaState<T> {
        val defaultDateFormat = "EEE, MMM d"
        val extendedDateFormat = "EEE, MMM d, yyyy"
        val weeklyDateFormat = "EEEE"
        val dateGrouper = DateGrouper(
            format = defaultDateFormat,
            weeklyFormat = weeklyDateFormat,
            extendedFormat = extendedDateFormat,
            stringToday = "Today",
            stringYesterday = "Yesterday"
        )
        val estimatedSize = data.size + data.size / 20
        val mappedData = ArrayList<MediaItem<T>>(estimatedSize)
        val mappedDataWithMonthly = ArrayList<MediaItem<T>>(estimatedSize)
        val mappedDataWithYearly = ArrayList<MediaItem<T>>(estimatedSize)
        val monthHeaderList = HashSet<String>()
        val yearHeaderList = HashSet<String>()
        val headers = ArrayList<MediaItem.Header<T>>(estimatedSize / 20 + 1)

        val groupedData = data.groupBy { dateGrouper.classify(it.timestamp) }
        groupedData.forEach { (date, group) ->
            val dateHeader = MediaItem.Header<T>(
                "header_$date", date, group.mapTo(HashSet(group.size)) { it.id }
            )
            headers.add(dateHeader)
            val groupedMedia = group.map { MediaItem.MediaViewItem("media_${it.id}", it) }
            // Big month header once a group lands a week or more back (same rule as
            // mapMediaToItem's default path).
            val firstTimestamp = group.first().timestamp
            val month = if (!isRelativeDateLabel(dateGrouper.daysUntilToday(firstTimestamp))) {
                if (firstTimestamp.epochSecToLocalDate().year == dateGrouper.currentYear) {
                    firstTimestamp.formatEpochSec("MMMM")
                } else {
                    firstTimestamp.formatEpochSec("MMMMyyyy")
                }
            } else ""
            if (month.isNotEmpty() && monthHeaderList.add(month)) {
                val bigMonthHeader = MediaItem.Header<T>(
                    "header_big_${month}_${group.size}", month, dateHeader.data
                )
                if (mappedData.isNotEmpty()) mappedData.add(bigMonthHeader)
                if (mappedDataWithMonthly.isNotEmpty()) mappedDataWithMonthly.add(bigMonthHeader)
            }
            mappedData.add(dateHeader)
            mappedDataWithMonthly.add(dateHeader)
            mappedData.addAll(groupedMedia)
            mappedDataWithMonthly.addAll(groupedMedia)
            val year = firstTimestamp.epochSecToLocalDate().year.toString()
            if (yearHeaderList.add(year) && mappedDataWithYearly.isNotEmpty()) {
                mappedDataWithYearly.add(
                    MediaItem.Header("header_big_${year}_${group.size}", year, dateHeader.data)
                )
            }
            mappedDataWithYearly.add(dateHeader)
            mappedDataWithYearly.addAll(groupedMedia)
        }
        return MediaState(
            isLoading = false,
            media = data,
            pagerMedia = data.distinctBy { it.id },
            headers = headers,
            mappedMedia = mappedData,
            mappedMediaWithMonthly = mappedDataWithMonthly,
            mappedMediaWithYearly = mappedDataWithYearly,
            dateHeader = "",
            dateSource = TimelineDateSource.CAPTURE_TIME,
        )
    }
}
