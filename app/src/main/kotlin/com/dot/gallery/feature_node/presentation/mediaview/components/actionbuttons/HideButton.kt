package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import kotlinx.coroutines.launch

@Composable
fun <T : Media> HideButton(
    media: T,
    vaults: VaultState,
    addMedia: (Vault, T) -> Unit,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val sheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Lock,
        followTheme = followTheme,
        enabled = remember(vaults, enabled) {
            vaults.vaults.isNotEmpty() && enabled
        },
        title = stringResource(R.string.hide),
    ) {
        scope.launch {
            sheetState.show()
        }
    }
    val context = LocalContext.current
    val result = rememberActivityResult(onResultOk = {
        scope.launch {
            sheetState.hide()
        }
    })
    SelectVaultSheet(
        state = sheetState,
        vaultState = vaults,
        onVaultSelected = { vault ->
            scope.launch {
                addMedia(vault, media).also {
                    val intentSender =
                        MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(media.getUri())
                        ).intentSender
                    val senderRequest: IntentSenderRequest =
                        IntentSenderRequest.Builder(intentSender)
                            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                            .build()
                    result.launch(senderRequest)
                }
            }
        }
    )
}