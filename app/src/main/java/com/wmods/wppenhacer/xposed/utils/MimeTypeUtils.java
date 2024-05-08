package com.wmods.wppenhacer.xposed.utils;

import android.webkit.MimeTypeMap;

public class MimeTypeUtils {
    public static String getMimeTypeFromExtension(String url) {
        String type = "";
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }
}
