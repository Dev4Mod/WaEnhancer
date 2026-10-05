package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.crossbowffs.remotepreferences.RemotePreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
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
import com.wmods.wppenhacer.xposed.spoofer.HookBL
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object FeatureLoader : YukiBaseHooker() {

    const val PACKAGE_WPP = "com.whatsapp"
    const val PACKAGE_BUSINESS = "com.whatsapp.w4b"

    private const val UPDATE_CHECK_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    private const val HOME_ACTIVITY = "HomeActivity"
    private const val PLUGIN_LOAD_TIMEOUT_SECONDS = 15L

    @JvmField
    var mApp: Application? = null

    lateinit var moduleContext: Context

    private val errors = Collections.synchronizedList(ArrayList<LoadError>())
    private var supportedVersions: List<String> = emptyList()
    private var currentVersion: String? = null
    private var lastUpdateCheckScheduledAt = 0L

    override fun onHook() {}

    /**
     * Called from [com.wmods.wppenhacer.WppXposed] right before the host Application's onCreate.
     */
    @JvmStatic
    fun start(loader: ClassLoader, application: Application, sourceDir: String) {
        val versionName = application.packageManager
            .getPackageInfo(application.packageName, 0).versionName
        try {
            load(loader, application, sourceDir, versionName.orEmpty())
        } catch (e: Throwable) {
            YukiLog.log(e)
            errors.add(LoadError.from("MainFeatures[Critical]", versionName, e))
        }
        hookErrorDialog()
    }

    private fun load(
        loader: ClassLoader,
        application: Application,
        sourceDir: String,
        versionName: String
    ) {
        check(Unobfuscator.initWithPath(sourceDir)) { "Unobfuscator not initialized" }
        initializeModuleContext(application)
        Utils.appClassLoader = loader
        mApp = application

        val pref = createPreferences(application)
        Feature.DEBUG = pref.getBoolean("enablelogs", true)
        Utils.xprefs = pref

        if (pref.getBoolean("bootloader_spoofer", false)) {
            HookBL.hook(this, loader, pref)
            YukiLog.log("Bootloader Spoofer is Injected")
        }

        YukiLog.log(versionName)
        currentVersion = versionName
        CrashHandler.install(application, versionName)
        supportedVersions = application.resources.getStringArray(supportedVersionsRes(application)).toList()
        application.registerActivityLifecycleCallbacks(WaCallback())
        ModuleReceivers.register(application)

        val startTime = System.currentTimeMillis()
        UnobfuscatorCache.init(application)
        SharedPreferencesWrapper.hookInit(this, application.classLoader)
        ReflectionUtils.initCache(application)

        checkVersionSupport(application, pref, versionName)

        initComponents(loader, pref)
        loadFeatures(loader, pref, versionName)
        ModuleReceivers.sendEnabledBroadcast(application)

        YukiLog.log("Loaded Hooks in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun createPreferences(application: Application): SharedPreferences =
        RemotePreferences(
            application,
            BuildConfig.APPLICATION_ID + ".preferences",
            BuildConfig.APPLICATION_ID + "_preferences"
        )

    private fun supportedVersionsRes(application: Application) =
        if (application.packageName == PACKAGE_WPP) {
            R.array.supported_versions_wpp
        } else {
            R.array.supported_versions_business
        }

    private fun initializeModuleContext(application: Application) {
        try {
            val context = application.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            moduleContext = android.view.ContextThemeWrapper(context, R.style.AppTheme)
        } catch (_: PackageManager.NameNotFoundException) {
            throw PackageManager.NameNotFoundException(
                Utils.application.getString(R.string.alert_module_notfound)
            )
        }
    }

    private fun checkVersionSupport(
        application: Application,
        pref: SharedPreferences,
        versionName: String
    ) {
        val isSupported = supportedVersions.any { versionName.startsWith(it.replace(".xx", "")) }
        if (isSupported) return

        disableExpirationVersion(application.classLoader)
        if (!pref.getBoolean("bypass_version_check", false)) {
            throw Exception(
                """
                Unsupported version: $versionName
                Only the function of ignoring the expiration of the WhatsApp version has been applied!
                """.trimIndent()
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun disableExpirationVersion(classLoader: ClassLoader) {
        val expirationClass = Unobfuscator.loadExpirationClass(classLoader)
        val methods = ReflectionUtils.findAllMethodsUsingFilter(expirationClass) { m ->
            m.returnType == Date::class.java
        }
        for (method in methods) {
            method.hook {
                before {
                    result = Calendar.getInstance().apply { set(2099, 11, 31) }.time
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun initComponents(loader: ClassLoader, pref: SharedPreferences) {
        FMessageWpp.initialize(loader)
        FStatusWpp.initialize(this, loader)
        ProtocolTreeNodeWpp.initialize(loader)
        AlertDialogWpp.initDialog(loader)
        WaContactWpp.initialize(this, loader)
        WppCore.initialize(this, loader, pref)
        DesignUtils.setPrefs(pref)
        Utils.init()

        WppCore.addListenerActivity { activity, type ->
            if (type != WppCore.ActivityChangeState.ChangeType.RESUMED) return@addListenerActivity
            askRestartIfNeeded(activity)
            if (App.isOriginalPackage && pref.getBoolean("update_check", true)) {
                scheduleUpdateCheck(activity)
            }
        }
    }

    private fun askRestartIfNeeded(activity: Activity) {
        if (!WppCore.getPrivBoolean("need_restart", false)) return
        WppCore.setPrivBoolean("need_restart", false)
        try {
            AlertDialogWpp(activity)
                .setMessage(activity.getString(R.string.restart_wpp))
                .setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                    if (!Utils.doRestart(activity)) {
                        Toast.makeText(
                            activity, "Unable to rebooting activity", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(activity.getString(R.string.no), null)
                .show()
        } catch (_: Throwable) {
        }
    }

    private fun scheduleUpdateCheck(activity: Activity) {
        if (activity.javaClass.simpleName != HOME_ACTIVITY) return

        val now = System.currentTimeMillis()
        val shouldSchedule = synchronized(this) {
            val due = now - lastUpdateCheckScheduledAt >= UPDATE_CHECK_COOLDOWN_MS
            if (due) lastUpdateCheckScheduledAt = now
            due
        }
        if (shouldSchedule) {
            activity.window.decorView.postDelayed({
                CompletableFuture.runAsync(UpdateChecker(activity))
            }, 2000)
        }
    }

    @Throws(Exception::class)
    private fun loadFeatures(loader: ClassLoader, pref: SharedPreferences, versionWpp: String) {
        YukiLog.log("Loading Plugins")
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "WAE-HookInstaller").apply { isDaemon = true }
        }
        val timings = Collections.synchronizedList(ArrayList<String>())

        for (clazz in FeatureRegistry.features) {
            CompletableFuture.runAsync({
                val startTime = System.currentTimeMillis()
                try {
                    val plugin = clazz
                        .getConstructor(ClassLoader::class.java, SharedPreferences::class.java)
                        .newInstance(loader, pref)
                    loadHooker(plugin)
                    plugin.doHook()
                } catch (e: Throwable) {
                    YukiLog.log(e)
                    errors.add(LoadError.from(clazz.simpleName, versionWpp, e))
                }
                val duration = System.currentTimeMillis() - startTime
                timings.add("* Loaded Plugin ${clazz.simpleName} in ${duration}ms")
            }, executor)
        }

        executor.shutdown()
        executor.awaitTermination(PLUGIN_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (Feature.DEBUG) {
            synchronized(timings) { timings.toList() }.forEach { YukiLog.log(it) }
        }
    }

    private fun hookErrorDialog() {
        Activity::class.java.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                if (instance.javaClass.simpleName != HOME_ACTIVITY) return@after
                val loadErrors = synchronized(errors) { errors.toList() }
                if (loadErrors.isNotEmpty()) showErrorDialog(instance as Activity, loadErrors)
            }
        }
    }

    private fun showErrorDialog(activity: Activity, loadErrors: List<LoadError>) {
        val message = buildString {
            append(activity.getString(R.string.version_error))
            append(loadErrors.joinToString("\n") { it.summary })
            append("\n\nCurrent Version: $currentVersion")
            append("\nSupported Versions:\n${supportedVersions.joinToString("\n")}")
        }
        AlertDialogWpp(activity)
            .setTitle(activity.getString(R.string.error_detected))
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.copy_to_clipboard)) { dialog, _ ->
                val clipboard =
                    activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("text", loadErrors.joinToString("\n"))
                )
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
}
