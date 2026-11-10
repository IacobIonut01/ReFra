/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import com.dot.gallery.cloud.core.capabilities.MapCapableProvider
import com.dot.gallery.cloud.core.capabilities.MemoriesCapableProvider
import com.dot.gallery.cloud.core.capabilities.OcrCapableProvider
import com.dot.gallery.cloud.core.capabilities.TextSearchCapableProvider
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.ShareLinkCapableProvider
import com.dot.gallery.cloud.core.capabilities.SmartSearchCapableProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.domain.annotation.Synchronized
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.core.logging.printDebug


@Singleton
class ProviderRegistry @Inject constructor() {

    // Keyed by cloud account id (CloudServerConfigEntity.id), NOT by ProviderType, so that
    // several accounts of the same provider type (e.g. two Immich servers) can be registered
    // simultaneously, each as its own isolated instance.
    val _providers = mutableMapOf<Long, MediaCapabilityProvider>()

    private val _connectionStates = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<Long, ConnectionState>> = _connectionStates.asStateFlow()

    @Synchronized
    fun register(configId: Long, provider: MediaCapabilityProvider) {
        _providers[configId] = provider
        val state = (provider as? RemoteMediaProvider)?.connectionState?.value
        if (state != null) updateConnectionState(configId, state)
        printDebug("ProviderRegistry: Registered ${provider.providerType.displayName} " +
                "(account #$configId) with capabilities: ${provider.capabilities}")
    }

    @Synchronized
    fun updateConnectionState(configId: Long, state: ConnectionState) {
        _connectionStates.value = _connectionStates.value + (configId to state)
    }

    @Synchronized
    fun unregister(configId: Long) {
        _providers.remove(configId)
        _connectionStates.value = _connectionStates.value - configId
    }

    @Synchronized
    fun getAll(): List<MediaCapabilityProvider> = _providers.values.toList()

    @Synchronized
    fun getAvailable(): List<MediaCapabilityProvider> =
        _providers.values.filter { it.isAvailable }

    /** Instance for a specific account. Preferred lookup once a config id is known. */
    @Synchronized
    fun getByConfigId(configId: Long): MediaCapabilityProvider? = _providers[configId]

    /** All registered instances of a given provider type (one per account). */
    @Synchronized
    fun getAllForType(type: ProviderType): List<MediaCapabilityProvider> =
        _providers.values.filter { it.providerType == type }

    /**
     * Backward-compatible lookup by type: returns the first registered instance of [type].
     * Call sites that must address a specific account should use [getByConfigId] instead.
     */
    @Synchronized
    fun get(type: ProviderType): MediaCapabilityProvider? =
        _providers.values.firstOrNull { it.providerType == type }

    inline fun <reified T : MediaCapabilityProvider> getByCapability(): List<T> =
        getAll().filterIsInstance<T>().filter { it.isAvailable }

    fun hasCapability(cap: ProviderCapability): Boolean =
        getAll().any { it.isAvailable && cap in it.capabilities }

    fun hasAnyProvider(): Boolean = getAll().isNotEmpty()

    fun hasConfiguredProvider(): Boolean = getAll().any { it.isAvailable }

    fun getRemoteProviders(): List<RemoteMediaProvider> = getByCapability()
    fun getPeopleProviders(): List<PeopleCapableProvider> = getByCapability()
    fun getOcrProviders(): List<OcrCapableProvider> = getByCapability()
    fun getSmartSearchProviders(): List<SmartSearchCapableProvider> = getByCapability()
    fun getTextSearchProviders(): List<TextSearchCapableProvider> = getByCapability()
    fun getMapProviders(): List<MapCapableProvider> = getByCapability()
    fun getShareLinkProviders(): List<ShareLinkCapableProvider> = getByCapability()
    fun getSyncProviders(): List<SyncCapableProvider> = getByCapability()
    fun getMemoriesProviders(): List<MemoriesCapableProvider> = getByCapability()
}

/**
 * Resolve the [RemoteMediaProvider] for a parsed cloud URI: prefer the exact account instance
 * ([CloudUri.configId]) and fall back to the first instance of the type for back-compat with
 * URIs minted before the `cfg` query param existed.
 */
fun ProviderRegistry.resolveRemote(providerType: ProviderType, configId: Long): RemoteMediaProvider? =
    ((if (configId > 0L) getByConfigId(configId) else null) ?: get(providerType)) as? RemoteMediaProvider
