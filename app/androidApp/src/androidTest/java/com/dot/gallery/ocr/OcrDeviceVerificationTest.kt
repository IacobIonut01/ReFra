/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.engine.OcrRequestPriority
import com.dot.gallery.core.ocr.engine.OcrSessionManager
import com.dot.gallery.core.ocr.engine.PpOcrV5Engine
import com.dot.gallery.core.ocr.model.OcrFileMatcher
import com.dot.gallery.core.ocr.model.OcrPackStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Runs the real PP-OCRv5 models on the device over text rendered with known content, and reports
 * accuracy (character error rate without spaces), how many lines were found, reading order, time and
 * peak memory per photo. The pinned models are downloaded from the catalog URLs into the app's model
 * store (verified by SHA-256) the first time; the test is skipped when they cannot be fetched.
 *
 * Run: ./gradlew :app:androidApp:connectedArm64-v8aNoMLDebugAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.dot.gallery.ocr.OcrDeviceVerificationTest
 * The report is printed to logcat under the tag OcrVerify.
 */
@RunWith(AndroidJUnit4::class)
class OcrDeviceVerificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = OcrPackStore(context)
    private val engine = PpOcrV5Engine(store, OcrSessionManager(store))

    @Test
    fun readsRenderedTextAccurately() = runBlocking {
        assumeTrue("OCR models could not be downloaded", install("latin"))
        val packs = setOf("latin")
        // Warm-up: opens the sessions so the timings below are per photo, not per first load.
        engine.recognize(paragraph(lines = 2, textSize = 44f, width = 800, height = 300), packs, OcrRequestPriority.INTERACTIVE)

        val results = listOf(
            run("paragraph 44px", packs, PARAGRAPH.take(12)) { paragraph(12, 44f, 1600, 1200) },
            run("small text 18px", packs, PARAGRAPH.take(20)) { paragraph(20, 18f, 2048, 1536) },
            run("long line", packs, listOf(LONG_LINE)) { singleLine(LONG_LINE, 32f, 2048, 200) },
            run("receipt", packs, RECEIPT.flatMap { listOf(it.first, it.second) }, checkOrder = true) { receipt() },
            run("upside down", packs, PARAGRAPH.take(4)) { rotated(paragraph(4, 44f, 1400, 500), 180f) },
            run("rotated 90° (top to bottom)", packs, PARAGRAPH.take(3)) { rotated(paragraph(3, 44f, 1400, 400), 90f) },
            run("dense page 28px", packs, (PARAGRAPH + PARAGRAPH).take(44)) { paragraph(44, 28f, 2048, 2048) },
        )
        val arabic = if (install("arabic")) {
            listOf(run("arabic", setOf("arabic"), ARABIC) { arabic() })
        } else {
            log("arabic: skipped, model could not be downloaded")
            emptyList()
        }
        // Reported for comparison, not asserted: smaller text, and the detector's old 960 px cap.
        val probes = listOf(
            run("tiny text 13px", packs, PARAGRAPH.take(24), informational = true) { paragraph(24, 13f, 2048, 1536) },
        )
        engine.releaseSessions()
        val capped = PpOcrV5Engine(store, OcrSessionManager(store), detectorMaxSide = 960)
        capped.recognize(paragraph(lines = 2, textSize = 44f, width = 800, height = 300), packs, OcrRequestPriority.INTERACTIVE)
        val cappedRuns = listOf(
            run("[960 cap] paragraph 44px", packs, PARAGRAPH.take(12), engine = capped, informational = true) { paragraph(12, 44f, 1600, 1200) },
            run("[960 cap] small text 18px", packs, PARAGRAPH.take(20), engine = capped, informational = true) { paragraph(20, 18f, 2048, 1536) },
            run("[960 cap] tiny text 13px", packs, PARAGRAPH.take(24), engine = capped, informational = true) { paragraph(24, 13f, 2048, 1536) },
            run("[960 cap] dense page 28px", packs, (PARAGRAPH + PARAGRAPH).take(44), engine = capped, informational = true) { paragraph(44, 28f, 2048, 2048) },
        )
        capped.releaseSessions()

        val all = results + arabic + probes + cappedRuns
        log("==== summary ====")
        all.forEach { log(it.summary()) }
        val failures = all.mapNotNull { it.failure() }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private suspend fun run(
        name: String,
        packs: Set<String>,
        expected: List<String>,
        checkOrder: Boolean = false,
        engine: PpOcrV5Engine = this.engine,
        informational: Boolean = false,
        render: () -> Bitmap,
    ): CaseResult {
        val bitmap = render()
        System.gc()
        val baseline = usedBytes()
        val peak = AtomicLong(baseline)
        val sampling = AtomicBoolean(true)
        val sampler = thread(name = "ocr-memory") {
            while (sampling.get()) {
                peak.accumulateAndGet(usedBytes(), ::maxOf)
                Thread.sleep(5)
            }
        }
        val start = System.nanoTime()
        val document = try {
            engine.recognize(bitmap, packs, OcrRequestPriority.INTERACTIVE)
        } finally {
            sampling.set(false)
            sampler.join()
        }
        val millis = (System.nanoTime() - start) / 1_000_000
        System.gc()
        val retained = (usedBytes() - baseline) / (1024 * 1024)
        val result = CaseResult(name, expected, document, millis, (peak.get() - baseline) / (1024 * 1024), retained, checkOrder, informational)
        log("---- $name: ${bitmap.width}x${bitmap.height}")
        document.lines.forEach { log("  [${"%.2f".format(it.confidence)} ${it.packId}] ${it.text}") }
        log("  ${result.summary()}")
        bitmap.recycle()
        return result
    }

    private class CaseResult(
        val name: String,
        val expected: List<String>,
        val document: OcrDocument,
        val millis: Long,
        val peakMegabytes: Long,
        val retainedMegabytes: Long,
        val checkOrder: Boolean,
        val informational: Boolean,
    ) {
        private val read = document.lines.map { it.text }
        private val matches = expected.map { line -> read.minOfOrNull { cer(line, it) } ?: 1f }
        val found = matches.count { it <= 0.3f }
        val errorRate: Float = run {
            val total = expected.sumOf { strip(it).length }
            val errors = expected.sumOf { line -> read.minOfOrNull { distance(strip(line), strip(it)) } ?: strip(line).length }
            errors.toFloat() / total
        }
        val inOrder: Boolean = run {
            val positions = expected.map { line -> read.indices.minByOrNull { cer(line, read[it]) } ?: -1 }
            positions.zipWithNext().all { (a, b) -> a < b }
        }

        fun summary() = "$name: lines ${found}/${expected.size}, CER ${"%.3f".format(errorRate)}, " +
            "${millis} ms, peak +${peakMegabytes} MB, kept +${retainedMegabytes} MB" + if (checkOrder) ", order ${if (inOrder) "ok" else "WRONG"}" else ""

        fun failure(): String? = when {
            informational -> null
            found < expected.size * 0.8 -> "$name: found only $found of ${expected.size} lines"
            errorRate > 0.10f -> "$name: character error rate ${"%.3f".format(errorRate)}"
            checkOrder && !inOrder -> "$name: reading order wrong: $read"
            else -> null
        }
    }

    // ---- rendering ----

    private fun canvas(width: Int, height: Int): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return bitmap to Canvas(bitmap).apply { drawColor(Color.WHITE) }
    }

    private fun paint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = Typeface.DEFAULT
    }

    private fun paragraph(lines: Int, textSize: Float, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = canvas(width, height)
        val paint = paint(textSize)
        val step = textSize * 1.6f
        repeat(lines) { index ->
            val text = (PARAGRAPH + PARAGRAPH)[index]
            canvas.drawText(text, textSize, textSize * 1.5f + index * step, paint)
        }
        return bitmap
    }

    private fun singleLine(text: String, textSize: Float, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = canvas(width, height)
        canvas.drawText(text, 24f, height / 2f + textSize / 3f, paint(textSize))
        return bitmap
    }

    private fun receipt(): Bitmap {
        val (bitmap, canvas) = canvas(1200, 900)
        val paint = paint(38f)
        val right = paint(38f).apply { textAlign = Paint.Align.RIGHT }
        RECEIPT.forEachIndexed { index, (item, price) ->
            val y = 120f + index * 110f
            canvas.drawText(item, 60f, y, paint)
            canvas.drawText(price, 1140f, y, right)
        }
        return bitmap
    }

    private fun arabic(): Bitmap {
        val (bitmap, canvas) = canvas(1400, 400)
        val paint = paint(56f).apply { textAlign = Paint.Align.RIGHT }
        ARABIC.forEachIndexed { index, text -> canvas.drawText(text, 1350f, 140f + index * 140f, paint) }
        return bitmap
    }

    private fun rotated(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also { source.recycle() }
    }

    // ---- models ----

    /** Downloads [packId]'s pinned models into the app's model store unless they are already there. */
    private fun install(packId: String): Boolean = runCatching {
        for (model in store.modelsFor(packId)) {
            if (store.isStored(model)) continue
            val target = store.destination(model)
            val part = File(target.path + ".part")
            target.parentFile!!.mkdirs()
            val connection = URL(model.onnx.url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            val sha = part.inputStream().use(OcrFileMatcher::sha256)
            check(part.length() == model.onnx.sizeBytes && sha == model.onnx.sha256) { "${model.id} does not verify" }
            check(part.renameTo(target)) { "could not move ${model.id}" }
            log("downloaded ${model.id} (${model.onnx.sizeBytes} bytes)")
        }
        store.refresh()
        store.areReady(listOf(packId))
    }.getOrElse {
        log("install $packId failed: $it")
        false
    }

    private fun usedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return Debug.getNativeHeapAllocatedSize() + runtime.totalMemory() - runtime.freeMemory()
    }

    private fun log(message: String) {
        // Instrumentation report: logcat is the only channel the test runner leaves for it.
        android.util.Log.i("OcrVerify", message)
    }

    private companion object {
        val PARAGRAPH = listOf(
            "The quick brown fox jumps over the lazy dog 1234",
            "Invoice number 58213 issued on 14 March 2026",
            "Please keep this receipt for your records",
            "Total amount due before tax: 249.90 EUR",
            "Opening hours Monday to Friday 08:00 - 18:30",
            "Platform 7 departs at 16:45 for Bucharest Nord",
            "Pack my box with five dozen liquor jugs",
            "Visit us online for more offers and news",
            "Room 214 is on the second floor, turn left",
            "Battery level 87 percent, charging complete",
            "Order 4471 will arrive between 10 and 12",
            "Sphinx of black quartz, judge my vow",
            "Customer service is open every day of the week",
            "Lot 2026-11 best before 30.04.2027",
            "Scan the code to download the timetable",
            "Exit B leads to the museum and the old town",
            "Water temperature today is 21 degrees",
            "Seats 12A and 12B are next to the window",
            "Recycle this package where facilities exist",
            "Thank you for shopping with us, see you soon",
            "Wi-Fi network GuestNet password on request",
            "Maximum load 630 kg or 8 persons",
            "Keep out of reach of children",
            "Printed in Romania on recycled paper",
        )
        const val LONG_LINE =
            "This single line of text is deliberately much longer than usual so that its width is more than thirty times its height"
        val RECEIPT = listOf(
            "Espresso" to "3.50",
            "Croissant with butter" to "4.20",
            "Orange juice" to "5.00",
            "Sparkling water" to "2.80",
            "Chocolate cake slice" to "6.40",
            "Total" to "21.90",
        )
        val ARABIC = listOf("مرحبا بالعالم", "هذا نص تجريبي")

        fun strip(text: String) = text.filterNot(Char::isWhitespace)

        fun cer(expected: String, actual: String): Float =
            distance(strip(expected), strip(actual)).toFloat() / maxOf(1, strip(expected).length)

        fun distance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                previous = current
            }
            return previous[b.length]
        }
    }
}
