package com.dot.gallery.feature_node.presentation.util

import com.bumptech.glide.load.Key
import com.bumptech.glide.signature.ObjectKey

object GlideInvalidation {

    fun <T> signature(obj: T): Key {
        return ObjectKey(obj.toString())
    }
}