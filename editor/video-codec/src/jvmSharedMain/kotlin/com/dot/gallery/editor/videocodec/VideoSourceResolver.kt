package com.dot.gallery.editor.videocodec

/** Maps an opaque host reference (`content://`, `file://`, path) to a local file. */
fun interface VideoSourceResolver {
    suspend fun resolve(reference: String): ResolvedVideoSource
}
