package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.viewOnceViewerActivityClass
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewOnceDownloadMenuMethod
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.io.File
import java.util.concurrent.CompletableFuture

class DownloadViewOnce(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (xprefs.getBoolean("downloadviewonce", false)) {
            val menuMethod = loadViewOnceDownloadMenuMethod(classLoader)
            // Media Activity
            menuMethod.hook {
                after {
                    val fmessageObj: Any? = ReflectionUtils.getArg(args, FMessageWpp.TYPE, 0)
                    val fMessage = FMessageWpp(fmessageObj)

                    // check media is view once
                    if (!fMessage.isViewOnce) return@after
                    val menu = ReflectionUtils.getArg(args, Menu::class.java, 0)
                    val item = menu!!.add(0, 0, 0, R.string.download).setIcon(R.drawable.download)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    item.setOnMenuItemClickListener {
                        CompletableFuture.runAsync {
                            try {
                                val file = fMessage.mediaFile
                                if (file == null) {
                                    Utils.showToast(
                                        Utils.application
                                            .getString(R.string.download_not_available), 1
                                    )
                                    return@runAsync
                                }
                                downloadFile(fMessage.key.remoteJid, file)
                            } catch (e: Exception) {
                                Utils.showToast(e.message, Toast.LENGTH_LONG)
                            }
                        }
                        true
                    }
                }
            }
            // View Once Activity
            viewOnceViewerActivityClass.resolve().firstMethod {
                name = "onCreateOptionsMenu"
                superclass()
                parameters(classLoader.loadClass("android.view.Menu"))
            }.hook {
                after {
                    val menu = args[0] as Menu
                    val item = menu.add(0, 0, 0, R.string.download).setIcon(R.drawable.download)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    item.setOnMenuItemClickListener {
                        CompletableFuture.runAsync {
                            val keyClass: Class<*> = FMessageWpp.Key.TYPE
                            val fieldType = ReflectionUtils.getFieldByType(
                                instance.javaClass,
                                keyClass
                            )
                            val keyMessageObj =
                                ReflectionUtils.getObjectField(fieldType, instance)
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
            }
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
            var savedTo = dest
            val error = Utils.copyFile(file, dest, name) { savedTo = it }
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(
                    Utils.application.getString(R.string.saved_to) + savedTo,
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
