/**
 * Original from
 * https://stackoverflow.com/a/70853761/12978728
 */
@file:Suppress("KotlinConstantConditions")

package com.dot.gallery.app.presentation.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
import android.content.pm.ActivityInfo.COLOR_MODE_HDR
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import com.dot.gallery.core.util.HdrCapabilities
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.app.presentation.edit.EditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.EDITOR_BUILTIN
import com.dot.gallery.domain.settings.EDITOR_STUDIO
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dot.gallery.core.util.or
import com.dot.gallery.feature.editor.adjustments.filters.Monochrome
import com.dot.gallery.app.presentation.main.MainActivity
import com.dot.gallery.core.Settings.Misc.rememberFullBrightnessView
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import com.dot.gallery.domain.util.volume
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.core.presentation.util.authorizedUri
import com.dot.gallery.core.presentation.util.mediaStoreVersion
import com.dot.gallery.core.presentation.util.resolveShareableUri
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.edit
import com.dot.gallery.generated.resources.error_toast
import com.dot.gallery.generated.resources.open_with
import com.dot.gallery.generated.resources.set_as
import com.dot.gallery.core.util.getString



fun Context.goBack(fallback: (() -> Unit)? = null) {
    if (this is ComponentActivity) {
        onBackPressedDispatcher.onBackPressed()
    } else {
        fallback?.invoke()
    }
}

@Composable
fun FullBrightnessWindow(content: @Composable () -> Unit) {
    ProvideWindowContext {
        val window = LocalWindowContext.current
        val enableFullBrightnessView by rememberFullBrightnessView()
        DisposableEffect(enableFullBrightnessView) {
            val layout = window?.attributes
            layout?.screenBrightness = if (enableFullBrightnessView) BRIGHTNESS_OVERRIDE_FULL else BRIGHTNESS_OVERRIDE_NONE
            window?.attributes = layout
            onDispose {
                layout?.screenBrightness = BRIGHTNESS_OVERRIDE_NONE
                window?.attributes = layout
            }
        }
        content()
    }
}

fun Context.setHdrMode(enabled: Boolean) {
    if (this !is Activity) return
    // Only ever opt the window into HDR on a display that can actually render it, so SDR-only
    // devices never pay the cost or risk washed-out UI.
    val allow = enabled && HdrCapabilities.isHdrDisplay(this)
    if (window.colorMode == COLOR_MODE_HDR && !allow) {
        window.colorMode = COLOR_MODE_DEFAULT
    } else if (window.colorMode == COLOR_MODE_DEFAULT && allow) {
        window.colorMode = COLOR_MODE_HDR
    }
    // Cap the HDR headroom (API 35+) so highlights don't over-brighten and wash out the SDR UI;
    // 0f restores the system default when HDR is off.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.desiredHdrHeadroom = if (allow) HdrCapabilities.DESIRED_HDR_HEADROOM else 0f
    }
}

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.navigationBars
    val density = LocalDensity.current
    return remember { with(density) { insets.getBottom(density).toDp() } }
}

@Composable
fun SecureWindow(content: @Composable () -> Unit) {
    ProvideWindowContext {
        val window = LocalWindowContext.current
        DisposableEffect(Unit) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        content()
    }
}

val LocalWindowContext = compositionLocalOf<Window?> { null }

@Composable
fun ProvideWindowContext(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val window = remember(context) {
        (context as Activity).window
    }
    CompositionLocalProvider(LocalWindowContext provides window, content = content)
}

suspend fun InternalDatabase.isMediaUpToDate(context: Context): Boolean {
    return getMediaDao().isMediaVersionUpToDate(context.mediaStoreVersion)
}

suspend fun InternalDatabase.isMetadataUpToDate(context: Context): Boolean {
    return getMetadataDao().isMediaVersionUpToDate(context.mediaStoreVersion)
}

@Composable
fun toastError(message: String? = null): Toast {
    val context = LocalContext.current
    return Toast.makeText(
        context,
        message ?: stringResource(Res.string.error_toast),
        Toast.LENGTH_SHORT
    )
}


fun Activity.toggleOrientation() {
    requestedOrientation =
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
            requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

@RequiresApi(Build.VERSION_CODES.S)
fun Context.launchManageMedia() {
    val intent = Intent().apply {
        action = Settings.ACTION_REQUEST_MANAGE_MEDIA
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

val isManageFilesAllowed: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && BuildConfig.ALLOW_ALL_FILES_ACCESS

@RequiresApi(Build.VERSION_CODES.S)
fun Context.launchManageFiles() {
    val intent = Intent().apply {
        action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

fun Context.getEditImageCapableApps(): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setType("image/*")
    }
    val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
    return resolveInfoList
        .filterNot { it.activityInfo.packageName == BuildConfig.APPLICATION_ID }
        .distinctBy { it.activityInfo.packageName }
}

fun Context.getEditVideoCapableApps(): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setType("video/*")
    }
    return packageManager.queryIntentActivities(intent, 0)
        .filterNot { it.activityInfo.packageName == BuildConfig.APPLICATION_ID }
        .distinctBy { it.activityInfo.packageName }
}

fun Context.launchEditImageIntent(packageName: String, uri: Uri) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri.authorizedUri(this@launchEditImageIntent), "image/*")
        putExtra("mimeType", "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(packageName)
    }
    startActivity(intent)
}

suspend fun <T: Media> Context.launchEditIntent(media: T) {
    val uri = if (media.isCloud) resolveShareableUri(media) else media.getUri().authorizedUri(this)
    val editorKey = if (media.isImage) SettingsKeys.defaultImageEditor else SettingsKeys.defaultVideoEditor
    val settingsStore = DataStoreSettingsStore(activeDataStore)
    var editor = runCatching {
        settingsStore.observe(editorKey).first()
    }.onFailure {
        printWarn("media-ops.edit", "Default editor preference unreadable", mapOf("error" to it.toString()))
    }.getOrDefault(editorKey.default)
    if (media.isImage && editor == EDITOR_BUILTIN) {
        val explicit = runCatching {
            settingsStore.observe(SettingsKeys.imageEditorChoiceExplicit).first()
        }.getOrDefault(false)
        val resolved = resolveImageEditorChoice(editor, explicit)
        if (resolved != editor) {
            runCatching { settingsStore.put(editorKey, resolved) }
                .onFailure {
                    printWarn("media-ops.edit", "Default editor migration failed", mapOf("error" to it.toString()))
                }
            editor = resolved
        }
    }
    withContext(Dispatchers.Main) {
        if (editor == EDITOR_STUDIO && launchStudioEditor(uri, media.mimeType)) return@withContext
        if (media.isImage) {
            if (editor != EDITOR_BUILTIN && editor != EDITOR_STUDIO &&
                launchExternalEditorPackage(editor, uri, media.mimeType)
            ) {
                return@withContext
            }
            EditActivity.launchEditor(this@launchEditIntent, uri)
        } else {
            if (editor != EDITOR_BUILTIN && editor != EDITOR_STUDIO &&
                launchExternalEditorPackage(editor, uri, media.mimeType)
            ) {
                return@withContext
            }
            val intent = Intent(Intent.ACTION_EDIT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, media.mimeType)
                putExtra("mimeType", media.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(Res.string.edit)))
        }
    }
}

/**
 * Opens the standalone ReFra photo/video editor for the MIME types its
 * hosts declare. Returns false when the media is unsupported or the host
 * activity is missing, so the caller falls back to the built-in editor.
 */
private fun Context.launchStudioEditor(uri: Uri, mimeType: String): Boolean {
    val component = studioEditorActivity(mimeType) ?: return false
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setClassName(packageName, component)
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (intent.resolveActivity(packageManager) == null) return false
    return runCatching { startActivity(intent) }
        .onFailure {
            printWarn(
                "media-ops.edit",
                "Standalone editor launch failed",
                mapOf("mime" to mimeType, "error" to it.toString()),
            )
        }
        .isSuccess
}

/**
 * Opens the external editor the user chose. Returns false when it is no
 * longer installed or refuses the file, so the caller falls back to the
 * built-in editor (images) or the app chooser (videos).
 */
private fun Context.launchExternalEditorPackage(packageName: String, uri: Uri, mimeType: String): Boolean {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, mimeType)
        putExtra("mimeType", mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(packageName)
    }
    return runCatching { startActivity(intent) }
        .onFailure {
            printWarn(
                "media-ops.edit",
                "Chosen video editor launch failed",
                mapOf("package" to packageName, "mime" to mimeType, "error" to it.toString()),
            )
        }
        .isSuccess
}

/**
 * The ReFra editor activity for [mimeType], or null when it cannot open it.
 * Every video type goes to the video editor, which reads the file's real
 * container and codecs rather than trusting the MIME type.
 */
internal fun studioEditorActivity(mimeType: String): String? {
    val mime = mimeType.lowercase()
    return when {
        mime in STUDIO_PHOTO_MIME_TYPES -> STUDIO_PHOTO_ACTIVITY
        mime.startsWith("video/") -> STUDIO_VIDEO_ACTIVITY
        else -> null
    }
}

/**
 * A persisted [EDITOR_BUILTIN] predates the studio default: builds before it
 * could only write "builtin" as the untouched default or as a pick that never
 * measured against the ReFra editor. Only a choice stamped with
 * `image_editor_choice_explicit` in a studio-aware build keeps the built-in
 * editor as the default.
 */
internal fun resolveImageEditorChoice(stored: String, explicitChoice: Boolean): String =
    if (stored == EDITOR_BUILTIN && !explicitChoice) EDITOR_STUDIO else stored

private val STUDIO_PHOTO_MIME_TYPES = setOf(
    "image/jpeg",
    "image/png",
    // Camera RAW, developed by the host's LibRaw decoder.
    "image/x-adobe-dng",
    "image/dng",
    "image/x-canon-cr2",
    "image/x-canon-cr3",
    "image/x-nikon-nef",
    "image/x-nikon-nrw",
    "image/x-sony-arw",
    "image/x-fuji-raf",
    "image/x-olympus-orf",
    "image/x-panasonic-rw2",
    "image/x-pentax-pef",
    "image/x-samsung-srw"
)
private const val STUDIO_PHOTO_ACTIVITY = "com.dot.gallery.editor.GalleryEditorActivity"
private const val STUDIO_VIDEO_ACTIVITY = "com.dot.gallery.editor.GalleryVideoEditorActivity"

suspend fun <T: Media> Context.launchUseAsIntent(media: T) =
    withContext(Dispatchers.Default) {
        val uri = if (media.isCloud) resolveShareableUri(media) else media.getUri().authorizedUri(this@launchUseAsIntent)
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, media.mimeType)
                putExtra("mimeType", media.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(Res.string.set_as)))
        }
    }

suspend fun <T: Media> Context.launchOpenWithIntent(media: T) =
    withContext(Dispatchers.Default) {
        val uri = if (media.isCloud) resolveShareableUri(media) else media.getUri().authorizedUri(this@launchOpenWithIntent)
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, media.mimeType)
                putExtra("mimeType", media.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(Res.string.open_with)))
        }
    }

@Composable
fun rememberIsMediaManager(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaStore.canManageMedia(context)
        else false
    }
}

@Composable
fun rememberWindowInsetsController(): WindowInsetsControllerCompat {
    val window = with(LocalActivity.current) { return@with this!!.window!! }
    return remember { WindowCompat.getInsetsController(window, window.decorView) }
}

fun Context.restartApplication() {
    val packageManager: PackageManager = packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}

/**
 * Resolve the launcher [activity-alias] short name for a given app-name + app-logo
 * combination. The two legacy aliases (ReFra-logo) keep their original names so existing
 * installs are not disrupted; the Gallery-logo combinations use dedicated aliases.
 */
fun launcherAliasFor(nameAlias: String, logoAlias: String): String {
    val galleryLogo = logoAlias == "Gallery"
    val monoLogo = logoAlias == "Monochrome"
    val galleryMonoLogo = logoAlias == "Gallery Mono"
    return when {
        nameAlias == "Gallery" && galleryMonoLogo -> "Launcher_Gallery_GalleryMono"
        nameAlias == "Gallery" && monoLogo -> "Launcher_Gallery_Mono"
        nameAlias == "Gallery" && galleryLogo -> "Launcher_Gallery_GalleryLogo"
        nameAlias == "Gallery" -> "Launcher_Gallery"
        galleryMonoLogo -> "Launcher_ReFra_GalleryMono"
        monoLogo -> "Launcher_ReFra_Mono"
        galleryLogo -> "Launcher_ReFra_GalleryLogo"
        else -> "Launcher_ReFra"
    }
}

/**
 * Whether a launcher [activity-alias] short name points at one of the
 * Gallery-logo variants.
 */
fun launcherAliasHasGalleryLogo(alias: String): Boolean =
    alias.endsWith("GalleryLogo")

/**
 * Whether a launcher [activity-alias] short name points at one of the
 * monochrome-logo variants.
 */
fun launcherAliasHasMonoLogo(alias: String): Boolean =
    alias.endsWith("Mono")

/**
 * Enable the launcher alias matching the given app-name + app-logo combination and disable
 * all others. [logoAlias] defaults to the ReFra logo for backward compatibility with callers
 * that only toggle the app name.
 */
fun Context.changeAppAlias(nameAlias: String, logoAlias: String = "ReFra") {
    val namespace = "com.dot.gallery"
    val aliases = listOf(
        "Launcher_ReFra",
        "Launcher_Gallery",
        "Launcher_ReFra_GalleryLogo",
        "Launcher_Gallery_GalleryLogo",
        "Launcher_ReFra_Mono",
        "Launcher_Gallery_Mono",
        "Launcher_ReFra_GalleryMono",
        "Launcher_Gallery_GalleryMono"
    )
    val targetAlias = launcherAliasFor(nameAlias, logoAlias)
    for (alias in aliases) {
        val component = ComponentName(packageName, "$namespace.$alias")
        val newState = if (alias == targetAlias) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
    applyLauncherSplashTheme(targetAlias)
}

/**
 * Match the splash screen to the launcher logo. On API 31+ the theme override is
 * persisted per package and used for subsequent launches; [Resources.ID_NULL]
 * restores the manifest theme. Below API 31 the compat splash resolves its icon
 * from the activity theme, so the alternate splash theme is applied instead — this
 * only has an effect when called before the activity's first draw (MainActivity
 * does so before installSplashScreen).
 */
fun Context.applyLauncherSplashTheme(alias: String) {
    val activity = this as? Activity ?: return
    val splashTheme = when {
        launcherAliasHasMonoLogo(alias) -> R.style.Theme_Gallery_Splash_Mono
        launcherAliasHasGalleryLogo(alias) -> R.style.Theme_Gallery_Splash_Gallery
        else -> null
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity.splashScreen.setSplashScreenTheme(splashTheme ?: Resources.ID_NULL)
    } else if (splashTheme != null) {
        activity.setTheme(splashTheme)
    }
}

/**
 * Returns the launcher [activity-alias] short name that is currently enabled, falling back to
 * the default manifest alias ("Launcher_ReFra") when none has been explicitly toggled.
 */
fun Context.currentLauncherAlias(): String {
    val namespace = "com.dot.gallery"
    val aliases = listOf(
        "Launcher_ReFra",
        "Launcher_Gallery",
        "Launcher_ReFra_GalleryLogo",
        "Launcher_Gallery_GalleryLogo",
        "Launcher_ReFra_Mono",
        "Launcher_Gallery_Mono",
        "Launcher_ReFra_GalleryMono",
        "Launcher_Gallery_GalleryMono"
    )
    for (alias in aliases) {
        val component = ComponentName(packageName, "$namespace.$alias")
        if (packageManager.getComponentEnabledSetting(component)
            == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            return alias
        }
    }
    return "Launcher_ReFra"
}