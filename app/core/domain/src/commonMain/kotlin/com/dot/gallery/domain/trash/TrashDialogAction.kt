package com.dot.gallery.domain.trash

enum class TrashDialogAction {
    TRASH,
    DELETE,
    RESTORE
}

fun resolveTrashDialogAction(
    trashRequested: Boolean,
    trashEnabled: Boolean,
    trashSupported: Boolean
): TrashDialogAction = if (trashRequested && trashEnabled && trashSupported) {
    TrashDialogAction.TRASH
} else {
    TrashDialogAction.DELETE
}
