package com.dot.gallery.feature_node.presentation.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.SetupWizard
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.ui.core.Icons
import com.dot.gallery.ui.core.icons.Encrypted

@Composable
fun VaultSetup(
    navigateUp: () -> Unit,
    onCreate: () -> Unit,
    vm: VaultViewModel
) {
    val context = LocalContext.current

    var nameError by remember { mutableStateOf("") }
    var newVault by remember { mutableStateOf(Vault(name = "")) }

    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricAvailable = remember {
        biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS
    }
    SetupWizard(
        icon = Icons.Encrypted,
        title = stringResource(R.string.vault_setup_title),
        subtitle = stringResource(R.string.vault_setup_subtitle),
        bottomBar = {
            OutlinedButton(onClick = navigateUp) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
            Button(
                onClick = {
                    vm.setVault(
                        vault = newVault,
                        onFailed = {
                            val newError = if (it.contains("Already exists")) {
                                context.getString(R.string.vault_already_exists, newVault.name)
                            } else it
                            printError("Error: $newError")
                            nameError = newError
                        },
                        onSuccess = {
                            onCreate()
                        }
                    )
                },
                enabled = isBiometricAvailable && nameError.isEmpty() && newVault.name.isNotEmpty()
            ) {
                Text(text = stringResource(id = R.string.get_started))
            }
        },
        content = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newVault.name,
                onValueChange = { newName ->
                    nameError = ""
                    newVault = newVault.copy(name = newName.filter { it.isLetterOrDigit() })
                },
                label = { Text(text = stringResource(R.string.vault_setup_name)) },
                singleLine = true,
                isError = nameError.isNotEmpty(),
                enabled = isBiometricAvailable
            )

            AnimatedVisibility(visible = !isBiometricAvailable) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    text = stringResource(R.string.vault_setup_security_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

            }

            AnimatedVisibility(visible = isBiometricAvailable) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        text = stringResource(R.string.vault_setup_summary2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        text = "Your files are accessed on the fly and never stored decrypted on the device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(visible = nameError.isNotEmpty()) {
                Text(
                    text = nameError,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}