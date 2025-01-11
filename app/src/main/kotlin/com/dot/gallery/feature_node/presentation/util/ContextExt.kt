/**
 * Original from
 * https://stackoverflow.com/a/70853761/12978728
 */
@file:Suppress("KotlinConstantConditions")

package com.dot.gallery.feature_node.presentation.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
import android.content.pm.ActivityInfo.COLOR_MODE_HDR
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
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
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
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

@Composable
fun ProvideInsets(content: @Composable () -> Unit) {
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val fixedInsets = remember {
        FixedInsets(
            statusBarHeight = systemBarsPadding.calculateTopPadding(),
            navigationBarsPadding = PaddingValues(
                bottom = systemBarsPadding.calculateBottomPadding(),
                start = systemBarsPadding.calculateStartPadding(layoutDirection),
                end = systemBarsPadding.calculateEndPadding(layoutDirection),
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
    if (this is Activity) {
        if (window.colorMode == COLOR_MODE_HDR && !enabled) {
            window.colorMode = COLOR_MODE_DEFAULT
        } else if (window.colorMode == COLOR_MODE_DEFAULT && enabled) {
            window.colorMode = COLOR_MODE_HDR
        }
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
    get() = "${MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL_PRIMARY)}/${MediaStore.getVersion(this)}"

suspend fun InternalDatabase.isMediaUpToDate(context: Context): Boolean {
    return getMediaDao().isMediaVersionUpToDate(context.mediaStoreVersion)
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
    return resolveInfoList.filterNot { it.activityInfo.packageName == BuildConfig.APPLICATION_ID }
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

fun <T: Media>  Context.launchEditIntent(media: T) {
    if (media.isImage) {
        EditActivity.launchEditor(this@launchEditIntent, media.getUri().authorizedUri(this@launchEditIntent))
    } else {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(media.getUri().authorizedUri(this@launchEditIntent), media.mimeType)
            putExtra("mimeType", media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.edit)))
    }
}

suspend fun <T: Media> Context.launchUseAsIntent(media: T) =
    withContext(Dispatchers.Default) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(media.getUri().authorizedUri(this@launchUseAsIntent), media.mimeType)
            putExtra("mimeType", media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.set_as)))
    }

suspend fun <T: Media> Context.launchOpenWithIntent(media: T) =
    withContext(Dispatchers.Default) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(media.getUri().authorizedUri(this@launchOpenWithIntent), media.mimeType)
            putExtra("mimeType", media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
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
    val window = with(LocalContext.current as Activity) { return@with window }
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