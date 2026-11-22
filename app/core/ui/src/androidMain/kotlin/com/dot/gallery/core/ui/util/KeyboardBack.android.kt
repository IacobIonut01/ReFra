/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.util

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.backClosesKeyboard(enabled: Boolean, onBack: () -> Unit): Modifier {
    val active = enabled && WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnBack by rememberUpdatedState(onBack)
    val closeAndGoBack = {
        closeKeyboard(focusManager, keyboard)
        currentOnBack()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // With predictive back the gesture never arrives as a key event: the keyboard registers
        // its own callback on this window at the default priority, so an overlay callback runs
        // first while the keyboard is open.
        OverlayBackCallback(active, closeAndGoBack)
        return this
    }
    // Older releases send the back key to the keyboard before the view tree; the pre-IME pass
    // is the only point that sees it first.
    return onPreInterceptKeyBeforeSoftKeyboard { event ->
        if (!active || event.key != Key.Back) return@onPreInterceptKeyBeforeSoftKeyboard false
        if (event.type == KeyEventType.KeyUp) closeAndGoBack()
        true
    }
}

private fun closeKeyboard(focusManager: FocusManager, keyboard: SoftwareKeyboardController?) {
    focusManager.clearFocus()
    keyboard?.hide()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun OverlayBackCallback(active: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(active, view) {
        val dispatcher = view.findOnBackInvokedDispatcher()
        if (!active || dispatcher == null) return@DisposableEffect onDispose { }
        val callback = OnBackInvokedCallback { currentOnBack() }
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        onDispose { dispatcher.unregisterOnBackInvokedCallback(callback) }
    }
}
