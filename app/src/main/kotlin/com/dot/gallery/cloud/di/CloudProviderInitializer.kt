/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.di

import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.CredentialEncryptor
import com.dot.gallery.cloud.core.auth.CloudConnectionErrorKind
import com.dot.gallery.cloud.core.auth.CloudConnectionException
import com.dot.gallery.cloud.core.MediaCapabilityProvider
import com.dot.gallery.cloud.core.ProviderInstanceFactory
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.cloud.sync.CloudIndexProgressManager
import com.dot.gallery.cloud.sync.fetchAllCloudIndexPages
import com.dot.gallery.cloud.sync.fetchCloudIndexPage
import com.dot.gallery.cloud.sync.shouldStartCloudIndex
import com.dot.gallery.core.Resource
import com.dot.gallery.core.backup.PendingCloudFavoriteStore
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

internal fun isNetworkSensitiveProviderType(providerType: ProviderType): Boolean =
    providerType == ProviderType.SMB || providerType == ProviderType.NFS

internal fun shouldReconfigureProvider(
    lastResolvedUrl: String?,
    resolvedUrl: String,
    connectionState: ConnectionState?,
    force: Boolean
): Boolean = force || lastResolvedUrl != resolvedUrl || connectionState != ConnectionState.CONNECTED

internal suspend fun retryCloudAuthentication(
    maxAttempts: Int = 3,
    delayBeforeRetry: suspend (Long) -> Unit = { delay(it) },
    authenticate: suspend () -> Unit
) {
    require(maxAttempts > 0)
    repeat(maxAttempts) { attempt ->
        try {
            authenticate()
            return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (attempt == maxAttempts - 1 || !error.isRetryableCloudAuthenticationFailure()) {
                throw error
            }
            delayBeforeRetry((attempt + 1) * 1_000L)
        }
    }
}

private fun Throwable.isRetryableCloudAuthenticationFailure(): Boolean {
    val errors = generateSequence(this) { it.cause }.toList()
    val classified = errors.filterIsInstance<CloudConnectionException>().firstOrNull()
    return if (classified != null) {
        classified.kind == CloudConnectionErrorKind.NETWORK ||
            classified.kind == CloudConnectionErrorKind.SERVER
    } else {
        errors.any { it is IOException && it !is SSLException }
    }
}

/**
 * Eagerly initializes all cloud providers by accepting the multibinding set.
 * Each provider module contributes to the set via @IntoSet, and
 * the provider's @Provides method handles registration into ProviderRegistry.
 *
 * Inject this class in GalleryApp.onCreate() to trigger initialization.
 * Call [initializeAsync] from a background coroutine to auto-configure
 * providers that have stored server configs.
 */
@Singleton
class CloudProviderInitializer @Inject constructor(
    private val providerFactories: Set<@JvmSuppressWildcards ProviderInstanceFactory>,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val credentialEncryptor: CredentialEncryptor,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudRepository: CloudRepository,
    private val urlResolver: ServerUrlResolver,
    private val offlineModeManager: OfflineModeManager,
    private val indexProgressManager: CloudIndexProgressManager,
    private val smartScanScheduler: SmartScanScheduler,
    private val pendingCloudFavoriteStore: PendingCloudFavoriteStore
) {

    private val factoriesByType by lazy { providerFactories.associateBy { it.providerType } }

    /** Last effective (resolved) server URL applied per account, to skip no-op reconfigures. */
    private val lastResolvedUrl = ConcurrentHashMap<Long, String>()
    private val accountMutexes = ConcurrentHashMap<Long, Mutex>()
    private val transientFailureConfigIds = ConcurrentHashMap.newKeySet<Long>()
    private val startupInitialization = CompletableDeferred<Unit>()

    /** Long-lived scope for non-blocking network prefetches (assets/trash). */
    private val prefetchScope = CoroutineScope(Dispatchers.IO)

    /**
     * App-lifetime scope for account reconfigures. Deliberately NOT tied to any ViewModel/UI
     * scope: a URL switch triggered from a settings screen must survive the user navigating
     * away, otherwise the in-flight re-authentication is cancelled ("Socket closed") and the
     * provider is left half-switched (new base URL, stale/absent auth) with no data reload.
     */
    private val reconfigureScope = CoroutineScope(Dispatchers.IO)

    /**
     * Pages through ALL of [provider]'s remote assets (and its trash) and caches them into
     * Room, non-blocking. This is what makes a provider's media appear in the timeline/albums.
     * Runs for BOTH startup auto-auth and runtime account registration so a freshly added
     * account populates immediately instead of only after the next app start.
     */
    private fun prefetchProviderData(provider: RemoteMediaProvider, label: String, configId: Long) {
        prefetchScope.launch {
            if (!shouldStartCloudIndex(offlineModeManager.effectiveOfflineNow)) return@launch
            indexProgressManager.start(configId, label)
            try {
                val previousSmartFeatureRevisions = cloudMediaDao.getSmartFeatureRevisions(configId)
                val total = fetchAllCloudIndexPages(
                    pageSize = PREFETCH_PAGE_SIZE,
                    maxPages = MAX_PREFETCH_PAGES,
                    fetchPage = { page, pageSize ->
                        fetchCloudIndexPage(PREFETCH_PAGE_TIMEOUT_MILLIS) {
                            provider.getRemoteAssets(page, pageSize).first()
                        }
                    },
                    onPage = { items, indexedCount ->
                        if (items.isNotEmpty()) cloudMediaDao.insertAll(items)
                        indexProgressManager.update(configId, indexedCount, label)
                    }
                ).getOrThrow()
                pendingCloudFavoriteStore.applyForAccount(
                    provider.providerType,
                    configId,
                    cloudMediaDao
                )
                printDebug("CloudProviderInitializer: Cached $total assets for $label")
                if (cloudMediaDao.getSmartFeatureRevisions(configId) != previousSmartFeatureRevisions) {
                    smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printDebug("CloudProviderInitializer: Asset prefetch failed for $label: ${e.message}")
            } finally {
                indexProgressManager.finish(configId)
            }
        }
        prefetchScope.launch {
            if (!shouldStartCloudIndex(offlineModeManager.effectiveOfflineNow)) return@launch
            try {
                val previousSmartFeatureRevisions = cloudMediaDao.getSmartFeatureRevisions(configId)
                val trashed = fetchCloudIndexPage(PREFETCH_PAGE_TIMEOUT_MILLIS) {
                    provider.getRemoteTrashed().first()
                }
                if (trashed is Resource.Success) {
                    trashed.data?.let { cloudMediaDao.insertAll(it) }
                    if (cloudMediaDao.getSmartFeatureRevisions(configId) != previousSmartFeatureRevisions) {
                        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printDebug("CloudProviderInitializer: Trash prefetch failed for $label: ${e.message}")
            }
        }
    }

    /**
     * Creates a fresh, UNconfigured provider instance for [type] (or null if that provider
     * is not built into this variant). Used by the add-account wizard to test a connection or
     * list remote albums before the config has been persisted/registered.
     */
    fun createTransientProvider(type: ProviderType): MediaCapabilityProvider? =
        factoriesByType[type]?.create()

    private fun accountMutex(configId: Long): Mutex =
        accountMutexes.getOrPut(configId) { Mutex() }

    private fun markAuthenticationFailed(configId: Long, error: Exception) {
        lastResolvedUrl.remove(configId)
        if (error.isRetryableCloudAuthenticationFailure()) transientFailureConfigIds.add(configId)
        else transientFailureConfigIds.remove(configId)
        registry.updateConnectionState(configId, ConnectionState.ERROR)
    }

    /**
     * Mints (or reuses), configures, authenticates and registers the provider instance for a
     * single account [configId]. Call after a new account is saved so it becomes usable
     * immediately, without waiting for the next app start. Must run off the main thread.
     */
    suspend fun registerAccount(configId: Long): Result<Unit> =
        accountMutex(configId).withLock { registerAccountUnlocked(configId) }

    private suspend fun registerAccountUnlocked(configId: Long): Result<Unit> {
        val entity = configDao.getById(configId)
            ?: return Result.failure(IllegalArgumentException("Cloud account not found"))
        if (!entity.isActive) return Result.failure(IllegalStateException("Cloud account is inactive"))
        CloudRuntimeSettings.apply(entity.toCloudServerConfig())
        val provider = (registry.getByConfigId(configId) as? RemoteMediaProvider)
            ?: (factoriesByType[entity.providerType]?.create() as? RemoteMediaProvider)
            ?: return Result.failure(IllegalStateException("Cloud provider is unavailable"))
        registry.register(entity.id, provider)
        registry.updateConnectionState(entity.id, ConnectionState.AUTHENTICATING)
        return try {
            val config = entity.toCloudServerConfig().let { cfg ->
                cfg.copy(
                    apiKey = cfg.apiKey?.let { credentialEncryptor.decrypt(it) },
                    password = cfg.password?.let { credentialEncryptor.decrypt(it) }
                )
            }
            val resolved = urlResolver.resolve(config)
            provider.configure(resolved)
            provider.authenticate(resolved).getOrThrow()
            lastResolvedUrl[entity.id] = resolved.serverUrl
            registry.updateConnectionState(entity.id, ConnectionState.CONNECTED)
            configDao.updateLastConnected(entity.id, System.currentTimeMillis())
            cloudRepository.notifyProviderConnected(entity.providerType, ConnectionState.CONNECTED)
            prefetchProviderData(provider, entity.displayName.ifBlank { entity.providerType.displayName }, entity.id)
            transientFailureConfigIds.remove(entity.id)
            printDebug("CloudProviderInitializer: Registered account ${entity.providerType} #${entity.id}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markAuthenticationFailed(entity.id, e)
            printDebug("CloudProviderInitializer: registerAccount failed for #${entity.id}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Auto-configure and authenticate remote providers that have an active
     * server config stored in the database. Must be called from a background
     * coroutine — never from the main thread.
     */
    suspend fun initializeAsync() {
        try {
            initializeStoredAccounts()
        } finally {
            startupInitialization.complete(Unit)
        }
    }

    private suspend fun initializeStoredAccounts() {
        val activeConfigs = configDao.getAll().first().filter { it.isActive }
        // Prime every account-qualified viewer/advanced preference before provider auth so UI and
        // playback never inherit settings from whichever account happened to initialize first.
        CloudRuntimeSettings.applyAll(activeConfigs.map { it.toCloudServerConfig() })
        for (storedEntity in activeConfigs) {
            accountMutex(storedEntity.id).withLock {
                val entity = configDao.getById(storedEntity.id)?.takeIf { it.isActive }
                    ?: return@withLock
                if (registry.connectionStates.value[entity.id] == ConnectionState.CONNECTED) {
                    return@withLock
                }
                CloudRuntimeSettings.apply(entity.toCloudServerConfig())
                val factory = factoriesByType[entity.providerType] ?: return@withLock
                val provider = factory.create() as? RemoteMediaProvider ?: return@withLock
                registry.register(entity.id, provider)
                registry.updateConnectionState(entity.id, ConnectionState.AUTHENTICATING)
                try {
                    val config = entity.toCloudServerConfig().let { cfg ->
                        cfg.copy(
                            apiKey = cfg.apiKey?.let { credentialEncryptor.decrypt(it) },
                            password = cfg.password?.let { credentialEncryptor.decrypt(it) }
                        )
                    }
                    val resolved = urlResolver.resolve(config)
                    retryCloudAuthentication {
                        provider.configure(resolved)
                        provider.authenticate(resolved).getOrThrow()
                    }
                    transientFailureConfigIds.remove(entity.id)
                    lastResolvedUrl[entity.id] = resolved.serverUrl
                    registry.updateConnectionState(entity.id, ConnectionState.CONNECTED)
                    configDao.updateLastConnected(entity.id, System.currentTimeMillis())
                    cloudRepository.notifyProviderConnected(entity.providerType, ConnectionState.CONNECTED)
                    printDebug("CloudProviderInitializer: Auto-authenticated ${entity.providerType} #${entity.id} with ${resolved.serverUrl}")
                    // Proactive cache: fetch fresh data from network in parallel (non-blocking).
                    prefetchProviderData(provider, entity.displayName.ifBlank { entity.providerType.displayName }, entity.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markAuthenticationFailed(entity.id, e)
                    printDebug("CloudProviderInitializer: Auto-auth failed for ${entity.providerType} #${entity.id}: ${e.message}")
                }
            }
        }
    }

    suspend fun retryTransientAuthenticationFailures() {
        startupInitialization.await()
        transientFailureConfigIds.toList().forEach { configId ->
            accountMutex(configId).withLock {
                if (!transientFailureConfigIds.remove(configId)) return@withLock
                val result = registerAccountUnlocked(configId)
                if (result.exceptionOrNull()?.isRetryableCloudAuthenticationFailure() == true) {
                    transientFailureConfigIds.add(configId)
                }
            }
        }
    }

    /**
     * Re-resolve and re-apply the server URL for a single account [configId] after its config
     * changed — e.g. the user toggled automatic URL switching or edited the local URL/SSID in
     * settings. Unlike [reconfigureActiveProviders] this does NOT require [autoUrlSwitch] to be
     * enabled, so turning the feature OFF correctly reverts the provider to its external URL.
     * No-op when the effective URL is unchanged. Must run off the main thread.
     */
    fun reconfigureAccountAsync(configId: Long) {
        reconfigureScope.launch { reconfigureAccount(configId) }
    }

    suspend fun applyRestoredAccount(configId: Long) {
        val entity = configDao.getById(configId) ?: return
        CloudRuntimeSettings.apply(entity.toCloudServerConfig())
        if (entity.isActive) {
            if (registry.getByConfigId(configId) == null) registerAccount(configId)
            else {
                lastResolvedUrl.remove(configId)
                reconfigureAccount(configId)
            }
        } else {
            (registry.getByConfigId(configId) as? com.dot.gallery.cloud.core.Disconnectable)?.disconnect()
            registry.unregister(configId)
        }
    }

    suspend fun reconfigureAccount(configId: Long) {
        reconfigureAccount(configId, force = false)
    }

    private suspend fun reconfigureAccount(configId: Long, force: Boolean) {
        accountMutex(configId).withLock {
            val entity = configDao.getById(configId) ?: return@withLock
            if (!entity.isActive) return@withLock
            val provider = registry.getByConfigId(configId) as? RemoteMediaProvider ?: return@withLock
            try {
                val config = entity.toCloudServerConfig().let { cfg ->
                    cfg.copy(
                        apiKey = cfg.apiKey?.let { credentialEncryptor.decrypt(it) },
                        password = cfg.password?.let { credentialEncryptor.decrypt(it) }
                    )
                }
                val resolved = urlResolver.resolve(config)
                if (!shouldReconfigureProvider(
                        lastResolvedUrl = lastResolvedUrl[entity.id],
                        resolvedUrl = resolved.serverUrl,
                        connectionState = registry.connectionStates.value[entity.id],
                        force = force
                    )
                ) return@withLock
                registry.updateConnectionState(entity.id, ConnectionState.AUTHENTICATING)
                provider.configure(resolved)
                provider.authenticate(resolved).getOrThrow()
                transientFailureConfigIds.remove(entity.id)
                lastResolvedUrl[entity.id] = resolved.serverUrl
                registry.updateConnectionState(entity.id, ConnectionState.CONNECTED)
                configDao.updateLastConnected(entity.id, System.currentTimeMillis())
                cloudRepository.notifyProviderConnected(entity.providerType, ConnectionState.CONNECTED)
                printDebug("CloudProviderInitializer: Reconfigured account #${entity.id} -> ${resolved.serverUrl}")
                // Re-pull data after URL changes; route-only reconnects avoid an eager full rescan.
                if (!force) {
                    prefetchProviderData(provider, entity.displayName.ifBlank { entity.providerType.displayName }, entity.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markAuthenticationFailed(entity.id, e)
                printDebug("CloudProviderInitializer: reconfigureAccount failed for #${entity.id}: ${e.message}")
            }
        }
    }

    suspend fun reconfigureNetworkSensitiveProviders() {
        configDao.getAll().first()
            .filter {
                it.isActive && isNetworkSensitiveProviderType(it.providerType) &&
                    registry.connectionStates.value[it.id] == ConnectionState.CONNECTED
            }
            .forEach { reconfigureAccount(it.id, force = true) }
    }

    /**
     * Re-resolve and re-apply server URLs for active auto-URL-switching providers. Intended to
     * be called when the network changes (e.g. moving between the local network and mobile data).
     * Only providers whose effective URL actually changed are reconfigured + re-authenticated.
     * Safe to call repeatedly; must run off the main thread.
     */
    suspend fun reconfigureActiveProviders() {
        val configIds = configDao.getAll().first()
            .filter { it.isActive && it.autoUrlSwitch }
            .map { it.id }
        for (configId in configIds) {
            accountMutex(configId).withLock {
                val entity = configDao.getById(configId)
                    ?.takeIf { it.isActive && it.autoUrlSwitch }
                    ?: return@withLock
                val provider = registry.getByConfigId(configId) as? RemoteMediaProvider
                    ?: return@withLock
                try {
                    val config = entity.toCloudServerConfig().let { cfg ->
                        cfg.copy(
                            apiKey = cfg.apiKey?.let { credentialEncryptor.decrypt(it) },
                            password = cfg.password?.let { credentialEncryptor.decrypt(it) }
                        )
                    }
                    val resolved = urlResolver.resolve(config)
                    if (lastResolvedUrl[entity.id] == resolved.serverUrl &&
                        registry.connectionStates.value[entity.id] == ConnectionState.CONNECTED
                    ) return@withLock
                    registry.updateConnectionState(entity.id, ConnectionState.AUTHENTICATING)
                    provider.configure(resolved)
                    provider.authenticate(resolved).getOrThrow()
                    transientFailureConfigIds.remove(entity.id)
                    lastResolvedUrl[entity.id] = resolved.serverUrl
                    registry.updateConnectionState(entity.id, ConnectionState.CONNECTED)
                    configDao.updateLastConnected(entity.id, System.currentTimeMillis())
                    cloudRepository.notifyProviderConnected(entity.providerType, ConnectionState.CONNECTED)
                    printDebug("CloudProviderInitializer: Reconfigured ${entity.providerType} #${entity.id} -> ${resolved.serverUrl}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markAuthenticationFailed(entity.id, e)
                    printDebug("CloudProviderInitializer: Reconfigure failed for ${entity.providerType} #${entity.id}: ${e.message}")
                }
            }
        }
    }

    companion object {
        /** Page size for the startup asset prefetch. */
        private const val PREFETCH_PAGE_SIZE = 200
        private const val PREFETCH_PAGE_TIMEOUT_MILLIS = 5 * 60_000L

        /**
         * Hard cap on prefetch pages (safety valve against a misbehaving provider that never
         * returns a short page). 500 pages * 200 = 100k assets, well beyond typical libraries.
         */
        private const val MAX_PREFETCH_PAGES = 500
    }
}
