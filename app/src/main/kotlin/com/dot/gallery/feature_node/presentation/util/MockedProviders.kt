package com.dot.gallery.feature_node.presentation.util

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.EventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class MockedEventHandler: EventHandler {
    override val updaterFlow: Flow<UIEvent> = emptyFlow()
    override var navigateAction: (String) -> Unit = {}
    override var toggleNavigationBarAction: (Boolean) -> Unit = {}
    override var navigateUpAction: () -> Unit = {}
    override var setFollowThemeAction: (Boolean) -> Unit = {}
    override fun pushEvent(event: UIEvent) {}
}

class MockedMediaDistributor: MediaDistributor {
    override val hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val dateFormatsFlow: StateFlow<Triple<String, String, String>> = MutableStateFlow(Triple("", "", ""))
    override var groupByMonth: Boolean = false
    override val settingsFlow: StateFlow<TimelineSettings?> = MutableStateFlow(null)
    override val albumsFlow: StateFlow<AlbumState> = MutableStateFlow(AlbumState())
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> = MutableStateFlow(emptyList())
    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> = MutableStateFlow(emptyList())
    override val timelineMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val albumsTimelinesMediaFlow: StateFlow<Map<Long, MediaState<Media.UriMedia>>> = MutableStateFlow(emptyMap())
    override fun albumTimelineMediaFlow(albumId: Long): StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val favoritesMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val trashMediaFlow: StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val metadataFlow: StateFlow<MediaMetadataState> = MutableStateFlow(MediaMetadataState())
    override val vaultsMediaFlow: StateFlow<VaultState> = MutableStateFlow(VaultState())
    override fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<Media.UriMedia>> = MutableStateFlow(MediaState())
    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> = MutableStateFlow(emptyList())
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

    override suspend fun <T : Media> updateMediaImageDescription(
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
}

class MockedMediaSelector: MediaSelector {
    override val selectedMedia: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override val isSelectionActive: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override fun clearSelection() = Unit
    override fun <T : Media> toggleSelection(mediaState: MediaState<T>, index: Int) = Unit
    override fun addToSelection(list: List<Long>) = Unit
    override fun removeFromSelection(list: List<Long>) = Unit
    override fun rawUpdateSelection(list: Set<Long>) = Unit
}

@Composable
fun SetupMockedMediaProviders(content: @Composable () -> Unit) {
    SetupMediaProviders(
        eventHandler = MockedEventHandler(),
        mediaDistributor = MockedMediaDistributor(),
        mediaHandler = MockedMediaHandler(),
        mediaSelector = MockedMediaSelector(),
        content = content
    )
}