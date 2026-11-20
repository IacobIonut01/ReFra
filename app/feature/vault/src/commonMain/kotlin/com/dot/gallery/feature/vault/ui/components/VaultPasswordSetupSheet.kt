/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayout
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.back
import com.dot.gallery.feature.vault.generated.resources.next
import com.dot.gallery.feature.vault.generated.resources.toggle_password_visibility_cd
import com.dot.gallery.feature.vault.generated.resources.vault_choose_lock_type
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_password
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_confirm_pin
import com.dot.gallery.feature.vault.generated.resources.vault_custom_password_summary
import com.dot.gallery.feature.vault.generated.resources.vault_draw_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_enter_password
import com.dot.gallery.feature.vault.generated.resources.vault_enter_pin
import com.dot.gallery.feature.vault.generated.resources.vault_password_mismatch
import com.dot.gallery.feature.vault.generated.resources.vault_password_too_short
import com.dot.gallery.feature.vault.generated.resources.vault_pattern_too_short
import com.dot.gallery.feature.vault.generated.resources.vault_pin_too_short
import com.dot.gallery.feature.vault.generated.resources.vault_type_password
import com.dot.gallery.feature.vault.generated.resources.vault_type_pattern
import com.dot.gallery.feature.vault.generated.resources.vault_type_pin

private enum class SetupStep { CHOOSE_TYPE, ENTER_FIRST, CONFIRM }

/**
 * Fullscreen bottom sheet for setting up vault authentication.
 * Step 1: Pick type (PIN / Pattern / Password) via OptionLayout.
 * Step 2: Enter secret.
 * Step 3: Confirm secret.
 * Calls [onSecretSet] with the chosen type and secret string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPasswordSetupSheet(
    state: AppBottomSheetState,
    onSecretSet: (type: VaultAuthType, secret: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val feedbackManager = rememberFeedbackManager()
    var selectedType by remember { mutableStateOf<VaultAuthType?>(null) }
    var step by remember { mutableStateOf(SetupStep.CHOOSE_TYPE) }
    var firstEntry by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val passwordTooShort = stringResource(Res.string.vault_password_too_short)
    val pinTooShort = stringResource(Res.string.vault_pin_too_short)
    val patternTooShort = stringResource(Res.string.vault_pattern_too_short)
    val mismatch = stringResource(Res.string.vault_password_mismatch)

    val pinLabel = stringResource(Res.string.vault_type_pin)
    val patternLabel = stringResource(Res.string.vault_type_pattern)
    val passwordLabel = stringResource(Res.string.vault_type_password)

    fun reset() {
        step = SetupStep.CHOOSE_TYPE
        selectedType = null
        firstEntry = ""
        errorMessage = null
    }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch { state.hide() }
                reset()
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            sheetMaxWidth = Dp.Unspecified,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            val scrollState = remember(step, selectedType) { ScrollState(0) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    if (step == SetupStep.CHOOSE_TYPE) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // Title
                    Text(
                        text = stringResource(
                            when (step) {
                                SetupStep.CHOOSE_TYPE -> Res.string.vault_choose_lock_type
                                SetupStep.ENTER_FIRST -> when (selectedType) {
                                    VaultAuthType.PIN -> Res.string.vault_enter_pin
                                    VaultAuthType.PATTERN -> Res.string.vault_draw_pattern
                                    else -> Res.string.vault_enter_password
                                }

                                SetupStep.CONFIRM -> when (selectedType) {
                                    VaultAuthType.PIN -> Res.string.vault_confirm_pin
                                    VaultAuthType.PATTERN -> Res.string.vault_confirm_pattern
                                    else -> Res.string.vault_confirm_password
                                }
                            }
                        ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    when (step) {
                        SetupStep.CHOOSE_TYPE -> {
                            Text(
                                text = stringResource(Res.string.vault_custom_password_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val options = remember(pinLabel, patternLabel, passwordLabel) {
                                listOf(
                                    OptionItem(
                                        icon = Icons.Outlined.Pin,
                                        text = pinLabel,
                                        onClick = {
                                            feedbackManager.vibrate()
                                            selectedType = VaultAuthType.PIN
                                            step = SetupStep.ENTER_FIRST
                                        }
                                    ),
                                    OptionItem(
                                        icon = Icons.Outlined.GridOn,
                                        text = patternLabel,
                                        onClick = {
                                            feedbackManager.vibrate()
                                            selectedType = VaultAuthType.PATTERN
                                            step = SetupStep.ENTER_FIRST
                                        }
                                    ),
                                    OptionItem(
                                        icon = Icons.Outlined.Password,
                                        text = passwordLabel,
                                        onClick = {
                                            feedbackManager.vibrate()
                                            selectedType = VaultAuthType.PASSWORD
                                            step = SetupStep.ENTER_FIRST
                                        }
                                    )
                                ).toMutableStateList()
                            }
                            OptionLayout(
                                modifier = Modifier.fillMaxWidth(),
                                optionList = options
                            )
                        }

                        SetupStep.ENTER_FIRST -> {
                            Spacer(Modifier.weight(1f))
                            AuthInput(
                                type = selectedType ?: VaultAuthType.PASSWORD,
                                errorMessage = errorMessage,
                                onComplete = { secret ->
                                    val minLength = when (selectedType) {
                                        VaultAuthType.PIN -> 6
                                        else -> 4
                                    }
                                    if (secret.length < minLength) {
                                        errorMessage = when (selectedType) {
                                            VaultAuthType.PIN -> pinTooShort
                                            VaultAuthType.PATTERN -> patternTooShort
                                            else -> passwordTooShort
                                        }
                                    } else {
                                        firstEntry = secret
                                        errorMessage = null
                                        step = SetupStep.CONFIRM
                                    }
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            SetupButton(
                                onClick = {
                                    feedbackManager.vibrate()
                                    reset()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                applyHorizontalPadding = false,
                                applyBottomPadding = false,
                                applyInsets = false,
                                text = stringResource(Res.string.back)
                            )
                        }

                        SetupStep.CONFIRM -> {
                            Spacer(Modifier.weight(1f))
                            AuthInput(
                                type = selectedType ?: VaultAuthType.PASSWORD,
                                errorMessage = errorMessage,
                                onComplete = { secret ->
                                    if (secret == firstEntry) {
                                        onSecretSet(selectedType ?: VaultAuthType.PASSWORD, secret)
                                        scope.launch { state.hide() }
                                        reset()
                                    } else {
                                        errorMessage = mismatch
                                    }
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            SetupButton(
                                onClick = {
                                    feedbackManager.vibrate()
                                    step = SetupStep.ENTER_FIRST
                                    firstEntry = ""
                                    errorMessage = null
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                applyHorizontalPadding = false,
                                applyBottomPadding = false,
                                applyInsets = false,
                                text = stringResource(Res.string.back)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthInput(
    type: VaultAuthType,
    errorMessage: String?,
    onComplete: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (type) {
            VaultAuthType.PIN -> {
                var pin by remember { mutableStateOf("") }
                LaunchedEffect(errorMessage) { if (errorMessage != null) pin = "" }
                VaultPinInput(
                    pin = pin,
                    isError = errorMessage != null,
                    onPinChange = { pin = it },
                    onPinComplete = onComplete
                )
                VaultErrorText(errorMessage)
            }

            VaultAuthType.PATTERN -> {
                VaultPatternLock(
                    isError = errorMessage != null,
                    onPatternComplete = onComplete
                )
                VaultErrorText(errorMessage)
            }

            VaultAuthType.PASSWORD -> {
                var password by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.vault_enter_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (password.isNotEmpty()) onComplete(password) }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(Res.string.toggle_password_visibility_cd)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                VaultErrorText(errorMessage)
                Spacer(modifier = Modifier.height(16.dp))
                val feedbackManager = rememberFeedbackManager()
                SetupButton(
                    onClick = {
                        feedbackManager.vibrate()
                        if (password.isNotEmpty()) onComplete(password)
                    },
                    enabled = password.isNotEmpty(),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    text = stringResource(Res.string.next)
                )
            }
        }
    }
}
