package com.dot.gallery.feature_node.presentation.exif

import android.media.MediaScannerConnection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.launchWriteRequest
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.toastError
import com.dot.gallery.feature_node.presentation.util.writeRequest
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Media> MetadataEditSheet(
    state: AppBottomSheetState,
    media: T,
    metadata: MediaMetadata?
) {
    val handler = LocalMediaHandler.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val context = LocalContext.current
    val cr = remember(context) { context.contentResolver }
    var imageDescription by rememberSaveable(metadata) {
        mutableStateOf(metadata?.imageDescription)
    }
    var newLabel by rememberSaveable { mutableStateOf(media.label) }
    val errorToast = toastError()
    val doUpdate: () -> Unit = {
        scope.launch {
            var done: Boolean
            printDebug("Updating media image description to $imageDescription")
            done = handler.updateMediaDescription(media, imageDescription ?: "")
            printDebug("Updated : $done")
            if (newLabel != media.label && newLabel.isNotBlank()) {
                done = handler.renameMedia(media, newLabel)
            }
            if (done) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(media.path),
                    arrayOf(media.mimeType),
                    null
                )
                state.hide()
            } else {
                errorToast.show()
            }
        }
    }
    val request = rememberActivityResult { doUpdate() }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch {
                    state.hide()
                }
            },
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.edit_metadata),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                )

                TextField(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    value = newLabel,
                    onValueChange = { newValue ->
                        newLabel = newValue
                    },
                    label = {
                        Text(text = stringResource(id = R.string.label))
                    },
                    singleLine = true,
                    shape = Shapes.large,
                    colors = TextFieldDefaults.colors(
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )

                TextField(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(112.dp),
                    value = imageDescription ?: "",
                    onValueChange = { newValue ->
                        imageDescription = newValue
                    },
                    label = {
                        Text(text = stringResource(R.string.description))
                    },
                    shape = Shapes.large,
                    colors = TextFieldDefaults.colors(
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )

                // Show info message for videos that description is stored locally only
                AnimatedVisibility(visible = media.isVideo) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.video_description_local_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement
                        .spacedBy(24.dp, Alignment.CenterHorizontally)
                ) {
                    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
                    val tertiaryOnContainer = MaterialTheme.colorScheme.onTertiaryContainer
                    Button(
                        onClick = {
                            scope.launch { state.hide() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tertiaryContainer,
                            contentColor = tertiaryOnContainer
                        )
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.Main) {
                                request.launchWriteRequest(
                                    media.writeRequest(cr),
                                    doUpdate
                                )
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.action_confirm))
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}