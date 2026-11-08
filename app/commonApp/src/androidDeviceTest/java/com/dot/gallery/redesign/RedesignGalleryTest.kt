/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.redesign

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks every redesigned screen and sheet from the "Misc screens + sheets" FigCraft page with
 * mocked data, in light and dark, and saves a PNG of each to the target app's external files
 * folder (redesign-gallery). Pull them with
 * `adb pull /sdcard/Android/data/<package>/files/redesign-gallery` to compare with the design.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class RedesignGalleryTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryRedesignEntry() {
        val entries = RedesignGalleryEntries.all
        var current by mutableStateOf(entries.first())
        var dark by mutableStateOf(false)
        rule.setContent {
            GalleryTheme(darkTheme = dark, dynamicColor = false, ignoreUserPreference = true) {
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .wrapContentHeight()
                        .testTag(TAG),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    when (val entry = current) {
                        is GalleryEntry.Sheet -> SheetFrame { entry.content(this) }
                        is GalleryEntry.Screen -> Box(Modifier.fillMaxWidth()) { entry.content() }
                    }
                }
            }
        }
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        for (entry in entries) {
            for (isDark in listOf(false, true)) {
                current = entry
                dark = isDark
                rule.waitForIdle()
                val image = rule.onNodeWithTag(TAG).captureToImage()
                assertTrue("${entry.name} rendered nothing", image.width > 0 && image.height > 0)
                savePng(resolver, "${entry.name}${if (isDark) "-dark" else ""}.png", image.asAndroidBitmap())
            }
        }
    }

    /**
     * Writes into Download/redesign-gallery through MediaStore, so the images outlive the test
     * APK, which the connected test task uninstalls when it finishes.
     */
    private fun savePng(resolver: ContentResolver, name: String, bitmap: Bitmap) {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, "$FOLDER/"),
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TAG = "redesign-gallery"
        val FOLDER = "${Environment.DIRECTORY_DOWNLOADS}/redesign-gallery"
    }
}

/** One screen or sheet rendered by [RedesignGalleryTest]. */
sealed interface GalleryEntry {
    val name: String

    class Sheet(override val name: String, val content: @Composable ColumnScope.() -> Unit) : GalleryEntry
    class Screen(override val name: String, val content: @Composable () -> Unit) : GalleryEntry
}

/** The sheet container without the modal window, so the body can be captured in a test. */
@Composable
fun SheetFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = ExpressiveSheetShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DragHandle(modifier = Modifier.padding(vertical = 5.dp))
            ExpressiveSheetColumn(content = content)
        }
    }
}
