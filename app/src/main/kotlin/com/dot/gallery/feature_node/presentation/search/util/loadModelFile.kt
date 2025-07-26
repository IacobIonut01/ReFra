package com.dot.gallery.feature_node.presentation.search.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

fun loadModelFile(context: Context, assetName: String): File {
    val file = File(context.cacheDir, assetName)
    if (!file.exists()) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
    return file
}

fun loadModelFromRaw(context: Context, resId: Int, fileName: String): File {
    val file = File(context.cacheDir, fileName)
    if (!file.exists()) {
        context.resources.openRawResource(resId).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
    return file
}