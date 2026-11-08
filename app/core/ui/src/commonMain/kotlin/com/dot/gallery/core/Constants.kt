/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.grid.GridCells

object Constants {

    /**
     * Legacy date formats used in media groups. As these are only
     * fallbacks: when a user has not set a custom pattern, the effective
     * pattern is derived from the system locale + 12/24-hour setting at read
     * time (see SystemDateFormatField / systemDateTimePattern).
     */
    const val WEEKLY_DATE_FORMAT = "EEEE"
    const val DEFAULT_DATE_FORMAT = "EEE, MMMM d"
    const val EXTENDED_DATE_FORMAT = "EEE, MMM d, yyyy"
    const val HEADER_DATE_FORMAT = "MMMM d\n" + "h:mm a"
    const val EXTENDED_HEADER_DATE_FORMAT =  "MMMM d yyyy\n" + "h:mm a"
    const val EXIF_DATE_FORMAT = "MMMM d, yyyy • h:mm a"

    /**
     * Value in ms
     */
    const val DEFAULT_LOW_VELOCITY_SWIPE_DURATION = 50

    /**
     * Smooth enough at 300ms
     */
    const val DEFAULT_NAVIGATION_ANIMATION_DURATION = 300

    /**
     * Syncs with status bar fade in/out
     */
    const val DEFAULT_TOP_BAR_ANIMATION_DURATION = 500

    /**
     * Animations
     */
    object Animation {

        val enterAnimation = fadeIn(tween(DEFAULT_LOW_VELOCITY_SWIPE_DURATION))
        val exitAnimation = fadeOut(tween(DEFAULT_LOW_VELOCITY_SWIPE_DURATION))

        val navigateInAnimation = fadeIn(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))
        val navigateUpAnimation = fadeOut(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))

        fun enterAnimation(durationMillis: Int): EnterTransition =
            fadeIn(tween(durationMillis))

        fun exitAnimation(durationMillis: Int): ExitTransition =
            fadeOut(tween(durationMillis))

    }

    object Target {
        const val TARGET_FAVORITES = "favorites"
        const val TARGET_TRASH = "trash"
    }

    val cellsList = listOf(
        GridCells.Fixed(9),
        GridCells.Fixed(8),
        GridCells.Fixed(7),
        GridCells.Fixed(6),
        GridCells.Fixed(5),
        GridCells.Fixed(4),
        GridCells.Fixed(3),
        GridCells.Fixed(2),
        GridCells.Fixed(1)
    )

    val mosaicColumnsList = listOf(6, 5, 4, 3)

    val albumCellsList = listOf(
        GridCells.Fixed(7),
        GridCells.Fixed(6),
        GridCells.Fixed(5),
        GridCells.Fixed(4),
        GridCells.Fixed(3),
        GridCells.Fixed(2),
        GridCells.Fixed(1)
    )
}
