package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSecuritySheet(
    state: AppBottomSheetState,
    vault: Vault?,
    currentAuthType: VaultAuthType?,
    onSetCustomSecurity: () -> Unit,
    onRemoveCustomSecurity: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    if (state.isVisible && vault != null) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch { state.hide() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.vault_manage_security),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                )

                val removeText = stringResource(R.string.vault_remove_custom_security)
                val removeSummary = stringResource(R.string.vault_remove_custom_security_summary)
                val setPasswordText = stringResource(R.string.vault_set_password)
                val changeText = stringResource(R.string.vault_change_security)
                val changeSummary = stringResource(R.string.vault_change_security_summary)

                val options = remember(currentAuthType, removeText, removeSummary, setPasswordText, changeText, changeSummary) {
                    buildList {
                        if (currentAuthType != null) {
                            add(
                                OptionItem(
                                    icon = Icons.Outlined.LockOpen,
                                    text = removeText,
                                    summary = removeSummary,
                                    onClick = { onRemoveCustomSecurity() }
                                )
                            )
                        }
                        add(
                            OptionItem(
                                icon = if (currentAuthType != null) Icons.Outlined.Password else Icons.Outlined.Lock,
                                text = if (currentAuthType != null) setPasswordText else changeText,
                                summary = changeSummary,
                                onClick = { onSetCustomSecurity() }
                            )
                        )
                    }.toMutableStateList()
                }

                OptionLayout(
                    modifier = Modifier.fillMaxWidth(),
                    optionList = options
                )
            }
        }
    }
}
