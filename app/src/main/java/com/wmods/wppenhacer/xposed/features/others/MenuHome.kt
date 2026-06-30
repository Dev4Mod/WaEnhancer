package com.wmods.wppenhacer.xposed.features.others

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.WppCore.setPrivBoolean
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedHelpers

class MenuHome(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {
    @Throws(Throwable::class)
    override fun doHook() {
        hookMenu()
        val action = prefs.getBoolean("buttonaction", true)

        // restart button
        addMenuItem { menu, activity ->
            insertRestartButton(
                menu,
                activity,
                action
            )
        }

        // dnd mode
        addMenuItem { menu, activity ->
            insertDNDOption(
                menu,
                activity,
                action
            )
        }

        // ghost mode
        addMenuItem { menu, activity ->
            insertGhostModeOption(
                menu,
                activity,
                action
            )
        }

        // freeze last seen
        addMenuItem { menu, activity ->
            insertFreezeLastSeenOption(
                menu,
                activity,
                action
            )
        }

        // open WAE
        addMenuItem { menu, activity ->
            this.insertOpenWae(
                menu,
                activity
            )
        }
    }

    private fun insertOpenWae(menu: Menu, activity: Activity) {
        val waeMenu = prefs.getBoolean("open_wae", true)
        if (!waeMenu) return
        val itemMenu = menu.add(0, 0, 9999, " " + activity.getString(R.string.app_name))
        val iconDraw = DesignUtils.getDrawableByName("ic_settings")
        iconDraw!!.setTint(-0x796960)
        itemMenu.icon = iconDraw
        itemMenu.setOnMenuItemClickListener {
            try {
                val intent = activity.packageManager.getLaunchIntentForPackage(
                    BuildConfig.APPLICATION_ID
                )
                intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (e: Exception) {
                Utils.showToast(e.message)
            }
            true
        }
    }

    private fun insertGhostModeOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val ghostmode = getPrivBoolean("ghostmode", false)
        if (!prefs.getBoolean("ghostmode", true)) {
            if (ghostmode) {
                setPrivBoolean("ghostmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val itemMenu = menu.add(0, 0, 0, R.string.ghost_mode)

        val iconDraw =
            activity.getDrawable(if (ghostmode) R.drawable.ghost_enabled else R.drawable.ghost_disabled)
        if (iconDraw != null) {
            iconDraw.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            itemMenu.icon = iconDraw
        }
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            AlertDialogWpp(activity).setTitle(
                activity.getString(
                    R.string.ghost_mode_s,
                    (if (ghostmode) "ON" else "OFF")
                )
            ).setMessage(activity.getString(R.string.ghost_mode_message))
                .setPositiveButton(activity.getString(R.string.disable)) { _, _ ->
                    setPrivBoolean("ghostmode", false)
                    Utils.doRestart(activity)
                }
                .setNegativeButton(activity.getString(R.string.enable)) { _, _ ->
                    setPrivBoolean("ghostmode", true)
                    Utils.doRestart(activity)
                }.show()
            true
        }
    }

    private fun insertRestartButton(menu: Menu, activity: Activity, newSettings: Boolean) {
        if (!prefs.getBoolean("restartbutton", true)) return
        val iconDraw = activity.getDrawable(R.drawable.refresh)
        iconDraw!!.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
        val itemMenu = menu.add(0, 0, 0, R.string.restart_whatsapp).setIcon(iconDraw)
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            Utils.doRestart(activity)
            true
        }
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref")
    private fun insertDNDOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val dndmode = getPrivBoolean("dndmode", false)
        if (!prefs.getBoolean("show_dndmode", false)) {
            if (getPrivBoolean("dndmode", false)) {
                setPrivBoolean("dndmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val item = menu.add(0, 0, 0, activity.getString(R.string.dnd_mode_title))
        val drawable = Utils.application
            .getDrawable(if (dndmode) R.drawable.airplane_enabled else R.drawable.airplane_disabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            if (!dndmode) {
                AlertDialogWpp(activity)
                    .setTitle(activity.getString(R.string.dnd_mode_title))
                    .setMessage(activity.getString(R.string.dnd_message))
                    .setPositiveButton(activity.getString(R.string.activate)) { _, _ ->
                        setPrivBoolean("dndmode", true)
                        Utils.doRestart(activity)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog?.dismiss() }
                    .create().show()
                return@setOnMenuItemClickListener true
            }
            setPrivBoolean("dndmode", false)
            Utils.doRestart(activity)
            true
        }
    }

    private fun insertFreezeLastSeenOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val freezelastseen = getPrivBoolean("freezelastseen", false)
        if (!prefs.getBoolean("show_freezeLastSeen", true)) {
            if (freezelastseen) {
                setPrivBoolean("freezelastseen", false)
                Utils.doRestart(activity)
            }
            return
        }

        val item = menu.add(0, 0, 0, activity.getString(R.string.freezelastseen_title))
        val drawable = Utils.application
            .getDrawable(if (freezelastseen) R.drawable.eye_disabled else R.drawable.eye_enabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            if (!freezelastseen) {
                AlertDialogWpp(activity)
                    .setTitle(activity.getString(R.string.freezelastseen_title))
                    .setMessage(activity.getString(R.string.freezelastseen_message))
                    .setPositiveButton(activity.getString(R.string.activate)) { _, _ ->
                        setPrivBoolean("freezelastseen", true)
                        Utils.doRestart(activity)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog?.dismiss() }
                    .create().show()
                return@setOnMenuItemClickListener true
            }
            setPrivBoolean("freezelastseen", false)
            Utils.doRestart(activity)
            true
        }
    }

    private fun hookMenu() {
        XposedHelpers.findAndHookMethod(
            homeActivityClass,
            "onCreateOptionsMenu",
            Menu::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as Menu
                    val activity = param.thisObject as Activity
                    for (menuItem in menuItems) {
                        menuItem.addMenu(menu, activity)
                    }
                }
            })
    }

    override fun getPluginName(): String {
        return "Menu Home"
    }

    fun interface HomeMenuItem {
        fun addMenu(menu: Menu, activity: Activity)
    }

    companion object {
        private var menuItems: HashSet<HomeMenuItem> = LinkedHashSet<HomeMenuItem>()

        @JvmStatic
        fun addMenuItem(homeMenuItem: HomeMenuItem) {
            menuItems += homeMenuItem
        }
    }
}
