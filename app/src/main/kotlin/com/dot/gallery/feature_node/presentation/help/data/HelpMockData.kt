/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.data

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrowseGallery
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Security
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.data.data_source.CategoryWithMediaCount
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.exif.MetadataDirectory
import com.dot.gallery.feature_node.presentation.exif.MetadataTag
import com.dot.gallery.feature_node.presentation.exif.MetadataViewState
import com.dot.gallery.feature_node.presentation.util.MockedMediaDistributor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object HelpMockData {

    val MOCK_PHOTOS: List<Media.UriMedia> = (1..24).map { i ->
        Media.UriMedia(
            id = i.toLong(),
            label = if (i % 5 == 0) "VID_${1000 + i}.mp4" else "IMG_${1000 + i}.jpg",
            uri = Uri.EMPTY,
            path = "/storage/emulated/0/DCIM/Camera/IMG_${1000 + i}.jpg",
            relativePath = "DCIM/Camera/",
            albumID = 1L,
            albumLabel = "Camera",
            timestamp = 1735689600L - (i * 86400L),
            expiryTimestamp = null,
            takenTimestamp = 1735689600L - (i * 86400L),
            fullDate = "January ${i}, 2026",
            mimeType = if (i % 5 == 0) "video/mp4" else "image/jpeg",
            favorite = if (i == 3 || i == 7) 1 else 0,
            trashed = 0,
            size = (1024 * 1024 * (1 + i % 5)).toLong(),
            duration = if (i % 5 == 0) "${(15 + i) * 1000L}" else null
        )
    }

    val MOCK_FAVORITES: List<Media.UriMedia> = MOCK_PHOTOS.filter { it.favorite == 1 }

    val MOCK_TRASH: List<Media.UriMedia> = (25..30).map { i ->
        Media.UriMedia(
            id = i.toLong(),
            label = "IMG_${1000 + i}.jpg",
            uri = Uri.EMPTY,
            path = "/storage/emulated/0/DCIM/Camera/IMG_${1000 + i}.jpg",
            relativePath = "DCIM/Camera/",
            albumID = 1L,
            albumLabel = "Camera",
            timestamp = 1735689600L - (i * 86400L),
            fullDate = "January ${i}, 2026",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 1,
            size = (1024 * 1024 * 2).toLong()
        )
    }

    private val MOCK_HEADER: MediaItem.Header<Media.UriMedia> = MediaItem.Header(
        key = "header_January 2026",
        text = "January 2026",
        data = MOCK_PHOTOS.map { it.id }.toSet()
    )

    private val MOCK_MAPPED_MEDIA: List<MediaItem<Media.UriMedia>> =
        listOf(MOCK_HEADER) + MOCK_PHOTOS.map { media ->
            MediaItem.MediaViewItem(
                key = media.id.toString(),
                media = media
            )
        }

    val MOCK_MEDIA_STATE: MediaState<Media.UriMedia> = MediaState(
        media = MOCK_PHOTOS,
        mappedMedia = MOCK_MAPPED_MEDIA,
        mappedMediaWithMonthly = MOCK_MAPPED_MEDIA,
        headers = listOf(MOCK_HEADER),
        dateHeader = "January 2026",
        isLoading = false,
        error = ""
    )

    val MOCK_FAVORITES_STATE: MediaState<Media.UriMedia> = MediaState(
        media = MOCK_FAVORITES + MOCK_PHOTOS.take(4),
        mappedMedia = (MOCK_FAVORITES + MOCK_PHOTOS.take(4)).map { media ->
            MediaItem.MediaViewItem(
                key = media.id.toString(),
                media = media
            )
        },
        isLoading = false,
        error = ""
    )

    val MOCK_TRASH_STATE: MediaState<Media.UriMedia> = MediaState(
        media = MOCK_TRASH,
        mappedMedia = MOCK_TRASH.map { media ->
            MediaItem.MediaViewItem(
                key = media.id.toString(),
                media = media
            )
        },
        isLoading = false,
        error = ""
    )

    val MOCK_ALBUMS: List<Album> = listOf(
        Album(id = 1L, label = "Camera", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "DCIM/Camera/", timestamp = 0, count = 245),
        Album(id = 2L, label = "Screenshots", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/Screenshots/", timestamp = 0, count = 89),
        Album(id = 3L, label = "Download", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Download/", timestamp = 0, count = 34),
        Album(id = 4L, label = "WhatsApp", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/WhatsApp/", timestamp = 0, count = 567),
    )

    val MOCK_ALBUM_STATE: AlbumState = AlbumState(
        albums = MOCK_ALBUMS,
        albumsWithBlacklisted = MOCK_ALBUMS,
        albumsUnpinned = MOCK_ALBUMS,
        albumsPinned = emptyList(),
        isLoading = false,
        error = ""
    )

    val MOCK_METADATA_STATE: MediaMetadataState = MediaMetadataState(
        metadata = emptyList(),
        isLoading = false
    )

    val MOCK_CATEGORIES = listOf("Nature", "Food", "People", "Travel", "Pets", "Architecture")

    val MOCK_EXIF_ROWS = listOf(
        "Date" to "Jan 15, 2026, 3:42 PM",
        "Camera" to "Google Pixel 9 Pro",
        "Resolution" to "4032 × 3024",
        "Size" to "4.2 MB",
        "ISO" to "125",
        "Focal length" to "6.81mm",
        "Location" to "Bucharest, Romania"
    )

    /**
     * A [MockedMediaDistributor] pre-populated with mock data so that real screen
     * composables (AlbumsScreen, etc.) render meaningful content in previews.
     */
    fun createPreviewDistributor() = object : MockedMediaDistributor() {
        override val albumsFlow: StateFlow<AlbumState> = MutableStateFlow(MOCK_ALBUM_STATE)
        override val timelineMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MOCK_MEDIA_STATE)
        override val favoritesMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MOCK_FAVORITES_STATE)
        override val trashMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MOCK_TRASH_STATE)
        override val metadataFlow: StateFlow<MediaMetadataState> = MutableStateFlow(MOCK_METADATA_STATE)
    }

    val MOCK_CATEGORIES_WITH_COUNT: List<CategoryWithMediaCount> = listOf(
        CategoryWithMediaCount(
            id = 1L, name = "Nature", searchTerms = "nature,landscape,trees",
            embedding = null, threshold = 0.5f, isUserCreated = false,
            isPinned = true, createdAt = 1735689600000L, updatedAt = 1735689600000L,
            mediaCount = 42, thumbnailMediaId = 1L
        ),
        CategoryWithMediaCount(
            id = 2L, name = "People", searchTerms = "people,portrait,face",
            embedding = null, threshold = 0.5f, isUserCreated = false,
            isPinned = false, createdAt = 1735689600000L, updatedAt = 1735689600000L,
            mediaCount = 28, thumbnailMediaId = 3L
        ),
        CategoryWithMediaCount(
            id = 3L, name = "Food", searchTerms = "food,meal,dish",
            embedding = null, threshold = 0.5f, isUserCreated = true,
            isPinned = false, createdAt = 1735689600000L, updatedAt = 1735689600000L,
            mediaCount = 15, thumbnailMediaId = 5L
        ),
        CategoryWithMediaCount(
            id = 4L, name = "Architecture", searchTerms = "building,architecture,city",
            embedding = null, threshold = 0.5f, isUserCreated = false,
            isPinned = false, createdAt = 1735689600000L, updatedAt = 1735689600000L,
            mediaCount = 12, thumbnailMediaId = 7L
        ),
        CategoryWithMediaCount(
            id = 5L, name = "Pets", searchTerms = "pet,dog,cat,animal",
            embedding = null, threshold = 0.5f, isUserCreated = true,
            isPinned = true, createdAt = 1735689600000L, updatedAt = 1735689600000L,
            mediaCount = 8, thumbnailMediaId = 9L
        ),
    )

    val MOCK_LOCATIONS: List<LocationMedia> = listOf(
        LocationMedia(media = MOCK_PHOTOS[0], location = "Bucharest, Romania"),
        LocationMedia(media = MOCK_PHOTOS[1], location = "Bucharest, Romania"),
        LocationMedia(media = MOCK_PHOTOS[2], location = "Paris, France"),
        LocationMedia(media = MOCK_PHOTOS[3], location = "Paris, France"),
        LocationMedia(media = MOCK_PHOTOS[4], location = "Paris, France"),
        LocationMedia(media = MOCK_PHOTOS[5], location = "New York, United States"),
        LocationMedia(media = MOCK_PHOTOS[6], location = "Tokyo, Japan"),
        LocationMedia(media = MOCK_PHOTOS[7], location = "Tokyo, Japan"),
    )

    val MOCK_METADATA_VIEW_STATE = MetadataViewState(
        isLoading = false,
        directories = listOf(
            MetadataDirectory(
                name = "EXIF",
                tags = listOf(
                    MetadataTag("Date/Time Original", "2026:01:15 15:42:30"),
                    MetadataTag("Make", "Google"),
                    MetadataTag("Model", "Pixel 9 Pro"),
                    MetadataTag("F-Number", "f/1.7"),
                    MetadataTag("ISO Speed Ratings", "125"),
                    MetadataTag("Focal Length", "6.81 mm"),
                    MetadataTag("Exposure Time", "1/120 sec"),
                )
            ),
            MetadataDirectory(
                name = "JPEG",
                tags = listOf(
                    MetadataTag("Image Width", "4032 pixels"),
                    MetadataTag("Image Height", "3024 pixels"),
                    MetadataTag("Compression Type", "Baseline"),
                )
            ),
        )
    )

    val MOCK_SETTINGS: List<SettingsEntity> = listOf(
        SettingsEntity.SwitchPreference(
            icon = Icons.Outlined.BrowseGallery,
            title = "Auto-hide search bar",
            summary = "Hide search bar when scrolling",
            isChecked = true,
            screenPosition = Position.Top
        ),
        SettingsEntity.SwitchPreference(
            icon = Icons.Outlined.GridView,
            title = "Group similar media",
            summary = "Cluster photos taken at the same time",
            isChecked = false,
            screenPosition = Position.Middle
        ),
        SettingsEntity.SwitchPreference(
            icon = Icons.Outlined.Favorite,
            title = "Show favorite icon",
            summary = "Display heart on favorited media",
            isChecked = true,
            screenPosition = Position.Middle
        ),
        SettingsEntity.SwitchPreference(
            icon = Icons.Outlined.Security,
            title = "Secure mode",
            summary = "Hide content in recent apps",
            isChecked = false,
            screenPosition = Position.Bottom
        ),
    )
}
