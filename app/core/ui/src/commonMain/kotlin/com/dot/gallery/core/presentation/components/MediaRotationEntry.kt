/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable


/**
 * [revision] counts completed rotations this session and joins the thumbnail signature, so a
 * rewritten file is re-decoded even when its timestamp and size did not change.
 */
@Immutable
data class MediaRotationEntry(val revision: Int = 0, val pending: PendingMediaRotation? = null)
