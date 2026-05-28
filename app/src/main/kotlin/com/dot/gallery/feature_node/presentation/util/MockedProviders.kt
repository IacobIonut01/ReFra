package com.dot.gallery.feature_node.presentation.util

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.presentation.components.MediaImageRenderer
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.domain.util.MediaGroupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

class MockedEventHandler: EventHandler {
    override val updaterFlow: Flow<UIEvent> = emptyFlow()
    override var navigateAction: (String) -> Unit = {}
    override var toggleNavigationBarAction: (Boolean) -> Unit = {}
    override var navigateUpAction: () -> Unit = {}
    override var setFollowThemeAction: (Boolean) -> Unit = {}
    override fun pushEvent(event: UIEvent) {}
}

open class MockedMediaDistributor: MediaDistributor {
    override val isRefreshing: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun invalidate() {}
    override val hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val dateFormatsFlow: StateFlow<Triple<String, String, String>> = MutableStateFlow(Triple("", "", ""))
    override var groupByMonth: Boolean = false
    override val groupSimilarMedia: StateFlow<Boolean> = MutableStateFlow(false)
    override val enabledGroupTypes: StateFlow<Set<MediaGroupType>> = MutableStateFlow(MediaGroupType.entries.toSet())
    override val mergeAlbumsByName: StateFlow<Boolean> = MutableStateFlow(false)
    override val settingsFlow: StateFlow<TimelineSettings?> = MutableStateFlow(null)
    override val albumsFlow: StateFlow<AlbumState> = MutableStateFlow(AlbumState())
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> = MutableStateFlow(emptyList())
    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> = MutableStateFlow(emptyList())
    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> = MutableStateFlow(emptyList())
    override val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>> = MutableStateFlow(emptyList())
    override val timelineMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val favoritesMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val trashMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val metadataFlow: StateFlow<MediaMetadataState> = MutableStateFlow(MediaMetadataState())
    override val locationsMediaFlow: SharedFlow<List<LocationMedia>> = MutableStateFlow(emptyList())
    override val geoMediaFlow: StateFlow<List<GeoMedia>> = MutableStateFlow(emptyList())
    override val vaultsMediaFlow: StateFlow<VaultState> = MutableStateFlow(VaultState())
    override fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> = MutableStateFlow(emptyList())
    override fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String
    ): Flow<MediaState<Media.UriMedia>> = emptyFlow()
    override val collectionsFlow: StateFlow<List<CollectionWithCount>> = MutableStateFlow(emptyList())
    override val collectionAlbumIdsFlow: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> = emptyFlow()
    override fun collectionMediaFlow(collectionId: Long): StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
}

class MockedMediaHandler: MediaHandler {
    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    ) = Unit

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) = Unit

    override suspend fun <T : Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    ) = Unit

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ) = Unit

    override suspend fun <T : Media> copyMedia(vararg sets: Pair<T, String>) = Unit

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) = Unit

    override suspend fun <T : Media> renameMedia(
        media: T,
        newName: String
    ): Boolean = false

    override suspend fun <T : Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean = false

    override suspend fun <T : Media> deleteMediaMetadata(
        media: T
    ): Boolean = false

    override suspend fun <T : Media> deleteMediaGPSMetadata(
        media: T
    ): Boolean = false

    override suspend fun <T : Media> updateMediaDescription(
        media: T,
        description: String
    ): Boolean = false

    override suspend fun saveImage(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? = null

    override suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean = false

    override suspend fun getCategoryForMediaId(mediaId: Long): String? = null
    override fun getClassifiedMediaCountAtCategory(category: String): Flow<Int> = emptyFlow()
    override fun getClassifiedMediaThumbnailByCategory(category: String): Flow<Media.ClassifiedMedia?> = emptyFlow()
    override suspend fun deleteAlbumThumbnail(albumId: Long) = Unit
    override suspend fun updateAlbumThumbnail(albumId: Long, newThumbnail: Uri) = Unit
    override fun hasAlbumThumbnail(albumId: Long): Flow<Boolean> = emptyFlow()
    override suspend fun collectMetadataFor(media: Media) = Unit
    override suspend fun <T : Media> addMedia(vault: Vault, media: T) = Unit
    override fun <T : Media> rotateImage(
        media: T,
        degrees: Int
    ): UUID = UUID.randomUUID()
}

class MockedMediaSelector: MediaSelector {
    override val selectedMedia: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override val isSelectionActive: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override fun clearSelection() = Unit
    override fun <T : Media> toggleSelection(mediaState: MediaState<T>, index: Int) = Unit
    override fun <T : Media> toggleSelectionById(mediaState: MediaState<T>, mediaId: Long) = Unit
    override fun addToSelection(list: List<Long>) = Unit
    override fun removeFromSelection(list: List<Long>) = Unit
    override fun rawUpdateSelection(list: Set<Long>) = Unit
}

/**
 * A [MediaImageRenderer] that renders colored placeholder boxes instead of real images.
 * Uses a fixed palette of theme-derived colors, cycling based on [model]'s hashCode.
 */
val MockedMediaImageRenderer = object : MediaImageRenderer {
    @Composable
    override fun RenderImage(
        modifier: Modifier,
        model: Any?,
        contentScale: ContentScale,
        contentDescription: String?,
        signature: Any?
    ) {
        val colors = listOf(
            Color(0xFFBBDEFB), // light blue
            Color(0xFFC8E6C9), // light green
            Color(0xFFFFCDD2), // light red
            Color(0xFFFFF9C4), // light yellow
            Color(0xFFD1C4E9), // light purple
        )
        val color = colors[(model?.hashCode()?.let { Math.abs(it) } ?: 0) % colors.size]
        Box(modifier = modifier.background(color))
    }
}

@Composable
fun SetupMockedMediaProviders(content: @Composable () -> Unit) {
    SetupMediaProviders(
        eventHandler = MockedEventHandler(),
        mediaDistributor = MockedMediaDistributor(),
        mediaHandler = MockedMediaHandler(),
        mediaSelector = MockedMediaSelector(),
        mediaImageRenderer = MockedMediaImageRenderer,
        content = content
    )
}