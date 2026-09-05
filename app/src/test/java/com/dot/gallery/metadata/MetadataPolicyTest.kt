package com.dot.gallery.metadata

import com.dot.gallery.core.Settings
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataPolicy
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.sandbox.openOriginalOrFallback
import com.dot.gallery.core.sandbox.shouldRequestOriginalMetadata
import com.dot.gallery.feature_node.data.repository.shouldUsePerFileMetadataIsolation
import com.dot.gallery.feature_node.domain.model.MetadataParsingPolicy
import com.dot.gallery.feature_node.domain.model.bestEffortReverseGeocode
import com.dot.gallery.feature_node.domain.model.metadataParsingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test(expected = CancellationException::class)
    fun reverseGeocoderCancellationIsPreserved() = runTest {
        bestEffortReverseGeocode<String>(
            enabled = true,
            latitude = 51.5,
            longitude = -0.1
        ) { _, _ -> throw CancellationException("cancelled") }
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
    fun locationOnlyRemovesOnlyLocation() {
        val policy = MetadataPolicy.forMode(MetadataRemovalMode.LOCATION)

        assertTrue(MetadataCategory.LOCATION in policy.removedCategories)
        assertFalse(MetadataCategory.TIMESTAMPS in policy.removedCategories)
        assertTrue(MetadataCategory.STRUCTURAL_FUNCTIONAL in policy.preservedCategories)
    }

    @Test
    fun privacyPreservesCaptureAndRenderingData() {
        val policy = MetadataPolicy.forMode(MetadataRemovalMode.PRIVACY)

        assertTrue(MetadataCategory.IDENTITY_DEVICE in policy.removedCategories)
        assertTrue(MetadataCategory.TIMESTAMPS in policy.removedCategories)
        assertTrue(MetadataCategory.PEOPLE in policy.removedCategories)
        assertFalse(MetadataCategory.CAPTURE_SETTINGS in policy.removedCategories)
        assertTrue(MetadataCategory.COLOR_HDR in policy.preservedCategories)
    }

    @Test
    fun everythingNeverSelectsStructuralOrColorMetadata() {
        val policy = MetadataPolicy.forMode(MetadataRemovalMode.EVERYTHING)

        assertFalse(MetadataCategory.STRUCTURAL_FUNCTIONAL in policy.removedCategories)
        assertFalse(MetadataCategory.COLOR_HDR in policy.removedCategories)
        assertTrue(MetadataCategory.CAPTURE_SETTINGS in policy.removedCategories)
        assertTrue(MetadataCategory.PROVENANCE in policy.removedCategories)
    }
}
