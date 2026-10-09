package com.dot.gallery.feature_node.presentation.albums.components

internal enum class AlbumThumbnailPresentation(
    val allowsMediaRequest: Boolean
) {
    MEDIA(allowsMediaRequest = true),
    LOCKED_PLACEHOLDER(allowsMediaRequest = false)
}

internal fun albumThumbnailPresentation(isLocked: Boolean): AlbumThumbnailPresentation =
    if (isLocked) {
        AlbumThumbnailPresentation.LOCKED_PLACEHOLDER
    } else {
        AlbumThumbnailPresentation.MEDIA
    }

/**
 * "Change cover" is a sub-page of the album option sheet. It must drop the
 * moment the sheet is dismissed or the system picker returns, otherwise the
 * next long-press reopens on the cover options instead of the album menu.
 */
internal fun nextAlbumCoverPickerState(
    currentlySelecting: Boolean,
    sheetVisible: Boolean,
    pickerFinished: Boolean = false
): Boolean = currentlySelecting && sheetVisible && !pickerFinished
