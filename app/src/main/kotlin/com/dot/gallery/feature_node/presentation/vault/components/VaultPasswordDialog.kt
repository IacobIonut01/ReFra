package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────
//  Unlock dialogs (type-aware)
// ──────────────────────────────────────────────

/**
 * Top-level unlock dialog that delegates to the correct input based on [authType].
 * Falls back to password mode when type is null (legacy).
 */
@Composable
fun VaultPasswordUnlockDialog(
    authType: VaultAuthType? = null,
    onDismiss: () -> Unit,
    onSubmit: (secret: String) -> Unit,
    errorMessage: String? = null
) {
    when (authType ?: VaultAuthType.PASSWORD) {
        VaultAuthType.PIN -> VaultPinUnlockDialog(onDismiss, onSubmit, errorMessage)
        VaultAuthType.PATTERN -> VaultPatternUnlockDialog(onDismiss, onSubmit, errorMessage)
        VaultAuthType.PASSWORD -> VaultTextPasswordUnlockDialog(onDismiss, onSubmit, errorMessage)
    }
}

@Composable
private fun VaultTextPasswordUnlockDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    errorMessage: String? = null
) {
    val feedbackManager = rememberFeedbackManager()
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            feedbackManager.vibrateStrong()
            password = ""
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Text(
            text = stringResource(R.string.vault_unlock),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_enter_password),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.vault_enter_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (password.isNotEmpty()) {
                        feedbackManager.vibrate()
                        onSubmit(password)
                    }
                }
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.toggle_password_visibility_cd)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        ErrorText(errorMessage)
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            SetupButton(
                onClick = {
                    feedbackManager.vibrate()
                    onDismiss()
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.action_cancel)
            )
            SetupButton(
                onClick = {
                    feedbackManager.vibrate()
                    onSubmit(password)
                },
                enabled = password.isNotEmpty(),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.vault_unlock)
            )
        }
    }
}

@Composable
private fun VaultPinUnlockDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    errorMessage: String? = null
) {
    val feedbackManager = rememberFeedbackManager()
    var pin by remember { mutableStateOf("") }
    val isError = errorMessage != null

    LaunchedEffect(isError) {
        if (isError) {
            feedbackManager.vibrateStrong()
            pin = ""
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.vault_unlock),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_enter_pin),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        VaultPinInput(
            pin = pin,
            isError = isError && pin.isEmpty(),
            onPinChange = { pin = it },
            onPinComplete = onSubmit
        )
        ErrorText(errorMessage)
        Spacer(modifier = Modifier.weight(1f))
        SetupButton(
            onClick = {
                feedbackManager.vibrate()
                onDismiss()
            },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            applyHorizontalPadding = false,
            applyBottomPadding = false,
            applyInsets = false,
            text = stringResource(R.string.action_cancel)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VaultPatternUnlockDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    errorMessage: String? = null
) {
    val feedbackManager = rememberFeedbackManager()
    val isError = errorMessage != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.vault_unlock),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_draw_pattern),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        VaultPatternLock(
            isError = isError,
            onPatternComplete = onSubmit
        )
        ErrorText(errorMessage)
        Spacer(modifier = Modifier.weight(1f))
        SetupButton(
            onClick = {
                feedbackManager.vibrate()
                onDismiss()
            },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            applyHorizontalPadding = false,
            applyBottomPadding = false,
            applyInsets = false,
            text = stringResource(R.string.action_cancel)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ──────────────────────────────────────────────
//  Setup bottom sheet (type picker + two-step confirm)
// ──────────────────────────────────────────────

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

    val passwordTooShort = stringResource(R.string.vault_password_too_short)
    val pinTooShort = stringResource(R.string.vault_pin_too_short)
    val patternTooShort = stringResource(R.string.vault_pattern_too_short)
    val mismatch = stringResource(R.string.vault_password_mismatch)

    val pinLabel = stringResource(R.string.vault_type_pin)
    val patternLabel = stringResource(R.string.vault_type_pattern)
    val passwordLabel = stringResource(R.string.vault_type_password)

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
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                if (step == SetupStep.CHOOSE_TYPE) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                // Title
                Text(
                    text = stringResource(
                        when (step) {
                            SetupStep.CHOOSE_TYPE -> R.string.vault_choose_lock_type
                            SetupStep.ENTER_FIRST -> when (selectedType) {
                                VaultAuthType.PIN -> R.string.vault_enter_pin
                                VaultAuthType.PATTERN -> R.string.vault_draw_pattern
                                else -> R.string.vault_enter_password
                            }
                            SetupStep.CONFIRM -> when (selectedType) {
                                VaultAuthType.PIN -> R.string.vault_confirm_pin
                                VaultAuthType.PATTERN -> R.string.vault_confirm_pattern
                                else -> R.string.vault_confirm_password
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
                            text = stringResource(R.string.vault_custom_password_summary),
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
                        AuthInput(
                            type = selectedType ?: VaultAuthType.PASSWORD,
                            errorMessage = errorMessage,
                            modifier = Modifier.weight(1f),
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
                            text = stringResource(R.string.back)
                        )
                    }
                    SetupStep.CONFIRM -> {
                        AuthInput(
                            type = selectedType ?: VaultAuthType.PASSWORD,
                            errorMessage = errorMessage,
                            modifier = Modifier.weight(1f),
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
                            text = stringResource(R.string.back)
                        )
                    }
                }
            }
        }
    }
}

private enum class SetupStep { CHOOSE_TYPE, ENTER_FIRST, CONFIRM }

@Composable
private fun AuthInput(
    type: VaultAuthType,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onComplete: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
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
                ErrorText(errorMessage)
            }
            VaultAuthType.PATTERN -> {
                VaultPatternLock(
                    isError = errorMessage != null,
                    onPatternComplete = onComplete
                )
                ErrorText(errorMessage)
            }
            VaultAuthType.PASSWORD -> {
                var password by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.vault_enter_password)) },
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
                                contentDescription = stringResource(R.string.toggle_password_visibility_cd)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                ErrorText(errorMessage)
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
                    text = stringResource(R.string.next)
                )
            }
        }
    }
}

@Composable
private fun ErrorText(message: String?) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = message.orEmpty(),
        color = if (message != null) MaterialTheme.colorScheme.error else Color.Transparent,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
