package com.dot.gallery.feature_node.presentation.vault.utils

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun rememberBiometricManager(): BiometricManager {
    val context = LocalContext.current
    return remember(context) {
        BiometricManager.from(context)
    }
}

@Composable
fun rememberBiometricCallback(
    onSuccess: () -> Unit,
    onFailed: () -> Unit
): BiometricPrompt.AuthenticationCallback {
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnFailed by rememberUpdatedState(onFailed)
    return remember {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                currentOnFailed()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                currentOnSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }
    }
}

@Composable
fun rememberBiometricState(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailed: () -> Unit
): BiometricState {
    val context = LocalContext.current
    val biometricManager = rememberBiometricManager()
    val callback = rememberBiometricCallback(onSuccess, onFailed)
    return remember(biometricManager, title, subtitle) {
        val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PromptInfo.Builder()
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
        } else {
            @Suppress("DEPRECATION")
            PromptInfo.Builder()
                .setDeviceCredentialAllowed(true)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
        }
        BiometricState(
            activity = context as FragmentActivity,
            biometricManager = biometricManager,
            promptInfo = promptInfo,
            callback = callback
        )
    }
}

class BiometricState(
    private val activity: FragmentActivity,
    biometricManager: BiometricManager,
    private val promptInfo: PromptInfo,
    private val callback: BiometricPrompt.AuthenticationCallback
) {
    val isSupported by mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS
        } else {
            val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
            keyguardManager?.isDeviceSecure == true
        }
    )

    fun authenticate() {
        if (isSupported) {
            // Create a fresh BiometricPrompt each time to avoid stale internal
            // BiometricFragment state that silently swallows subsequent callbacks.
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, callback)
            prompt.authenticate(promptInfo)
        }
    }

}