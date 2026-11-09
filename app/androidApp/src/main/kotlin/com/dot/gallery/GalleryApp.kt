/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import android.annotation.SuppressLint
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.NetworkConstraintGuard
import com.dot.gallery.core.workers.EditorExportBridgeHolder
import com.dot.gallery.editor.GalleryEditorExportBridge
import com.dot.gallery.editor.GalleryRawDecoder
import com.dot.gallery.editor.android.EditorExportDispatch
import com.dot.gallery.editor.android.EditorRawDispatch
import com.dot.gallery.core.work.workScheduler
import dev.brewkits.kmpworkmanager.KmpWorkManager
import dev.brewkits.kmpworkmanager.generated.AndroidWorkerFactoryGenerated
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.util.CloudMediaDownloadHook
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.cloud.image.fetchHttpRange
import com.dot.gallery.cloud.image.supportCloudMedia
import com.dot.gallery.cloud.network.LanBindingSocketFactory
import com.dot.gallery.cloud.offline.CloudCacheInterceptor
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.AppIdentity
import com.dot.gallery.domain.DomainHooks
import com.dot.gallery.core.logging.AppLogger
import com.dot.gallery.core.logging.CrashCapture
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.metadata.MetadataSanitizer
import com.dot.gallery.core.sandbox.IsolatedImageDecoder
import com.dot.gallery.core.sandbox.SandboxedDecoderHolder
import com.dot.gallery.core.security.AdvancedProtectionMonitor
import com.dot.gallery.core.decoder.supportApng
import com.dot.gallery.core.decoder.supportLocalThumbnailDecoders
import com.dot.gallery.core.decoder.supportHeifDecoder
import com.dot.gallery.core.decoder.supportAnimatedJxlDecoder
import com.dot.gallery.core.decoder.supportJxlDecoder
import com.dot.gallery.core.decoder.supportSandboxedHeifDecoder
import com.dot.gallery.core.decoder.supportSandboxedJxlDecoder
import com.dot.gallery.core.decoder.supportVaultDecoder
import com.dot.gallery.core.decoder.supportVideoFrame2
import com.dot.gallery.core.decoder.supportPsdDecoder
import com.dot.gallery.core.decoder.supportJp2Decoder
import com.dot.gallery.core.decoder.supportTiffDecoder
import com.dot.gallery.core.decoder.supportRawDecoder
import com.dot.gallery.core.workers.enqueueCaptureTimeIndex
import com.dot.gallery.core.workers.enqueueMetadataLocationRepair
import com.dot.gallery.core.workers.enqueueRedactedLocationRepair
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.app.presentation.frameextract.FrameSourceCleanup
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.decode.supportAnimatedHeif
import com.github.panpf.sketch.decode.supportAnimatedWebp
import com.github.panpf.sketch.decode.supportGif
import com.github.panpf.sketch.decode.supportSvg
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.request.saveCellularTraffic
import com.github.panpf.sketch.request.supportPauseLoadWhenScrolling
import com.github.panpf.sketch.resize.Precision
import com.dot.gallery.injection.AppComponent
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.dot.gallery.core.metrics.StartupTracer
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.dot.gallery.core.util.or
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.cloud.sync.FreeUpSpaceAutoScheduler
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.core.workers.scheduleDataHealthPeriodic
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.workers.TempVaultCleanupWorker
import com.dot.gallery.core.presentation.util.StaticMapDefaults
import com.dot.gallery.core.presentation.util.runNetworkCallbackSetup
import android.content.Context
import com.dot.gallery.injection.ComponentOwner
import com.dot.gallery.injection.create
import com.dot.gallery.app.BuildConfig as AppBuildConfig


/** Reserved [com.dot.gallery.cloud.core.ProviderRegistry] config id for the local People provider. */
private const val LOCAL_PEOPLE_CONFIG_ID = -1000L

class GalleryApp : Application(), SingletonSketch.Factory, ComponentOwner {

    /**
     * kotlin-inject root graph — the single owner of every singleton.
     * `injectViewModel` reads it; lazy so isolated processes never build it.
     */
    override val component: AppComponent by lazy { AppComponent::class.create(this) }

    /**
     * Push variant-scoped values into the commonApp `BuildConfig` shim.
     * Runs before providers attach (which precedes onCreate), so every reader
     * — including ContentProvider.attachInfo paths — sees real values.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        BuildConfig.VERSION_NAME = AppBuildConfig.VERSION_NAME
        BuildConfig.VERSION_CODE = AppBuildConfig.VERSION_CODE
        BuildConfig.APPLICATION_ID = AppBuildConfig.APPLICATION_ID
        BuildConfig.BUILD_TYPE = AppBuildConfig.BUILD_TYPE
        BuildConfig.FLAVOR = AppBuildConfig.FLAVOR
        BuildConfig.CONTENT_AUTHORITY = AppBuildConfig.CONTENT_AUTHORITY
        BuildConfig.ALLOW_INSECURE_TLS = AppBuildConfig.ALLOW_INSECURE_TLS
        BuildConfig.ALLOW_ALL_FILES_ACCESS = AppBuildConfig.ALLOW_ALL_FILES_ACCESS
        BuildConfig.ENABLE_INDEXING = AppBuildConfig.ENABLE_INDEXING
        BuildConfig.ML_MODELS_BUNDLED = AppBuildConfig.ML_MODELS_BUNDLED
    }

    @SuppressLint("NewApi")
    override fun createSketch(context: PlatformContext): Sketch = StartupTracer.trace("Sketch.create") { Sketch.Builder(this).apply {
        components {
            supportPauseLoadWhenScrolling()
            supportSvg()
            supportGif()
            supportApng()
            supportVideoFrame2()
            supportAnimatedWebp()
            supportAnimatedHeif()
            supportSandboxedHeifDecoder()
            supportSandboxedJxlDecoder()
            supportAnimatedJxlDecoder()
            supportHeifDecoder()
            supportJxlDecoder()
            supportPsdDecoder()
            supportJp2Decoder()
            supportTiffDecoder()
            supportRawDecoder()
            supportVaultDecoder()
            supportCloudMedia()
            supportLocalThumbnailDecoders()
        }
        val diskCache = DiskCache.Builder(context, FileSystem.SYSTEM)
            .directory(sketchCacheDirectory(context.cacheDir))
            .maxSize(150 * 1024 * 1024).build()

        memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.70)
                .build()
        }

        decodeParallelismLimited(maxOf(2, Runtime.getRuntime().availableProcessors().coerceAtMost(6)))

        resultCache(diskCache)
        downloadCache(diskCache)

        globalImageOptions(
            ImageOptions {
                crossfade(false)
                precision(Precision.LESS_PIXELS)
                saveCellularTraffic(false)
            }
        )
    }.build() }

    private val scheduler by lazy { component.backgroundTaskScheduler }

    private val smartScanScheduler: SmartScanScheduler by lazy { component.smartScanScheduler }

    private val repository: MediaRepository by lazy { component.mediaRepository }

    private val metadataSanitizer: MetadataSanitizer by lazy { component.metadataSanitizer }

    private val mediaDistributor: MediaDistributor by lazy { component.mediaDistributor }

    private val modelManager: ModelManager by lazy { component.modelManager }

    private val isolatedImageDecoder: IsolatedImageDecoder by lazy { component.isolatedImageDecoder }

    private val providerRegistry: ProviderRegistry by lazy { component.providerRegistry }

    private val cloudProviderInitializer: CloudProviderInitializer by lazy { component.cloudProviderInitializer }

    private val cloudSyncScheduler: CloudSyncScheduler by lazy { component.cloudSyncScheduler }

    private val freeUpSpaceAutoScheduler: FreeUpSpaceAutoScheduler by lazy { component.freeUpSpaceAutoScheduler }

    private val localPeopleProvider: LocalPeopleProvider by lazy { component.localPeopleProvider }

    private val cloudMediaCache: CloudMediaCache by lazy { component.cloudMediaCache }

    private val offlineModeManager: OfflineModeManager by lazy { component.offlineModeManager }

    private val startupGate: StartupWorkGate by lazy { component.startupWorkGate }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Module-side print*/logEvent helpers route through these sinks — install
        // before anything logs or traces.
        AppLogger.sink = AppLog::log
        AppIdentity.applicationId = BuildConfig.APPLICATION_ID
        DomainHooks.applicationId = BuildConfig.APPLICATION_ID
        DomainHooks.errorLogger = { tag, message, throwable ->
            AppLog.log(LogLevel.ERROR, tag, message, throwable)
        }
        DomainHooks.httpRangeFetcher = ::fetchHttpRange
        StaticMapDefaults.apiKey = BuildConfig.CARTO_BASEMAP_KEY
        AppIdentity.allowAllFilesAccess = BuildConfig.ALLOW_ALL_FILES_ACCESS
        AppIdentity.mlModelsBundled = BuildConfig.ML_MODELS_BUNDLED
        AppIdentity.isReleaseBuild = BuildConfig.BUILD_TYPE == "release"
        AppIdentity.contentAuthority = BuildConfig.CONTENT_AUTHORITY
        AppIdentity.versionName = BuildConfig.VERSION_NAME
        AppIdentity.versionCode = BuildConfig.VERSION_CODE.toLong()
        AppIdentity.flavor = BuildConfig.FLAVOR
        AppIdentity.buildType = BuildConfig.BUILD_TYPE
        CloudTrace.persistentSink = { level, tag, message, throwable ->
            AppLog.log(level, tag, message, throwable)
        }
        CloudTrace.verboseLogging = { CloudRuntimeSettings.verboseLoggingEnabled }
        CloudMediaDownloadHook.downloadCloudMedia = CloudMediaDownloader::downloadCloudMedia
        CloudMediaDownloadHook.downloadCloudMediaExact = CloudMediaDownloader::downloadCloudMediaExact
        AppLogger.breadcrumbSink = AppLog::breadcrumb
        AppLogger.debugLogToSystemLog = BuildConfig.BUILD_TYPE != "release"
        com.dot.gallery.core.logging.crashWriter = AppLog::writeCrashSync
        StartupTracer.clock = SystemClock::elapsedRealtime
        StartupTracer.currentThreadName = { Thread.currentThread().name }
        StartupTracer.logDebug = { Log.d("StartupPerf", it) }
        StartupTracer.logInfo = { Log.i("StartupPerf", it) }
        val onCreateSpan = StartupTracer.begin("App.onCreate")
        // Isolated-process services inherit this Application class but must NOT
        // run Hilt injection (WorkManager/JobScheduler are unavailable there).
        if (getProcessName() != packageName) return

        StartupTracer.trace("App.super.onCreate") {
            super.onCreate()
        }
        StartupTracer.trace("AppLog.init") {
            AppLog.init(this@GalleryApp)
            CrashCapture.install()
        }
        // Offline builds strip ACCESS_NETWORK_STATE: purge persisted network-constrained
        // WorkSpecs before WorkManager schedules them and arm the scheduler guard so no
        // new constrained spec can be enqueued (see NetworkConstraintGuard).
        NetworkConstraintGuard.init(this@GalleryApp)
        // kmpworkmanager owns androidx.work's initialization (the androidx WorkManagerInitializer
        // is disabled in the manifest) — must run before any scheduler access or worker dispatch.
        StartupTracer.trace("KmpWorkManager.initialize") {
            KmpWorkManager.initialize(this@GalleryApp, AndroidWorkerFactoryGenerated())
            // WorkManager's database may first exist now — rearm so the write-time
            // trigger lands on the fresh schema and any constrained spec written by
            // library-internal rescheduling during init is removed.
            NetworkConstraintGuard.rearm()
            WorkComponentHolder.install(component)
            // Editor exports run as workers with a live notification —
            // the bridge is both the sessions' dispatch hook and the
            // worker-side executor.
            GalleryEditorExportBridge(this@GalleryApp).also { bridge ->
                EditorExportBridgeHolder.bridge = bridge
                EditorExportDispatch.photo = bridge
                EditorExportDispatch.video = bridge
                EditorRawDispatch.decoder = GalleryRawDecoder
            }
        }
        appScope.launch {
            Settings.Misc.getDeveloperModeFlow(this@GalleryApp).collectLatest {
                AppLog.setEnabled(it)
            }
        }
        appScope.launch {
            Settings.Misc.getDevLogMinLevelFlow(this@GalleryApp).collectLatest { name ->
                AppLog.minLevel =
                    runCatching { LogLevel.valueOf(name) }.getOrDefault(LogLevel.DEBUG)
            }
        }
        appScope.launch {
            Settings.Misc.getDevLogBreadcrumbsFlow(this@GalleryApp).collectLatest {
                AppLog.breadcrumbsEnabled = it
            }
        }
        appScope.launch {
            startupGate.awaitFirstContent()
            NetworkConstraintGuard.rearm()
            scheduler.enqueueCaptureTimeIndex()
            scheduler.enqueueMetadataLocationRepair()
            scheduler.enqueueRedactedLocationRepair()
            metadataSanitizer.recoverPendingTransactions()
            FrameSourceCleanup.sweep(this@GalleryApp)
        }

        CloudFetcherRegistryHolder.registry = providerRegistry
        // Register on-device local capability providers (People). Keyed by a reserved negative
        // config id so it never collides with real cloud account ids. isAvailable() gates on the
        // face model being installed, so it stays hidden until models are present.
        providerRegistry.register(LOCAL_PEOPLE_CONFIG_ID, localPeopleProvider)
        // ONE shared OkHttp client for all cloud image/video loading (Glide, Sketch, ZoomImage,
        // ExoPlayer). A single pooled client keeps connections warm — a big win for a flinging
        // grid hitting the same host repeatedly — and a disk cache avoids re-downloading
        // thumbnails across sessions. Previously a client was only created on insecure-TLS builds
        // (and with no cache), so secure builds fell back to a fresh, uncached OkHttpClient() per
        // request.
        run {
            val builder = OkHttpClient.Builder()
                .cache(Cache(File(cacheDir, "cloud_http_cache"), 256L * 1024 * 1024))
                // Persistent offline cache: serves keyed image requests from disk (even online),
                // fails fast when offline + uncached, and write-through-caches new responses.
                .addInterceptor(CloudCacheInterceptor(cloudMediaCache, offlineModeManager))
                // Bind LAN-destined image/video sockets to Wi-Fi so thumbnails load from a
                // local server even when that Wi-Fi has no internet (default net = mobile data).
                .socketFactory(LanBindingSocketFactory(this))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
            if (BuildConfig.ALLOW_INSECURE_TLS) {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            }
            CloudFetcherRegistryHolder.okHttpClient = builder.build()
        }

        StartupTracer.trace("AdvancedProtectionMonitor.init") {
            AdvancedProtectionMonitor.init(this)
        }

        StartupTracer.trace("SandboxedDecoderHolder.init") {
            SandboxedDecoderHolder.init(isolatedImageDecoder, this)
        }

        appScope.launch {
            startupGate.awaitFirstContent()
            StartupTracer.trace("TempVaultCleanupWorker.schedule") {
                TempVaultCleanupWorker.schedule(scheduler)
            }
            // One-time cleanup of leaked vault temp files for users upgrading from affected versions
            TempVaultCleanupWorker.runLegacyFilesdirCleanup(this@GalleryApp)
        }

        // Initialize ML models (copies from assets on withML, checks presence on noML)
        // Auto-configure cloud providers asynchronously (off main thread)
        val cloudInitialization = appScope.async {
            startupGate.awaitFirstContent()
            runCatching { cloudProviderInitializer.initializeAsync() }
                .onFailure { if (it is CancellationException) throw it }
            runCatching { cloudSyncScheduler.reconcile() }
                .onFailure { if (it is CancellationException) throw it }
            runCatching { freeUpSpaceAutoScheduler.syncAll() }
                .onFailure { if (it is CancellationException) throw it }
        }
        appScope.launch {
            startupGate.awaitFirstContent()
            listOf(
                "SearchIndexerUpdater",
                "DatabaseUpdaterWorker",
                "MetadataCollection",
                "FaceIndexer",
                "CategoryWorker"
            ).forEach { scheduler.cancel(it) }
            scheduler.cancelByTag("CategoryClassifier")
            scheduler.cancelByTag("ImageClassifier")
            listOf(
                async {
                    runCatching {
                        StartupTracer.trace("ModelManager.initializeModels") {
                            modelManager.initializeModels()
                        }
                    }.onFailure { if (it is CancellationException) throw it }
                },
                cloudInitialization
            ).awaitAll()
            if (!smartScanScheduler.resumeActiveRun()) {
                smartScanScheduler.automaticIfNeeded(SmartScanFeature.ALL_MASK)
            }
            runCatching { component.backupJobRuntime.recoverActiveJob() }
                .onFailure { if (it is CancellationException) throw it }
            runCatching {
                com.dot.gallery.core.work.workScheduler()
                    .scheduleDataHealthPeriodic()
            }.onFailure { if (it is CancellationException) throw it }
        }

        // Re-resolve auto-switching URLs and renew route-sensitive connections when the network
        // changes (e.g. moving between local Wi-Fi, mobile data, and a VPN).
        registerNetworkChangeReconfigure()

        // "Sync when app is opened": on every foreground transition enqueue
        // a one-time sync/upload/download pass for sync-enabled accounts so local
        // photo changes and remote changes land immediately. Throttled + gated by
        // the setting inside the scheduler.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    startupGate.awaitFirstContent()
                    runCatching { cloudSyncScheduler.syncOnAppOpen() }
                        .onFailure { if (it is CancellationException) throw it }
                }
            }
        })

        StartupTracer.end(onCreateSpan)
    }

    /**
     * Registers a default-network callback that re-resolves auto-switching server URLs and renews
     * route-sensitive connections whenever connectivity changes, so providers transparently follow
     * the active local, mobile, or VPN route.
     */
    private fun registerNetworkChangeReconfigure() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        var currentDefaultNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val routeChanged = network != currentDefaultNetwork
                currentDefaultNetwork = network
                appScope.launch {
                    startupGate.awaitFirstContent()
                    cloudProviderInitializer.retryTransientAuthenticationFailures()
                    if (routeChanged) cloudProviderInitializer.reconfigureNetworkSensitiveProviders()
                    cloudProviderInitializer.reconfigureActiveProviders()
                }
            }

            override fun onLost(network: Network) {
                if (network == currentDefaultNetwork) currentDefaultNetwork = null
                appScope.launch {
                    startupGate.awaitFirstContent()
                    cloudProviderInitializer.reconfigureActiveProviders()
                }
            }
        }
        if (!runNetworkCallbackSetup(
                initializeCurrentNetwork = { currentDefaultNetwork = connectivityManager.activeNetwork },
                registerCallback = { connectivityManager.registerDefaultNetworkCallback(callback) }
            )) {
            // ACCESS_NETWORK_STATE unavailable or callback registration failed; skip auto-switch.
            return
        }
    }

}