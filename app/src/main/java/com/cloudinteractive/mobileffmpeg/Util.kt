package com.cloudinteractive.mobileffmpeg

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import java.io.*

fun getRealPathFromURI_API19(context: Context, uri: Uri): String {
    val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        // ExternalStorageProvider
        when {
            isExternalStorageDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]

                // This is for checking Main Memory
                return if ("primary".equals(type, ignoreCase = true)) {
                    if (split.size > 1) {
                        Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    } else {
                        Environment.getExternalStorageDirectory().toString() + "/"
                    }
                    // This is for checking SD Card
                } else {
                    "storage" + "/" + docId.replace(":", "/")
                }
            }
            isDownloadsDocument(uri) -> {
                val fileName = getFilePath(context, uri)
                if (fileName != null) {
                    return Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                }
                var id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) {
                    id = id.replaceFirst("raw:".toRegex(), "")
                    val file = File(id)
                    if (file.exists()) return id
                }
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            }
            isMediaDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                contentUri?.apply {
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            }
        }
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
        // Return the remote address
        return if (isGooglePhotosUri(uri)) {
            uri.lastPathSegment!!
        } else {
            if (Build.VERSION.SDK_INT >= 24) {
                getFilePathFromURI(context, uri)
            } else {
                getDataColumn(context, uri, null, null)
            }
        }
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path!!
    }
    return ""
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is ExternalStorageProvider.
 */
private fun isExternalStorageDocument(uri: Uri): Boolean {
    return "com.android.externalstorage.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is DownloadsProvider.
 */
private fun isDownloadsDocument(uri: Uri): Boolean {
    return "com.android.providers.downloads.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is MediaProvider.
 */
private fun isMediaDocument(uri: Uri): Boolean {
    return "com.android.providers.media.documents" == uri.authority
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is Google Photos.
 */
private fun isGooglePhotosUri(uri: Uri): Boolean {
    return "com.google.android.apps.photos.content" == uri.authority
}

private fun getFilePath(context: Context, uri: Uri?): String? {
    var cursor: Cursor? = null
    val projection = arrayOf(
        MediaStore.MediaColumns.DISPLAY_NAME
    )
    try {
        cursor = context.contentResolver.query(uri!!, projection, null, null,
            null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            return cursor.getString(index)
        }
    } catch (e: Exception) {
        e.printStackTrace();
    } finally {
        cursor?.close()
    }
    return null
}


private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(
        column
    )
    try {
        cursor = context.contentResolver.query(uri, projection, selection, selectionArgs,
            null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(index)
        }
    } catch (e: Exception) {
        e.printStackTrace();
    } finally {
        cursor?.close()
    }
    return ""
}


fun getFilePathFromURI(context: Context, contentUri: Uri?): String {
    val rootDataDir = context.filesDir
    val fileName = getFilePath(context, contentUri)
    try {
        if (!TextUtils.isEmpty(fileName)) {
            val copyFile = File(rootDataDir.toString() + File.separator + fileName)
            copyFile(context, contentUri, copyFile)
            return copyFile.absolutePath
        }
    } catch (e: Exception) {
        return ""
    }
    return ""
}

fun copyFile(context: Context, srcUri: Uri?, dstFile: File?) {
    try {
        val inputStream = context.contentResolver.openInputStream(srcUri!!)
            ?: return
        val outputStream: OutputStream = FileOutputStream(dstFile)
        copyStream(inputStream, outputStream)
        inputStream.close()
        outputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Throws(java.lang.Exception::class, IOException::class)
fun copyStream(input: InputStream?, output: OutputStream?): Int {
    val BUFFER_SIZE = 1024 * 2
    val buffer = ByteArray(BUFFER_SIZE)
    val `in` = BufferedInputStream(input, BUFFER_SIZE)
    val out = BufferedOutputStream(output, BUFFER_SIZE)
    var count = 0
    var n = 0
    try {
        while (`in`.read(buffer, 0, BUFFER_SIZE).also({ n = it }) != -1) {
            out.write(buffer, 0, n)
            count += n
        }
        out.flush()
    } finally {
        try {
            out.close()
        } catch (e: IOException) {
        }
        try {
            `in`.close()
        } catch (e: IOException) {
        }
    }
    return count
}