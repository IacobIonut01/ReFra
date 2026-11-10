/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.injection

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.location.Geocoder
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.data.repository.CloudRepositoryImpl
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.cloud.sync.CloudIndexProgressManager
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.cloud.sync.FreeUpSpaceAutoScheduler
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.EditBackupManager
import com.dot.gallery.core.EditBackupsRuntimeImpl
import com.dot.gallery.core.EditRuntimeImpl
import com.dot.gallery.core.MediaRepositoryRuntimeImpl
import com.dot.gallery.core.ModelDownloadRuntimeImpl
import com.dot.gallery.core.CopyMediaRuntimeImpl
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaDistributorImpl
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.BulkRotateCoordinator
import com.dot.gallery.core.MediaHandlerImpl
import com.dot.gallery.core.presentation.components.FloatingProgressRegistry
import com.dot.gallery.core.presentation.components.MediaRotationTracker
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.MediaSelectorImpl
import com.dot.gallery.core.decryption.DecryptManager
import com.dot.gallery.core.decryption.MediaMetadataSidecarCache
import com.dot.gallery.core.encryption.EncryptedDatabaseFactory
import com.dot.gallery.core.encryption.EncryptionBackendState
import com.dot.gallery.core.encryption.EncryptionStateMonitor
import com.dot.gallery.core.memory.AdaptiveDecryptConfig
import com.dot.gallery.core.memory.ByteArrayPool
import com.dot.gallery.core.metadata.AndroidMetadataSanitizer
import com.dot.gallery.core.metadata.MetadataSanitizer
import com.dot.gallery.core.metrics.MetricsCollector
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.sandbox.IsolatedImageDecoder
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.PrivateFolderRepository
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.core.smart.SmartScanSchedulerImpl
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.CloudAlbumSyncDao
import com.dot.gallery.data.dao.CloudDeleteLocalPrefDao
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudOfflinePinDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.dao.CloudTagDao
import com.dot.gallery.data.dao.CloudUploadPrefDao
import com.dot.gallery.data.dao.DetectedFaceDao
import com.dot.gallery.data.dao.PersonDao
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.dao.SyncStateDao
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.data.repository.CoreMediaRepository
import com.dot.gallery.data.repository.MediaRepositoryImpl
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.data.repository.MediaRepositoryRuntime
import com.dot.gallery.feature.classifier.ui.search.RemoteSearchProvider
import com.dot.gallery.feature.classifier.ui.search.SearchHelper
import com.dot.gallery.app.presentation.search.SearchHelperImpl
import com.dot.gallery.handover.HandoverClient
import com.dot.gallery.handover.HandoverIndexMirror
import com.dot.gallery.handover.HandoverSearchProvider
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.dot.gallery.cloud.sync.CloudAlbumCopyWorker
import com.dot.gallery.cloud.sync.CloudDownloadWorker
import com.dot.gallery.cloud.sync.CloudOfflineDownloadWorker
import com.dot.gallery.cloud.sync.CloudSyncWorker
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.sync.FreeUpSpaceWorker
import com.dot.gallery.cloud.ui.CloudAccountsViewModel
import com.dot.gallery.cloud.ui.CloudMediaViewModel
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.cloud.ui.CloudUploadSettingsViewModel
import com.dot.gallery.cloud.ui.archive.CloudArchiveViewModel
import com.dot.gallery.cloud.ui.backup.CloudBackupViewModel
import com.dot.gallery.cloud.ui.backup.UploadDetailsViewModel
import com.dot.gallery.cloud.ui.destinations.CloudDestinationsViewModel
import com.dot.gallery.cloud.ui.memories.MemoriesViewModel
import com.dot.gallery.cloud.ui.offline.OfflineModeViewModel
import com.dot.gallery.cloud.ui.people.PeopleListViewModel
import com.dot.gallery.cloud.ui.people.PersonDetailViewModel
import com.dot.gallery.cloud.ui.settings.CloudSettingsViewModel
import com.dot.gallery.cloud.ui.sharing.SharedLinksViewModel
import com.dot.gallery.cloud.ui.space.FreeUpSpaceViewModel
import com.dot.gallery.cloud.ui.sync.SyncStatusViewModel
import com.dot.gallery.core.presentation.vm.NavigationViewModel
import com.dot.gallery.core.work.workScheduler
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import com.dot.gallery.core.workers.CaptureTimeIndexWorker
import com.dot.gallery.core.workers.CategoryWorker
import com.dot.gallery.core.workers.ClassifierWorker
import com.dot.gallery.core.workers.DatabaseEncryptionWorker
import com.dot.gallery.core.workers.DatabaseUpdaterWorker
import com.dot.gallery.core.workers.EditBackupWorker
import com.dot.gallery.core.workers.MediaCopyWorker
import com.dot.gallery.core.workers.MetadataCollectionWorker
import com.dot.gallery.core.workers.MetadataLocationRepairWorker
import com.dot.gallery.core.workers.ModelDownloadWorker
import com.dot.gallery.core.workers.RedactedLocationRepairWorker
import com.dot.gallery.core.workers.RotateMediaWorker
import com.dot.gallery.core.workers.SearchIndexerUpdaterWorker
import com.dot.gallery.core.workers.SmartScanWorker
import com.dot.gallery.core.workers.TempVaultCleanupWorker
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.domain.model.Media
import com.dot.gallery.core.ClassifierRuntimeImpl
import com.dot.gallery.core.MediaViewBackendImpl
import com.dot.gallery.core.VaultRuntimeImpl
import com.dot.gallery.core.vault.VaultRuntime
import com.dot.gallery.feature.classifier.ClassifierRuntime
import com.dot.gallery.feature.viewer.MediaViewBackend
import com.dot.gallery.feature.viewer.ViewerRuntime
import com.dot.gallery.cloud.image.CloudImageSource
import com.dot.gallery.cloud.media.CloudDataSource
import com.dot.gallery.cloud.core.ProviderType
import androidx.media3.datasource.DataSource
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.dot.gallery.feature.albums.AlbumsViewModel
import com.dot.gallery.feature.albums.ui.AlbumTimelineViewModel
import com.dot.gallery.feature.albums.CloudAlbumManageViewModel
import com.dot.gallery.app.presentation.cast.FCastViewModel
import com.dot.gallery.feature.classifier.CategoriesViewModel
import com.dot.gallery.feature.classifier.CategoryEditorViewModel
import com.dot.gallery.feature.classifier.CategoryViewModel
import com.dot.gallery.feature.albums.ui.collections.CollectionViewModel
import com.dot.gallery.feature.editor.EditRuntime
import com.dot.gallery.feature.editor.EditViewModel
import com.dot.gallery.feature.exif.CopyMediaRuntime
import com.dot.gallery.feature.exif.CopyMediaViewModel
import com.dot.gallery.feature.exif.MetadataViewViewModel
import com.dot.gallery.app.presentation.frameextract.FrameExportWorker
import com.dot.gallery.app.presentation.frameextract.FramePickerViewModel
import com.dot.gallery.feature.ignored.ui.IgnoredViewModel
import com.dot.gallery.feature.ignored.ui.setup.IgnoredSetupViewModel
import com.dot.gallery.feature.library.LibraryViewModel
import com.dot.gallery.feature.library.location.LocationsViewModel
import com.dot.gallery.feature.viewer.MediaViewViewModel
import com.dot.gallery.feature.viewer.components.video.VideoPlayerViewModel
import com.dot.gallery.feature.picker.PickerViewModel
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderMoveViewModel
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderViewModel
import com.dot.gallery.feature.search.SearchViewModel
import com.dot.gallery.app.presentation.settings.subsettings.HandoverSettingsViewModel
import com.dot.gallery.feature.settings.subsettings.BackupRestoreViewModel
import com.dot.gallery.feature.settings.subsettings.DeveloperViewModel
import com.dot.gallery.feature.settings.subsettings.EditBackupsRuntime
import com.dot.gallery.feature.settings.subsettings.ModelDownloadRuntime
import com.dot.gallery.feature.settings.subsettings.EditBackupsViewModel
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.feature.viewer.standalone.StandaloneViewModel
import com.dot.gallery.feature.timeline.storycards.StoryCardsViewModel
import com.dot.gallery.feature.vault.VaultViewModel
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.cloud.genericwebdav.di.WebDavProviderComponent
import com.dot.gallery.cloud.immich.di.ImmichProviderComponent
import com.dot.gallery.cloud.nextcloud.di.NextcloudProviderComponent
import com.dot.gallery.cloud.nfs.di.NfsProviderComponent
import com.dot.gallery.cloud.owncloud.di.OwnCloudProviderComponent
import com.dot.gallery.cloud.smb.di.SmbProviderComponent
import com.dot.gallery.cloud.local.FaceCropLoader
import com.dot.gallery.cloud.local.LocalOcrProvider
import com.dot.gallery.cloud.local.LocalPeopleBlurrer
import com.dot.gallery.cloud.sync.CloudAlbumCopyRequestStore
import com.dot.gallery.cloud.sync.FreeUpSpaceEngine
import com.dot.gallery.core.backup.ConfigBackupManager
import com.dot.gallery.core.backup.ConfigBackupManagerImpl
import com.dot.gallery.core.backup.PendingCloudFavoriteStore
import com.dot.gallery.cloud.core.CredentialEncryptor
import com.dot.gallery.core.smart.SmartThumbnailLoader
import com.dot.gallery.core.smart.SmartScanProcessorRegistry
import com.dot.gallery.core.startup.StartupPrefill
import com.dot.gallery.app.presentation.cast.FCastSession
import com.dot.gallery.app.presentation.frameextract.FrameSourceMaterializer
import com.dot.gallery.feature.library.LibraryCategorySource
import com.dot.gallery.feature.library.LibraryContentSource
import com.dot.gallery.feature.library.LibrarySnapshotCache
import com.dot.gallery.feature.library.location.MapGeoMediaSource
import com.dot.gallery.app.presentation.mediaview.VisualSearchLauncher
import com.dot.gallery.cloud.ui.backup.CloudBackupInfoViewModel
import com.dot.gallery.core.smart.CategoryClassificationPhaseProcessor
import com.dot.gallery.core.smart.FaceClusterPhaseProcessor
import com.dot.gallery.core.smart.FaceIndexPhaseProcessor
import com.dot.gallery.core.smart.MetadataPhaseProcessor
import com.dot.gallery.core.smart.OcrIndexPhaseProcessor
import com.dot.gallery.core.smart.SearchIndexPhaseProcessor
import com.dot.gallery.core.smart.SmartScanPhaseProcessor
import com.dot.gallery.core.smart.SourceSyncProcessor
import com.dot.gallery.data.getBundledDatabase

/**
 * kotlin-inject root graph for the app process.
 *
 * Migration notes (K5): this component is the single source of truth for every
 * singleton/service it declares. `enableJavaxAnnotations` makes kotlin-inject
 * honour the existing `javax.inject` annotations, so every `@Inject`-constructor
 * class is already constructible here without source changes.
 *
 * Cloud provider factories come from per-flavour superinterfaces
 * (`ImmichProviderComponent` etc.) — every flavour source set ships either the
 * real interface or an empty same-FQN stub from its `no*` twin.
 */
@Singleton
@Component
abstract class AppComponent(
    @get:Provides val application: Application
) : ImmichProviderComponent,
    OwnCloudProviderComponent,
    NextcloudProviderComponent,
    WebDavProviderComponent,
    SmbProviderComponent,
    NfsProviderComponent,
    ViewerRuntime {

    override fun videoDataSourceFactory(): DataSource.Factory = CloudDataSource.Factory(application)

    override fun videoPlayerViewModel(
        savedStateHandle: SavedStateHandle,
        media: Media,
        dataSourceFactory: DataSource.Factory,
    ): VideoPlayerViewModel = videoPlayerViewModelFactory(savedStateHandle, media, dataSourceFactory)

    override suspend fun cloudImageSource(
        providerType: ProviderType,
        remoteId: String,
        configId: Long,
    ): ImageSource = CloudImageSource.create(application, providerType, remoteId, configId)

    @Provides
    fun provideApplicationContext(): Context = application

    @Provides
    fun provideClassifierRuntime(impl: ClassifierRuntimeImpl): ClassifierRuntime = impl

    @Provides
    fun provideMediaViewBackend(impl: MediaViewBackendImpl): MediaViewBackend = impl

    @Provides
    fun provideVaultRuntime(impl: VaultRuntimeImpl): VaultRuntime = impl

    @Provides
    fun provideSmartScanScheduler(impl: SmartScanSchedulerImpl): SmartScanScheduler = impl

    @Provides
    fun provideLibrarySnapshotCache(impl: StartupMediaCache): LibrarySnapshotCache = impl

    @Provides
    fun provideEditBackupsRuntime(impl: EditBackupsRuntimeImpl): EditBackupsRuntime = impl

    @Provides
    fun provideConfigBackupManager(impl: ConfigBackupManagerImpl): ConfigBackupManager = impl

    @Provides
    fun provideModelDownloadRuntime(impl: ModelDownloadRuntimeImpl): ModelDownloadRuntime = impl

    @Provides
    fun provideCopyMediaRuntime(impl: CopyMediaRuntimeImpl): CopyMediaRuntime = impl

    @Provides
    fun provideEditRuntime(impl: EditRuntimeImpl): EditRuntime = impl

    @Provides
    fun provideMediaRepositoryRuntime(impl: MediaRepositoryRuntimeImpl): MediaRepositoryRuntime = impl

    @Provides
    @Singleton
    fun provideContentResolver(): ContentResolver = application.contentResolver

    @Provides
    @Singleton
    fun provideFloatingProgressRegistry(): FloatingProgressRegistry = FloatingProgressRegistry()

    @Provides
    @Singleton
    fun provideMediaRotationTracker(): MediaRotationTracker = MediaRotationTracker()

    @Provides
    @Singleton
    fun provideDatabase(
        startupGate: StartupWorkGate
    ): InternalDatabase = StartupTracer.trace("AppComponent.provideDatabase") {
        try {
            EncryptedDatabaseFactory.create(application, startupGate).also {
                EncryptionStateMonitor.reportDatabase(EncryptionBackendState.ENCRYPTED)
            }
        } catch (_: Exception) {
            StartupTracer.trace("AppComponent.provideDatabase.fallbackPlaintext") {
                getBundledDatabase(application)
            }.also {
                EncryptionStateMonitor.reportDatabase(EncryptionBackendState.PLAINTEXT_FALLBACK)
            }
        }
    }

    @Provides
    @Singleton
    fun provideKeychainHolder(): KeychainHolder =
        StartupTracer.trace("AppComponent.provideKeychainHolder") { KeychainHolder(application) }

    @Provides
    @Singleton
    fun provideBackgroundTaskScheduler(): BackgroundTaskScheduler =
        StartupTracer.trace("AppComponent.provideBackgroundTaskScheduler") { workScheduler() }

    @Provides
    @Singleton
    fun provideBackupJobController(
        runtime: com.dot.gallery.core.backup.BackupJobRuntime
    ): com.dot.gallery.core.backup.BackupJobController = runtime

    @Provides
    @Singleton
    fun provideDataHealthController(
        impl: com.dot.gallery.core.workers.DataHealthControllerImpl
    ): com.dot.gallery.core.backup.DataHealthController = impl

    @Provides
    @Singleton
    fun provideEventHandler(repository: MediaRepository): EventHandler =
        DefaultEventHandler(updateDatabase = repository::updateInternalDatabase)

    @Provides
    @Singleton
    fun provideMediaDistributor(
        repository: MediaRepository,
        cloudRepository: CloudRepository,
        eventHandler: EventHandler,
        database: InternalDatabase,
        startupGate: StartupWorkGate
    ): MediaDistributor = StartupTracer.trace("AppComponent.provideMediaDistributor") {
        MediaDistributorImpl(
            application,
            repository,
            cloudRepository,
            eventHandler,
            database.getScannedMediaDao(),
            database.getSmartScanDao(),
            database.getMediaCaptureTimeDao(),
            startupGate
        )
    }

    @Provides
    @Singleton
    fun provideMediaSelector(): MediaSelector = MediaSelectorImpl()

    @Provides
    @Singleton
    fun provideMediaHandler(
        mediaRepository: MediaRepository,
        scheduler: BackgroundTaskScheduler,
        providerRegistry: ProviderRegistry,
        cloudMediaDao: CloudMediaDao,
        cloudServerConfigDao: CloudServerConfigDao,
        indexMirror: HandoverIndexMirror,
        bulkRotateCoordinator: BulkRotateCoordinator,
    ): MediaHandler = StartupTracer.trace("AppComponent.provideMediaHandler") {
        MediaHandlerImpl(
            mediaRepository, application, scheduler, providerRegistry, cloudMediaDao,
            cloudServerConfigDao, indexMirror, bulkRotateCoordinator
        )
    }

    @Provides
    @Singleton
    fun provideIsolatedMetadataParser(): IsolatedMetadataParser =
        StartupTracer.trace("AppComponent.provideIsolatedMetadataParser") {
            IsolatedMetadataParser(application)
        }

    @Provides
    @Singleton
    fun provideIsolatedImageDecoder(): IsolatedImageDecoder =
        StartupTracer.trace("AppComponent.provideIsolatedImageDecoder") {
            IsolatedImageDecoder(application)
        }

    @Provides
    @Singleton
    fun provideMetadataSanitizer(
        sanitizer: AndroidMetadataSanitizer
    ): MetadataSanitizer = sanitizer

    @Provides
    @Singleton
    fun provideMediaRepository(
        mediaRepositoryRuntime: MediaRepositoryRuntime,
        database: InternalDatabase,
        keychainHolder: KeychainHolder,
        geocoder: Geocoder?,
        isolatedParser: IsolatedMetadataParser,
        metadataSanitizer: MetadataSanitizer,
        smartScanScheduler: SmartScanScheduler,
        startupCache: StartupMediaCache,
        startupGate: StartupWorkGate,
    ): MediaRepository = StartupTracer.trace("AppComponent.provideMediaRepository") {
        MediaRepositoryImpl(
            application,
            mediaRepositoryRuntime,
            database,
            keychainHolder,
            geocoder,
            isolatedParser,
            metadataSanitizer,
            smartScanScheduler,
            startupCache,
            startupGate
        )
    }

    @Provides
    @Singleton
    fun provideCoreMediaRepository(repository: MediaRepository): CoreMediaRepository = repository

    @Provides
    @Singleton
    fun provideOcrPackStore(): com.dot.gallery.core.ocr.model.OcrPackStore =
        com.dot.gallery.core.ocr.model.OcrPackStore(application)

    @Provides
    @Singleton
    fun provideOcrRepository(database: com.dot.gallery.data.InternalDatabase): com.dot.gallery.core.ocr.repository.OcrRepository =
        com.dot.gallery.core.ocr.repository.OcrRepository(database.getOcrResultDao())

    @Provides
    @Singleton
    fun provideOcrSessionManager(store: com.dot.gallery.core.ocr.model.OcrPackStore): com.dot.gallery.core.ocr.engine.OcrSessionManager =
        com.dot.gallery.core.ocr.engine.OcrSessionManager(store)

    @Provides
    @Singleton
    fun providePpOcrV5Engine(
        store: com.dot.gallery.core.ocr.model.OcrPackStore,
        sessions: com.dot.gallery.core.ocr.engine.OcrSessionManager,
    ): com.dot.gallery.core.ocr.engine.PpOcrV5Engine = com.dot.gallery.core.ocr.engine.PpOcrV5Engine(store, sessions)

    @Provides
    @Singleton
    fun provideModelManager(): ModelManager =
        StartupTracer.trace("AppComponent.provideModelManager") { ModelManager(application) }

    @Provides
    @Singleton
    fun provideSearchHelper(modelManager: ModelManager): SearchHelper =
        StartupTracer.trace("AppComponent.provideSearchHelper") { SearchHelperImpl(modelManager) }

    @Provides
    @Singleton
    fun provideRemoteSearchProvider(handoverClient: HandoverClient): RemoteSearchProvider =
        StartupTracer.trace("AppComponent.provideRemoteSearchProvider") {
            HandoverSearchProvider(handoverClient)
        }

    @Provides
    @Singleton
    fun provideGeocoder(): Geocoder? =
        StartupTracer.trace("AppComponent.provideGeocoder") {
            if (Geocoder.isPresent()) Geocoder(application) else null
        }

    @Provides
    @Singleton
    fun provideDecryptManager(metrics: MetricsCollector): DecryptManager =
        StartupTracer.trace("AppComponent.provideDecryptManager") { DecryptManager(application, metrics) }

    @Provides
    @Singleton
    fun provideMediaMetadataSidecarCache(): MediaMetadataSidecarCache =
        StartupTracer.trace("AppComponent.provideMediaMetadataSidecarCache") {
            MediaMetadataSidecarCache(application)
        }

    @Provides
    @Singleton
    fun provideAdaptiveDecryptConfig(): AdaptiveDecryptConfig =
        StartupTracer.trace("AppComponent.provideAdaptiveDecryptConfig") {
            AdaptiveDecryptConfig(application)
        }

    @Provides
    @Singleton
    fun provideMetricsCollector(): MetricsCollector = MetricsCollector()

    @Provides
    @Singleton
    fun provideByteArrayPool(): ByteArrayPool = ByteArrayPool()

    @Provides
    @Singleton
    fun providePrivateFolderRepository(): PrivateFolderRepository =
        StartupTracer.trace("AppComponent.providePrivateFolderRepository") {
            PrivateFolderRepository(application)
        }

    @Provides
    @Singleton
    fun provideEditBackupManager(
        database: InternalDatabase
    ): EditBackupManager = StartupTracer.trace("AppComponent.provideEditBackupManager") {
        EditBackupManager(application, database.getEditHistoryDao())
    }

    // CloudModule

    @Provides
    @Singleton
    fun provideCloudMediaDao(database: InternalDatabase): CloudMediaDao =
        database.getCloudMediaDao()

    @Provides
    @Singleton
    fun provideCloudServerConfigDao(database: InternalDatabase): CloudServerConfigDao =
        database.getCloudServerConfigDao()

    @Provides
    @Singleton
    fun providePersonDao(database: InternalDatabase): PersonDao =
        database.getPersonDao()

    @Provides
    @Singleton
    fun provideDetectedFaceDao(database: InternalDatabase): DetectedFaceDao =
        database.getDetectedFaceDao()

    @Provides
    @Singleton
    fun provideSyncStateDao(database: InternalDatabase): SyncStateDao =
        database.getSyncStateDao()

    @Provides
    @Singleton
    fun provideCloudAlbumSyncDao(database: InternalDatabase): CloudAlbumSyncDao =
        database.getCloudAlbumSyncDao()

    @Provides
    @Singleton
    fun provideCloudUploadPrefDao(database: InternalDatabase): CloudUploadPrefDao =
        database.getCloudUploadPrefDao()

    @Provides
    @Singleton
    fun provideCloudDeleteLocalPrefDao(database: InternalDatabase): CloudDeleteLocalPrefDao =
        database.getCloudDeleteLocalPrefDao()

    @Provides
    @Singleton
    fun provideCloudOfflinePinDao(database: InternalDatabase): CloudOfflinePinDao =
        database.getCloudOfflinePinDao()

    @Provides
    @Singleton
    fun provideCloudTagDao(database: InternalDatabase): CloudTagDao =
        database.getCloudTagDao()

    @Provides
    @Singleton
    fun provideSmartScanDao(database: InternalDatabase): SmartScanDao =
        database.getSmartScanDao()

    @Provides
    @Singleton
    fun provideCloudRepository(
        registry: ProviderRegistry,
        cloudMediaDao: CloudMediaDao,
        cloudTagDao: CloudTagDao,
        urlResolver: ServerUrlResolver,
        configDao: CloudServerConfigDao,
        syncStateDao: SyncStateDao,
    ): CloudRepository = CloudRepositoryImpl(
        registry, cloudMediaDao, cloudTagDao, urlResolver,
        configDao, syncStateDao, application
    )

    // SmartScanModule — phase processor set

    @IntoSet
    @Provides
    fun provideSourceSyncProcessor(processor: SourceSyncProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideMetadataProcessor(processor: MetadataPhaseProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideSearchIndexProcessor(processor: SearchIndexPhaseProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideCategoryProcessor(processor: CategoryClassificationPhaseProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideFaceIndexProcessor(processor: FaceIndexPhaseProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideFaceClusterProcessor(processor: FaceClusterPhaseProcessor): SmartScanPhaseProcessor = processor

    @IntoSet
    @Provides
    fun provideOcrIndexProcessor(processor: OcrIndexPhaseProcessor): SmartScanPhaseProcessor = processor

    // ── graph accessors — injectViewModel/InjectWorkerFactory and app code read from here

    abstract val contentResolver: ContentResolver
    abstract val database: InternalDatabase
    abstract val keychainHolder: KeychainHolder
    abstract val backgroundTaskScheduler: BackgroundTaskScheduler
    abstract val eventHandler: EventHandler
    abstract val mediaDistributor: MediaDistributor
    abstract val mediaSelector: MediaSelector
    abstract val mediaHandler: MediaHandler
    abstract val isolatedMetadataParser: IsolatedMetadataParser
    abstract val isolatedImageDecoder: IsolatedImageDecoder
    abstract val metadataSanitizer: MetadataSanitizer
    abstract val mediaRepository: MediaRepository
    abstract override val modelManager: ModelManager
    abstract val searchHelper: SearchHelper
    abstract val remoteSearchProvider: RemoteSearchProvider
    abstract val geocoder: Geocoder?
    abstract val decryptManager: DecryptManager
    abstract val mediaMetadataSidecarCache: MediaMetadataSidecarCache
    abstract val adaptiveDecryptConfig: AdaptiveDecryptConfig
    abstract val metricsCollector: MetricsCollector
    abstract val byteArrayPool: ByteArrayPool
    abstract val privateFolderRepository: PrivateFolderRepository
    abstract val editBackupManager: EditBackupManager
    abstract val cloudMediaDao: CloudMediaDao
    abstract val cloudServerConfigDao: CloudServerConfigDao
    abstract val personDao: PersonDao
    abstract val detectedFaceDao: DetectedFaceDao
    abstract val syncStateDao: SyncStateDao
    abstract val cloudAlbumSyncDao: CloudAlbumSyncDao
    abstract val cloudUploadPrefDao: CloudUploadPrefDao
    abstract val cloudDeleteLocalPrefDao: CloudDeleteLocalPrefDao
    abstract val cloudOfflinePinDao: CloudOfflinePinDao
    abstract val cloudTagDao: CloudTagDao
    abstract val smartScanDao: SmartScanDao
    abstract val cloudRepository: CloudRepository
    abstract val cloudProviderInitializer: CloudProviderInitializer
    abstract val faceCropLoader: FaceCropLoader
    abstract val localOcrProvider: LocalOcrProvider
    abstract val localPeopleBlurrer: LocalPeopleBlurrer
    abstract val localPeopleProvider: LocalPeopleProvider
    abstract val serverUrlResolver: ServerUrlResolver
    abstract val cloudMediaCache: CloudMediaCache
    abstract val offlineModeManager: OfflineModeManager
    abstract val cloudAlbumCopyRequestStore: CloudAlbumCopyRequestStore
    abstract val cloudIndexProgressManager: CloudIndexProgressManager
    abstract val cloudSyncScheduler: CloudSyncScheduler
    abstract val freeUpSpaceEngine: FreeUpSpaceEngine
    abstract val freeUpSpaceAutoScheduler: FreeUpSpaceAutoScheduler
    abstract val configBackupManager: ConfigBackupManager
    abstract val pendingCloudFavoriteStore: PendingCloudFavoriteStore
    abstract val providerRegistry: ProviderRegistry
    abstract val credentialEncryptor: CredentialEncryptor
    abstract val androidMetadataSanitizer: AndroidMetadataSanitizer
    abstract val smartScanScheduler: SmartScanScheduler
    abstract val smartThumbnailLoader: SmartThumbnailLoader
    abstract val smartScanProcessorRegistry: SmartScanProcessorRegistry
    abstract val startupMediaCache: StartupMediaCache
    abstract val startupPrefill: StartupPrefill
    abstract val startupWorkGate: StartupWorkGate
    abstract val fCastSession: FCastSession
    abstract val frameSourceMaterializer: FrameSourceMaterializer
    abstract val libraryCategorySource: LibraryCategorySource
    abstract val libraryContentSource: LibraryContentSource
    abstract val mapGeoMediaSource: MapGeoMediaSource
    abstract val visualSearchLauncher: VisualSearchLauncher

    // ── view model accessors — unscoped, one instance per ViewModelStore

    abstract val cloudBackupInfoViewModel: CloudBackupInfoViewModel
    abstract val albumsViewModel: AlbumsViewModel
    abstract val backupRestoreViewModel: BackupRestoreViewModel
    abstract val backupHubViewModel: com.dot.gallery.app.presentation.settings.subsettings.BackupHubViewModel
    abstract val categoriesViewModel: CategoriesViewModel
    abstract val categoryEditorViewModel: CategoryEditorViewModel
    abstract val categoryViewModel: CategoryViewModel
    abstract val cloudAccountsViewModel: CloudAccountsViewModel
    abstract val cloudAlbumManageViewModel: CloudAlbumManageViewModel
    abstract val cloudArchiveViewModel: CloudArchiveViewModel
    abstract val cloudBackupViewModel: CloudBackupViewModel
    abstract val cloudMediaViewModel: CloudMediaViewModel
    abstract val cloudSelectionViewModel: CloudSelectionViewModel
    abstract val cloudSettingsViewModel: CloudSettingsViewModel
    abstract val collectionViewModel: CollectionViewModel
    abstract val developerViewModel: DeveloperViewModel
    abstract val editBackupsViewModel: EditBackupsViewModel
    abstract val handoverSettingsViewModel: HandoverSettingsViewModel
    abstract val editViewModel: EditViewModel
    abstract val fCastViewModel: FCastViewModel
    abstract val ignoredSetupViewModel: IgnoredSetupViewModel
    abstract val ignoredViewModel: IgnoredViewModel
    abstract val libraryViewModel: LibraryViewModel
    abstract val mediaViewViewModel: MediaViewViewModel
    abstract val memoriesViewModel: MemoriesViewModel
    abstract val metadataViewViewModel: MetadataViewViewModel
    abstract val navigationViewModel: NavigationViewModel
    abstract val offlineModeViewModel: OfflineModeViewModel
    abstract val peopleListViewModel: PeopleListViewModel
    abstract val pickerViewModel: PickerViewModel
    abstract val privateFolderMoveViewModel: PrivateFolderMoveViewModel
    abstract val privateFolderViewModel: PrivateFolderViewModel
    abstract val searchViewModel: SearchViewModel
    abstract val sharedLinksViewModel: SharedLinksViewModel
    abstract val smartFeaturesViewModel: SmartFeaturesViewModel
    abstract val storyCardsViewModel: StoryCardsViewModel
    abstract val syncStatusViewModel: SyncStatusViewModel
    abstract val uploadDetailsViewModel: UploadDetailsViewModel
    abstract val vaultViewModel: VaultViewModel
    abstract val albumTimelineViewModel: (savedStateHandle: SavedStateHandle) -> AlbumTimelineViewModel
    abstract val cloudDestinationsViewModel: (savedStateHandle: SavedStateHandle) -> CloudDestinationsViewModel
    abstract val cloudUploadSettingsViewModel: (savedStateHandle: SavedStateHandle) -> CloudUploadSettingsViewModel
    abstract val copyMediaViewModel: (savedStateHandle: SavedStateHandle) -> CopyMediaViewModel
    abstract val framePickerViewModel: (savedStateHandle: SavedStateHandle) -> FramePickerViewModel
    abstract val freeUpSpaceViewModel: (savedStateHandle: SavedStateHandle) -> FreeUpSpaceViewModel
    abstract val personDetailViewModel: (savedStateHandle: SavedStateHandle) -> PersonDetailViewModel
    abstract val videoPlayerViewModelFactory: (savedStateHandle: SavedStateHandle, media: Media, dataSourceFactory: DataSource.Factory) -> VideoPlayerViewModel
    abstract val standaloneViewModel: (reviewMode: Boolean, isSecure: Boolean, dataList: List<Uri>) -> StandaloneViewModel
    abstract val locationsViewModel: (gpsLocationNameCity: String, gpsLocationNameCountry: String, latitude: Double?, longitude: Double?) -> LocationsViewModel

    // ── workers — resolved fresh per task by the @Worker adapters via WorkComponentHolder

    abstract val backupExportWorker: com.dot.gallery.core.backup.BackupExportWorker
    abstract val backupRestoreWorker: com.dot.gallery.core.backup.BackupRestoreWorker
    abstract val backupJobRuntime: com.dot.gallery.core.backup.BackupJobRuntime
    abstract val backupJobStore: com.dot.gallery.core.backup.BackupJobStore
    abstract val dataHealthWorker: com.dot.gallery.core.workers.DataHealthWorker
    abstract val dataHealthStore: com.dot.gallery.core.workers.DataHealthStore
    abstract val dataHealthControllerImpl: com.dot.gallery.core.workers.DataHealthControllerImpl

    abstract val cloudAlbumCopyWorker: CloudAlbumCopyWorker
    abstract val cloudDownloadWorker: CloudDownloadWorker
    abstract val cloudOfflineDownloadWorker: CloudOfflineDownloadWorker
    abstract val cloudSyncWorker: CloudSyncWorker
    abstract val cloudUploadWorker: CloudUploadWorker
    abstract val freeUpSpaceWorker: FreeUpSpaceWorker
    abstract val captureTimeIndexWorker: CaptureTimeIndexWorker
    abstract val categoryWorker: CategoryWorker
    abstract val classifierWorker: ClassifierWorker
    abstract val databaseEncryptionWorker: DatabaseEncryptionWorker
    abstract val databaseUpdaterWorker: DatabaseUpdaterWorker
    abstract val editBackupWorker: EditBackupWorker
    abstract val mediaCopyWorker: MediaCopyWorker
    abstract val metadataCollectionWorker: MetadataCollectionWorker
    abstract val metadataLocationRepairWorker: MetadataLocationRepairWorker
    abstract val modelDownloadWorker: ModelDownloadWorker
    abstract val ocrPackInstallWorker: com.dot.gallery.core.workers.OcrPackInstallWorker
    abstract val ocrPackStore: com.dot.gallery.core.ocr.model.OcrPackStore
    abstract val ocrRepository: com.dot.gallery.core.ocr.repository.OcrRepository
    abstract val ppOcrV5Engine: com.dot.gallery.core.ocr.engine.PpOcrV5Engine
    abstract val redactedLocationRepairWorker: RedactedLocationRepairWorker
    abstract val rotateMediaWorker: RotateMediaWorker
    abstract val floatingProgressRegistry: FloatingProgressRegistry
    abstract val mediaRotationTracker: MediaRotationTracker
    abstract val searchIndexerUpdaterWorker: SearchIndexerUpdaterWorker
    abstract val smartScanWorker: SmartScanWorker
    abstract val tempVaultCleanupWorker: TempVaultCleanupWorker
    abstract val vaultOperationWorker: VaultOperationWorker
    abstract val frameExportWorker: FrameExportWorker
    abstract val editorExportWorker: com.dot.gallery.core.workers.EditorExportWorker
}
