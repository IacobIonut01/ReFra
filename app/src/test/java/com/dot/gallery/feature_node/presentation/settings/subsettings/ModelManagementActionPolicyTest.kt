package com.dot.gallery.feature_node.presentation.settings.subsettings

import com.dot.gallery.core.ml.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManagementActionPolicyTest {

    @Test
    fun readyModelsCanOnlyBeDeletedWhenInstallIsPossible() {
        // canInstall = INTERNET permission (network download) OR bundled withML assets.
        val installable = resolveModelManagementAction(ModelStatus.READY, canInstall = true)
        val notInstallable = resolveModelManagementAction(ModelStatus.READY, canInstall = false)

        assertEquals(ModelManagementAction.DELETE, installable)
        assertTrue(installable.enabled)
        assertEquals(ModelManagementAction.INSTALLED_OFFLINE, notInstallable)
        assertFalse(notInstallable.enabled)
    }

    @Test
    fun bundledBuildsCanDeleteAndReinstallWithoutInternet() {
        // WithML ships the models inside the APK, so (re)install never needs the network:
        // READY -> DELETE and NOT_INSTALLED -> DOWNLOAD (a local asset copy) even offline.
        assertEquals(
            ModelManagementAction.DELETE,
            resolveModelManagementAction(ModelStatus.READY, canInstall = true)
        )
        assertEquals(
            ModelManagementAction.DOWNLOAD,
            resolveModelManagementAction(ModelStatus.NOT_INSTALLED, canInstall = true)
        )
    }

    @Test
    fun bundledModelCopyIsNeverInteractive() {
        listOf(true, false).forEach { canInstall ->
            val action = resolveModelManagementAction(ModelStatus.COPYING, canInstall)

            assertEquals(ModelManagementAction.COPYING, action)
            assertFalse(action.enabled)
        }
    }

    @Test
    fun activeDownloadCanOnlyBeCancelledWhenInstallIsPossible() {
        val installable = resolveModelManagementAction(ModelStatus.DOWNLOADING, canInstall = true)
        val notInstallable = resolveModelManagementAction(ModelStatus.DOWNLOADING, canInstall = false)

        assertEquals(ModelManagementAction.CANCEL_DOWNLOAD, installable)
        assertTrue(installable.enabled)
        assertEquals(ModelManagementAction.UNAVAILABLE_OFFLINE, notInstallable)
        assertFalse(notInstallable.enabled)
    }

    @Test
    fun missingModelsCanOnlyBeInstalledWhenInstallIsPossible() {
        listOf(ModelStatus.NOT_INSTALLED, ModelStatus.ERROR).forEach { status ->
            val installable = resolveModelManagementAction(status, canInstall = true)
            val notInstallable = resolveModelManagementAction(status, canInstall = false)

            assertEquals(ModelManagementAction.DOWNLOAD, installable)
            assertTrue(installable.enabled)
            assertEquals(ModelManagementAction.UNAVAILABLE_OFFLINE, notInstallable)
            assertFalse(notInstallable.enabled)
        }
    }
}
