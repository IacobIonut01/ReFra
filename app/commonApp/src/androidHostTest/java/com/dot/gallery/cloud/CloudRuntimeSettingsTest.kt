/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.ProviderType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class CloudRuntimeSettingsTest {

    @Before
    fun setUp() {
        CloudRuntimeSettings.applyAll(emptyList())
    }

    @After
    fun tearDown() {
        CloudRuntimeSettings.applyAll(emptyList())
    }

    @Test
    fun twoAccountsResolveOnlySettingsFromUriConfigId() {
        CloudRuntimeSettings.applyAll(
            listOf(
                config(id = 101L, readOnly = true, loadOriginalImage = false),
                config(id = 202L, readOnly = false, loadOriginalImage = true),
            )
        )

        val first = CloudRuntimeSettings.settingsForCloudUri(
            "cloud://IMMICH/shared-id?size=preview&cfg=101"
        )
        val second = CloudRuntimeSettings.settingsForCloudUri(
            "cloud://IMMICH/shared-id?size=preview&cfg=202"
        )
        val missing = CloudRuntimeSettings.settingsForCloudUri(
            "cloud://IMMICH/shared-id?size=preview&cfg=303"
        )

        assertTrue(first.readOnlyMode)
        assertFalse(first.loadOriginalImage)
        assertFalse(second.readOnlyMode)
        assertTrue(second.loadOriginalImage)
        assertFalse(missing.readOnlyMode)
        assertFalse(missing.loadOriginalImage)
    }

    @Test
    fun accountUpdateEmitsAndDoesNotOverwriteOtherAccount() = runBlocking {
        CloudRuntimeSettings.applyAll(
            listOf(
                config(id = 101L, readOnly = false, loadOriginalImage = false),
                config(id = 202L, readOnly = false, loadOriginalImage = true),
            )
        )
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            CloudRuntimeSettings.settingsByConfigId
                .map { it.getValue(101L).readOnlyMode }
                .distinctUntilChanged()
                .take(2)
                .toList()
        }

        CloudRuntimeSettings.apply(config(id = 101L, readOnly = true, loadOriginalImage = false))

        assertEquals(listOf(false, true), observed.await())
        assertTrue(CloudRuntimeSettings.settingsByConfigId.value.getValue(202L).loadOriginalImage)
    }

    @Test
    fun serializedConfigWithoutViewerFieldsKeepsHistoricalDefaults() {
        val decoded = Json.decodeFromString<CloudServerConfig>(
            """
            {
              "id": 101,
              "providerType": "IMMICH",
              "serverUrl": "https://example.test"
            }
            """.trimIndent()
        )

        assertTrue(decoded.loadPreviewImage)
        assertFalse(decoded.loadOriginalImage)
        assertTrue(decoded.autoPlayVideos)
        assertFalse(decoded.loopVideos)
        assertFalse(decoded.forceOriginalVideo)
        assertFalse(decoded.readOnlyMode)
    }

    private fun config(
        id: Long,
        readOnly: Boolean,
        loadOriginalImage: Boolean,
    ) = CloudServerConfig(
        id = id,
        providerType = ProviderType.IMMICH,
        serverUrl = "https://$id.example.test",
        readOnlyMode = readOnly,
        loadOriginalImage = loadOriginalImage,
    )
}
