package com.wmods.wppenhacer.xposed.bridge.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.wmods.wppenhacer.xposed.bridge.service.HookBinder

class HookProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return false
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "getHookBinder") {
            val result = Bundle()
            result.putBinder("binder", HookBinder)
            return result
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String {
        return ""
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return 0
    }
}
