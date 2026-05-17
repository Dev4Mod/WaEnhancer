package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.graphics.drawable.Drawable
import android.os.Environment
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.edit
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.views.dialog.BottomDialogWpp
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import com.wmods.wppenhacer.xposed.bridge.client.BaseClient
import com.wmods.wppenhacer.xposed.bridge.client.BridgeClientKt
import com.wmods.wppenhacer.xposed.bridge.client.ProviderClientKt
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("StaticFieldLeak")
object WppCore {


    @JvmStatic
    val listenerActivity = ConcurrentHashMap.newKeySet<ActivityChangeState>()

    @JvmField
    internal var mCurrentActivity: Activity? = null

    private var mGenJidMethod: Method? = null
    private var bottomDialog: Class<*>? = null
    private lateinit var privPrefs: SharedPreferences
    private var mStartUpConfig: Any? = null
    private var mActionUser: Any? = null
    private var mWaDatabase: SQLiteDatabase? = null

    @JvmField
    var client: BaseClient? = null

    private var mCachedMessageStore: Any? = null
    private var convertLidToJid: Method? = null
    private var mWaJidMapRepository: Any? = null
    private var convertJidToLid: Method? = null
    private var actionUser: Class<*>? = null
    private var cachedMessageStoreKey: Method? = null
    private var conversationJidField: Field? = null
    private var meManagerPhoneJidField: Field? = null
    private var meManagerInstance: Any? = null
    private var mConversationDelegate: Any? = null
    private var statusToMessageMethod: Method? = null
    private var statusToMessageMapper: Any? = null

    @JvmStatic
    @Throws(Exception::class)
    fun Initialize(loader: ClassLoader, pref: XSharedPreferences) {
        privPrefs = Utils.getApplication().getSharedPreferences("WaGlobal", Context.MODE_PRIVATE)

        // init UserJID
        val companionField = FMessageWpp.UserJid.TYPE_JID.getDeclaredField("Companion")
        mGenJidMethod = ReflectionUtils.findMethodUsingFilter(companionField.type) { m ->
            m.parameterCount == 1 && String::class.java == m.parameterTypes[0] && FMessageWpp.UserJid.TYPE_JID == m.returnType
        }

        // Bottom Dialog
        bottomDialog = Unobfuscator.loadDialogViewClass(loader)

        conversationJidField = Unobfuscator.loadUserJidConversationDelegate(loader)
        XposedBridge.hookAllConstructors(
            conversationJidField?.declaringClass,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    mConversationDelegate = param.thisObject
                }
            })

        // StartUpPrefs
        val startPrefsConfig = Unobfuscator.loadStartPrefsConfig(loader)
        XposedBridge.hookMethod(startPrefsConfig, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                mStartUpConfig = param.thisObject
            }
        })

        // ActionUser
        actionUser = Unobfuscator.loadActionUser(loader)
        XposedBridge.log("ActionUser: ${actionUser?.name}")
        XposedBridge.hookAllConstructors(actionUser, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                mActionUser = param.thisObject
            }
        })

        // CachedMessageStore
        cachedMessageStoreKey = Unobfuscator.loadCachedMessageStoreKey(loader)
        XposedBridge.hookAllConstructors(
            cachedMessageStoreKey?.declaringClass,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    mCachedMessageStore = param.thisObject
                }
            })

        // WaJidMap
        convertLidToJid = Unobfuscator.loadConvertLidToJid(loader)
        convertJidToLid = Unobfuscator.loadConvertJidToLid(loader)
        XposedBridge.hookAllConstructors(convertLidToJid?.declaringClass, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                mWaJidMapRepository = param.thisObject
            }
        })

        // load me current PhoneJid
        val meManagerClass = Unobfuscator.loadMeManagerClass(loader)
        meManagerPhoneJidField =
            ReflectionUtils.getFieldByType(meManagerClass, FMessageWpp.UserJid.TYPE_PHONEUSERJID)
        XposedBridge.hookAllConstructors(meManagerClass, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                meManagerInstance = param.thisObject
            }
        })

        // Load wa database
        loadWADatabase()
        hookStatusToMessageMapper(loader)

        if (!pref.getBoolean("lite_mode", false)) {
            initBridge(Utils.getApplication())
        }
    }

    private fun hookStatusToMessageMapper(loader: ClassLoader) {
        statusToMessageMethod = Unobfuscator.loadFStatusToFMessage(loader)
        XposedBridge.hookAllConstructors(
            statusToMessageMethod?.declaringClass,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    statusToMessageMapper = param.thisObject
                }
            })
    }

    @JvmStatic
    fun getPhoneJidFromUserJid(lid: Any?): Any? {
        if (lid == null) return null
        try {
            var rawString = XposedHelpers.callMethod(lid, "getRawString") as? String
            if (rawString == null || !rawString.contains("@lid")) return lid
            rawString = rawString.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
            val newUser = createUserJid(rawString)
            val result = ReflectionUtils.callMethod(convertLidToJid, mWaJidMapRepository, newUser)
            return result ?: lid
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return lid
    }

    @JvmStatic
    fun getUserJidFromPhoneJid(userJid: Any?): Any? {
        if (userJid == null) return null
        try {
            var rawString = XposedHelpers.callMethod(userJid, "getRawString") as? String
            if (rawString == null || rawString.contains("@lid")) return userJid
            rawString = rawString.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
            val newUser = createUserJid(rawString)
            val result = ReflectionUtils.callMethod(convertJidToLid, mWaJidMapRepository, newUser)
            return result ?: userJid
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return userJid
    }

    @JvmStatic
    @Throws(Exception::class)
    fun initBridge(context: Context) {
        val prefsCacheHooks = UnobfuscatorCache.getInstance().sPrefsCacheHooks
        var preferredOrder = prefsCacheHooks.getInt(
            "preferredOrder",
            1
        ) // 0 for ProviderClient first, 1 for BridgeClient first

        var connected = false
        if (preferredOrder == 0) {
            if (tryConnectBridge(ProviderClientKt(context))) {
                connected = true
            } else if (tryConnectBridge(BridgeClientKt(context))) {
                connected = true
                preferredOrder = 1 // Update preference to BridgeClient first
            }
        } else {
            if (tryConnectBridge(BridgeClientKt(context))) {
                connected = true
            } else if (tryConnectBridge(ProviderClientKt(context))) {
                connected = true
                preferredOrder = 0 // Update preference to ProviderClient first
            }
        }

        if (!connected) {
            throw Exception(context.getString(R.string.bridge_error))
        }

        // Update the preferred order if it changed
        prefsCacheHooks.edit { putInt("preferredOrder", preferredOrder) }
    }

    @JvmStatic
    @Throws(Exception::class)
    private fun tryConnectBridge(baseClient: BaseClient): Boolean {
        try {
            XposedBridge.log("Trying to connect to ${baseClient.javaClass.simpleName}")
            client = baseClient
            val canLoadFuture: CompletableFuture<Boolean> = baseClient.connect()
            val canLoad = canLoadFuture.get()
            if (!canLoad) throw Exception()
        } catch (_: Exception) {
            return false
        }
        return true
    }

    @JvmStatic
    fun sendMessage(number: String, message: String) {
        try {
            val senderMethod = ReflectionUtils.findMethodUsingFilterIfExists(actionUser) { method ->
                List::class.java.isAssignableFrom(method.returnType) &&
                        ReflectionUtils.findIndexOfType(
                            method.parameterTypes,
                            String::class.java
                        ) != -1
            }
            if (senderMethod != null) {
                val userJid = createUserJid("$number@s.whatsapp.net")
                if (userJid == null) {
                    Utils.showToast("UserJID not found", Toast.LENGTH_SHORT)
                    return
                }
                val newObject = arrayOfNulls<Any>(senderMethod.parameterCount)
                for (i in newObject.indices) {
                    val param = senderMethod.parameterTypes[i]
                    newObject[i] = ReflectionUtils.getDefaultValue(param)
                }
                val index =
                    ReflectionUtils.findIndexOfType(senderMethod.parameterTypes, String::class.java)
                newObject[index] = message
                val index2 =
                    ReflectionUtils.findIndexOfType(senderMethod.parameterTypes, List::class.java)
                newObject[index2] = Collections.singletonList(userJid)
                senderMethod.invoke(getActionUser(), *newObject)
                Utils.showToast("Message sent to $number", Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Utils.showToast("Error in sending message:${e.message}", Toast.LENGTH_SHORT)
            XposedBridge.log(e)
        }
    }

    @JvmStatic
    fun sendReaction(s: String, objMessage: Any?) {
        try {
            val senderMethod = ReflectionUtils.findMethodUsingFilter(actionUser) { method ->
                method.parameterCount == 3 && Arrays.equals(
                    method.parameterTypes,
                    arrayOf(FMessageWpp.TYPE, String::class.java, Boolean::class.javaPrimitiveType)
                )
            }
            senderMethod.invoke(getActionUser(), objMessage, s, !TextUtils.isEmpty(s))
        } catch (e: Exception) {
            Utils.showToast("Error in sending reaction:${e.message}", Toast.LENGTH_SHORT)
            XposedBridge.log(e)
        }
    }

    @JvmStatic
    fun getActionUser(): Any? {
        try {
            if (mActionUser == null) {
                mActionUser = actionUser?.constructors?.get(0)?.newInstance()
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return mActionUser
    }

    @JvmStatic
    fun loadWADatabase() {
        if (mWaDatabase != null) return
        val dataDir = Utils.getApplication().filesDir.parentFile
        val database = File(dataDir, "databases/wa.db")
        if (database.exists()) {
            mWaDatabase = SQLiteDatabase.openDatabase(
                database.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        }
    }

    @JvmStatic
    fun getCurrentActivity(): Activity? {
        return mCurrentActivity
    }

    @JvmStatic
    @Synchronized
    fun getHomeActivityClass(loader: ClassLoader): Class<*> {
        val oldHomeClass = XposedHelpers.findClassIfExists("com.whatsapp.HomeActivity", loader)
        return oldHomeClass ?: XposedHelpers.findClass("com.whatsapp.home.ui.HomeActivity", loader)
    }

    @JvmStatic
    @Synchronized
    fun getTabsPagerClass(loader: ClassLoader): Class<*> {
        val oldHomeClass = XposedHelpers.findClassIfExists("com.whatsapp.TabsPager", loader)
        return oldHomeClass ?: XposedHelpers.findClass("com.whatsapp.home.ui.TabsPager", loader)
    }

    @JvmStatic
    @Synchronized
    fun getViewOnceViewerActivityClass(loader: ClassLoader): Class<*> {
        val oldClass =
            XposedHelpers.findClassIfExists("com.whatsapp.messaging.ViewOnceViewerActivity", loader)
        return oldClass ?: XposedHelpers.findClass(
            "com.whatsapp.viewonce.ui.messaging.ViewOnceViewerActivity",
            loader
        )
    }

    @JvmStatic
    @Synchronized
    fun getAboutActivityClass(loader: ClassLoader): Class<*> {
        val oldClass = XposedHelpers.findClassIfExists("com.whatsapp.settings.About", loader)
        return oldClass ?: XposedHelpers.findClass("com.whatsapp.settings.ui.About", loader)
    }

    @JvmStatic
    @Synchronized
    fun getDataUsageActivityClass(loader: ClassLoader): Class<*> {
        val oldClass = XposedHelpers.findClassIfExists(
            "com.whatsapp.settings.SettingsDataUsageActivity",
            loader
        )
        return oldClass
            ?: XposedHelpers.findClass("com.whatsapp.settings.ui.SettingsDataUsageActivity", loader)
    }

    @JvmStatic
    @Synchronized
    @Throws(Exception::class)
    fun getTextStatusComposerFragmentClass(loader: ClassLoader): Class<*> {
        val classes = arrayOf(
            "com.whatsapp.status.composer.TextStatusComposerFragment",
            "com.whatsapp.statuscomposer.composer.TextStatusComposerFragment"
        )
        for (clazz in classes) {
            val result = XposedHelpers.findClassIfExists(clazz, loader)
            if (result != null) return result
        }
        throw Exception("TextStatusComposerFragmentClass not found")
    }

    @JvmStatic
    @Synchronized
    @Throws(Exception::class)
    fun getVoipManagerClass(loader: ClassLoader): Class<*> {
        val classes = arrayOf(
            "com.whatsapp.voipcalling.Voip",
            "com.whatsapp.calling.voipcalling.Voip"
        )
        for (clazz in classes) {
            val result = XposedHelpers.findClassIfExists(clazz, loader)
            if (result != null) return result
        }
        throw Exception("VoipManagerClass not found")
    }

    @JvmStatic
    @Synchronized
    @Throws(Exception::class)
    fun getVoipCallInfoClass(loader: ClassLoader): Class<*> {
        val classes = arrayOf(
            "com.whatsapp.voipcalling.CallInfo",
            "com.whatsapp.calling.infra.voipcalling.CallInfo"
        )
        for (clazz in classes) {
            val result = XposedHelpers.findClassIfExists(clazz, loader)
            if (result != null) return result
        }
        throw Exception("VoipCallInfoClass not found")
    }

    @JvmStatic
    fun getDefaultTheme(): Int {
        if (mStartUpConfig != null) {
            val result =
                ReflectionUtils.findMethodUsingFilterIfExists(mStartUpConfig!!.javaClass) { method ->
                    method.parameterCount == 0 && method.returnType == Int::class.javaPrimitiveType
                }
            if (result != null) {
                val value = ReflectionUtils.callMethod(result, mStartUpConfig)
                if (value != null) return value as Int
            }
        }
        val startupPrefs =
            Utils.getApplication().getSharedPreferences("startup_prefs", Context.MODE_PRIVATE)
        return startupPrefs.getInt("night_mode", 0)
    }

    @JvmStatic
    fun getContactName(userJid: FMessageWpp.UserJid): String {
        loadWADatabase()
        if (mWaDatabase == null || userJid.isNull) return "Whatsapp Contact"
        val name = getSContactName(userJid, false)
        if (!TextUtils.isEmpty(name)) return name
        return getWppContactName(userJid)
    }

    @JvmStatic
    fun getSContactName(userJid: FMessageWpp.UserJid?, saveOnly: Boolean): String {
        loadWADatabase()
        if (mWaDatabase == null || userJid == null) return ""
        val selection = if (saveOnly) "jid = ? AND raw_contact_id > 0" else "jid = ?"
        var name: String? = null
        val rawJid = userJid.phoneRawString
        val cursor = mWaDatabase?.query(
            "wa_contacts", arrayOf("display_name"), selection,
            arrayOf(rawJid), null, null, null
        )
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0)
            cursor.close()
        }
        return name ?: ""
    }

    @JvmStatic
    fun getWppContactName(userJid: FMessageWpp.UserJid): String {
        loadWADatabase()
        if (mWaDatabase == null || userJid.isNull) return ""
        var name: String? = null
        val rawJid = userJid.phoneRawString
        val cursor2 = mWaDatabase?.query(
            "wa_vnames", arrayOf("verified_name"), "jid = ?",
            arrayOf(rawJid), null, null, null
        )
        if (cursor2 != null && cursor2.moveToFirst()) {
            name = cursor2.getString(0)
            cursor2.close()
        }
        return name ?: ""
    }

    @JvmStatic
    fun getFMessageFromKey(messageKey: Any?): Any? {
        if (messageKey == null) return null
        return try {
            if (mCachedMessageStore == null) {
                XposedBridge.log("CachedMessageStore is null")
                return null
            }
            cachedMessageStoreKey?.invoke(mCachedMessageStore, messageKey)
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }

    @JvmStatic
    fun createUserJid(rawjid: String?): Any? {
        if (rawjid == null) return null
        return try {
            mGenJidMethod?.invoke(null, rawjid)
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }

    @JvmStatic
    fun getCurrentUserJid(): FMessageWpp.UserJid? {
        return try {
            val conversation = getCurrentConversation() ?: return null
            var conversationDelegate = mConversationDelegate
            if (conversation.javaClass.simpleName == "HomeActivity") {
                val convFragmentMethod =
                    Unobfuscator.loadHomeConversationFragmentMethod(conversation.classLoader)
                val convFragment = convFragmentMethod.invoke(null, conversation)
                val convField =
                    Unobfuscator.loadAntiRevokeConvFragmentField(conversation.classLoader)
                conversationDelegate = convField.get(convFragment)
            }
            FMessageWpp.UserJid(conversationJidField?.get(conversationDelegate))
        } catch (_: Exception) {
            FMessageWpp.UserJid()
        }
    }

    @JvmStatic
    fun stripJID(str: String?): String? {
        try {
            if (str == null) return null
            if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                return str.substring(0, str.indexOf("."))
            } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast")
                || str.contains("@lid")
            ) {
                return str.substring(0, str.indexOf("@"))
            }
            return str
        } catch (e: Exception) {
            XposedBridge.log(e)
            return str
        }
    }


    @JvmStatic
    fun getMyName(): String {
        val startupPrefs =
            Utils.getApplication().getSharedPreferences("startup_prefs", Context.MODE_PRIVATE)
        return startupPrefs.getString("push_name", "WhatsApp") ?: "WhatsApp"
    }

    @JvmStatic
    fun getMainPrefs(): SharedPreferences {
        return Utils.getApplication().getSharedPreferences(
            "${Utils.getApplication().packageName}_preferences_light", Context.MODE_PRIVATE
        )
    }

    @JvmStatic
    fun getMyBio(): String {
        val mainPrefs = getMainPrefs()
        val currentStatus = mainPrefs.getString("my_current_status", "").orEmpty()
        if (currentStatus.trim().isEmpty()) {
            return mainPrefs.getString("my_current_evolved_about_text", "").orEmpty()
        }
        return currentStatus
    }

    @JvmStatic
    fun getMyPhoto(): Drawable? {
        val datafolder = Utils.getApplication().cacheDir.parentFile
        val file = File(datafolder, "files/me.jpg")
        if (file.exists()) return Drawable.createFromPath(file.absolutePath)
        return null
    }

    @JvmStatic
    fun createBottomDialog(context: Context): BottomDialogWpp {
        return BottomDialogWpp(XposedHelpers.newInstance(bottomDialog, context, 0) as Dialog)
    }

    @JvmStatic
    fun getCurrentConversation(): Activity? {
        if (mCurrentActivity == null) return null
        try {
            val conversation =
                XposedHelpers.findClass("com.whatsapp.Conversation", mCurrentActivity!!.classLoader)
            if (conversation.isInstance(mCurrentActivity)) return mCurrentActivity

            val home = getHomeActivityClass(mCurrentActivity!!.classLoader)
            if (mCurrentActivity!!.resources.configuration.smallestScreenWidthDp >= 600 && home.isInstance(
                    mCurrentActivity
                )
            ) {
                return mCurrentActivity
            }
        } catch (_: Exception) {
        }
        return null
    }

    @SuppressLint("DiscouragedPrivateApi")
    @JvmStatic
    fun getCurrentChatTitle(): String? {
        try {
            val conversation = getCurrentConversation() ?: return null

            try {
                val f = Activity::class.java.getDeclaredField("mTitle")
                f.isAccessible = true
                val titleObj = f.get(conversation)
                if (titleObj is CharSequence) {
                    val title = titleObj.toString()
                    if (!TextUtils.isEmpty(title) && !title.equals("WhatsApp", ignoreCase = true)) {
                        return title
                    }
                }
            } catch (_: Throwable) {
            }

            val activityTitle = conversation.title
            if (!activityTitle.isNullOrEmpty() && !activityTitle.toString()
                    .equals("WhatsApp", ignoreCase = true)
            ) {
                return activityTitle.toString()
            }
        } catch (t: Throwable) {
        }
        return null
    }

    @JvmStatic
    fun getPrivPrefs(): SharedPreferences {
        return privPrefs
    }

    @JvmStatic
    fun setPrivString(key: String, value: String?) {
        privPrefs.edit(commit = true) { putString(key, value) }
    }

    @JvmStatic
    fun getPrivString(key: String, defaultValue: String?): String? {
        return privPrefs.getString(key, defaultValue)
    }

    @JvmStatic
    fun getPrivJSON(key: String, defaultValue: JSONObject?): JSONObject? {
        val jsonStr = privPrefs.getString(key, null) ?: return defaultValue
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            defaultValue
        }
    }

    @JvmStatic
    fun setPrivJSON(key: String, value: JSONObject?) {
        privPrefs.edit(commit = true) { putString(key, value?.toString()) }
    }

    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun removePrivKey(s: String?) {
        if (s != null && privPrefs.contains(s)) {
            privPrefs.edit(commit = true) { remove(s) }
        }
    }

    @SuppressLint("ApplySharedPref")
    @JvmStatic
    fun setPrivBoolean(key: String, value: Boolean) {
        privPrefs.edit(commit = true) { putBoolean(key, value) }
    }

    @JvmStatic
    fun getPrivBoolean(key: String, defaultValue: Boolean): Boolean {
        return privPrefs.getBoolean(key, defaultValue)
    }

    @JvmStatic
    fun addListenerActivity(listener: ActivityChangeState) {
        listenerActivity.add(listener)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getClientBridge(): WaeIIFace? {
        if (!isBridgeConnected()) {
            synchronized(WppCore::class.java) {
                if (!isBridgeConnected()) {
                    if (client == null) {
                        throw Exception("Bridge client not initialized")
                    }
                    client?.tryReconnect()
                    if (!isBridgeConnected()) {
                        throw Exception("Failed connect to Bridge")
                    }
                }
            }
        }
        return client?.service
    }

    @JvmStatic
    private fun isBridgeConnected(): Boolean {
        val currentClient = client ?: return false
        val service = currentClient.service
        return service != null && service.asBinder().isBinderAlive && service.asBinder()
            .pingBinder()
    }

    @JvmStatic
    fun getMyUserJid(): FMessageWpp.UserJid? {
        return try {
            FMessageWpp.UserJid(meManagerPhoneJidField?.get(meManagerInstance))
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }

    @JvmStatic
    fun getRootWhatsAppDir(): File {
        val mediaDirs = Utils.getApplication().externalMediaDirs
        val appName =
            Utils.getApplication().packageManager.getApplicationLabel(Utils.getApplication().applicationInfo)
        if (mediaDirs.isNotEmpty()) {
            val rootDir = File(mediaDirs[0], appName.toString())
            if (rootDir.exists()) return rootDir
        }
        return File(Environment.getExternalStorageDirectory(), appName.toString())
    }

    @JvmStatic
    fun getFMessageFromFStatus(status: Any?): Any? {
        if (status == null) return null

        return try {
            ensureStatusToMessageMapperCreated()
            val mapper = statusToMessageMapper
            val mapperMethod = statusToMessageMethod
            if (mapper == null || mapperMethod == null) {
                XposedBridge.log("mMapFStatusToFMessage is null")
                return null
            }
            mapperMethod.invoke(mapper, status)
        } catch (exception: Exception) {
            XposedBridge.log(exception)
            null
        }
    }

    private fun ensureStatusToMessageMapperCreated() {
        if (statusToMessageMapper == null) {
            statusToMessageMethod?.declaringClass?.getDeclaredConstructor()?.newInstance()
        }
    }

    @JvmStatic
    fun getWaDatabase(): SQLiteDatabase? {
        loadWADatabase()
        return mWaDatabase
    }

    interface ActivityChangeState {
        fun onChange(activity: Activity, type: ChangeType)

        enum class ChangeType {
            CREATED, STARTED, ENDED, RESUMED, PAUSED
        }
    }
}