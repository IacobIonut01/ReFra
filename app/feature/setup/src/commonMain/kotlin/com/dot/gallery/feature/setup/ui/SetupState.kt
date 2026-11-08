/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

/**
 * Everything the setup wizard needs to render, resolved app-side.
 *
 * [pages] is the ordered page list for this install — the app shell derives it from the
 * permissions the APK declares and whether it bundles the model pack (offline builds drop the
 * cloud page; AI models require either network or bundled models).
 */
data class SetupState(
    val pages: List<SetupPage> = listOf(SetupPage.WELCOME, SetupPage.PERMISSIONS, SetupPage.TIPS),
    /** `BuildConfig.VERSION_NAME`. No longer shown by the wizard. */
    val versionName: String = "",
    val permissions: SetupPermissionsState = SetupPermissionsState(),
    val looksFeel: SetupLooksFeelState = SetupLooksFeelState(),
    val cloud: SetupCloudState = SetupCloudState(),
    val aiModels: SetupAiModelsState = SetupAiModelsState(),
)
