/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.logging.printError
import com.dot.gallery.editor.android.AndroidPhotoMediaAccess
import com.dot.gallery.editor.android.AndroidVideoMediaAccess
import com.dot.gallery.editor.android.AndroidVoiceRecorder
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.ui.EditorHostTools
import com.dot.gallery.editor.ui.EditorLaunchPose
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.EditorWorkspaceTheme
import com.dot.gallery.editor.ui.LocalEditorHostTools
import com.dot.gallery.editor.ui.LocalEditorLaunchPose
import com.dot.gallery.editor.ui.PhotoEditorScreen
import com.dot.gallery.editor.ui.video.VideoEditorScreen
import com.dot.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.Dispatchers

private const val TAG = "docs.editor"
private const val PHOTO_READY_TAG = "docs-studio-photo-ready"
private const val VIDEO_READY_TAG = "docs-studio-video-ready"

/**
 * The ReFra editor scenes: the real [PhotoEditorScreen] and
 * [VideoEditorScreen] over live sessions that open the bundled docs media
 * from this package's own MediaStore rows, the same `content://` path the
 * Gallery host hands the editor. Each state is an [EditorLaunchPose], so the
 * tool opens through the workspace's own selection. Nothing is exported.
 */
internal val editorScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "studio-photo",
        title = "ReFra photo editor",
        pathTrace = listOf("Photo", "Edit"),
        states = listOf(
            "home", "tools", "light", "colour", "curves", "white-balance", "looks",
            "detail", "lens-blur", "crop", "perspective", "markup", "borders",
            "pins",
        ),
        captureWindow = true,
        settleMillis = 1500L,
        requiredReadyTag = PHOTO_READY_TAG,
        // The editor is dark-only until its light theme is fixed.
        themes = listOf("dark"),
    ) { state -> DocsStudioPhotoScene(state) },
    DocsScene(
        id = "studio-video",
        title = "ReFra video editor",
        pathTrace = listOf("Video", "Edit"),
        states = listOf(
            "timeline", "tools", "trim", "transform", "colour", "add-audio", "mixer",
            "export", "settings",
        ),
        captureWindow = true,
        settleMillis = 2000L,
        requiredReadyTag = VIDEO_READY_TAG,
        themes = listOf("dark"),
    ) { state -> DocsStudioVideoScene(state) },
)

private fun studioPhotoPose(state: String?): EditorLaunchPose? = mapOf(
    "home" to null,
    "tools" to EditorLaunchPose(catalogueOpen = true),
    "light" to EditorLaunchPose(tool = EditorToolIds.PhotoLight.value),
    "colour" to EditorLaunchPose(tool = EditorToolIds.PhotoColour.value),
    "curves" to EditorLaunchPose(tool = EditorToolIds.PhotoCurves.value),
    "white-balance" to EditorLaunchPose(tool = EditorToolIds.PhotoWhiteBalance.value),
    "looks" to EditorLaunchPose(tool = EditorToolIds.PhotoLooks.value),
    "detail" to EditorLaunchPose(tool = EditorToolIds.PhotoDetail.value),
    "lens-blur" to EditorLaunchPose(tool = EditorToolIds.PhotoLensBlur.value),
    "crop" to EditorLaunchPose(tool = EditorToolIds.PhotoCrop.value),
    "perspective" to EditorLaunchPose(tool = EditorToolIds.PhotoPerspective.value),
    "markup" to EditorLaunchPose(tool = EditorToolIds.PhotoMarkup.value),
    "borders" to EditorLaunchPose(tool = EditorToolIds.PhotoBorders.value),
    "pins" to EditorLaunchPose(pinEditorOpen = true),
)[state]

private fun studioVideoPose(state: String?): EditorLaunchPose? = mapOf(
    "timeline" to null,
    "tools" to EditorLaunchPose(catalogueOpen = true),
    "trim" to EditorLaunchPose(tool = EditorToolIds.VideoTrim.value),
    "transform" to EditorLaunchPose(tool = EditorToolIds.VideoTransform.value),
    "colour" to EditorLaunchPose(tool = EditorToolIds.VideoColour.value),
    "add-audio" to EditorLaunchPose(tool = EditorToolIds.VideoAddAudio.value),
    "mixer" to EditorLaunchPose(tool = EditorToolIds.VideoMixer.value),
    "export" to EditorLaunchPose(tool = EditorToolIds.VideoExport.value),
    "settings" to EditorLaunchPose(settingsOpen = true),
)[state]

/**
 * Host seams as the Gallery provides them once the Cutout and Face
 * Detection models are installed, so the catalogue and the Markup faces
 * action show what those users see. The docs scenes never run either model.
 */
private val docsHostTools = EditorHostTools(
    openSegmenter = { null },
    detectFaces = { emptyList() },
    openInExternalEditor = {},
)

@Composable
private fun StudioEditorHost(content: @Composable () -> Unit) {
    // The Gallery editor host always renders the dark studio palette.
    GalleryTheme(darkTheme = true) {
        EditorWorkspaceTheme {
            CompositionLocalProvider(LocalEditorHostTools provides docsHostTools) {
                content()
            }
        }
    }
}

@Composable
fun DocsStudioPhotoScene(state: String?) {
    val context = LocalContext.current
    val media = rememberDocsMediaStoreImage(
        resourcePath = "drawable/docs_lake.jpg",
        displayName = "docs_editor_lake.jpg",
    )
    val scope = rememberCoroutineScope()
    val session = remember {
        PhotoEditorSession(
            scope = scope,
            media = AndroidPhotoMediaAccess(context, onError = ::reportEditorError),
            onError = ::reportEditorError,
        )
    }
    val reference = media.uri.toString()
    LaunchedEffect(session, reference) { session.open(reference) }
    DisposableEffect(session) { onDispose { session.close() } }
    val editorState by session.state.collectAsState()
    StudioEditorHost {
        CompositionLocalProvider(LocalEditorLaunchPose provides studioPhotoPose(state)) {
            Box(Modifier.fillMaxSize()) {
                PhotoEditorScreen(
                    session = session,
                    onClose = {},
                    onRetryOpen = { session.open(reference) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (editorState.document != null && !editorState.opening &&
                    editorState.preview != null
                ) {
                    Spacer(Modifier.size(0.dp).testTag(PHOTO_READY_TAG))
                }
            }
        }
    }
}

@Composable
fun DocsStudioVideoScene(state: String?) {
    val context = LocalContext.current
    val media = rememberDocsMediaStoreVideo(
        resourcePath = "files/docs_flower.mp4",
        displayName = "docs_editor_flower.mp4",
    )
    val scope = rememberCoroutineScope()
    val session = remember {
        val access = AndroidVideoMediaAccess(context, onError = ::reportEditorError)
        VideoEditorSession(
            scope = scope,
            media = access,
            playback = access.createPlayback(
                workerDispatcher = Dispatchers.IO,
                onFocusLost = {},
            ),
            voiceRecorder = AndroidVoiceRecorder(context, onError = ::reportEditorError),
            onError = ::reportEditorError,
        )
    }
    val reference = media.uri.toString()
    LaunchedEffect(session, reference) { session.open(reference) }
    DisposableEffect(session) { onDispose { session.close() } }
    val editorState by session.state.collectAsState()
    StudioEditorHost {
        CompositionLocalProvider(LocalEditorLaunchPose provides studioVideoPose(state)) {
            Box(Modifier.fillMaxSize()) {
                VideoEditorScreen(
                    session = session,
                    onClose = {},
                    onRetryOpen = { session.open(reference) },
                    // The video host consumes the system-bar insets itself.
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    onRequestAddMedia = {},
                    onRequestAddAudio = {},
                )
                if (editorState.sequence != null && !editorState.opening &&
                    editorState.preview != null
                ) {
                    Spacer(Modifier.size(0.dp).testTag(VIDEO_READY_TAG))
                }
            }
        }
    }
}

private fun reportEditorError(error: Throwable) {
    printError(TAG, "Docs editor scene error", error)
}
