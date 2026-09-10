package com.dot.gallery

import com.dot.gallery.feature_node.presentation.util.isLanRouteAvailable
import com.dot.gallery.feature_node.presentation.util.runNetworkCallbackOperation
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryAppTest {

    @Test
    fun sketchCacheDirectoryUsesInternalCacheDirectory() {
        val internalCacheDirectory = File("/data/user/0/com.dot.gallery/cache")

        val result = sketchCacheDirectory(internalCacheDirectory)

        assertEquals(internalCacheDirectory.absolutePath.toPath(), result.parent)
        assertEquals("sketch", result.name)
    }

    @Test
    fun networkCallbackOperationHandlesMissingPermission() {
        assertFalse(runNetworkCallbackOperation { throw SecurityException("missing permission") })
    }

    @Test
    fun networkCallbackOperationReportsSuccessfulRegistration() {
        assertTrue(runNetworkCallbackOperation {})
    }

    @Test
    fun vpnCanRouteTrafficToLanResources() {
        assertTrue(isLanRouteAvailable(isWifi = false, isEthernet = false, isVpn = true))
        assertFalse(isLanRouteAvailable(isWifi = false, isEthernet = false, isVpn = false))
    }
}
