package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import android.media.MediaScannerConnection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.launchWriteRequest
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
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
    val sheetState = rememberAppBottomSheetState()
    var newLabel by rememberSaveable(media) { mutableStateOf(media.label) }
    val focusRequester = remember { FocusRequester() }
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

    val confirmRename: () -> Unit = {
        if (newLabel.isNotBlank() && newLabel != media.label) {
            scope.launch {
                sheetState.hide()
                request.launchWriteRequest(media.writeRequest(cr), doRename)
            }
        }
    }

    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.DriveFileRenameOutline,
        followTheme = followTheme,
        title = stringResource(R.string.rename_collection),
        enabled = enabled
    ) {
        newLabel = it.label
        scope.launch { sheetState.show() }
    }

    ModalSheet(
        sheetState = sheetState,
        title = stringResource(R.string.rename_collection),
        subtitle = stringResource(R.string.rename_media_description),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                label = { Text(stringResource(R.string.rename_media_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .imePadding(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirmRename() })
            )
            Spacer(Modifier.height(16.dp))
            SetupButton(
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                enabled = newLabel.isNotBlank() && newLabel != media.label,
                text = stringResource(R.string.rename_collection),
                onClick = confirmRename
            )
            LaunchedEffect(sheetState.isVisible) {
                if (sheetState.isVisible) focusRequester.requestFocus()
            }
        }
    )
}
