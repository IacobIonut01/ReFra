/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.mediaview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_desc_cast_item
import com.dot.gallery.core.ui.generated.resources.action_desc_copy_item
import com.dot.gallery.core.ui.generated.resources.action_desc_download_item
import com.dot.gallery.core.ui.generated.resources.action_desc_edit_item
import com.dot.gallery.core.ui.generated.resources.action_desc_favorite_item
import com.dot.gallery.core.ui.generated.resources.action_desc_open_item
import com.dot.gallery.core.ui.generated.resources.action_desc_rename_item
import com.dot.gallery.core.ui.generated.resources.action_desc_restore_item
import com.dot.gallery.core.ui.generated.resources.action_desc_share_item
import com.dot.gallery.core.ui.generated.resources.action_desc_trash_item
import com.dot.gallery.core.ui.generated.resources.action_desc_visual_search_item
import com.dot.gallery.core.ui.generated.resources.cast
import com.dot.gallery.core.ui.generated.resources.copy_to_clipboard
import com.dot.gallery.core.ui.generated.resources.download
import com.dot.gallery.core.ui.generated.resources.edit
import com.dot.gallery.core.ui.generated.resources.favorite
import com.dot.gallery.core.ui.generated.resources.open_with
import com.dot.gallery.core.ui.generated.resources.rename
import com.dot.gallery.core.ui.generated.resources.restore
import com.dot.gallery.core.ui.generated.resources.share
import com.dot.gallery.core.ui.generated.resources.trash
import com.dot.gallery.core.ui.generated.resources.visual_search

/**
 * One action that can live in the media viewer's quick-action chrome — the bottom
 * pill or one of the two configurable top-bar slots.
 *
 * Every action is additionally gated by the current item's capabilities at render
 * time, so a configured action simply stays hidden when it does not apply.
 */
@Serializable
enum class ViewerQuickAction(
    val labelRes: StringResource,
    val descriptionRes: StringResource,
) {
    SHARE(
        labelRes = Res.string.share,
        descriptionRes = Res.string.action_desc_share_item,
    ),
    VISUAL_SEARCH(
        labelRes = Res.string.visual_search,
        descriptionRes = Res.string.action_desc_visual_search_item,
    ),
    CAST(
        labelRes = Res.string.cast,
        descriptionRes = Res.string.action_desc_cast_item,
    ),
    COPY_TO_CLIPBOARD(
        labelRes = Res.string.copy_to_clipboard,
        descriptionRes = Res.string.action_desc_copy_item,
    ),
    FAVORITE(
        labelRes = Res.string.favorite,
        descriptionRes = Res.string.action_desc_favorite_item,
    ),
    OPEN_AS(
        labelRes = Res.string.open_with,
        descriptionRes = Res.string.action_desc_open_item,
    ),
    RESTORE(
        labelRes = Res.string.restore,
        descriptionRes = Res.string.action_desc_restore_item,
    ),
    DOWNLOAD(
        labelRes = Res.string.download,
        descriptionRes = Res.string.action_desc_download_item,
    ),
    RENAME(
        labelRes = Res.string.rename,
        descriptionRes = Res.string.action_desc_rename_item,
    ),
    EDIT(
        labelRes = Res.string.edit,
        descriptionRes = Res.string.action_desc_edit_item,
    ),
    TRASH(
        labelRes = Res.string.trash,
        descriptionRes = Res.string.action_desc_trash_item,
    );

    val icon: ImageVector
        get() = when (this) {
            SHARE -> Icons.Outlined.Share
            VISUAL_SEARCH -> Icons.Outlined.ImageSearch
            CAST -> Icons.Outlined.Cast
            COPY_TO_CLIPBOARD -> Icons.Outlined.ContentCopy
            FAVORITE -> Icons.Outlined.FavoriteBorder
            OPEN_AS -> Icons.AutoMirrored.Outlined.OpenInNew
            RESTORE -> Icons.Outlined.RestoreFromTrash
            DOWNLOAD -> Icons.Outlined.Download
            RENAME -> Icons.Outlined.DriveFileRenameOutline
            EDIT -> Icons.Outlined.Edit
            TRASH -> Icons.Outlined.DeleteOutline
        }
}

/**
 * Persisted viewer quick-action bar: the bottom-pill actions plus the two
 * configurable top-bar slots. [sanitized] drops duplicates, keeps an action in only
 * one place (top slots win over the bottom list) and falls back to the defaults when
 * the bottom list would be empty, so a malformed value can never lock the bar.
 *
 * Serialization note: the pref codec uses the default `Json` (`encodeDefaults = false`,
 * `explicitNulls = true`), so a stored config written before the top slots existed
 * decodes with the default slots, while a cleared slot round-trips as an explicit null.
 */
@Serializable
data class ViewerActionBarConfig(
    val actions: List<ViewerQuickAction> = DEFAULT_ACTIONS,
    val topStart: ViewerQuickAction? = DEFAULT_TOP_START,
    val topEnd: ViewerQuickAction? = DEFAULT_TOP_END,
) {
    /** Actions pinned to the top bar, in slot order. */
    val topActions: List<ViewerQuickAction>
        get() = listOfNotNull(topStart, topEnd)

    /** The actions rendered for [placement]; meant to be called on [sanitized] output. */
    fun actionsFor(placement: ViewerActionPlacement): List<ViewerQuickAction> = when (placement) {
        ViewerActionPlacement.BOTTOM -> actions
        ViewerActionPlacement.TOP_START -> listOfNotNull(topStart)
        ViewerActionPlacement.TOP_END -> listOfNotNull(topEnd)
    }

    fun sanitized(): ViewerActionBarConfig {
        var start = topStart
        var end = topEnd
        if (end != null && end == start) end = null
        val topSet = setOfNotNull(start, end)
        val bottom = actions.distinct() - topSet
        return ViewerActionBarConfig(
            actions = bottom.ifEmpty { DEFAULT_ACTIONS - topSet },
            topStart = start,
            topEnd = end,
        )
    }

    /**
     * Retired `show_favorite_button` switch migration: users who had it off keep the
     * favorite action out of their bar. A bar that would be left empty falls back to the
     * defaults minus favorite so [sanitized] cannot resurrect it.
     */
    fun withoutFavorite(): ViewerActionBarConfig {
        val base = sanitized()
        val topSet = setOfNotNull(base.topStart, base.topEnd) - ViewerQuickAction.FAVORITE
        val cleaned = base.actions - ViewerQuickAction.FAVORITE
        return ViewerActionBarConfig(
            actions = cleaned.ifEmpty { DEFAULT_ACTIONS - ViewerQuickAction.FAVORITE - topSet },
            topStart = base.topStart?.takeUnless { it == ViewerQuickAction.FAVORITE },
            topEnd = base.topEnd?.takeUnless { it == ViewerQuickAction.FAVORITE },
        )
    }

    /**
     * Retired `visual_search_position = bottom` migration: the visual-search action moves
     * from whichever top slot holds it into the quick-actions list, right after SHARE to
     * match the old default order. The other slot is left alone.
     */
    fun withVisualSearchInBottomBar(): ViewerActionBarConfig {
        val newActions = if (ViewerQuickAction.VISUAL_SEARCH in actions) {
            actions
        } else {
            val shareIndex = actions.indexOf(ViewerQuickAction.SHARE)
            actions.toMutableList().apply {
                add(if (shareIndex >= 0) shareIndex + 1 else 0, ViewerQuickAction.VISUAL_SEARCH)
            }
        }
        return copy(
            actions = newActions,
            topStart = topStart?.takeUnless { it == ViewerQuickAction.VISUAL_SEARCH },
            topEnd = topEnd?.takeUnless { it == ViewerQuickAction.VISUAL_SEARCH },
        )
    }

    /**
     * Puts [action] into the [placement] top slot (TOP_START or TOP_END): the action
     * leaves the bottom list and is cleared from the other slot, so it lives in exactly
     * one place. Refuses the change when it would leave the bottom bar empty, and a null
     * [action] only clears the slot without returning it to the bottom list.
     */
    fun withTopSlot(
        placement: ViewerActionPlacement,
        action: ViewerQuickAction?,
    ): ViewerActionBarConfig {
        require(placement != ViewerActionPlacement.BOTTOM)
        val base = sanitized()
        val newTopStart = when (placement) {
            ViewerActionPlacement.TOP_START -> action
            else -> base.topStart?.takeUnless { it == action }
        }
        val newTopEnd = when (placement) {
            ViewerActionPlacement.TOP_END -> action
            else -> base.topEnd?.takeUnless { it == action }
        }
        val newActions = if (action != null) base.actions - action else base.actions
        if (newActions.isEmpty()) return this
        return ViewerActionBarConfig(
            actions = newActions,
            topStart = newTopStart,
            topEnd = newTopEnd,
        )
    }

    companion object {
        val DEFAULT_TOP_START = ViewerQuickAction.VISUAL_SEARCH
        val DEFAULT_TOP_END = ViewerQuickAction.CAST

        /** Bottom-pill defaults — the pre-slots list minus VISUAL_SEARCH, which moved to a top slot. */
        val DEFAULT_ACTIONS = listOf(
            ViewerQuickAction.SHARE,
            ViewerQuickAction.COPY_TO_CLIPBOARD,
            ViewerQuickAction.FAVORITE,
            ViewerQuickAction.OPEN_AS,
            ViewerQuickAction.RESTORE,
            ViewerQuickAction.DOWNLOAD,
            ViewerQuickAction.RENAME,
            ViewerQuickAction.EDIT,
            ViewerQuickAction.TRASH,
        )
    }
}
