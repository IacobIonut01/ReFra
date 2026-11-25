package com.dot.gallery.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.app.BuildConfig
import com.dot.gallery.app.presentation.util.launchEditIntent
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.presentation.util.FileProviderShareDirs
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.editor.android.EditorViewModel
import com.dot.gallery.editor.android.VideoEditorViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioInternalEditorActivityTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    @Test
    fun studioPhotoLaunchOpensOwnProviderUri() {
        val file = writeSharedFile("studio-internal-photo.png") { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.rgb(48, 96, 144))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } finally {
                bitmap.recycle()
            }
        }
        try {
            assertStudioLaunchOpens(
                file = file,
                mimeType = "image/png",
                activityClass = GalleryInternalEditorActivity::class.java,
                imageEditor = true
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun studioVideoLaunchOpensOwnProviderUri() {
        val file = writeSharedFile("studio-internal-video.mp4") { output ->
            instrumentation.context.assets
                .open("editor/tiny.mp4")
                .use { input -> input.copyTo(output) }
        }
        try {
            assertStudioLaunchOpens(
                file = file,
                mimeType = "video/mp4",
                activityClass = GalleryInternalVideoEditorActivity::class.java,
                imageEditor = false
            )
        } finally {
            file.delete()
        }
    }

    private fun writeSharedFile(name: String, write: (FileOutputStream) -> Unit): File {
        val directory = FileProviderShareDirs.cache(
            context,
            FileProviderShareDirs.MEDIA_DIRECTORY
        )
        val file = File(directory, name)
        FileOutputStream(file).use(write)
        return file
    }

    private fun <T : Activity> assertStudioLaunchOpens(
        file: File,
        mimeType: String,
        activityClass: Class<T>,
        imageEditor: Boolean
    ) {
        val source = FileProvider.getUriForFile(
            context,
            BuildConfig.CONTENT_AUTHORITY,
            file
        )
        val media = UriMedia(
            id = 1,
            label = file.name,
            uri = source,
            path = file.absolutePath,
            relativePath = file.parent.orEmpty(),
            albumID = 0,
            albumLabel = "",
            timestamp = System.currentTimeMillis() / 1000,
            fullDate = "",
            mimeType = mimeType,
            favorite = 0,
            trashed = 0,
            size = file.length()
        )
        val monitor = instrumentation.addMonitor(activityClass.name, null, false)
        try {
            runBlocking {
                val settings = DataStoreSettingsStore(context.activeDataStore)
                val editorKey = if (imageEditor) {
                    SettingsKeys.defaultImageEditor
                } else {
                    SettingsKeys.defaultVideoEditor
                }
                val previousEditor = settings.observe(editorKey).first()
                try {
                    settings.put(editorKey, EDITOR_STUDIO)
                    newTaskContext().launchEditIntent(media)
                } finally {
                    settings.put(editorKey, previousEditor)
                }
            }
            val activity = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("Studio editor did not launch", activity)
            assertEquals(activityClass, activity.javaClass)
            awaitResumed(activity)
            if (imageEditor) {
                awaitPhotoLoaded(activity)
            } else {
                awaitVideoLoaded(activity)
            }
            instrumentation.runOnMainSync { activity.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun photoViewModel(activity: Activity): EditorViewModel {
        val getter = activity.javaClass.methods.single {
            it.returnType == EditorViewModel::class.java
        }
        return getter.invoke(activity) as EditorViewModel
    }

    private fun videoViewModel(activity: Activity): VideoEditorViewModel {
        val getter = activity.javaClass.methods.single {
            it.returnType == VideoEditorViewModel::class.java
        }
        return getter.invoke(activity) as VideoEditorViewModel
    }

    private fun awaitPhotoLoaded(activity: Activity, timeoutMs: Long = 10_000) {
        val viewModel = photoViewModel(activity)
        awaitCondition("Studio photo did not load", timeoutMs) {
            val state = viewModel.session.state.value
            if (state.error != null) fail("Studio photo failed to open")
            !state.opening && state.document != null
        }
    }

    private fun awaitVideoLoaded(activity: Activity, timeoutMs: Long = 10_000) {
        val viewModel = videoViewModel(activity)
        awaitCondition("Studio video did not load", timeoutMs) {
            val state = viewModel.session.state.value
            if (state.error != null) fail("Studio video failed to open")
            !state.opening && state.sequence != null
        }
    }

    private fun awaitCondition(
        failureMessage: String,
        timeoutMs: Long,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail(failureMessage)
    }

    private fun newTaskContext(): Context = object : ContextWrapper(context) {
        override fun startActivity(intent: Intent) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            super.startActivity(intent)
        }
    }

    private fun awaitResumed(activity: Activity, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = (activity as LifecycleOwner).lifecycle.currentState
            if (state == Lifecycle.State.RESUMED) return
            if (state == Lifecycle.State.DESTROYED || activity.isFinishing) {
                fail("Studio editor finished before opening the media")
            }
            Thread.sleep(50)
        }
        fail("Studio editor did not resume")
    }
}
