package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.Color
import com.dot.gallery.editor.core.model.PhotoExportFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorSettingsTest {

    private class MemoryPersistence(var stored: EditorSettings = EditorSettings()) :
        EditorSettingsPersistence {
        var saves = 0
        override fun load() = stored
        override fun save(settings: EditorSettings) {
            stored = settings; saves++
        }
    }

    @Test
    fun storeLoadsAndPersistsOnlyRealChanges() {
        val persistence = MemoryPersistence(EditorSettings(videoSnap = false))
        val store = EditorSettingsStore(persistence)
        assertEquals(false, store.settings.videoSnap)

        store.update { it.copy(videoSnap = false) }
        assertEquals(0, persistence.saves)

        store.update { it.copy(histogramMode = PhotoHistogramMode.Luma) }
        assertEquals(1, persistence.saves)
        assertEquals(PhotoHistogramMode.Luma, persistence.stored.histogramMode)
    }

    @Test
    fun copyFormatFollowsTheSourceUnlessSupported() {
        val supported = setOf(PhotoExportFormat.JPEG, PhotoExportFormat.PNG)
        assertEquals(
            SaveFormatDecision.Write(null),
            resolveSaveFormat(EditorSettings(), isRaw = false, tiffAllowed = false, supported)
        )
        assertEquals(
            SaveFormatDecision.Write(PhotoExportFormat.PNG),
            resolveSaveFormat(
                EditorSettings(copyFormat = PhotoExportFormat.PNG), false, false, supported
            )
        )
        // A stored format the host can no longer write falls back to the source.
        assertEquals(
            SaveFormatDecision.Write(null),
            resolveSaveFormat(
                EditorSettings(copyFormat = PhotoExportFormat.JXL), false, false, supported
            )
        )
    }

    @Test
    fun rawAsksUnlessAWritableFormatIsStored() {
        val supported = emptySet<PhotoExportFormat>()
        assertEquals(
            SaveFormatDecision.Ask,
            resolveSaveFormat(EditorSettings(), isRaw = true, tiffAllowed = true, supported)
        )
        assertEquals(
            SaveFormatDecision.Write(PhotoExportFormat.JPEG),
            resolveSaveFormat(
                EditorSettings(rawFormat = PhotoExportFormat.JPEG), true, false, supported
            )
        )
        // TIFF only applies to a develop-only edit; otherwise the sheet asks.
        assertEquals(
            SaveFormatDecision.Ask,
            resolveSaveFormat(
                EditorSettings(rawFormat = PhotoExportFormat.TIFF_16), true, false, supported
            )
        )
        assertEquals(
            SaveFormatDecision.Write(PhotoExportFormat.TIFF_16),
            resolveSaveFormat(
                EditorSettings(rawFormat = PhotoExportFormat.TIFF_16), true, true, supported
            )
        )
    }

    @Test
    fun copyFormatsOfferOriginalThenNonTiffSupported() {
        val formats = photoCopyFormats(
            setOf(PhotoExportFormat.TIFF_8, PhotoExportFormat.WEBP, PhotoExportFormat.JPEG)
        )
        assertNull(formats.first())
        assertEquals(listOf(null, PhotoExportFormat.JPEG, PhotoExportFormat.WEBP), formats)
    }

    @Test
    fun videoSchemeTakesHostAccentsAndKeepsNeutrals() {
        val host = V3LightColors.copy(
            primary = Color.Red, secondaryContainer = Color.Green, tertiary = Color.Blue
        )
        val scheme = V3VideoDarkColors.withAccentsOf(host)
        assertEquals(Color.Red, scheme.primary)
        assertEquals(Color.Green, scheme.secondaryContainer)
        assertEquals(Color.Blue, scheme.tertiary)
        assertEquals(V3VideoDarkColors.surface, scheme.surface)
        assertEquals(V3VideoDarkColors.surfaceContainerHigh, scheme.surfaceContainerHigh)
    }
}
