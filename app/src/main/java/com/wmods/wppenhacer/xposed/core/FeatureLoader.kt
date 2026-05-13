package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.UpdateChecker
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.customization.BubbleColors
import com.wmods.wppenhacer.xposed.features.customization.ContactBlockedVerify
import com.wmods.wppenhacer.xposed.features.customization.CustomThemeV2
import com.wmods.wppenhacer.xposed.features.customization.CustomTime
import com.wmods.wppenhacer.xposed.features.customization.CustomToolbar
import com.wmods.wppenhacer.xposed.features.customization.CustomView
import com.wmods.wppenhacer.xposed.features.customization.FilterGroups
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView
import com.wmods.wppenhacer.xposed.features.customization.HideTabs
import com.wmods.wppenhacer.xposed.features.customization.IGStatus
import com.wmods.wppenhacer.xposed.features.customization.SeparateGroup
import com.wmods.wppenhacer.xposed.features.customization.ShowOnline
import com.wmods.wppenhacer.xposed.features.general.AntiRevoke
import com.wmods.wppenhacer.xposed.features.general.CallType
import com.wmods.wppenhacer.xposed.features.general.ChatLimit
import com.wmods.wppenhacer.xposed.features.general.DeleteStatus
import com.wmods.wppenhacer.xposed.features.general.LiteMode
import com.wmods.wppenhacer.xposed.features.general.NewChat
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.features.general.PinnedLimit
import com.wmods.wppenhacer.xposed.features.general.RecoverDeleteForMe
import com.wmods.wppenhacer.xposed.features.general.SeenTick
import com.wmods.wppenhacer.xposed.features.general.ShareLimit
import com.wmods.wppenhacer.xposed.features.general.ShowEditMessage
import com.wmods.wppenhacer.xposed.features.general.Tasker
import com.wmods.wppenhacer.xposed.features.listeners.ContactItemListener
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.features.media.CallRecording
import com.wmods.wppenhacer.xposed.features.media.DownloadProfile
import com.wmods.wppenhacer.xposed.features.media.DownloadViewOnce
import com.wmods.wppenhacer.xposed.features.media.MediaPreview
import com.wmods.wppenhacer.xposed.features.media.MediaQuality
import com.wmods.wppenhacer.xposed.features.media.StatusDownload
import com.wmods.wppenhacer.xposed.features.others.ActivityController
import com.wmods.wppenhacer.xposed.features.others.AudioTranscript
import com.wmods.wppenhacer.xposed.features.others.BackupRestore
import com.wmods.wppenhacer.xposed.features.others.Channels
import com.wmods.wppenhacer.xposed.features.others.ChatFilters
import com.wmods.wppenhacer.xposed.features.others.CopyStatus
import com.wmods.wppenhacer.xposed.features.others.DebugFeature
import com.wmods.wppenhacer.xposed.features.others.GoogleTranslate
import com.wmods.wppenhacer.xposed.features.others.GroupAdmin
import com.wmods.wppenhacer.xposed.features.others.JumpFirstMessage
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.features.others.Stickers
import com.wmods.wppenhacer.xposed.features.others.TextStatusComposer
import com.wmods.wppenhacer.xposed.features.others.ToastViewer
import com.wmods.wppenhacer.xposed.features.privacy.AntiWa
import com.wmods.wppenhacer.xposed.features.privacy.CallPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.CustomPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.DndMode
import com.wmods.wppenhacer.xposed.features.privacy.FreezeLastSeen
import com.wmods.wppenhacer.xposed.features.privacy.HideChat
import com.wmods.wppenhacer.xposed.features.privacy.HideSeen
import com.wmods.wppenhacer.xposed.features.privacy.LockedChatsEnhancer
import com.wmods.wppenhacer.xposed.features.privacy.TagMessage
import com.wmods.wppenhacer.xposed.features.privacy.TypingPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.ViewOnce
import com.wmods.wppenhacer.xposed.spoofer.HookBL
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.SELinuxHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.services.BaseService
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FeatureLoader {

    companion object {
        @JvmField
        var mApp: Application? = null

        const val PACKAGE_WPP = "com.whatsapp"
        const val PACKAGE_BUSINESS = "com.whatsapp.w4b"

        private val list = ArrayList<ErrorItem>()
        private var supportedVersions: List<String>? = null
        private var currentVersion: String? = null

        @JvmStatic
        fun start(loader: ClassLoader, pref: XSharedPreferences, sourceDir: String) {
            if (!Unobfuscator.initWithPath(sourceDir)) {
                XposedBridge.log("Can't init dexkit")
                return
            }

            Feature.DEBUG = pref.getBoolean("enablelogs", true)
            Utils.xprefs = pref

            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        mApp = param.args[0] as Application
                        val application = mApp!!

                        if (pref.getBoolean("bootloader_spoofer", false)) {
                            HookBL.hook(loader, pref)
                            XposedBridge.log("Bootloader Spoofer is Injected")
                        }

                        val packageManager = application.packageManager
                        @Suppress("DEPRECATION")
                        pref.registerOnSharedPreferenceChangeListener { _, _ -> pref.reload() }

                        val packageInfo = packageManager.getPackageInfo(application.packageName, 0)
                        XposedBridge.log(packageInfo.versionName)
                        currentVersion = packageInfo.versionName

                        val resIdArray = if (application.packageName == PACKAGE_WPP)
                            R.array.supported_versions_wpp
                        else
                            R.array.supported_versions_business

                        supportedVersions =
                            application.resources.getStringArray(resIdArray).toList()
                        application.registerActivityLifecycleCallbacks(WaCallback())
                        registerReceivers()

                        try {
                            val timeMillis = System.currentTimeMillis()
                            UnobfuscatorCache.init(application)
                            SharedPreferencesWrapper.hookInit(application.classLoader)
                            ReflectionUtils.initCache(application)

                            val isSupported = supportedVersions?.any { s ->
                                packageInfo.versionName?.startsWith(s.replace(".xx", "")) ?: false
                            } ?: false

                            if (!isSupported) {
                                disableExpirationVersion(application.classLoader)
                                if (!pref.getBoolean("bypass_version_check", false)) {
                                    val errorMsg = """
                                        Unsupported version: ${packageInfo.versionName}
                                        Only the function of ignoring the expiration of the WhatsApp version has been applied!
                                    """.trimIndent()
                                    throw Exception(errorMsg)
                                }
                            }

                            initComponents(loader, pref)
                            plugins(loader, pref, packageInfo.versionName!!)
                            sendEnabledBroadcast(application)

                            val totalTime = System.currentTimeMillis() - timeMillis
                            XposedBridge.log("Loaded Hooks in ${totalTime}ms")

                        } catch (e: Throwable) {
                            XposedBridge.log(e)
                            val error = ErrorItem().apply {
                                pluginName = "MainFeatures[Critical]"
                                whatsAppVersion = packageInfo.versionName
                                moduleVersion = BuildConfig.VERSION_NAME
                                message = e.message
                                errorDetail = e.stackTrace
                                    .filter { s ->
                                        !s.className.startsWith("android") && !s.className.startsWith(
                                            "com.android"
                                        )
                                    }
                                    .joinToString(prefix = "[", postfix = "]")
                            }
                            list.add(error)
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                WppCore.getHomeActivityClass(loader), "onCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (list.isNotEmpty()) {
                            val activity = param.thisObject as Activity
                            val msg = list.joinToString("\n") { "${it.pluginName} - ${it.message}" }

                            AlertDialogWpp(activity)
                                .setTitle(activity.getString(R.string.error_detected))
                                .setMessage(
                                    "${activity.getString(R.string.version_error)}$msg\n\nCurrent Version: $currentVersion\nSupported Versions:\n${
                                        supportedVersions?.joinToString(
                                            "\n"
                                        )
                                    }"
                                )
                                .setPositiveButton(activity.getString(R.string.copy_to_clipboard)) { dialog, _ ->
                                    val clipboard =
                                        mApp?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(
                                        "text",
                                        list.joinToString("\n") { it.toString() })
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(
                                        mApp,
                                        R.string.copied_to_clipboard,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    }
                })
        }


        @JvmStatic
        @Throws(Exception::class)
        fun disableExpirationVersion(classLoader: ClassLoader) {
            val expirationClass = Unobfuscator.loadExpirationClass(classLoader)
            val method =
                ReflectionUtils.findMethodUsingFilter(expirationClass) { m -> m.returnType == Date::class.java }
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val calendar = Calendar.getInstance().apply {
                        set(2099, 11, 31)
                    }
                    param.result = calendar.time
                }
            })
        }

        @Throws(Exception::class)
        private fun initComponents(loader: ClassLoader, pref: XSharedPreferences) {
            FMessageWpp.initialize(loader)
            FStatusWpp.initialize(loader)
            ProtocolTreeNodeWpp.initialize(loader)
            AlertDialogWpp.initDialog(loader)
            WaContactWpp.initialize(loader)
            WppCore.Initialize(loader, pref)
            DesignUtils.setPrefs(pref)
            Utils.init(loader)

            WppCore.addListenerActivity(object : WppCore.ActivityChangeState {
                override fun onChange(
                    activity: Activity,
                    type: WppCore.ActivityChangeState.ChangeType
                ) {
                    if (type == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                        checkUpdate(activity)
                    }

                    if (type == WppCore.ActivityChangeState.ChangeType.CREATED && activity.javaClass.simpleName == "HomeActivity") {
                        checkPrefsLoad(pref, activity)
                    }

                    if (App.isOriginalPackage() && pref.getBoolean("update_check", true)) {
                        if (activity.javaClass.simpleName == "HomeActivity" && type == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                            activity.window.decorView.postDelayed({
                                CompletableFuture.runAsync(UpdateChecker(activity))
                            }, 2000)
                        }
                    }
                }

                private fun checkPrefsLoad(prefs: XSharedPreferences, activity: Activity) {
                    val fileService = SELinuxHelper.getAppDataFileService()
                    if (fileService.checkFileExists(prefs.file.absolutePath) &&
                        !fileService.checkFileAccess(prefs.file.absolutePath, BaseService.R_OK)
                    ) {
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "[ERROR-PREFS]Unable to read WAE preferences. Contact the Developer",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }


            })

        }

        private fun checkUpdate(activity: Activity) {
            if (WppCore.getPrivBoolean("need_restart", false)) {
                WppCore.setPrivBoolean("need_restart", false)
                try {
                    AlertDialogWpp(activity)
                        .setMessage(activity.getString(R.string.restart_wpp))
                        .setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                            if (!Utils.doRestart(activity)) {
                                Toast.makeText(
                                    activity,
                                    "Unable to rebooting activity",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .setNegativeButton(activity.getString(R.string.no), null)
                        .show()
                } catch (ignored: Throwable) {
                }
            }
        }

        @SuppressLint("WrongConstant")
        private fun registerReceivers() {
            val app = mApp ?: return

            // Reboot receiver
            val restartReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (context.packageName == intent.getStringExtra("PKG")) {
                        val appName =
                            context.packageManager.getApplicationLabel(context.applicationInfo)
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.rebooting)} $appName...",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (!Utils.doRestart(context)) {
                            Toast.makeText(
                                context,
                                "Unable to rebooting $appName",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                app, restartReceiver,
                IntentFilter("${BuildConfig.APPLICATION_ID}.WHATSAPP.RESTART"),
                ContextCompat.RECEIVER_EXPORTED
            )

            // Wpp receiver
            val wppReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    sendEnabledBroadcast(context)
                }
            }
            ContextCompat.registerReceiver(
                app, wppReceiver,
                IntentFilter("${BuildConfig.APPLICATION_ID}.CHECK_WPP"),
                ContextCompat.RECEIVER_EXPORTED
            )

            // Dialog receiver restart
            val restartManualReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    WppCore.setPrivBoolean("need_restart", true)
                }
            }
            ContextCompat.registerReceiver(
                app, restartManualReceiver,
                IntentFilter("${BuildConfig.APPLICATION_ID}.MANUAL_RESTART"),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        private fun sendEnabledBroadcast(context: Context) {
            try {
                val wppIntent = Intent("${BuildConfig.APPLICATION_ID}.RECEIVER_WPP").apply {
                    putExtra(
                        "VERSION",
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    )
                    putExtra("PKG", context.packageName)
                    setPackage(BuildConfig.APPLICATION_ID)
                }
                context.sendBroadcast(wppIntent)
            } catch (ignored: Exception) {
            }
        }

        @Throws(Exception::class)
        private fun plugins(loader: ClassLoader, pref: XSharedPreferences, versionWpp: String) {
            val classes = arrayOf(
                DebugFeature::class.java,
                ContactItemListener::class.java,
                ConversationItemListener::class.java,
                MenuStatusListener::class.java,
                ShowEditMessage::class.java,
                AntiRevoke::class.java,
                CustomToolbar::class.java,
                CustomView::class.java,
                SeenTick::class.java,
                BubbleColors::class.java,
                CallPrivacy::class.java,
                ActivityController::class.java,
                CustomThemeV2::class.java,
                ChatLimit::class.java,
                SeparateGroup::class.java,
                ShowOnline::class.java,
                DndMode::class.java,
                FreezeLastSeen::class.java,
                TypingPrivacy::class.java,
                HideChat::class.java,
                HideSeen::class.java,
                HideSeenView::class.java,
                TagMessage::class.java,
                HideTabs::class.java,
                IGStatus::class.java,
                LiteMode::class.java,
                MediaQuality::class.java,
                NewChat::class.java,
                Others::class.java,
                PinnedLimit::class.java,
                CustomTime::class.java,
                ShareLimit::class.java,
                StatusDownload::class.java,
                ViewOnce::class.java,
                CallType::class.java,
                MediaPreview::class.java,
                FilterGroups::class.java,
                Tasker::class.java,
                DeleteStatus::class.java,
                DownloadViewOnce::class.java,
                Channels::class.java,
                DownloadProfile::class.java,
                ChatFilters::class.java,
                GroupAdmin::class.java,
                Stickers::class.java,
                CopyStatus::class.java,
                TextStatusComposer::class.java,
                ToastViewer::class.java,
                MenuHome::class.java,
                AntiWa::class.java,
                CustomPrivacy::class.java,
                AudioTranscript::class.java,
                GoogleTranslate::class.java,
                ContactBlockedVerify::class.java,
                LockedChatsEnhancer::class.java,
                CallRecording::class.java,
                BackupRestore::class.java,
                RecoverDeleteForMe::class.java,
                JumpFirstMessage::class.java
            )

            XposedBridge.log("Loading Plugins")
            val executorService = Executors.newWorkStealingPool(
                Runtime.getRuntime().availableProcessors().coerceAtMost(4)
            )
            val times = Collections.synchronizedList(ArrayList<String>())

            for (clazz in classes) {
                CompletableFuture.runAsync({
                    val startTime = System.currentTimeMillis()
                    try {
                        val constructor = clazz.getConstructor(
                            ClassLoader::class.java,
                            XSharedPreferences::class.java
                        )
                        val plugin = constructor.newInstance(loader, pref) as Feature
                        plugin.doHook()
                    } catch (e: Throwable) {
                        XposedBridge.log(e)
                        val error = ErrorItem().apply {
                            pluginName = clazz.simpleName
                            whatsAppVersion = versionWpp
                            moduleVersion = BuildConfig.VERSION_NAME
                            message = e.message
                            errorDetail = e.stackTrace
                                .filter { s ->
                                    !s.className.startsWith("android") && !s.className.startsWith(
                                        "com.android"
                                    )
                                }
                                .joinToString(prefix = "[", postfix = "]")
                        }
                        list.add(error)
                    }
                    val duration = System.currentTimeMillis() - startTime
                    times.add("* Loaded Plugin ${clazz.simpleName} in ${duration}ms")
                }, executorService)
            }

            executorService.shutdown()
            executorService.awaitTermination(15, TimeUnit.SECONDS)

            if (Feature.DEBUG) {
                times.forEach { XposedBridge.log(it) }
            }
        }
    }

    private class ErrorItem {
        var pluginName: String? = null
        var whatsAppVersion: String? = null
        var errorDetail: String? = null
        var moduleVersion: String? = null
        var message: String? = null

        override fun toString(): String {
            return """
                pluginName='$pluginName'
                moduleVersion='$moduleVersion'
                whatsAppVersion='$whatsAppVersion'
                Message=$message
                error='$errorDetail'
            """.trimIndent()
        }
    }
}