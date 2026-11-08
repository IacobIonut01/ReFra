package com.dot.gallery.app.presentation.setup

import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.ACCESS_LOCAL_NETWORK
import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.ACCESS_MEDIA_LOCATION
import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.INTERNET
import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.MANAGE_EXTERNAL_STORAGE
import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.MANAGE_MEDIA
import com.dot.gallery.app.presentation.setup.SetupVariant.Companion.POST_NOTIFICATIONS
import com.dot.gallery.feature.setup.ui.SetupPage
import com.dot.gallery.feature.setup.ui.SetupPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupVariantTest {

    /** The main manifest's permissions as merged for the standard variant. */
    private val standardManifest = setOf(
        POST_NOTIFICATIONS,
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        ACCESS_MEDIA_LOCATION,
        INTERNET,
        ACCESS_LOCAL_NETWORK,
        "android.permission.ACCESS_WIFI_STATE",
        MANAGE_MEDIA,
        MANAGE_EXTERNAL_STORAGE,
    )

    /** The offline variant strips the network permissions after merging. */
    private val offlineManifest = standardManifest - setOf(INTERNET, ACCESS_LOCAL_NETWORK, "android.permission.ACCESS_WIFI_STATE")

    private fun variant(
        declared: Set<String> = standardManifest,
        sdkInt: Int = 37,
        allowAllFilesAccess: Boolean = true,
        hasRemoteProvider: Boolean = true,
        modelsBundled: Boolean = false,
    ) = SetupVariant(declared, sdkInt, allowAllFilesAccess, hasRemoteProvider, modelsBundled)

    @Test
    fun standardBuildListsEveryDeclaredPermissionInOrder() {
        assertEquals(
            listOf(
                SetupPermission.IMAGES,
                SetupPermission.VIDEO,
                SetupPermission.MEDIA_LOCATION,
                SetupPermission.INTERNET,
                SetupPermission.LOCAL_NETWORK,
                SetupPermission.NOTIFICATIONS,
                SetupPermission.MANAGE_MEDIA,
                SetupPermission.MANAGE_FILES,
            ),
            variant().permissions,
        )
    }

    @Test
    fun offlineBuildHidesNetworkPermissions() {
        val permissions = variant(declared = offlineManifest).permissions
        assertFalse(SetupPermission.INTERNET in permissions)
        assertFalse(SetupPermission.LOCAL_NETWORK in permissions)
        assertEquals(listOf(SetupPermission.IMAGES, SetupPermission.VIDEO), permissions.take(2))
    }

    @Test
    fun localNetworkNeedsInternetEvenWhenDeclared() {
        val permissions = variant(declared = offlineManifest + ACCESS_LOCAL_NETWORK).permissions
        assertFalse(SetupPermission.LOCAL_NETWORK in permissions)
    }

    @Test
    fun manageFilesNeedsTheDeclarationAndTheBuildFlag() {
        assertFalse(SetupPermission.MANAGE_FILES in variant(allowAllFilesAccess = false).permissions)
        assertFalse(
            SetupPermission.MANAGE_FILES in variant(declared = standardManifest - MANAGE_EXTERNAL_STORAGE).permissions
        )
    }

    @Test
    fun olderSdksDropRuntimeOnlyRows() {
        assertEquals(
            listOf(
                SetupPermission.IMAGES,
                SetupPermission.VIDEO,
                SetupPermission.MEDIA_LOCATION,
                SetupPermission.INTERNET,
            ),
            variant(sdkInt = 29).permissions,
        )
        val sdk33 = variant(sdkInt = 33).permissions
        assertTrue(SetupPermission.NOTIFICATIONS in sdk33)
        assertFalse(SetupPermission.LOCAL_NETWORK in sdk33)
    }

    @Test
    fun photosAndVideosAlwaysListed() {
        assertEquals(
            listOf(SetupPermission.IMAGES, SetupPermission.VIDEO),
            variant(declared = emptySet()).permissions,
        )
    }

    @Test
    fun standardNoMlShowsCloudAndModelDownloads() {
        assertEquals(
            listOf(
                SetupPage.WELCOME,
                SetupPage.PERMISSIONS,
                SetupPage.LOOKS,
                SetupPage.CLOUD,
                SetupPage.AI_MODELS,
                SetupPage.TIPS,
            ),
            variant().pages,
        )
    }

    @Test
    fun offlineNoMlOmitsCloudAndModels() {
        assertEquals(
            listOf(SetupPage.WELCOME, SetupPage.PERMISSIONS, SetupPage.LOOKS, SetupPage.TIPS),
            variant(declared = offlineManifest, hasRemoteProvider = false).pages,
        )
    }

    @Test
    fun offlineWithMlKeepsModelsPage() {
        val pages = variant(declared = offlineManifest, hasRemoteProvider = false, modelsBundled = true).pages
        assertTrue(SetupPage.AI_MODELS in pages)
        assertFalse(SetupPage.CLOUD in pages)
    }

    @Test
    fun cloudPageNeedsARemoteProvider() {
        assertFalse(SetupPage.CLOUD in variant(hasRemoteProvider = false).pages)
    }
}
