package com.dot.gallery.editor.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.ui.EditorHostTools
import com.dot.gallery.editor.ui.EditorLookPresetStore
import com.dot.gallery.editor.ui.EditorSettingsStore
import com.dot.gallery.editor.ui.LocalEditorSettingsStore
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.LocalEditorHostTools
import com.dot.gallery.editor.ui.LocalEditorLookPresetStore
import com.dot.gallery.editor.ui.PhotoEditorScreen
import com.dot.gallery.editor.ui.PhotoRenderPreference
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.launch

/**
 * The externally-callable photo editor: launched with [EditorIntents.create]
 * (or any action in [acceptedActions]) against a `content://` JPEG/PNG,
 * shows [PhotoEditorScreen], writes a new copy through MediaStore, and
 * finishes with the [EditorIntents] result contract.
 *
 * Result rules:
 * - save committed while the document is clean finishes immediately with
 *   [Activity.RESULT_OK] + the saved copy (external-editor convention);
 * - closing later still returns the last saved copy, otherwise
 *   [Activity.RESULT_CANCELED];
 * - a bad request (wrong action, no/non-`content` data, or an
 *   `EXTRA_OUTPUT` we cannot honour) finishes with
 *   [EditorIntents.RESULT_ERROR] and a safe message — never silently.
 *
 * Hosts theme via [EditorHostTheme] and log via [reportError]; both default
 * implementations are gallery-free so the base activity works standalone.
 */
open class EditorActivity : ComponentActivity() {

    // internal so instrumentation tests can assert the retained session state.
    internal val viewModel: EditorViewModel by viewModels()

    /**
     * Actions this activity treats as edit requests; subclasses exposing a
     * further custom intent action add it here (the base accepts both the
     * library's [EditorIntents.ACTION_EDIT] and the platform
     * [Intent.ACTION_EDIT]).
     */
    protected open fun acceptedActions(): Set<String> =
        setOf(EditorIntents.ACTION_EDIT, Intent.ACTION_EDIT)

    /** The editor chrome; hosts wrap their own theme around [content]. */
    @Composable
    protected open fun EditorHostTheme(content: @Composable () -> Unit) {
        EditorTheme(darkTheme = isSystemInDarkTheme()) { content() }
    }

    /** Diagnostics sink — no Gallery logging dependency in the library. */
    protected open fun reportError(error: Throwable) {
        Logger.getLogger(TAG).log(Level.WARNING, "photo editor error", error)
    }

    /**
     * What this host adds to the shared tools. The base offers Open in; the
     * Gallery host adds its on-device selection and sharing.
     */
    protected open fun editorHostTools(): EditorHostTools =
        EditorHostTools(openInExternalEditor = ::openInExternalEditor)

    /** Hands the source photo to another app's editor. */
    protected fun openInExternalEditor() {
        val source = intent.data ?: return
        val edit = Intent(Intent.ACTION_EDIT)
            .setDataAndType(source, contentResolver.getType(source) ?: "image/*")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        val chooser = Intent.createChooser(edit, null).putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, javaClass))
        )
        try {
            startActivity(chooser)
        } catch (error: ActivityNotFoundException) {
            reportError(error)
        }
    }

    /**
     * Preview backend selection — [PhotoRenderPreference.Auto] by default.
     * Instrumented hosts subclass to force [PhotoRenderPreference.Cpu] when
     * a pixel-exact baseline capture is wanted.
     */
    protected open fun photoRenderPreference(): PhotoRenderPreference =
        PhotoRenderPreference.Auto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A caller that never sees a later setResult still gets Cancelled.
        setResult(Activity.RESULT_CANCELED)

        val error = validateRequest()
        if (error != null) {
            setResult(EditorIntents.RESULT_ERROR, EditorIntents.errorResult(error))
            finish()
            return
        }

        viewModel.errorReporter = ::reportError
        val source = requireNotNull(intent.data)
        val persistable =
            intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        viewModel.start(source, persistable, restoring = savedInstanceState != null)

        // External-editor convention: a save that lands while the recipe is
        // still committed and clean finishes the task with the saved copy.
        // The receipt is marked handled the first time it is seen — a dirty
        // save keeps the editor open, and a later undo back to clean must
        // not retroactively finish it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.session.state.collect { state ->
                    val exported = state.exported ?: return@collect
                    if (exported.reference == viewModel.handledExportRef) {
                        return@collect
                    }
                    viewModel.markExportHandled(exported.reference)
                    if (!state.isDirty && !state.isDrafting && !isFinishing) {
                        setResult(
                            Activity.RESULT_OK,
                            EditorIntents.savedResult(exported)
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
                // The outer Surface paints behind the system bars: the
                // screen is edge to edge and every surface inside places
                // its own chrome against the safe-drawing insets.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Black,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    // The Layers surface's picker: the system document
                    // picker over JPEG/PNG. A picked URI tries to extend a
                    // persistable read grant so a later export can re-open
                    // the layer source; the live grant still covers this
                    // session when persistence is refused, so the failure
                    // is reported but never blocks the import.
                    val addImageLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }.onFailure(::reportError)
                            viewModel.session.addImageLayer(uri.toString())
                        }
                    }
                    // The recovery seam's picker: the missing reference is
                    // held until the dialog resolves, then the relink
                    // replays the pending recipe with the replacement.
                    var pendingRelink by remember {
                        mutableStateOf<String?>(null)
                    }
                    val relinkLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        val missing = pendingRelink
                        pendingRelink = null
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
                    var closeRequest by remember { mutableIntStateOf(0) }
                    // User looks live in app preferences so they survive
                    // the session; the screen falls back to an in-memory
                    // store when no host provides one.
                    val lookPresets = remember {
                        EditorLookPresetStore(
                            AndroidLookPresetPersistence(applicationContext)
                        )
                    }
                    val hostTools = remember { editorHostTools() }
                    val editorSettings = remember {
                        EditorSettingsStore(AndroidEditorSettingsPersistence(applicationContext))
                    }
                    CompositionLocalProvider(
                        LocalEditorLookPresetStore provides lookPresets,
                        LocalEditorHostTools provides hostTools,
                        LocalEditorSettingsStore provides editorSettings
                    ) {
                    PhotoEditorScreen(
                        session = viewModel.session,
                        closeRequest = closeRequest,
                        onClose = ::finishWithResult,
                        onRetryOpen = viewModel::retryOpen,
                        // The editor paints edge to edge: the shared screen
                        // consumes no window insets itself, so each host
                        // surface places its chrome against the system bars
                        // instead of padding the whole canvas away.
                        onOpenExport = ::openExportedCopy,
                        renderPreference = photoRenderPreference(),
                        onRequestAddImage = {
                            addImageLauncher.launch(
                                arrayOf("image/jpeg", "image/png")
                            )
                        },
                        onRequestRelink = { missing ->
                            pendingRelink = missing
                            relinkLauncher.launch(
                                arrayOf("image/jpeg", "image/png")
                            )
                        }
                    )
                    }
                    // Android Back must use the same tool/dirty-close unwind
                    // as the header, rather than finishing the host activity.
                    BackHandler { closeRequest++ }
                }
            }
        }
    }

    override fun onStop() {
        // A stopped editor may never resume — the debounced autosave must
        // not still hold the last committed edit when the process dies.
        viewModel.flushState()
        super.onStop()
    }

    private fun validateRequest(): String? = when {
        intent.action !in acceptedActions() ->
            "Unsupported action"
        intent.hasExtra(MediaStore.EXTRA_OUTPUT) ->
            "Saving into EXTRA_OUTPUT is not supported; read the returned copy"
        intent.data == null ->
            "No photo to edit"
        intent.data?.scheme != "content" ->
            "Only shared content photos can be edited"
        // Without the grant flag the caller never delegated read access —
        // fail before the session touches the URI at all.
        intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ->
            "Read access to the photo was not granted"
        else -> null
    }

    private fun finishWithResult() {
        val exported = viewModel.session.state.value.exported
        if (exported != null) {
            setResult(Activity.RESULT_OK, EditorIntents.savedResult(exported))
        }
        finish()
    }

    private fun openExportedCopy(result: PhotoExportResult) {
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
        const val TAG = "editor.photo"
    }
}
