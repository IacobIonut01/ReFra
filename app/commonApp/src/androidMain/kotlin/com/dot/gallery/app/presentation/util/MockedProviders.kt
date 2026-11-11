package com.dot.gallery.app.presentation.util

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.core.AlbumMediaLoadMode
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MediaContainerFormat
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.presentation.components.MockedMediaImageRenderer
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.ImageEmbedding
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.repository.CaptureDateEditCapability
import com.dot.gallery.data.repository.CaptureDateEditResult
import com.dot.gallery.data.repository.MediaMutationResult
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.LockedAlbum
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.PinnedAlbum
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.domain.util.MediaGroupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.util.ext.overrideImage
import com.dot.gallery.core.util.ext.saveImage
import com.dot.gallery.core.workers.copyMedia
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.core.util.ext.renameMedia
import java.util.UUID
import com.dot.gallery.domain.uri.path






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
    override var groupByYear: Boolean = false
    override val groupSimilarMedia: StateFlow<Boolean> = MutableStateFlow(false)
    override val enabledGroupTypes: StateFlow<Set<MediaGroupType>> = MutableStateFlow(MediaGroupType.entries.toSet())
    override val mergeAlbumsByName: StateFlow<Boolean> = MutableStateFlow(false)
    override val settingsFlow: StateFlow<TimelineSettings?> = MutableStateFlow(null)
    override val albumsFlow: StateFlow<AlbumState> = MutableStateFlow(AlbumState())
    override val blacklistedAlbumsFlow: StateFlow<List<IgnoredAlbum>> = MutableStateFlow(emptyList())
    override val pinnedAlbumsFlow: StateFlow<List<PinnedAlbum>> = MutableStateFlow(emptyList())
    override val lockedAlbumsFlow: StateFlow<List<LockedAlbum>> = MutableStateFlow(emptyList())
    override val mergedSubfolderAlbumsFlow: StateFlow<List<MergedSubfolderAlbum>> = MutableStateFlow(emptyList())
    override val timelineMediaFlow: StateFlow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
    override fun albumTimelineMediaFlow(
        albumId: Long,
        loadMode: AlbumMediaLoadMode
    ): Flow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
    override val favoritesMediaFlow: StateFlow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
    override val trashMediaFlow: StateFlow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
    override val pendingRemovalIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override val favoriteOverrides: StateFlow<Map<Long, Boolean>> = MutableStateFlow(emptyMap())
    override fun markPendingRemoval(ids: Collection<Long>, scope: PendingRemovalScope) {}
    override fun unmarkPendingRemoval(ids: Collection<Long>) {}
    override fun setFavoriteOverride(mediaId: Long, favorite: Boolean) {}
    override fun clearFavoriteOverride(mediaId: Long) {}
    override val cloudSyncStates: StateFlow<Map<Long, SyncState>> = MutableStateFlow(emptyMap())
    override val metadataFlow: StateFlow<MediaMetadataState> = MutableStateFlow(MediaMetadataState())
    override val locationsMediaFlow: SharedFlow<List<LocationMedia>> = MutableStateFlow(emptyList())
    override val geoMediaFlow: StateFlow<List<GeoMedia>> = MutableStateFlow(emptyList())
    override val vaultsMediaFlow: StateFlow<VaultState> = MutableStateFlow(VaultState())
    override fun vaultMediaFlow(vault: Vault?): StateFlow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
    override val imageEmbeddingsFlow: StateFlow<List<ImageEmbedding>> = MutableStateFlow(emptyList())
    override fun locationBasedMedia(
        gpsLocationNameCity: String,
        gpsLocationNameCountry: String,
        latitude: Double?,
        longitude: Double?,
        additionalMediaIds: Flow<Set<Long>>,
    ): Flow<MediaState<UriMedia>> = emptyFlow()
    override val collectionsFlow: StateFlow<List<CollectionWithCount>> = MutableStateFlow(emptyList())
    override val collectionAlbumIdsFlow: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override fun collectionAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> = emptyFlow()
    override fun collectionMediaFlow(collectionId: Long): StateFlow<MediaState<UriMedia>> = MutableStateFlow(MediaState())
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
    ) = MediaMutationResult.COMPLETED

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ) = Unit

    override suspend fun <T : Media> copyMedia(vararg sets: Pair<T, String>) = Unit

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) = MediaMutationResult.COMPLETED

    override suspend fun <T : Media> renameMedia(
        media: T,
        newName: String
    ): Boolean = false

    override suspend fun <T : Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean = false

    override suspend fun <T : Media> copyMediaForMove(
        mediaList: List<T>,
        newPath: String,
        onProgress: suspend (Float) -> Unit
    ): List<Uri> = emptyList()

    override suspend fun discardMediaCopies(uris: List<Uri>) = Unit

    override suspend fun probeMetadataSanitization(
        media: Media
    ): SanitizationCapability = SanitizationCapability(MediaContainerFormat.UNKNOWN, emptySet())

    override suspend fun sanitizeMediaMetadata(
        media: Media,
        categories: Set<MetadataCategory>,
        saveMode: MetadataSaveMode
    ): SanitizationResult = SanitizationResult.Unsupported(MediaContainerFormat.UNKNOWN, "Unavailable in preview")

    override suspend fun probeCaptureDateEdit(media: Media): CaptureDateEditCapability =
        CaptureDateEditCapability.UNSUPPORTED

    override suspend fun updateMediaCaptureDate(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = CaptureDateEditResult.Failed("Unavailable in preview")

    override suspend fun createDatedCopy(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = CaptureDateEditResult.Failed("Unavailable in preview")

    override suspend fun <T : Media> updateMediaDescription(
        media: T,
        description: String
    ): Boolean = false

    override suspend fun saveImage(
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? = null

    override suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean = false

    override suspend fun getCategoryForMediaId(mediaId: Long): String? = null
    override fun getClassifiedMediaCountAtCategory(category: String): Flow<Int> = emptyFlow()
    override fun getClassifiedMediaThumbnailByCategory(category: String): Flow<ClassifiedMedia?> = emptyFlow()
    override suspend fun deleteAlbumThumbnail(albumId: Long) = Unit
    override suspend fun updateAlbumThumbnail(albumId: Long, newThumbnail: Uri) = Unit
    override fun hasAlbumThumbnail(albumId: Long): Flow<Boolean> = emptyFlow()
    override suspend fun collectMetadataFor(media: Media) = Unit
    override suspend fun <T : Media> addMedia(vault: Vault, media: T) = Unit
    override suspend fun <T : Media> rotateImage(
        media: T,
        degrees: Int,
        forceCopy: Boolean
    ): String = "mock_rotate_$media.id"
    override fun <T : Media> rotateImages(items: List<Pair<T, Boolean>>, degrees: Int): String? =
        items.takeIf { it.isNotEmpty() }?.let { "mock_rotate_batch" }
    override suspend fun <T : Media> downloadCloudMedia(mediaList: List<T>): Result<Int> = Result.success(0)
}

class MockedMediaSelector: MediaSelector {
    override val selectedMedia: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override val isSelectionActive: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override fun clearSelection() = Unit
    override fun startSelection() = Unit
    override fun <T : Media> toggleSelection(mediaState: MediaState<T>, index: Int) = Unit
    override fun <T : Media> toggleSelectionById(mediaState: MediaState<T>, mediaId: Long) = Unit
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
        mediaImageRenderer = MockedMediaImageRenderer,
        content = content
    )
}