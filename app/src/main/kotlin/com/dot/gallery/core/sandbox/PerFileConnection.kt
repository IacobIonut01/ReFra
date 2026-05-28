/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.ServiceConnection
import android.os.Messenger

/**
 * Holds the state of a per-file isolated service connection.
 * Each instance represents a unique Zygote child process bound
 * via [android.content.Context.bindIsolatedService].
 *
 * Must be closed via [IsolatedMetadataParser.unbindPerFile] when
 * the parse operation is complete.
 */
data class PerFileConnection(
    val serviceConnection: ServiceConnection,
    val messenger: Messenger,
    val instanceName: String
)
