/**
 * Original from
 * https://stackoverflow.com/a/70853761/12978728
 */
@file:Suppress("KotlinConstantConditions")

package com.dot.gallery.feature_node.presentation.util

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
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.allowVibrations
import com.dot.gallery.core.Settings.Misc.rememberFullBrightnessView
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.presentation.edit.EditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalFixedInsets = compositionLocalOf<FixedInsets> { error("no FixedInsets provided!") }

data class FixedInsets(
    val statusBarHeight: Dp = 0.dp,
    val navigationBarsPadding: PaddingValues = PaddingValues(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvideInsets(content: @Composable () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    // Use the *IgnoringVisibility insets so the values stay correct while the
    // system bars are hidden (immersive media view) — the reason these were
    // "fixed" in the first place — while still recomputing on configuration
    // changes such as screen rotation (#929). The previous keyless remember
    // captured the initial-orientation insets and never updated on rotation.
    val statusBarsPadding = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
    // IMPORTANT: key the remember on the resolved Dp primitives, NOT on the
    // PaddingValues objects. asPaddingValues() returns a stable instance whose
    // calculateXPadding() reads the live insets lazily, so as a remember key it
    // stays equal across a rotation and the block would never recompute (#929).
    val statusTop = statusBarsPadding.calculateTopPadding()
    val navBottom = navigationBarsPadding.calculateBottomPadding()
    val navStart = navigationBarsPadding.calculateStartPadding(layoutDirection)
    val navEnd = navigationBarsPadding.calculateEndPadding(layoutDirection)
    val fixedInsets = remember(statusTop, navBottom, navStart, navEnd) {
        FixedInsets(
            statusBarHeight = statusTop,
            navigationBarsPadding = PaddingValues(
                bottom = navBottom,
                start = navStart,
                end = navEnd,
            ),
        )
    }

    CompositionLocalProvider(
        values = arrayOf(
            LocalFixedInsets provides fixedInsets,
        ),
    ) {
        content()
    }
}

@Composable
fun rememberNavigationBarHeight(): Dp {
    val insets = LocalFixedInsets.current
    val layoutDirection = LocalLayoutDirection.current

    val navigationBarPaddingBottom = remember(insets) {
        insets.navigationBarsPadding.calculateBottomPadding()
    }
    val navigationBarPaddingTop = remember(insets) {
        insets.navigationBarsPadding.calculateTopPadding()
    }
    val navigationBarPaddingStart = remember(insets, layoutDirection) {
        insets.navigationBarsPadding.calculateStartPadding(layoutDirection)
    }
    val navigationBarPaddingEnd = remember(insets, layoutDirection) {
        insets.navigationBarsPadding.calculateEndPadding(layoutDirection)
    }

    return remember(insets, layoutDirection) {
        if (navigationBarPaddingTop > 0.dp) {
            navigationBarPaddingTop
        } else if (navigationBarPaddingBottom > 0.dp) {
            navigationBarPaddingBottom
        } else if (navigationBarPaddingStart > 0.dp) {
            navigationBarPaddingStart
        } else {
            navigationBarPaddingEnd
        }
    }
}

@Composable
fun rememberGestureNavigationEnabled(): Boolean {
    val navigationBarHeight = rememberNavigationBarHeight()

    return remember(navigationBarHeight) {
        navigationBarHeight < 48.dp
    }
}

/**
 * Returns true when the system navigation bar sits on the sides instead of the
 * bottom (e.g. 3-button navigation in landscape). In that case there is no
 * bottom inset, but there is a horizontal one.
 */
@Composable
fun rememberNavigationBarOnSides(): Boolean {
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    // Key on the resolved Dp primitives (see ProvideInsets) so this recomputes on
    // rotation instead of caching the initial-orientation result (#929).
    val bottom = navBarsPadding.calculateBottomPadding()
    val start = navBarsPadding.calculateStartPadding(layoutDirection)
    val end = navBarsPadding.calculateEndPadding(layoutDirection)
    return remember(bottom, start, end) {
        bottom == 0.dp && (start > 0.dp || end > 0.dp)
    }
}

/**
 * Bottom inset to reserve for the floating bottom navigation pill (and the
 * content behind it). When the navigation bar is on the sides there is no
 * bottom system inset, so a fixed 16.dp gap is used instead of [PaddingValues]
 * bottom padding.
 */
@Composable
fun rememberBottomBarInset(paddingValues: PaddingValues): Dp {
    val onSides = rememberNavigationBarOnSides()
    val bottomInset = paddingValues.calculateBottomPadding()
    return remember(onSides, bottomInset) {
        if (onSides) 16.dp else bottomInset
    }
}

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

val Context.mediaStoreVersion: String
    @SuppressLint("NewApi")
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val generations = MediaStore.getExternalVolumeNames(this)
            .sorted()
            .joinToString(separator = ",") { volume ->
                "$volume:${MediaStore.getGeneration(this, volume)}"
            }
        "$generations/${MediaStore.getVersion(this)}"
    } else {
        MediaStore.getVersion(this)
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
        message ?: stringResource(id = R.string.error_toast),
        Toast.LENGTH_SHORT
    )
}

class FeedbackManager(private val view: View, scope: CoroutineScope) {

    private var isAvailable by mutableStateOf(true)

    init {
        scope.launch {
            allowVibrations(view.context).collectLatest {
                isAvailable = it
            }
        }
    }

    fun vibrate() {
        if (isAvailable) {
            view.reallyPerformHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun vibrateStrong() {
        if (isAvailable) {
            view.reallyPerformHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

@Composable
fun rememberFeedbackManager(): FeedbackManager {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    return remember(view, scope) {
        FeedbackManager(view, scope)
    }
}

@Suppress("DEPRECATION")
private fun View.reallyPerformHapticFeedback(feedbackConstant: Int) {
    if (context.isTouchExplorationEnabled()) {
        // Don't mess with a blind person's vibrations
        return
    }
    // Either this needs to be set to true, or android:hapticFeedbackEnabled="true" needs to be set in XML
    isHapticFeedbackEnabled = true

    /**
     * [HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING]
     * is deprecated in Android 13+
     */
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        performHapticFeedback(feedbackConstant)
    } else {
        // Most of the constants are off by default: for example, clicking on a button doesn't cause the phone to vibrate anymore
        // if we still want to access this vibration, we'll have to ignore the global settings on that.
        performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
}

private fun Context.isTouchExplorationEnabled(): Boolean {
    // can be null during unit tests
    val accessibilityManager =
        getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager?
    return accessibilityManager?.isTouchExplorationEnabled ?: false
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
    withContext(Dispatchers.Main) {
        if (media.isImage) {
            EditActivity.launchEditor(this@launchEditIntent, uri)
        } else {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, media.mimeType)
                putExtra("mimeType", media.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.edit)))
        }
    }
}

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
            startActivity(Intent.createChooser(intent, getString(R.string.set_as)))
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
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
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