package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import android.media.MediaScannerConnection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.launchWriteRequest
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.toastError
import com.dot.gallery.feature_node.presentation.util.writeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun <T : Media> RenameButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val context = LocalContext.current
    val handler = LocalMediaHandler.current
    val scope = rememberCoroutineScope()
    val cr = remember(context) { context.contentResolver }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var newLabel by rememberSaveable(media) { mutableStateOf(media.label) }
    val errorToast = toastError()

    val doRename: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val done = newLabel != media.label &&
                newLabel.isNotBlank() &&
                handler.renameMedia(media, newLabel)
            if (done) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(media.path),
                    arrayOf(media.mimeType),
                    null
                )
            } else {
                errorToast.show()
            }
        }
    }
    val request = rememberActivityResult { doRename() }

    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.DriveFileRenameOutline,
        followTheme = followTheme,
        title = stringResource(R.string.rename_collection),
        enabled = enabled
    ) {
        newLabel = it.label
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.rename_collection)) },
            text = {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newLabel.isNotBlank() && newLabel != media.label,
                    onClick = {
                        showDialog = false
                        request.launchWriteRequest(media.writeRequest(cr), doRename)
                    }
                ) {
                    Text(stringResource(R.string.rename_collection))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
