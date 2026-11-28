package com.dot.gallery.editor

class GalleryInternalVideoEditorActivity : GalleryVideoEditorActivity() {
    override fun allowsOwnProviderUris(): Boolean = true
}
