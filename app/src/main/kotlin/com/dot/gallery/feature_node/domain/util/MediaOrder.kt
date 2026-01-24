/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.util

import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MediaOrder(open val orderType: OrderType) {
    @Serializable
    data class Label(
        @SerialName("orderType_label")
        override val orderType: OrderType
    ) : MediaOrder(orderType)

    @Serializable
    data class Date(
        @SerialName("orderType_date")
        override val orderType: OrderType
    ) : MediaOrder(orderType)

    @Serializable
    data class DateModified(
        @SerialName("orderType_date_modified")
        override val orderType: OrderType
    ) : MediaOrder(orderType)

    @Serializable
    data class Expiry(
        @SerialName("orderType_expiry")
        override val orderType: OrderType = OrderType.Descending
    ): MediaOrder(orderType)

    fun <T: Media> sortMedia(media: List<T>): List<T> {
        return when (orderType) {
            OrderType.Ascending -> {
                when (this) {
                    is Date -> media.sortedBy { it.definedTimestamp }
                    is DateModified -> media.sortedBy { it.timestamp }
                    is Label -> media.sortedBy { it.label.lowercase() }
                    is Expiry -> media.sortedBy { it.expiryTimestamp ?: it.definedTimestamp }
                }
            }

            OrderType.Descending -> {
                when (this) {
                    is Date -> media.sortedByDescending { it.definedTimestamp }
                    is DateModified -> media.sortedByDescending { it.timestamp }
                    is Label -> media.sortedByDescending { it.label.lowercase() }
                    is Expiry -> media.sortedByDescending { it.expiryTimestamp ?: it.definedTimestamp }
                }
            }
        }
    }

    fun sortAlbums(albums: List<Album>): List<Album> {
        return when (orderType) {
            OrderType.Ascending -> {
                when (this) {
                    is Date -> albums.sortedBy { it.timestamp }
                    is DateModified -> albums.sortedBy { it.timestamp }
                    is Label -> albums.sortedBy { it.label.lowercase() }
                    else -> albums
                }
            }

            OrderType.Descending -> {
                when (this) {
                    is Date -> albums.sortedByDescending { it.timestamp }
                    is DateModified -> albums.sortedByDescending { it.timestamp }
                    is Label -> albums.sortedByDescending { it.label.lowercase() }
                    else -> albums
                }
            }
        }
    }

    companion object {
        val Default = Date(OrderType.Descending)
    }
}