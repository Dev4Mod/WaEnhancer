package com.wmods.wppenhacer.xposed.features.media

import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType

class DownloadProfile(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        val profileClass =
            findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ViewProfilePhoto")
        XposedHelpers.findAndHookMethod(
            profileClass,
            "onCreateOptionsMenu",
            Menu::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as Menu
                    val item = menu.add(0, 0, 0, R.string.download)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    item.setIcon(R.drawable.download)
                    item.setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener { _: MenuItem? ->
                        val subCls: Class<*>? = param.thisObject.javaClass.getSuperclass()
                        if (subCls == null) {
                            log("SubClass is null")
                            return@OnMenuItemClickListener true
                        }
                        val field = ReflectionUtils.getFieldByExtendType(subCls, WaContactWpp.TYPE)
                        val fieldObj = ReflectionUtils.getObjectField(field, param.thisObject)
                        val waContact = WaContactWpp(fieldObj)
                        val userJid = waContact.userJid
                        val inputStream = waContact.getProfilePhoto(true) ?: return@OnMenuItemClickListener false
                        val destPath: String?
                        try {
                            destPath = Utils.getDestination("Profile Photo")
                        } catch (e: Exception) {
                            Utils.showToast(e.toString(), 1)
                            return@OnMenuItemClickListener true
                        }
                        val name = Utils.generateName(userJid, "jpg")
                        val error = Utils.copyFile(inputStream, destPath, name)
                        if (TextUtils.isEmpty(error)) {
                            Toast.makeText(
                                Utils.application,
                                Utils.application.getString(R.string.saved_to) + destPath,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                Utils.application,
                                Utils.application
                                    .getString(R.string.error_when_saving_try_again) + " " + error,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        true
                    })
                }
            })
    }

    override fun getPluginName(): String {
        return "Download Profile Picture"
    }
}
