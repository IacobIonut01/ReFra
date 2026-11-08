/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.mediaview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
 * One action that can live in the media viewer's bottom quick-action pill.
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
 * Persisted viewer quick-action bar: the actions to show and their left-to-right
 * order. [sanitized] drops duplicates and falls back to the defaults when the
 * stored list is empty, so a malformed value can never lock the bar.
 */
@Serializable
data class ViewerActionBarConfig(
    val actions: List<ViewerQuickAction> = DEFAULT_ACTIONS,
) {
    fun sanitized(): ViewerActionBarConfig {
        val cleaned = actions.distinct()
        return if (cleaned.isEmpty()) ViewerActionBarConfig() else ViewerActionBarConfig(cleaned)
    }

    companion object {
        val DEFAULT_ACTIONS = listOf(
            ViewerQuickAction.SHARE,
            ViewerQuickAction.VISUAL_SEARCH,
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
