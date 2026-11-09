/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.insight

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.insight.CodeFormat
import com.dot.gallery.core.insight.CodeInsight
import com.dot.gallery.core.insight.CodePayload
import com.dot.gallery.core.insight.CodeScanNative
import com.dot.gallery.core.insight.FramePool
import com.dot.gallery.core.insight.ZxingCodeAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * Runs the real `libcodescan.so` over the synthetic fixtures in `androidTest/assets/codescan`,
 * with the frame sizes the viewer uses: 600 px for the quick pass (the preview), 1600 px for the
 * thorough pass. Lives in the app-process tier because the library is built by :app:androidApp.
 */
@RunWith(AndroidJUnit4::class)
class CodeScanNativeTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Before
    fun libraryIsBuiltWithTheReader() {
        assertTrue("libcodescan.so is the stub; run scripts/native/build-zxing.sh", CodeScanNative.isAvailable)
    }

    private fun load(name: String): Bitmap =
        assets.open("codescan/$name").use { BitmapFactory.decodeStream(it) }!!

    private fun scan(name: String, quickSide: Int = PREVIEW_SIDE, thorough: Boolean = false): List<CodeInsight> {
        val bitmap = load(name)
        // Same downscale the viewer's preview request applies before the frame is built.
        val pool = FramePool(maxSide = quickSide)
        val frame = checkNotNull(pool.fill(bitmap)) { "frame" }
        bitmap.recycle()
        return runBlocking { ZxingCodeAnalyzer().analyze(frame, thorough) }.filterIsInstance<CodeInsight>()
    }

    @Test
    fun readsALinkOnTheQuickPass() {
        val codes = scan("qr_url.jpg")
        assertEquals(1, codes.size)
        val code = codes.single()
        assertEquals(CodeFormat.QR_CODE, code.format)
        val url = code.payload as CodePayload.Url
        assertEquals("canalnights.example", url.host)
        // The code sits at x 420..820 of 1200 and y 260..660 of 900 (plus its quiet zone).
        assertTrue(code.quad.left in 0.33f..0.40f)
        assertTrue(code.quad.right in 0.64f..0.70f)
        assertTrue(code.quad.top in 0.27f..0.34f)
    }

    @Test
    fun readsAWifiCodeSeenInPerspective() {
        val wifi = scan("qr_wifi_perspective.jpg").single().payload as CodePayload.Wifi
        assertEquals("Ponte Guest", wifi.ssid)
        assertEquals("gondola-2026", wifi.password)
    }

    @Test
    fun invertedCodeNeedsTheThoroughPass() {
        assertTrue(scan("qr_inverted.jpg").isEmpty())
        val codes = scan("qr_inverted.jpg", quickSide = FramePool.DEFAULT_MAX_SIDE, thorough = true)
        assertEquals("INVERTED-CODE-42", codes.single().text)
    }

    @Test
    fun readsTwoCodesInOnePhoto() {
        val codes = scan("two_codes.jpg").sortedBy { it.quad.left }
        assertEquals(listOf(CodeFormat.QR_CODE, CodeFormat.DATA_MATRIX), codes.map { it.format })
        assertEquals("TABLE-BOOKING-7", codes[1].text)
    }

    @Test
    fun readsAProductBarcode() {
        val code = scan("ean13.jpg").single()
        assertEquals(CodeFormat.EAN_13, code.format)
        assertEquals(CodePayload.Product("4006381333931"), code.payload)
    }

    @Test
    fun smallCodeInALargePhotoIsFoundAfterDownscaling() {
        assertEquals("https://small.example/", scan("small_qr_large_photo.jpg").single().text)
    }

    @Test
    fun photoWithoutACodeGivesNothing() {
        assertTrue(scan("no_code.jpg", quickSide = FramePool.DEFAULT_MAX_SIDE, thorough = true).isEmpty())
    }

    @Test
    fun disabledFormatsAreNotRead() {
        val bitmap = load("two_codes.jpg")
        val frame = checkNotNull(FramePool(PREVIEW_SIDE).fill(bitmap)) { "frame" }
        val onlyQr = ZxingCodeAnalyzer(formatMask = CodeFormat.QR_CODE.bit)
        val codes = runBlocking { onlyQr.analyze(frame, thorough = false) }.filterIsInstance<CodeInsight>()
        assertEquals(listOf(CodeFormat.QR_CODE), codes.map { it.format })
        val none = ZxingCodeAnalyzer(formatMask = 0)
        assertTrue(runBlocking { none.analyze(frame, thorough = false) }.isEmpty())
    }

    @Test
    fun hardwareBitmapsAreCopiedNotRejected() {
        val bytes = assets.open("codescan/qr_url.jpg").use { it.readBytes() }
        val hardware = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
        assertEquals(Bitmap.Config.HARDWARE, hardware.config)
        val frame = checkNotNull(FramePool(PREVIEW_SIDE).fill(hardware)) { "frame" }
        assertEquals(1, runBlocking { ZxingCodeAnalyzer().analyze(frame, false) }.size)
        assertTrue("the shared bitmap must not be recycled", !hardware.isRecycled)
    }

    @Test
    fun recycledBitmapIsSkipped() {
        val bitmap = load("qr_url.jpg")
        bitmap.recycle()
        assertNull(FramePool(PREVIEW_SIDE).fill(bitmap))
    }

    @Test
    fun quickPassIsFast() {
        val bitmap = load("qr_url.jpg")
        val pool = FramePool(PREVIEW_SIDE)
        val analyzer = ZxingCodeAnalyzer()
        val timings = (0 until 25).map {
            val start = System.nanoTime()
            val frame = pool.fill(bitmap)!!
            runBlocking { analyzer.analyze(frame, thorough = false) }
            (System.nanoTime() - start) / 1_000_000.0
        }.drop(5).sorted()
        val median = timings[timings.size / 2]
        assertTrue("quick pass median $median ms (max ${timings.last()} ms)", median < QUICK_PASS_BUDGET_MS)
    }

    @Test
    fun thoroughPassStaysWithinBudget() {
        val bitmap = load("no_code.jpg")
        val pool = FramePool()
        val analyzer = ZxingCodeAnalyzer()
        val timings = (0 until 8).map {
            val start = System.nanoTime()
            val frame = pool.fill(bitmap)!!
            runBlocking { analyzer.analyze(frame, thorough = true) }
            (System.nanoTime() - start) / 1_000_000.0
        }.drop(2).sorted()
        val median = timings[timings.size / 2]
        assertTrue("thorough pass median $median ms", median < THOROUGH_PASS_BUDGET_MS)
    }

    @Suppress("DEPRECATION")
    @Test
    fun scanningAPhotoWithoutCodesAllocatesNothingOnTheJavaHeap() {
        val bitmap = load("no_code.jpg")
        val pool = FramePool(PREVIEW_SIDE)
        val meta = IntArray(ZxingCodeAnalyzer.MAX_CODES * CodeScanNative.META_STRIDE)
        val size = IntArray(2)
        val buffer = ByteBuffer.allocateDirect(PREVIEW_SIDE * PREVIEW_SIDE)
        val mask = CodeFormat.maskForGroups(CodeFormat.DEFAULT_GROUPS)
        fun scanOnce(): Array<String>? {
            check(CodeScanNative.prepareFrame(bitmap, buffer, PREVIEW_SIDE, size))
            return CodeScanNative.scan(buffer, size[0], size[1], mask, false, 8, 1024, meta)
        }
        repeat(3) { scanOnce() }

        // Make sure the counter works on this runtime before trusting a zero.
        Debug.resetThreadAllocCount()
        Debug.startAllocCounting()
        val probe = arrayOfNulls<Any>(4)
        Debug.stopAllocCounting()
        assumeTrue("allocation counting unsupported", Debug.getThreadAllocCount() > 0 && probe.isNotEmpty())

        Debug.resetThreadAllocCount()
        Debug.startAllocCounting()
        val result = scanOnce()
        Debug.stopAllocCounting()
        val allocations = Debug.getThreadAllocCount()
        assertNull(result)
        assertEquals("Java allocations during a scan with nothing found", 0, allocations)
        pool.release()
    }

    private companion object {
        const val PREVIEW_SIDE = 600
        const val QUICK_PASS_BUDGET_MS = 40.0
        const val THOROUGH_PASS_BUDGET_MS = 250.0
    }
}
