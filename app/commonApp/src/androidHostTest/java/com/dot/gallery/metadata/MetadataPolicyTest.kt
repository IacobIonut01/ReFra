package com.dot.gallery.metadata

import com.dot.gallery.data.entity.metadataParsingPolicy
import com.dot.gallery.data.entity.isRedactedCoordinate
import com.dot.gallery.core.Settings
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataPolicy
import com.dot.gallery.core.util.shouldRequestOriginalMetadata
import com.dot.gallery.data.repository.shouldUsePerFileMetadataIsolation
import com.dot.gallery.data.entity.MetadataParsingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.core.util.openOriginalOrFallback
import com.dot.gallery.domain.model.bestEffortReverseGeocode



class MetadataPolicyTest {
    @Test
    fun bulkMetadataReusesSharedIsolationUnlessPerFileWasRequested() {
        assertFalse(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_SHARED,
                bulk = true
            )
        )
        assertFalse(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_HYBRID,
                bulk = true
            )
        )
        assertTrue(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_PER_FILE,
                bulk = true
            )
        )
    }

    @Test
    fun hybridUsesPerFileIsolationForInteractiveMetadata() {
        assertFalse(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_SHARED,
                bulk = false
            )
        )
        assertTrue(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_HYBRID,
                bulk = false
            )
        )
        assertTrue(
            shouldUsePerFileMetadataIsolation(
                Settings.Security.METADATA_ISOLATION_PER_FILE,
                bulk = false
            )
        )
    }

    @Test
    fun metadataParsingPolicyKeepsBulkIsolation() {
        assertEquals(MetadataParsingPolicy.BULK_ISOLATED_ONLY, metadataParsingPolicy(bulk = true))
        assertEquals(MetadataParsingPolicy.ON_DEMAND_COMPATIBLE, metadataParsingPolicy(bulk = false))
    }

    @Test
    fun reverseGeocoderRunsWhenEnabled() = runTest {
        var lookupCalled = false

        val result = bestEffortReverseGeocode(
            enabled = true,
            latitude = 51.5,
            longitude = -0.1
        ) { _, _ ->
            lookupCalled = true
            "London"
        }

        assertEquals("London", result)
        assertTrue(lookupCalled)
    }

    @Test
    fun reverseGeocoderFailureIsNonFatal() = runTest {
        val result = bestEffortReverseGeocode<String>(
            enabled = true,
            latitude = 51.5,
            longitude = -0.1
        ) { _, _ -> throw IllegalStateException("geocoder unavailable") }

        assertNull(result)
    }

    @Test
    fun reverseGeocoderSkipsZeroAndInvalidCoordinates() = runTest {
        val calls = mutableListOf<Pair<Double, Double>>()

        assertNull(bestEffortReverseGeocode(true, 0.0, 0.0) { a, b -> calls += a to b; "x" })
        assertNull(bestEffortReverseGeocode(true, -0.0, -0.0) { a, b -> calls += a to b; "x" })
        assertNull(bestEffortReverseGeocode(true, 91.0, 0.0) { a, b -> calls += a to b; "x" })
        assertNull(bestEffortReverseGeocode(true, 0.0, 181.0) { a, b -> calls += a to b; "x" })
        assertNull(bestEffortReverseGeocode(true, Double.NaN, 0.0) { a, b -> calls += a to b; "x" })
        assertEquals("x", bestEffortReverseGeocode(true, 0.0, 1.0) { a, b -> calls += a to b; "x" })

        assertEquals(listOf(0.0 to 1.0), calls)
    }

    @Test(expected = CancellationException::class)
    fun reverseGeocoderCancellationIsPreserved() = runTest {
        bestEffortReverseGeocode<String>(
            enabled = true,
            latitude = 51.5,
            longitude = -0.1
        ) { _, _ -> throw CancellationException("cancelled") }
    }

    @Test
    fun redactedCoordinateSignatureIsExactZeroPair() {
        assertTrue(isRedactedCoordinate(0.0, 0.0))
        assertTrue(isRedactedCoordinate(-0.0, -0.0))
        assertFalse(isRedactedCoordinate(0.0, 26.1))
        assertFalse(isRedactedCoordinate(44.4, 26.1))
        assertFalse(isRedactedCoordinate(Double.NaN, 0.0))
    }

    @Test
    fun originalMetadataIsRequestedOnlyForGrantedMediaStoreAccess() {
        assertTrue(shouldRequestOriginalMetadata(29, permissionGranted = true, isMediaStoreUri = true))
        assertFalse(shouldRequestOriginalMetadata(28, permissionGranted = true, isMediaStoreUri = true))
        assertFalse(shouldRequestOriginalMetadata(29, permissionGranted = false, isMediaStoreUri = true))
        assertFalse(shouldRequestOriginalMetadata(29, permissionGranted = true, isMediaStoreUri = false))
    }

    @Test
    fun originalMetadataFailureFallsBackToRegularAccess() {
        var fallbackCalled = false

        val result = openOriginalOrFallback(
            requestOriginal = true,
            openOriginal = { throw SecurityException("original denied") },
            openFallback = {
                fallbackCalled = true
                "regular"
            }
        )

        assertEquals("regular", result)
        assertTrue(fallbackCalled)
    }

    @Test
    fun locationPresetRemovesOnlyLocation() {
        assertEquals(setOf(MetadataCategory.LOCATION), MetadataPolicy.LOCATION)
        assertFalse(MetadataPolicy.removesEverything(MetadataPolicy.LOCATION))
    }

    @Test
    fun removeAllNeverSelectsStructuralOrColorMetadata() {
        assertFalse(MetadataCategory.STRUCTURAL_FUNCTIONAL in MetadataPolicy.ALL)
        assertFalse(MetadataCategory.COLOR_HDR in MetadataPolicy.ALL)
        assertTrue(MetadataCategory.CAPTURE_SETTINGS in MetadataPolicy.ALL)
        assertTrue(MetadataCategory.PROVENANCE in MetadataPolicy.ALL)
        assertTrue(MetadataPolicy.removesEverything(MetadataPolicy.ALL))
    }

    @Test
    fun normalizeDropsCategoriesThatAreAlwaysPreserved() {
        val requested = setOf(MetadataCategory.LOCATION, MetadataCategory.COLOR_HDR, MetadataCategory.STRUCTURAL_FUNCTIONAL)
        assertEquals(setOf(MetadataCategory.LOCATION), MetadataPolicy.normalize(requested))
        assertTrue(MetadataPolicy.removesEverything(MetadataPolicy.ALL + MetadataPolicy.alwaysPreserved))
    }
}
