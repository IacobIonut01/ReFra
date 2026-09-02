package com.dot.gallery

import android.os.Build
import com.dot.gallery.core.Settings
import com.dot.gallery.core.encryption.EncryptionBackendState
import com.dot.gallery.core.encryption.EncryptionRuntimeState
import com.dot.gallery.core.presentation.components.util.MediaAccessState
import com.dot.gallery.core.presentation.components.util.resolveMediaAccessState
import com.dot.gallery.feature_node.presentation.dateformat.dateFormatEditorText
import com.dot.gallery.feature_node.presentation.help.data.SettingsSearchRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTruthRegressionTest {

    @Test
    fun selectedPhotosPermissionCountsAsLimitedMediaAccess() {
        assertEquals(
            MediaAccessState.LIMITED,
            resolveMediaAccessState(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                selectedMediaGranted = true,
            )
        )
        assertEquals(
            MediaAccessState.NONE,
            resolveMediaAccessState(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
    }

    @Test
    fun plaintextFallbackIsNeverReportedAsFullyEncrypted() {
        val fallback = EncryptionRuntimeState(
            settings = EncryptionBackendState.PLAINTEXT_FALLBACK,
            database = EncryptionBackendState.ENCRYPTED,
        )
        assertTrue(fallback.hasPlaintextFallback)
        assertFalse(fallback.fullyEncrypted)

        val encrypted = EncryptionRuntimeState(
            settings = EncryptionBackendState.ENCRYPTED,
            database = EncryptionBackendState.ENCRYPTED,
        )
        assertTrue(encrypted.fullyEncrypted)
    }

    @Test
    fun albumDateGroupingMigratesLegacyInverseOnlyWhenNewValueIsMissing() {
        assertFalse(Settings.Album.resolveAlbumGroupByDate(null, legacyHideTimeline = true))
        assertTrue(Settings.Album.resolveAlbumGroupByDate(null, legacyHideTimeline = false))
        assertTrue(Settings.Album.resolveAlbumGroupByDate(true, legacyHideTimeline = true))
    }

    @Test
    fun modifiedTimestampUpdatesDefaultToDisabled() {
        assertFalse(Settings.Album.resolveUpdateModifiedDate(null))
        assertTrue(Settings.Album.resolveUpdateModifiedDate(true))
        assertFalse(Settings.Album.resolveUpdateModifiedDate(false))
    }

    @Test
    fun dateEditorKeepsBlankRawValueInsteadOfResolvedPreview() {
        assertEquals("", dateFormatEditorText(""))
        assertEquals("dd/MM/yyyy", dateFormatEditorText("dd/MM/yyyy"))
    }

    @Test
    fun settingsRegistryPublishesNewEntriesWithoutRecreatingConsumer() {
        val route = "test_settings_truth_route"
        SettingsSearchRegistry.register(route, listOf("First", "First", ""))
        assertEquals(listOf("First"), SettingsSearchRegistry.entries.value[route])

        SettingsSearchRegistry.register(route, listOf("Second"))
        assertEquals(listOf("Second"), SettingsSearchRegistry.entries.value[route])

        SettingsSearchRegistry.register(route, emptyList())
        assertFalse(SettingsSearchRegistry.entries.value.containsKey(route))
    }
}
