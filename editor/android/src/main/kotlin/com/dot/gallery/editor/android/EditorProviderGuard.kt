package com.dot.gallery.editor.android

import android.content.Context
import android.net.Uri

internal object EditorProviderGuard {

    fun isOwnProviderUri(context: Context, uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        val provider = context.packageManager.resolveContentProvider(
            withoutUserId(authority),
            0,
        ) ?: return false
        return provider.packageName == context.packageName
    }

    fun withoutUserId(authority: String): String {
        val separator = authority.lastIndexOf('@')
        if (separator <= 0 || separator == authority.lastIndex) return authority
        val userId = authority.substring(0, separator).toIntOrNull() ?: return authority
        return if (userId >= 0) authority.substring(separator + 1) else authority
    }
}
