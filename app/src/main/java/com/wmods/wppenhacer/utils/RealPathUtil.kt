package com.wmods.wppenhacer.utils

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri

object RealPathUtil {
    /** Resolve a file path from SAF, MediaStore, or file-provider Uris. */
    @SuppressLint("NewApi")
    @JvmStatic
    fun getRealFilePath(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val split = DocumentsContract.getDocumentId(uri).split(":")
                val type = split[0]
                if (type.equals("primary", ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                return getDownloadsPath(context, DocumentsContract.getDocumentId(uri))
            } else if (isMediaDocument(uri)) {
                val split = DocumentsContract.getDocumentId(uri).split(":")
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                return getDataColumn(context, contentUri, "_id=?", arrayOf(split[1]))
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) return uri.lastPathSegment
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    @SuppressLint("NewApi")
    @JvmStatic
    fun getRealFolderPath(context: Context, uri: Uri): String? {
        if (DocumentsContract.isTreeUri(uri)) {
            if (isExternalStorageDocument(uri)) {
                val split = DocumentsContract.getTreeDocumentId(uri).split(":")
                val type = split[0]
                if (type.equals("primary", ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory()
                        .toString() + "/" + (split.getOrNull(1) ?: "")
                }
            } else if (isDownloadsDocument(uri)) {
                return getDownloadsPath(context, DocumentsContract.getTreeDocumentId(uri))
            } else if (isMediaDocument(uri)) {
                val split = DocumentsContract.getTreeDocumentId(uri).split(":")
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                return getDataColumn(context, contentUri, "_id=?", arrayOf(split[1]))
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) return uri.lastPathSegment
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /** Downloads ids may be numeric, "raw:<path>" or "msf:<mediastore id>". */
    private fun getDownloadsPath(context: Context, id: String): String? {
        if (id.startsWith("raw:")) return id.removePrefix("raw:")
        if (id.startsWith("msf:")) {
            return getDataColumn(
                context,
                MediaStore.Files.getContentUri("external"),
                "_id=?",
                arrayOf(id.removePrefix("msf:"))
            )
        }
        val numericId = id.toLongOrNull() ?: return null
        val contentUri = ContentUris.withAppendedId(
            "content://downloads/public_downloads".toUri(),
            numericId
        )
        return getDataColumn(context, contentUri, null, null)
    }

    @JvmStatic
    fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        try {
            cursor =
                context.contentResolver.query(uri, arrayOf("_data"), selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("_data"))
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    @JvmStatic
    fun isExternalStorageDocument(uri: Uri): Boolean =
        uri.authority == "com.android.externalstorage.documents"

    @JvmStatic
    fun isDownloadsDocument(uri: Uri): Boolean =
        uri.authority == "com.android.providers.downloads.documents"

    @JvmStatic
    fun isMediaDocument(uri: Uri): Boolean =
        uri.authority == "com.android.providers.media.documents"

    @JvmStatic
    fun isGooglePhotosUri(uri: Uri): Boolean =
        uri.authority == "com.google.android.apps.photos.content"
}
