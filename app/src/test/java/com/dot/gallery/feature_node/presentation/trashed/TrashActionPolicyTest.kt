package com.dot.gallery.feature_node.presentation.trashed

import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.remoteTrashPermitted
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.trashed.components.RemovalClass
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.trashed.components.planRemoval
import com.dot.gallery.feature_node.presentation.trashed.components.removalClass
import com.dot.gallery.feature_node.presentation.trashed.components.resolveTrashDialogAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uses [Media.EncryptedMedia] because it carries no [android.net.Uri] field,
 * keeping the test runnable on the plain JVM (no Robolectric/mocked Android
 * framework). Its `isCloud` is always false, so the cloud paths are exercised
 * through the predicate parameters of [removalClass] and [planRemoval].
 */
class TrashActionPolicyTest {

    @Test
    fun supportedEnabledTrashRequestIsRecoverable() {
        assertEquals(
            TrashDialogAction.TRASH,
            resolveTrashDialogAction(
                trashRequested = true,
                trashEnabled = true,
                trashSupported = true
            )
        )
    }

    @Test
    fun disabledTrashRequestIsPermanentDelete() {
        assertEquals(
            TrashDialogAction.DELETE,
            resolveTrashDialogAction(
                trashRequested = true,
                trashEnabled = false,
                trashSupported = true
            )
        )
    }

    @Test
    fun unsupportedTrashRequestIsPermanentDelete() {
        assertEquals(
            TrashDialogAction.DELETE,
            resolveTrashDialogAction(
                trashRequested = true,
                trashEnabled = true,
                trashSupported = false
            )
        )
    }

    @Test
    fun explicitDeleteNeverBecomesTrash() {
        assertEquals(
            TrashDialogAction.DELETE,
            resolveTrashDialogAction(
                trashRequested = false,
                trashEnabled = true,
                trashSupported = true
            )
        )
    }

    // === removalClass truth table (#1279) ===

    @Test
    fun localItemIsTrashableRegardlessOfCloudFlags() {
        for (readOnly in listOf(false, true)) {
            for (supportsTrash in listOf(false, true)) {
                assertEquals(
                    RemovalClass.TRASHABLE,
                    removalClass(
                        isCloud = false,
                        isReadOnlyCloud = readOnly,
                        providerSupportsTrash = supportsTrash
                    )
                )
            }
        }
    }

    @Test
    fun readOnlyCloudItemIsBlocked() {
        for (supportsTrash in listOf(false, true)) {
            assertEquals(
                RemovalClass.BLOCKED,
                removalClass(
                    isCloud = true,
                    isReadOnlyCloud = true,
                    providerSupportsTrash = supportsTrash
                )
            )
        }
    }

    @Test
    fun trashCapableCloudItemIsTrashable() {
        assertEquals(
            RemovalClass.TRASHABLE,
            removalClass(
                isCloud = true,
                isReadOnlyCloud = false,
                providerSupportsTrash = true
            )
        )
    }

    @Test
    fun cloudItemWithoutBinIsDeleteOnly() {
        assertEquals(
            RemovalClass.DELETE_ONLY,
            removalClass(
                isCloud = true,
                isReadOnlyCloud = false,
                providerSupportsTrash = false
            )
        )
    }

    @Test
    fun mediaExtensionTreatsNonUriMediaAsLocal() {
        // EncryptedMedia.isCloud is always false, so even hostile flags must
        // classify it TRASHABLE — the local-item rule wins over cloud flags.
        assertEquals(
            RemovalClass.TRASHABLE,
            media(1).removalClass(
                isReadOnlyCloud = true,
                providerSupportsTrash = false
            )
        )
    }

    // === planRemoval ===

    @Test
    fun planRemovalPartitionsMixedBatch() {
        val local = media(1)
        val trashableCloud = media(2)
        val deleteOnlyCloud = media(3)
        val blockedCloud = media(4)
        val plan = listOf(local, trashableCloud, deleteOnlyCloud, blockedCloud).planRemoval(
            isCloud = { it.id != 1L },
            isReadOnlyCloud = { it.id == 4L },
            providerSupportsTrash = { it.id == 2L },
        )
        assertEquals(listOf(1L, 2L), plan.trashable.map { it.id })
        assertEquals(listOf(3L), plan.deleteOnly.map { it.id })
        assertEquals(listOf(4L), plan.blocked.map { it.id })
    }

    @Test
    fun planRemovalTargetsCoverTrashableAndDeleteOnly() {
        val plan = listOf(media(1), media(2), media(3)).planRemoval(
            isCloud = { it.id != 1L },
            isReadOnlyCloud = { it.id == 3L },
            providerSupportsTrash = { false },
        )
        assertEquals(listOf(1L, 2L), plan.targets.map { it.id })
        assertEquals(listOf(3L), plan.blocked.map { it.id })
    }

    @Test
    fun planRemovalOfEmptyListIsEmpty() {
        val plan = emptyList<Media.EncryptedMedia>().planRemoval(
            isReadOnlyCloud = { false },
            providerSupportsTrash = { false },
        )
        assertTrue(plan.trashable.isEmpty())
        assertTrue(plan.deleteOnly.isEmpty())
        assertTrue(plan.blocked.isEmpty())
        assertTrue(plan.targets.isEmpty())
    }

    // === remoteTrashPermitted (MediaHandlerImpl.trashMedia guard) ===

    @Test
    fun trashPermittedOnBinCapableProvider() {
        assertTrue(
            remoteTrashPermitted(
                trash = true,
                capabilities = setOf(ProviderCapability.TRASH)
            )
        )
    }

    @Test
    fun trashRefusedOnDeleteOnlyProvider() {
        assertFalse(
            remoteTrashPermitted(
                trash = true,
                capabilities = emptySet()
            )
        )
    }

    @Test
    fun restorePermittedOnDeleteOnlyProvider() {
        // A provider without a bin never holds restorable items, but a stray
        // restore call must not be blocked by the #1279 trash guard.
        assertTrue(
            remoteTrashPermitted(
                trash = false,
                capabilities = emptySet()
            )
        )
    }

    private fun media(id: Long): Media.EncryptedMedia =
        Media.EncryptedMedia(
            id = id,
            label = "m$id",
            bytes = ByteArray(0),
            path = "/path/$id",
            relativePath = "/",
            albumID = 1L,
            albumLabel = "album",
            timestamp = id,
            fullDate = "",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 0,
            size = 0L,
        )
}
