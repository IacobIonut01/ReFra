package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.VaultViewModel
import com.dot.gallery.feature_node.presentation.vault.components.AddToVaultSheet
import com.dot.gallery.feature_node.presentation.vault.components.SelectVaultSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun <T : Media> HideButton(
    media: T,
    vaults: VaultState,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val sheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Lock,
        followTheme = followTheme,
        enabled = enabled,
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
    val vaultViewModel = hiltViewModel<VaultViewModel>()
    var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()
    val addToVaultSheetState = rememberAppBottomSheetState()
    var selectedVault by remember { mutableStateOf<Vault?>(null) }
    val hidingText = stringResource(R.string.vault_hide_in_progress)
    fun startHide(vault: Vault, deleteOriginals: Boolean) {
        Toast.makeText(context, hidingText, Toast.LENGTH_SHORT).show()
        if (deleteOriginals) {
            vaultViewModel.hideAndRequestDeletion(vault, media.getUri())
        } else {
            vaultViewModel.addMediaKeepOriginals(vault, listOf(media.getUri()))
        }
    }
    SelectVaultSheet(
        state = sheetState,
        vaultState = vaults,
        onVaultSelected = { vault ->
            scope.launch {
                when (vaultEncryptBehavior) {
                    Settings.Vault.ENCRYPT_DELETE -> startHide(vault, deleteOriginals = true)
                    Settings.Vault.ENCRYPT_KEEP -> startHide(vault, deleteOriginals = false)
                    else -> {
                        selectedVault = vault
                        addToVaultSheetState.show()
                    }
                }
            }
        }
    )
    AddToVaultSheet(
        state = addToVaultSheetState,
        onEncryptAndDelete = {
            val vault = selectedVault ?: return@AddToVaultSheet
            startHide(vault, deleteOriginals = true)
        },
        onEncryptAndKeep = {
            val vault = selectedVault ?: return@AddToVaultSheet
            startHide(vault, deleteOriginals = false)
        },
        onBehaviorChanged = { vaultEncryptBehavior = it }
    )

    // Show user feedback (Toast) for hide operations outside vault screen
    LaunchedEffect(Unit) {
        vaultViewModel.userMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Collect deletion batches emitted by ViewModel
    LaunchedEffect(Unit) {
        vaultViewModel.pendingDeletions.collect { leftovers ->
            if (leftovers.isNotEmpty()) {
                if (SdkCompat.supportsMediaStoreRequests) {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        leftovers
                    ).intentSender
                    val senderRequest: IntentSenderRequest =
                        IntentSenderRequest.Builder(intentSender)
                            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                            .build()
                    result.launch(senderRequest)
                } else {
                    // On API 29, delete directly via ContentResolver
                    withContext(Dispatchers.IO) {
                        leftovers.forEach { uri ->
                            runCatching {
                                context.contentResolver.delete(uri, null, null)
                            }
                        }
                    }
                }
            }
        }
    }
}