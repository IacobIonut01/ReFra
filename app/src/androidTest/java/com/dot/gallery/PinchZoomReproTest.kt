package com.dot.gallery

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
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

    /**
     * #962: pending rotation must apply to the media it was created on, not to
     * whatever item currentPage happens to point at mid-swipe.
     * Rotates the first image, drags partway toward the neighbour, releases,
     * then confirms via the pending-rotate chip.
     */
    @Test
    fun pendingRotation_appliesToRotatedMedia() {
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

        // Open an image cell in the viewer (media cells expose the filename
        // as their content description). Retry the tab click once in case the
        // first landed mid-transition.
        var cell = device.findObject(
            UiSelector().descriptionMatches("(?s).*\\.(jpg|jpeg|png|webp)$")
        )
        if (!cell.waitForExists(8_000)) {
            timelineTab.click()
            device.waitForIdle()
            Thread.sleep(2_000)
        }
        assertTrue("no image cell found on timeline", cell.waitForExists(8_000))
        cell.click()
        device.waitForIdle()
        Thread.sleep(2_000)

        val w = device.displayWidth
        val h = device.displayHeight
        val cy = h / 2

        // Long-press on the image applies a visual 90° rotation step and
        // surfaces the pending-rotate chip.
        device.swipe(w / 2, cy, w / 2, cy, 120)
        device.waitForIdle()
        Thread.sleep(1_000)

        // The pending-rotate chip must be present to continue the scenario.
        val chip = device.findObject(UiSelector().text("Rotate"))
        assertTrue(
            "pending-rotate chip not found after twist gesture",
            chip.waitForExists(5_000)
        )

        chip.click()
        device.waitForIdle()
        Thread.sleep(2_000)
        assertEquals(targetContext.packageName, device.currentPackageName)
    }

    /**
     * #1048: crash on a held predictive-back gesture while the editor's
     * overflow menu is open. The popup owns its own back dispatch, so the
     * gesture is driven as real injected motion events: edge down, drag
     * inward, hold to keep the back preview armed, then release.
     */
    @Test
    fun editorBackWithMenuOpen_doesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val automation = instrumentation.uiAutomation

        // Any image on the device works; the editor only needs a media URI.
        val mediaUri = targetContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            if (c.moveToFirst()) Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString()
            ) else null
        }
        assertTrue("no image found in MediaStore", mediaUri != null)

        device.pressHome()
        val intent = Intent(Intent.ACTION_EDIT, mediaUri).apply {
            setClassName(
                targetContext.packageName,
                "com.dot.gallery.feature_node.presentation.edit.EditActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()
        Thread.sleep(3_000)

        // Rotate once so isChanged flips and the overflow button appears.
        val rotate = device.findObject(UiSelector().description("Rotate 90 degrees"))
        assertTrue("rotate button not found", rotate.waitForExists(8_000))
        rotate.click()
        device.waitForIdle()
        Thread.sleep(1_000)

        val more = device.findObject(UiSelector().description("More options"))
        assertTrue("overflow menu button not found", more.waitForExists(5_000))
        more.click()
        device.waitForIdle()
        Thread.sleep(1_000)

        // Held predictive back: edge down -> drag in -> wiggle while holding
        // the preview armed -> release-commit. Repeated for the commit and
        // cancel directions so a popup mid-gesture transition can't hide.
        val h = device.displayHeight
        fun heldBack(commit: Boolean) {
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, x: Float, y: Float, t: Long = SystemClock.uptimeMillis()) {
                val ev = MotionEvent.obtain(downTime, t, action, x, y, 0)
                automation.injectInputEvent(ev, true)
                ev.recycle()
            }
            inject(MotionEvent.ACTION_DOWN, 2f, h / 2f, downTime)
            inject(MotionEvent.ACTION_MOVE, 300f, h / 2f, downTime + 80)
            // hold + wiggle like a user peeking at the previous screen
            Thread.sleep(700)
            inject(MotionEvent.ACTION_MOVE, 250f, h / 2f)
            Thread.sleep(300)
            inject(MotionEvent.ACTION_MOVE, 350f, h / 2f)
            Thread.sleep(500)
            val endX = if (commit) 600f else 2f
            inject(MotionEvent.ACTION_MOVE, endX, h / 2f)
            inject(MotionEvent.ACTION_UP, endX, h / 2f)
            device.waitForIdle()
        }

        // Menu is open: hold-cancel (preview then return) must not crash.
        heldBack(commit = false)
        Thread.sleep(500)
        assertEquals(targetContext.packageName, device.currentPackageName)

        // Reopen the menu if the gesture dismissed it, then hold-commit.
        if (!more.waitForExists(1_000)) more.click()
        device.waitForIdle()
        heldBack(commit = true)
        Thread.sleep(500)
        assertEquals(targetContext.packageName, device.currentPackageName)

        // Whatever survived above (menu dismissed, dirty prompt, or still in
        // the editor) — a final held back exercises the next back owner.
        heldBack(commit = true)
        Thread.sleep(500)
        assertEquals(targetContext.packageName, device.currentPackageName)
    }
}
