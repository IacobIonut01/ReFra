package com.dot.gallery

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1119: pinch-zoom on the mosaic grid crashes ~50% of the time.
 * Drives real two-pointer pinch gestures on the timeline grid and
 * asserts the app stays in the foreground through repeated column changes.
 */
@RunWith(AndroidJUnit4::class)
class PinchZoomReproTest {

    @Test
    fun pinchZoomMosaicGrid_doesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        device.pressHome()
        val intent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForIdle()
        Thread.sleep(4_000)

        // Land on the timeline (it hosts the mosaic grid).
        val timelineTab = device.findObject(UiSelector().description("Timeline"))
        if (timelineTab.waitForExists(5_000)) timelineTab.click()
        device.waitForIdle()
        Thread.sleep(2_000)

        // The mosaic LazyVerticalGrid is the scrollable node on the timeline.
        val grid = device.findObject(UiSelector().scrollable(true))
        assertTrue("scrollable grid not found", grid.waitForExists(10_000))

        repeat(24) { i ->
            // Fling first so the pinch lands on a scrolling / animating grid,
            // and don't wait for idle so back-to-back pinches can race the
            // column-change settle animation.
            device.swipe(
                device.displayWidth / 2, (device.displayHeight * 0.8).toInt(),
                device.displayWidth / 2, (device.displayHeight * 0.3).toInt(),
                10
            )
            if (i % 2 == 0) {
                // Spread fingers -> zoom in -> fewer columns
                grid.pinchOut(60, 40)
            } else {
                // Bring fingers together -> zoom out -> more columns
                grid.pinchIn(60, 40)
            }
            assertEquals(targetContext.packageName, device.currentPackageName)
        }
        device.waitForIdle()
    }
}
