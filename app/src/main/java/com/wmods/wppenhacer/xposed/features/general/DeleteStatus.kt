package com.wmods.wppenhacer.xposed.features.general

import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.utils.Utils
import android.content.SharedPreferences
import android.widget.Toast
import org.luckypray.dexkit.query.enums.StringMatchType

class DeleteStatus(classLoader: ClassLoader, preferences:SharedPreferences) : Feature(classLoader, preferences) {

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
                val activity = WppCore.getCurrentActivity()
                val messageId = statusData.currentItem.messageID

                MessageStore.getInstance().deleteStatusByMessageKey(messageId) { success ->
                    if (success && activity != null && statusPlaybackActivityClass.isInstance(activity)) {
                        activity.runOnUiThread {
                            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                            val itemList = statusData.getCurrentItemList()
                            val isLastItem = statusData.currentIndex >= itemList.size - 1

                            if (itemList.size <= 1 || isLastItem) {
                                activity.finish()
                            } else {
                                activity.recreate()
                                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            }
                        }
                    }
                }
            }
        }
        MenuStatusListener.menuStatuses.add(item)
    }

    override fun getPluginName(): String {
        return "Delete Status"
    }
}
