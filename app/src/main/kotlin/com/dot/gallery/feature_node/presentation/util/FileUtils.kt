/**
 * Source
 * https://github.com/saparkhid/AndroidFileNamePicker/blob/main/javautil/FileUtils.java
 */
package com.dot.gallery.feature_node.presentation.util

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import java.io.File
import java.io.FileOutputStream
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import androidx.core.net.toUri

@Suppress("NOTHING_TO_INLINE")
inline fun String.sentenceCase(): String = lowercase().replaceFirstChar { it.uppercase() }

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

    val formattedSize = size / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.2f %s", formattedSize, units[digitGroups])
}

fun File.formattedFileSize(context: Context): String {
    var fileSize = this.length().toDouble() / 1024.0
    var fileSizeName = context.getString(R.string.kb)
    if (fileSize > 1024.0) {
        fileSize /= 1024.0
        fileSizeName = context.getString(R.string.mb)
        if (fileSize > 1024.0) {
            fileSize /= 1024.0
            fileSizeName = context.getString(R.string.gb)
        }
    }
    val roundingSize = DecimalFormat("#.##").apply {
        roundingMode = RoundingMode.DOWN
    }
    return "${roundingSize.format(fileSize)} $fileSizeName"
}

class FileUtils(var context: Context) {
    fun getPath(uri: Uri): String? {
        val selection: String?
        val selectionArgs: Array<String>?
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var fullPath = getPathFromExtSD(split)
            if (fullPath == null || !fileExists(fullPath)) {
                fullPath = copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER)
            }
            return if (fullPath != "") {
                fullPath
            } else {
                null
            }
        }
        // DownloadsProvider
        if (isDownloadsDocument(uri)) {
            context.contentResolver.query(
                uri, arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME
                ), null, null, null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val fileName = cursor.getString(0)
                    val path = Environment.getExternalStorageDirectory()
                        .toString() + "/Download/" + fileName
                    if (!TextUtils.isEmpty(path)) {
                        return path
                    }
                }
            }
            val id: String = DocumentsContract.getDocumentId(uri)
            if (!TextUtils.isEmpty(id)) {
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:".toRegex(), "")
                }
                val contentUriPrefixesToTry = arrayOf(
                    "content://downloads/public_downloads",
                    "content://downloads/my_downloads"
                )
                for (contentUriPrefix in contentUriPrefixesToTry) {
                    return try {
                        val contentUri = ContentUris.withAppendedId(
                            contentUriPrefix.toUri(),
                            id.toLong()
                        )
                        getDataColumn(context, contentUri, null, null)
                    } catch (e: NumberFormatException) {
                        //In Android 8 and Android P the id is not a number
                        uri.path!!.replaceFirst("^/document/raw:".toRegex(), "")
                            .replaceFirst("^raw:".toRegex(), "")
                    }
                }
            }
        }
        // MediaProvider
        if (isMediaDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]
            var contentUri: Uri? = null
            when (type) {
                "image" -> {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                "video" -> {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }
            selection = "_id=?"
            selectionArgs = arrayOf(
                split[1]
            )
            return getDataColumn(context, contentUri, selection, selectionArgs)
        }
        if (isGoogleDriveUri(uri)) {
            return getDriveFilePath(uri)
        }
        if (isWhatsAppFile(uri)) {
            return getFilePathForWhatsApp(uri)
        }
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) {
                return uri.lastPathSegment
            }
            if (isGoogleDriveUri(uri)) {
                return getDriveFilePath(uri)
            }
            if (uri.path != null && uri.path!!.contains("Android")) {
                Log.d(Constants.TAG, "Uri: $uri")
                return uri.toString()
            }
            return copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER)
        }
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER)
    }

    private fun getDriveFilePath(uri: Uri): String {
        val returnCursor = context.contentResolver.query(uri, null, null, null, null)
        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        val nameIndex = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        returnCursor.close()
        val file = File(context.cacheDir, name)
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            var read: Int
            val maxBufferSize = 1 * 1024 * 1024
            val bytesAvailable = inputStream!!.available()
            val bufferSize = min(bytesAvailable, maxBufferSize)
            val buffers = ByteArray(bufferSize)
            while (inputStream.read(buffers).also { read = it } != -1) {
                outputStream.write(buffers, 0, read)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
        }
        return file.path
    }

    /***
     * Used for Android Q+
     * @param uri
     * @param newDirName if you want to create a directory, you can set this variable
     * @return
     */
    private fun copyFileToInternalStorage(uri: Uri, newDirName: String): String {
        val returnCursor = context.contentResolver.query(
            uri, arrayOf(
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
            ), null, null, null
        )


        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        val nameIndex = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        returnCursor.close()
        val output: File = if (newDirName != "") {
            val randomCollisionAvoidance = UUID.randomUUID().toString()
            val dir =
                File(context.filesDir.toString() + File.separator + newDirName + File.separator + randomCollisionAvoidance)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(context.filesDir.toString() + File.separator + newDirName + File.separator + randomCollisionAvoidance + File.separator + name)
        } else {
            File(context.filesDir.toString() + File.separator + name)
        }
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(output)
            var read: Int
            val bufferSize = 1024
            val buffers = ByteArray(bufferSize)
            while (inputStream!!.read(buffers).also { read = it } != -1) {
                outputStream.write(buffers, 0, read)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
        }
        return output.path
    }

    private fun getFilePathForWhatsApp(uri: Uri): String {
        return copyFileToInternalStorage(uri, "whatsapp")
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection,
                selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    private fun isWhatsAppFile(uri: Uri): Boolean {
        return "com.whatsapp.provider.media" == uri.authority
    }

    private fun isGoogleDriveUri(uri: Uri): Boolean {
        return "com.google.android.apps.docs.storage" == uri.authority || "com.google.android.apps.docs.storage.legacy" == uri.authority
    }

    companion object {
        var FALLBACK_COPY_FOLDER = "upload_part"
        private const val TAG = "FileUtils"
        private var contentUri: Uri? = null
        private fun fileExists(filePath: String): Boolean {
            val file = File(filePath)
            return file.exists()
        }

        private fun getPathFromExtSD(pathData: Array<String>): String? {
            val type = pathData[0]
            val relativePath = File.separator + pathData[1]
            var fullPath: String
            // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
            // something like "71F8-2C0A", some kind of unique id per storage
            // don't know any API that can get the root path of that storage based on its id.
            //
            // so no "primary" type, but let the check here for other devices
            if ("primary".equals(type, ignoreCase = true)) {
                fullPath = Environment.getExternalStorageDirectory().toString() + relativePath
                if (fileExists(fullPath)) {
                    return fullPath
                }
            }
            if ("home".equals(type, ignoreCase = true)) {
                fullPath = "/storage/emulated/0/Documents$relativePath"
                if (fileExists(fullPath)) {
                    return fullPath
                }
            }

            // Environment.isExternalStorageRemovable() is `true` for external and internal storage
            // so we cannot relay on it.
            //
            // instead, for each possible path, check if file exists
            // we'll start with secondary storage as this could be our (physically) removable sd card
            fullPath = System.getenv("SECONDARY_STORAGE")!! + relativePath
            if (fileExists(fullPath)) {
                return fullPath
            }
            fullPath = System.getenv("EXTERNAL_STORAGE")!! + relativePath
            return if (fileExists(fullPath)) {
                fullPath
            } else null
        }

        private fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        private fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }
    }
}

fun Uri.isFromApps(): Boolean =
    scheme.toString() == "content" && toString().contains("Android/")