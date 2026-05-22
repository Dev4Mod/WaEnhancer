package com.wmods.wppenhacer.xposed.features.general

import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import de.robv.android.xposed.XSharedPreferences
import org.luckypray.dexkit.query.enums.StringMatchType

class DeleteStatus(classLoader: ClassLoader, preferences: XSharedPreferences) : Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        val statusPlaybackActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackActivity")

        val item = object : MenuStatusListener.OnMenuItemStatusListener() {

            override fun addMenu(menu: Menu, statusData: MenuStatusListener.StatusData): MenuItem? {
                if (menu.findItem(R.string.delete_for_me) != null) return null
                if (statusData.currentItem.isFromMe) return null
                return menu.add(0, R.string.delete_for_me, 0, R.string.delete_for_me)
            }

            override fun onClick(item: MenuItem, statusData: MenuStatusListener.StatusData) {
                try {
                    MessageStore.getInstance().deleteStatusByMessageKey(statusData.currentItem.messageID)

                    val activity = WppCore.getCurrentActivity()
                    if (activity != null && statusPlaybackActivityClass.isInstance(activity)) {
                        activity.recreate()
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        }
        MenuStatusListener.menuStatuses.add(item)
    }

    override fun getPluginName(): String {
        return "Delete Status"
    }
}