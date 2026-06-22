package com.wmods.wppenhacer.xposed.features.media

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.viewOnceViewerActivityClass
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewOnceDownloadMenuMethod
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.util.concurrent.CompletableFuture

class DownloadViewOnce(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (prefs.getBoolean("downloadviewonce", false)) {
            val menuMethod = loadViewOnceDownloadMenuMethod(classLoader)
            // Media Activity
            XposedBridge.hookMethod(menuMethod, object : XC_MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val fmessageObj: Any? = ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0)
                    val fMessage = FMessageWpp(fmessageObj)

                    // check media is view once
                    if (!fMessage.isViewOnce) return
                    val menu = ReflectionUtils.getArg(param.args, Menu::class.java, 0)
                    val item = menu!!.add(0, 0, 0, R.string.download).setIcon(R.drawable.download)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    item.setOnMenuItemClickListener {
                        try {
                            val file = fMessage.mediaFile
                            if (file == null) {
                                Utils.showToast(
                                    Utils.application
                                        .getString(R.string.download_not_available), 1
                                )
                                return@setOnMenuItemClickListener true
                            }
                            downloadFile(fMessage.key.remoteJid, file)
                        } catch (e: Exception) {
                            Utils.showToast(e.message, Toast.LENGTH_LONG)
                        }
                        true
                    }
                }
            })
            // View Once Activity
            XposedHelpers.findAndHookMethod(
                viewOnceViewerActivityClass,
                "onCreateOptionsMenu",
                classLoader.loadClass("android.view.Menu"),
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val menu = param.args[0] as Menu
                        val item = menu.add(0, 0, 0, R.string.download).setIcon(R.drawable.download)
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        item.setOnMenuItemClickListener {
                            CompletableFuture.runAsync {
                                val keyClass: Class<*> = FMessageWpp.Key.TYPE
                                val fieldType = ReflectionUtils.getFieldByType(
                                    param.thisObject.javaClass,
                                    keyClass
                                )
                                val keyMessageObj =
                                    ReflectionUtils.getObjectField(fieldType, param.thisObject)
                                val fmessage = FMessageWpp.Key(keyMessageObj).fMessage
                                val file = fmessage!!.mediaFile
                                if (file == null) {
                                    Utils.showToast(
                                        Utils.application
                                            .getString(R.string.download_not_available), 1
                                    )
                                    return@runAsync
                                }
                                try {
                                    downloadFile(fmessage.key.remoteJid, file)
                                } catch (e: Exception) {
                                    Utils.showToast(e.message, Toast.LENGTH_LONG)
                                }
                            }
                            true
                        }
                    }
                })
        }
    }

    override fun getPluginName(): String {
        return "Download View Once"
    }

    companion object {

        private fun downloadFile(userJid: UserJid?, file: File) {
            val dest = Utils.getDestination("View Once")
            val fileExtension =
                file.absolutePath.substring(file.absolutePath.lastIndexOf(".") + 1)
            val name = Utils.generateName(userJid!!, fileExtension)
            val error = Utils.copyFile(file, dest, name)
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(
                    Utils.application.getString(R.string.saved_to) + dest,
                    Toast.LENGTH_LONG
                )
            } else {
                Utils.showToast(
                    Utils.application
                        .getString(R.string.error_when_saving_try_again) + ":" + error,
                    Toast.LENGTH_LONG
                )
            }
        }
    }
}
