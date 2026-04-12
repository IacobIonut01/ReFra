/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.util

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed class OrderType : Parcelable {
    @Serializable
    @Parcelize
    data object Ascending : OrderType()

    @Serializable
    @Parcelize
    data object Descending : OrderType()
}
