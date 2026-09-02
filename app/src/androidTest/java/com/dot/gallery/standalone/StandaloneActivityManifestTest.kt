package com.dot.gallery.standalone

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.feature_node.presentation.standalone.StandaloneActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandaloneActivityManifestTest {

    @Test
    fun externalViewerReusesOneNonDocumentTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, StandaloneActivity::class.java),
            0
        )

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        assertEquals(ActivityInfo.DOCUMENT_LAUNCH_NEVER, info.documentLaunchMode)
    }
}
