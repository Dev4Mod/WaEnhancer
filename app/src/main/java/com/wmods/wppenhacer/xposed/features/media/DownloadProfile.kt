package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType

class DownloadProfile(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        val profileClass =
            findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ViewProfilePhoto")
        profileClass.resolve().firstMethod {
            name = "onCreateOptionsMenu"
            superclass()
            parameters(Menu::class.java)
        }.hook {
            after {
                val menu = args[0] as Menu
                val item = menu.add(0, 0, 0, R.string.download)
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                item.setIcon(R.drawable.download)
                item.setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener { _: MenuItem? ->
                    val subCls: Class<*>? = instance.javaClass.getSuperclass()
                    if (subCls == null) {
                        log("SubClass is null")
                        return@OnMenuItemClickListener true
                    }
                    val field = ReflectionUtils.getFieldByExtendType(subCls, WaContactWpp.TYPE)
                    val fieldObj = ReflectionUtils.getObjectField(field, instance)
                    val waContact = WaContactWpp(fieldObj)
                    val userJid = waContact.userJid
                    val inputStream =
                        waContact.getProfilePhoto(true) ?: return@OnMenuItemClickListener false
                    Utils.executor.execute {
                        val destPath: String?
                        try {
                            destPath = Utils.getDestination("Profile Photo")
                        } catch (e: Exception) {
                            Utils.showToast(e.toString(), Toast.LENGTH_LONG)
                            return@execute
                        }
                        val name = Utils.generateName(userJid, "jpg")
                        var savedTo = destPath
                        val error = Utils.copyFile(inputStream, destPath, name) { savedTo = it }
                        if (TextUtils.isEmpty(error)) {
                            Utils.showToast(
                                Utils.application.getString(R.string.saved_to) + savedTo,
                                Toast.LENGTH_LONG
                            )
                        } else {
                            Utils.showToast(
                                Utils.application.getString(R.string.error_when_saving_try_again) + " " + error,
                                Toast.LENGTH_LONG
                            )
                        }
                    }
                    true
                })
            }
        }
    }

    override fun getPluginName(): String {
        return "Download Profile Picture"
    }
}
