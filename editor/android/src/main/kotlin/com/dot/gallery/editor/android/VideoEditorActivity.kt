package com.dot.gallery.editor.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.EditorSettingsStore
import com.dot.gallery.editor.ui.LocalEditorSettingsStore
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.editor.ui.video.VideoEditorScreen
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.launch

/**
 * The externally-callable video editor: launched with
 * [VideoEditorIntents.create] (or any action in [acceptedActions]) against
 * a `content://` MP4/QuickTime source, shows [VideoEditorScreen], writes a
 * new copy through MediaStore, and finishes with the [VideoEditorIntents]
 * result contract.
 *
 * Result rules match the photo host:
 * - save committed while the sequence is clean finishes immediately with
 *   [Activity.RESULT_OK] + the saved copy;
 * - closing later still returns the last saved copy, otherwise
 *   [Activity.RESULT_CANCELED];
 * - a bad request (wrong action, no/non-`content` data, a missing read
 *   grant, or an `EXTRA_OUTPUT` we cannot honour) finishes with
 *   [VideoEditorIntents.RESULT_ERROR] and a safe message — never silently.
 *
 * Hosts theme via [EditorHostTheme] and log via [reportError]; both default
 * implementations are gallery-free so the base activity works standalone.
 */
open class VideoEditorActivity : ComponentActivity() {

    // internal so instrumentation tests can assert the retained session state.
    internal val viewModel: VideoEditorViewModel by viewModels()

    /**
     * Actions this activity treats as edit requests; subclasses exposing a
     * further custom intent action add it here (the base accepts both the
     * library's [VideoEditorIntents.ACTION_EDIT] and the platform
     * [Intent.ACTION_EDIT]).
     */
    protected open fun acceptedActions(): Set<String> =
        setOf(VideoEditorIntents.ACTION_EDIT, Intent.ACTION_EDIT)

    /** The editor chrome; hosts wrap their own theme around [content]. */
    @Composable
    protected open fun EditorHostTheme(content: @Composable () -> Unit) {
        // Dark only until the editor's light theme is fixed.
        EditorTheme(darkTheme = true) { content() }
    }

    /** Diagnostics sink — no Gallery logging dependency in the library. */
    protected open fun reportError(error: Throwable) {
        Logger.getLogger(TAG).log(Level.WARNING, "video editor error", error)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A caller that never sees a later setResult still gets Cancelled.
        setResult(Activity.RESULT_CANCELED)

        val error = validateRequest()
        if (error != null) {
            setResult(
                VideoEditorIntents.RESULT_ERROR,
                VideoEditorIntents.errorResult(error)
            )
            finish()
            return
        }

        viewModel.errorReporter = ::reportError
        val source = requireNotNull(intent.data)
        val persistable =
            intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        viewModel.start(source, persistable, restoring = savedInstanceState != null)

        // External-editor convention: a save that lands while the sequence
        // is still committed and clean finishes the task with the saved
        // copy. The receipt is marked handled the first time it is seen —
        // a dirty save keeps the editor open, and a later undo back to
        // clean must not retroactively finish it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.session.state.collect { state ->
                    val exported = state.exported ?: return@collect
                    if (exported.reference == viewModel.handledExportRef) {
                        return@collect
                    }
                    viewModel.markExportHandled(exported.reference)
                    if (!state.isDirty && !isFinishing) {
                        setResult(
                            Activity.RESULT_OK,
                            VideoEditorIntents.savedResult(exported)
                        )
                        finish()
                    }
                }
            }
        }

        setContent {
            EditorHostTheme {
                // System bars follow the actual surface luminance, not the
                // host's theme name — a light host theme gets dark icons.
                val darkSurface =
                    MaterialTheme.colorScheme.surface.luminance() < 0.5f
                LaunchedEffect(darkSurface) {
                    val style = if (darkSurface) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                // The outer Surface paints behind the system bars (edge-to-
                // edge leaves the window background otherwise); the screen
                // still pads itself inside safeDrawing exactly once.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    // The add-media seam: the system document picker over
                    // video types, multi-select. Each picked URI tries to
                    // extend a persistable read grant so a project restore
                    // can re-open the source; the live grant still covers
                    // this session when persistence is refused, so a
                    // refusal is reported but never blocks the import.
                    val pendingInsertion = remember {
                        mutableStateOf(VideoInsertion.End)
                    }
                    val addMediaLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        for (uri in uris) {
                            runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }.onFailure(::reportError)
                            viewModel.session.addVideoSource(
                                uri.toString(), pendingInsertion.value
                            )
                        }
                    }
                    // The add-audio seam: the system document picker over
                    // every audio type, multi-select — each picked URI
                    // lands its own lane through addAudioSource.
                    val addAudioLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        for (uri in uris) {
                            runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }.onFailure(::reportError)
                            viewModel.session.addAudioSource(uri.toString())
                        }
                    }
                    // The mic seam: the voice surface's Continue fires the
                    // platform prompt; the result either re-queries support
                    // (granted → Ready) or marks the refusal (Denied copy).
                    val micLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted: Boolean ->
                        if (granted) {
                            viewModel.session.refreshVoicePermission()
                        } else {
                            viewModel.session.voicePermissionDenied()
                        }
                    }
                    // The recovery seam's picker: a missing source can be
                    // video or audio, so the dialog accepts both and the
                    // held reference rides through the relink.
                    val pendingRelink = remember {
                        mutableStateOf<String?>(null)
                    }
                    val relinkLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        val missing = pendingRelink.value
                        pendingRelink.value = null
                        if (uri != null && missing != null) {
                            runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }.onFailure(::reportError)
                            viewModel.session.relinkRestore(
                                missing, uri.toString()
                            )
                        }
                    }
                    // Settings pages persist in app preferences.
                    val editorSettings = remember {
                        EditorSettingsStore(AndroidEditorSettingsPersistence(applicationContext))
                    }
                    CompositionLocalProvider(LocalEditorSettingsStore provides editorSettings) {
                        VideoEditorScreen(
                            session = viewModel.session,
                            onClose = ::finishWithResult,
                            onRetryOpen = viewModel::retryOpen,
                            // Insets are consumed here, at the host — shared
                            // content owns none of them.
                            modifier =
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                            onOpenExport = ::openExportedCopy,
                            onRequestAddMedia = { insertion ->
                                pendingInsertion.value = insertion
                                addMediaLauncher.launch(
                                    arrayOf("video/*")
                                )
                            },
                            onRequestAddAudio = {
                                addAudioLauncher.launch(arrayOf("audio/*"))
                            },
                            onRequestMicrophone = {
                                micLauncher.launch(
                                    android.Manifest.permission.RECORD_AUDIO
                                )
                            },
                            onRequestRelink = { missing ->
                                pendingRelink.value = missing
                                relinkLauncher.launch(
                                    arrayOf("video/*", "audio/*")
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        // The editor is not a background music service — a stopped editor
        // pauses playback, and its position rides the saved-state bundle.
        viewModel.session.pause()
        viewModel.flushState()
        super.onStop()
    }

    private fun validateRequest(): String? = when {
        intent.action !in acceptedActions() ->
            "Unsupported action"
        intent.hasExtra(MediaStore.EXTRA_OUTPUT) ->
            "Saving into EXTRA_OUTPUT is not supported; read the returned copy"
        intent.data == null ->
            "No video to edit"
        intent.data?.scheme != "content" ->
            "Only shared content videos can be edited"
        // Without the grant flag the caller never delegated read access —
        // fail before the session touches the URI at all.
        intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ->
            "Read access to the video was not granted"
        else -> null
    }

    private fun finishWithResult() {
        val exported = viewModel.session.state.value.exported
        if (exported != null) {
            setResult(Activity.RESULT_OK, VideoEditorIntents.savedResult(exported))
        }
        finish()
    }

    private fun openExportedCopy(result: VideoExportResult) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(result.reference), result.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (error: ActivityNotFoundException) {
            reportError(error)
        }
    }

    private companion object {
        const val TAG = "editor.video"
    }
}
