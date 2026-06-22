package com.wmods.wppenhacer.xposed.utils

import android.webkit.MimeTypeMap

object MimeTypeUtils {

    @JvmStatic
    fun getMimeTypeFromExtension(url: String): String {
        var type = ""
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
        }
        return type
    }
}
