package com.wmods.wppenhacer.xposed.features.media

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.utils.MimeTypeUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XSharedPreferences
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File

class StatusDownload(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("downloadstatus", false)) return

        val downloadStatus = object : MenuStatusListener.OnMenuItemStatusListener() {
            override fun addMenu(menu: Menu, statusData: MenuStatusListener.StatusData): MenuItem? {
                if (menu.findItem(R.string.download) != null) return null
                val item = statusData.currentItem
                if (item.isFromMe) return null
                if (!item.isMediaFile) return null
                val menuItem = menu.add(0, R.string.download, 0, R.string.download)
                if (item.getMediaFile() == null) {
                    menuItem.title = Utils.getString(R.string.download) + " ⏳"
                }
                return menuItem
            }

            override fun onClick(item: MenuItem, statusData: MenuStatusListener.StatusData) {
                downloadFile(statusData.currentItem)
            }
        }
        MenuStatusListener.menuStatuses.add(downloadStatus)

        val sharedMenu = object : MenuStatusListener.OnMenuItemStatusListener() {
            override fun addMenu(menu: Menu, statusData: MenuStatusListener.StatusData): MenuItem? {
                val item = statusData.currentItem
                if (item.isFromMe) return null
                if (menu.findItem(R.string.share_as_status) != null) return null
                return menu.add(0, R.string.share_as_status, 0, R.string.share_as_status)
            }

            override fun onClick(item: MenuItem, statusData: MenuStatusListener.StatusData) {
                sharedStatus(statusData.currentItem)
            }
        }
        MenuStatusListener.menuStatuses.add(sharedMenu)
    }

    private fun sharedStatus(statusItem: StatusItemWpp) {
        try {
            val fMessage = statusItem.fMessage
            if (!statusItem.isMediaFile) {
                val intent = Intent()
                var clazz: Class<*>
                try {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "TextStatusComposerActivity")
                } catch (ignored: Exception) {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ConsolidatedStatusComposerActivity")
                    intent.putExtra("status_composer_mode", 2)
                }
                intent.setClassName(Utils.getApplication().packageName, clazz.name)
                intent.putExtra("android.intent.extra.TEXT", fMessage?.messageStr)
                WppCore.getCurrentActivity()?.startActivity(intent)
                return
            }

            val file = statusItem.getMediaFile()
            if (file == null) {
                Utils.showToast(Utils.getString(R.string.download_not_available), Toast.LENGTH_SHORT)
                return
            }

            val intent = Intent()
            val clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "MediaComposerActivity")
            intent.setClassName(Utils.getApplication().packageName, clazz.name)
            intent.putExtra("jids", arrayListOf("status@broadcast"))
            intent.putExtra("android.intent.extra.STREAM", arrayListOf(Uri.fromFile(file)))
            intent.putExtra("android.intent.extra.TEXT", fMessage?.messageStr)
            WppCore.getCurrentActivity()?.startActivity(intent)

        } catch (e: Throwable) {
            Utils.showToast(e.message, Toast.LENGTH_SHORT)
        }
    }

    private fun downloadFile(statusItem: StatusItemWpp) {
        try {
            val file = statusItem.getMediaFile()
            if (file == null) {
                Utils.showToast(Utils.getString(R.string.download_not_available), Toast.LENGTH_LONG)
                return
            }
            val userJid = statusItem.senderJid ?: return
            val fileType = file.name.substring(file.name.lastIndexOf(".") + 1)
            val destination = getStatusDestination(file)
            val name = Utils.generateName(userJid, fileType)
            val error = Utils.copyFile(file, destination, name)

            if (TextUtils.isEmpty(error)) {
                Utils.showToast(Utils.getString(R.string.saved_to) + destination, Toast.LENGTH_SHORT)
            } else {
                Utils.showToast("${Utils.getString(R.string.error_when_saving_try_again)}: $error", Toast.LENGTH_SHORT)
            }
        } catch (e: Throwable) {
            Utils.showToast(e.message, Toast.LENGTH_SHORT)
        }
    }

    override fun getPluginName(): String {
        return "Download Status"
    }

    @Throws(Exception::class)
    private fun getStatusDestination(f: File): String {
        val fileName = f.name.lowercase()
        val mimeType = MimeTypeUtils.getMimeTypeFromExtension(fileName)

        val folderPath = when {
            mimeType.contains("video") -> "Status Videos"
            mimeType.contains("image") -> "Status Images"
            mimeType.contains("audio") -> "Status Sounds"
            else -> "Status Media"
        }

        return Utils.getDestination(folderPath)
    }
}