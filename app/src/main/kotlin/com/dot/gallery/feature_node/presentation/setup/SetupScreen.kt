package com.dot.gallery.feature_node.presentation.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Settings.Misc.rememberIsMediaManager
import com.dot.gallery.core.presentation.components.SetupWizard
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.util.RepeatOnResume
import com.dot.gallery.feature_node.presentation.util.isManageFilesAllowed
import com.dot.gallery.feature_node.presentation.util.launchManageFiles
import com.dot.gallery.feature_node.presentation.util.launchManageMedia
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(
    onPermissionGranted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var firstLaunch by remember { mutableStateOf(true) }
    var permissionGranted by remember { mutableStateOf(false) }
    val mediaPermissions = rememberMultiplePermissionsState(Constants.PERMISSIONS) {
        firstLaunch = false
        permissionGranted = it.all { item -> item.value }
    }
    val appName = "${stringResource(id = R.string.app_name)} v${BuildConfig.VERSION_NAME}"
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            onPermissionGranted()
        } else if (!firstLaunch) Toast.makeText(
            context,
            resources.getString(R.string.some_permissions_are_not_granted), Toast.LENGTH_LONG
        )
            .show()
    }

    SetupWizard(
        painter = painterResource(R.drawable.monochrome_icon),
        title = stringResource(id = R.string.welcome),
        subtitle = appName,
        contentPadding = 0.dp,
        bottomBar = {
            OutlinedButton(
                onClick = { (context as Activity).finish() }
            ) {
                Text(text = stringResource(id = R.string.action_cancel))
            }

            Button(
                onClick = {
                    scope.launch {
                        mediaPermissions.launchMultiplePermissionRequest()
                    }
                }
            ) {
                Text(text = stringResource(R.string.get_started))
            }
        },
        content = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.required)
            )
            val options = remember(context) {
                context.requiredPermissionsList.map { (icon, title, summary) ->
                    OptionItem(
                        icon = icon,
                        text = title,
                        summary = summary,
                        enabled = true,
                        onClick = { }
                    )
                }
            }
            OptionLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.8f),
                optionList = options
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var useMediaManager by rememberIsMediaManager()
                var isStorageManager by remember { mutableStateOf(Environment.isExternalStorageManager()) }
                RepeatOnResume {
                    isStorageManager = Environment.isExternalStorageManager()
                    useMediaManager = MediaStore.canManageMedia(context)
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    text = stringResource(R.string.optional)
                )
                val grantedString = stringResource(R.string.granted)
                val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
                val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
                val optionsList = remember(useMediaManager, isStorageManager) {
                    listOf(
                        OptionItem(
                            icon = Icons.Rounded.PermMedia,
                            text = resources.getString(R.string.permission_manage_media_title),
                            summary = if (!useMediaManager) resources.getString(R.string.permission_manage_media_summary) else grantedString,
                            enabled = !useMediaManager,
                            onClick = {
                                scope.launch {
                                    context.launchManageMedia()
                                }
                            },
                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer
                        ),
                        OptionItem(
                            icon = Icons.Rounded.FileOpen,
                            text = resources.getString(R.string.permission_manage_files_title),
                            summary = if (!isStorageManager && isManageFilesAllowed) resources.getString(
                                R.string.permission_manage_files_summary
                            ) else grantedString,
                            enabled = !isStorageManager && isManageFilesAllowed,
                            onClick = {
                                scope.launch {
                                    context.launchManageFiles()
                                }
                            },

                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer
                        )
                    ).toMutableList()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var isGranted by rememberSaveable(context) {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val notificationPermission = rememberPermissionState(
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        onPermissionResult = { isGranted = it }
                    )
                    optionsList.add(
                        OptionItem(
                            icon = Icons.Rounded.Notifications,
                            text = resources.getString(R.string.post_notifications),
                            summary = if (!isGranted) resources.getString(R.string.post_notifications_summary) else grantedString,
                            enabled = !isGranted,
                            onClick = {
                                scope.launch {
                                    notificationPermission.launchPermissionRequest()
                                }
                            },
                            containerColor = secondaryContainer,
                            contentColor = onSecondaryContainer
                        ),
                    )
                }

                OptionLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.8f),
                    optionList = optionsList
                )
            }
        }
    )
}

private val Context.requiredPermissionsList: Array<Triple<ImageVector, String, String>>
    get() {
        return arrayOf(
            Triple(
                Icons.Rounded.Image,
                getString(R.string.read_media_images),
                getString(R.string.read_media_images_summary)
            ),
            Triple(
                Icons.Rounded.VideoFile,
                getString(R.string.read_media_videos),
                getString(R.string.read_media_videos_summary)
            ),
            Triple(
                Icons.Rounded.LocationOn,
                getString(R.string.access_media_location),
                getString(R.string.access_media_location_summary)
            ),
            Triple(
                Icons.Rounded.SignalWifi4Bar,
                getString(R.string.internet),
                getString(R.string.internet_summary)
            )
        ).apply {
            if (packageManager.checkPermission(
                    Manifest.permission.INTERNET,
                    packageName
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                this.dropLast(1)
            }
        }
    }
