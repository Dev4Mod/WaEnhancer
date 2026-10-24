package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import android.os.BaseBundle
import android.os.Message
import android.os.PowerManager
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.listeners.OnMultiClickListener
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.PropsHook.Companion.booleans
import com.wmods.wppenhacer.xposed.features.general.PropsHook.Companion.integers
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.AnimationUtil
import com.wmods.wppenhacer.xposed.utils.AudioOpusConverter
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.collapseAndHide
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Properties
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max

class Others(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    private lateinit var properties: Properties
    private val hiddenHomeFilterViews =
        Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    override fun doHook() {
        properties = Utils.getProperties(xprefs, "custom_css", "custom_filters")
        val menuWIcons = xprefs.getBoolean("menuwicon", false)
        val newSettings = getNewSettingsVariant()
        val filterChats = xprefs.getString("chatfilter", "2")
        val filterSeen = xprefs.getBoolean("filterseen", false)
        var statusStyle = xprefs.getString("status_style", "0")?.toInt() ?: 0
        val disableMetaAI = xprefs.getBoolean("metaai", false)
        val disableSensorProximity = xprefs.getBoolean("disable_sensor_proximity", false)
        val proximityAudios = xprefs.getBoolean("proximity_audios", false)
        val showOnline = xprefs.getBoolean("showonline", false)
        val floatingMenu = xprefs.getBoolean("floatingmenu", false)
        val filterItems = xprefs.getString("filter_items", null)
        val autonextStatus = xprefs.getBoolean("autonext_status", false)
        val audioType = xprefs.getString("audio_type", "0")?.toInt() ?: 0
        val audioTranscription = xprefs.getBoolean("audio_transcription", false)
        val oldStatus = xprefs.getBoolean("oldstatus", false)
        val igstatus = xprefs.getBoolean("igstatus", false)
        val animationEmojis = xprefs.getBoolean("animation_emojis", false)
        val disableDocumentScanner = xprefs.getBoolean("disable_document_scanner", false)
        val disableProfileStatus = xprefs.getBoolean("disable_profile_status", false)
        val disableExpiration = xprefs.getBoolean("disable_expiration", false)
        val disableAd = xprefs.getBoolean("disable_ads", false)

        integers[3877] = if (oldStatus) (if (igstatus) 2 else 0) else 2

        booleans[18250] = false
        // 11528 gates WhatsApp's "+" add custom list chip in the chat filter row.
        // Only force it off when WAE hides the filter row (#838).
        if (!filterSeen) booleans[11528] = false

        booleans[4497] = menuWIcons
        booleans[4023] = false
        booleans[16250] = false

        if (newSettings == 2) {
            WppCore.homeActivityClass.resolve().method {
                name = "onCreateOptionsMenu"
            }.hookAll {
                after {
                    val menu = args[0] as Menu
                    val menuItem = menu.findItem(Utils.getID("me_tab_menu_item", "id"))
                    menuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            }
        }
        booleans[14862] = newSettings != 0 // WHATS_HAPPENING_SENDING_ENABLED_CODE
        integers[18564] = newSettings // ME_TAB_V2_VARIANTS_CODE

        booleans[2889] = floatingMenu

        // new text composer
        booleans[15708] = true

        // change page id
        booleans[2358] = false

        // disable contact filter
        booleans[7769] = false

        // disable new Media Picker
        booleans[9286] = false

        // Instant Video
        booleans[3354] = true
        booleans[5418] = true
        booleans[9051] = true

        // disable new toolbar
        booleans[11824] = false
        booleans[6481] = false

        // Enable music in Stories
        booleans[13591] = true
        booleans[10024] = true

        // show all status
        booleans[6798] = true

        // auto play emojis settings
        booleans[3575] = animationEmojis
        booleans[9757] = animationEmojis

        // emojis maps
        booleans[10639] = animationEmojis
        booleans[12495] = animationEmojis
        booleans[11066] = animationEmojis

        booleans[7589] = true  // Media select quality
        booleans[6972] = false // Media select quality
        booleans[5625] = true  // Enable option to autodelete channels media

        booleans[8643] = true  // Enable TextStatusComposerActivityV2
//        booleans[3403] = true  // Enable Sticker Suggestion
        booleans[8607] = true  // Enable Dialer keyboard
        booleans[9578] = true  // Enable Privacy Checkup
        integers[8135] = 2  // Call Filters

        // Enable Translate Message
        booleans[9141] = true
        booleans[8925] = true

        booleans[10380] = false // fix crash bug in Settings/Archived
        if (disableDocumentScanner) {
            booleans[10048] = false // ML Kit document scanner in DocumentPicker
        }

        booleans[0x34b9] = true // Enable Select People in call
        booleans[0x351c] = true // Enable new colors style in Text Composer

        // Enable show count until viewed
        booleans[0x2289] = true
        booleans[0x373f] = true

        // add yours in stories
        booleans[0x2ce2] = true
        booleans[0x2ce3] = true

        booleans[0x345a] = true // new edit profile name

        // new stories selection
        booleans[0x32ca] = true
        booleans[0x32cb] = true

        // Send View Once Text
        booleans[30762] = true
        // Schedule Message Native
        booleans[23845] = true

        if (disableMetaAI) {
            SharedPreferencesWrapper.addHook { key, value ->
                if (key == "bonsai_meta_ai_button_setting_enabled") {
                    return@addHook false
                }
                value
            }

            integers[15535] = 0
            booleans[8025] = false
            booleans[6251] = false
            booleans[8026] = false
            booleans[14886] = false
        }

        if (audioTranscription) {
            booleans[8632] = true
            booleans[2890] = true
            booleans[9215] = false
            booleans[9216] = true
            booleans[6808] = true
            booleans[10286] = true
            booleans[11596] = true
            booleans[13949] = true
        }

        // Whatsapp Status Style
        val retStatusStyle = Unobfuscator.loadStatusStyleMethod(classLoader)
        retStatusStyle.hook {
            replaceAny { statusStyle }
        }
        statusStyle = if (oldStatus) 0 else statusStyle
        integers[9973] = 1
        booleans[6285] = true
        integers[8522] = statusStyle
        integers[8521] = statusStyle

        // Status in Group
        booleans[13956] = true
        booleans[13957] = true

        // new popup menu in chat
        booleans[21541] = false

        hookSearchbar(filterChats)

        if (disableSensorProximity) {
            disableSensorProximity()
        }

        if (proximityAudios) {
            val classes = Unobfuscator.loadProximitySensorListenerClasses(classLoader)
            for (cls in classes) {
                cls.resolve().method {
                    name = "onSensorChanged"
                }.hookAll {
                    replaceUnit { }
                }
            }
        }

        if (filterItems != null && xprefs.getBoolean("custom_filters", true)) {
            filterItems(filterItems)
        }

        if (autonextStatus) {
            autoNextStatus()
        }

        if (audioType > 0) {
            try {
                sendAudioType(audioType)
            } catch (e: Exception) {
                logDebug(e)
            }
        }

        customPlayBackSpeed()

        showOnline(showOnline)

        animationList()

        stampCopiedMessage()

        doubleTapReaction()

        alwaysOnline()

        callInfo()

        if (disableProfileStatus) {
            disablePhotoProfileStatus()
        }

        if (disableExpiration) {
            FeatureLoader.disableExpirationVersion(classLoader)
        }

        if (disableAd) {
            disableAds()
        }

        if (!filterSeen) {
            disableHomeFilters()
        }

        if (xprefs.getBoolean("disable_swipe_up_in_group", false)) {
            disableSwipeUpInGroup()
        }

    }


    private fun disableSwipeUpInGroup() {
        Unobfuscator.loadSwipeUpInGroupMethod(classLoader).hook {
            before {
                result = ReflectionUtils.getDefaultValue((method as Method).returnType)
            }
        }
    }

    private fun getNewSettingsVariant(): Int {
        val type = xprefs.getString("configui_mode", "-1")?.toInt() ?: -1
        return if (type != -1) {
            type
        } else {
            if (xprefs.getBoolean("novaconfig", false)) 2 else 0
        }
    }

    private fun disableHomeFilters() {
        Unobfuscator.loadChatFilterViewMethod(classLoader).hook {
            after {
                val filterView = args[0] as? View ?: return@after
                filterView.collapseAndHide()
            }
        }

        val filterDimenId = try {
            Unobfuscator.loadFilterDimenId(classLoader)
        } catch (e: Throwable) {
            logDebug(e)
            return
        }

        try {
            Unobfuscator.loadConversationsHeightMethod(classLoader).hook {
                after {
                    val context = Utils.application
                    if (filterDimenId != 0) {
                        val filterViewHeight =
                            context.resources.getDimensionPixelSize(filterDimenId)
                        val originalHeight = result as Int
                        result = max(originalHeight - filterViewHeight, 0)
                    }
                }
            }
        } catch (e: Throwable) {
            logDebug(e)
        }
    }

    private fun disableAds() {
        booleans[22904] = true
        booleans[14306] = false
        try {
            val loadAd = Unobfuscator.loadAdVerifyMethod(classLoader)
            loadAd.hook {
                before {
                    val enumParam = args[0] as Enum<*>
                    if (enumParam.name == "WAMO") {
                        val retClass = (method as Method).returnType as Class<out Enum<*>>
                        val pauseEnum = java.lang.Enum.valueOf(retClass, "PAUSED")
                        result = pauseEnum
                    }
                }
            }
        } catch (e: Throwable) {
            logDebug(e)
        }
    }

    private fun disablePhotoProfileStatus() {
        val statusDataClass = Unobfuscator.loadStatusDataClass(classLoader)
        val statusProfileMethod = Unobfuscator.loadStatusProfileMethod(classLoader)
        val photoProfileClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".WDSProfilePhoto"
        )
        val isCalledFromProfileStatus = ThreadLocal<Boolean>()

        statusProfileMethod.hook {
            before {
                isCalledFromProfileStatus.set(true)
            }
            after {
                isCalledFromProfileStatus.set(false)
            }
        }

        val methods = ReflectionUtils.findAllMethodsUsingFilter(statusDataClass) {
            it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        }

        methods.forEach {
            it.hook {
                after {
                    if (isCalledFromProfileStatus.get() ?: false)
                        result = false
                }
            }
        }

        photoProfileClass.resolve().method {
            name = "setStatusIndicatorEnabled"
        }.hookAll {
            before {
                if (args[0] as Boolean) {
                    result = null
                }
            }
        }
    }

    private fun disableSensorProximity() {
        PowerManager::class.java.resolve().method {
            name = "newWakeLock"
        }.hookAll {
            before {
                if (args[0] == PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) {
                    result = null
                }
            }
        }
    }

    private fun callInfo() {
        if (!xprefs.getBoolean("call_info", false)) return

        val clsCallEventCallback = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "VoiceServiceEventCallback"
        )
        val clsWamCall =
            Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "WamCall")

        clsCallEventCallback.resolve().method {
            name = "fieldstatsReady"
        }.hookAll {
            before {
                if (clsWamCall.isInstance(args[0])) {

                    val callinfo =
                        ReflectionUtils.callMethod(instance, "getCallInfo") ?: return@before
                    val userJid =
                        FMessageWpp.UserJid(ReflectionUtils.callMethod(callinfo, "getPeerJid"))
                    if (userJid.isNull) return@before
                    CompletableFuture.runAsync {
                        try {
                            showCallInformation(args[0]!!, userJid)
                        } catch (e: Exception) {
                            logDebug(e)
                        }
                    }
                }
            }
        }
    }

    private fun showCallInformation(wamCall: Any, userJid: FMessageWpp.UserJid) {
        if (userJid.isGroup) return
        val sb = StringBuilder()
        val contact = WppCore.getContactName(userJid)
        val number = userJid.phoneNumber
        if (!TextUtils.isEmpty(contact))
            sb.append(String.format(Utils.application.getString(R.string.contact_s), contact))
                .append("\n")
        sb.append(String.format(Utils.application.getString(R.string.phone_number_s), number))
            .append("\n")

        val ip = ReflectionUtils.getObjectField(wamCall, "callPeerIpStr") as String?
        if (ip != null) {
            val client = OkHttpClient.Builder().build()
            val url = "http://ip-api.com/json/$ip"
            val request = Request.Builder().url(url).build()
            val content = client.newCall(request).execute().body.string()
            val json = JSONObject(content)
            val country = json.getString("country")
            val city = json.getString("city")
            sb.append(String.format(Utils.application.getString(R.string.country_s), country))
                .append("\n")
                .append(String.format(Utils.application.getString(R.string.city_s), city))
                .append("\n")
                .append(String.format(Utils.application.getString(R.string.ip_s), ip)).append("\n")
        }
        val platform = ReflectionUtils.getObjectField(wamCall, "callPeerPlatform") as String?
        if (platform != null)
            sb.append(String.format(Utils.application.getString(R.string.platform_s), platform))
                .append("\n")
        val wppVersion = ReflectionUtils.getObjectField(wamCall, "callPeerAppVersion") as String?
        if (wppVersion != null)
            sb.append(
                String.format(
                    Utils.application.getString(R.string.wpp_version_s),
                    wppVersion
                )
            ).append("\n")

        Utils.showNotification(
            Utils.application.getString(R.string.call_information),
            sb.toString()
        )
    }

    private fun alwaysOnline() {
        if (!xprefs.getBoolean("always_online", false)) return
        val stateChange = Unobfuscator.loadStateChangeMethod(classLoader)
        stateChange.hook {
            replaceUnit { }
        }
    }

    private fun doubleTapReaction() {
        if (!xprefs.getBoolean("doubletap2like", false)) return

        val emoji = xprefs.getString("doubletap2like_emoji", "👍") ?: "👍"

        val conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader)

        conversationRowClass!!.resolve().constructor { }.hookAll {
            after {
                val viewGroup = instance as ViewGroup
                viewGroup.setOnTouchListener(null)
            }
        }

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                val messageId = fMessage.key.messageID
                val onMultiClickListener = object : OnMultiClickListener(2, 500) {
                    override fun onMultiClick(v: View) {
                        if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return
                        val reactionView =
                            v.findViewById<ViewGroup>(Utils.getID("reactions_bubble_layout", "id"))
                        if (reactionView != null && reactionView.isVisible) {
                            for (i in 0 until reactionView.childCount) {
                                val child = reactionView.getChildAt(i)
                                if (child is TextView) {
                                    if (child.text.toString().contains(emoji)) {
                                        WppCore.sendReaction("", fMessage.getObject())
                                        return
                                    }
                                }
                            }
                        }
                        WppCore.sendReaction(emoji, fMessage.getObject())
                    }
                }
                view.setOnClickListener(onMultiClickListener)
            }
        })
    }

    private fun stampCopiedMessage() {
        if (!xprefs.getBoolean("stamp_copied_message", false)) return

        val copiedMessage = Unobfuscator.loadCopiedMessageMethod(classLoader)

        copiedMessage.hook {
            before {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                val collection = args.last() as java.util.Collection<*>
                args[args.lastIndex] = object : ArrayList<Any>(collection as Collection<Any>) {
                    override val size: Int
                        get() = 1
                }
            }
        }
    }

    private fun animationList() {
        val animation = xprefs.getString("animation_list", "default") ?: "default"

        val onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus))
        val field1 = Unobfuscator.loadViewHolderField1(classLoader)
        logDebug(Unobfuscator.getFieldDescriptor(field1))
        val absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader)

        onChangeStatus.hook {
            after {
                val viewHolder = field1.get(instance)
                val viewField =
                    ReflectionUtils.findFieldUsingFilter(absViewHolderClass) { field -> field.type == View::class.java }
                val view = viewField.get(viewHolder) as View

                if (animation != "default") {
                    view.startAnimation(AnimationUtil.getAnimation(animation))
                } else if (properties.containsKey("home_list_animation")) {
                    val anim =
                        AnimationUtil.getAnimation(properties.getProperty("home_list_animation"))
                    if (anim != null) {
                        view.startAnimation(anim)
                    }
                }
            }
        }
    }

    private fun customPlayBackSpeed() {
        val voicenoteSpeed = xprefs.getFloat("voicenote_speed", 2.0f)
        val playBackSpeed = Unobfuscator.loadPlaybackSpeed(classLoader)

        playBackSpeed.hook {
            before {
                val index = ReflectionUtils.findIndexOfType(args, Float::class.javaPrimitiveType!!)
                if (index != -1) {
                    if (args[index] as? Float == 2.0f) {
                        args[index] = voicenoteSpeed
                    }
                }
            }
        }

        val voicenoteClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "VoiceNoteProfileAvatarView"
        )
        val method = ReflectionUtils.findAllMethodsUsingFilter(voicenoteClass) { method1 ->
            method1.parameterCount == 4 && method1.parameterTypes[0] == Int::class.javaPrimitiveType && method1.returnType == Void.TYPE
        }

        method[method.size - 1].hook {
            after {
                if (args[0] as Int == 3) {
                    val view = instance as View
                    val playback =
                        view.findViewById<TextView>(Utils.getID("fast_playback_overlay", "id"))
                    if (playback != null) {
                        playback.text = voicenoteSpeed.toString().replace(".", ",") + "×"
                    }
                }
            }
        }
    }

    private fun sendAudioType(selectedAudioType: Int) {
        val sendAudioTypeMethod = Unobfuscator.loadMediaTypeMethod(classLoader)

        var newFile: File? = null

        sendAudioTypeMethod.hook {
            before {
                newFile = null
                val results = ReflectionUtils.findInstancesOfType(args, Int::class.javaObjectType)
                if (results.size < 2) {
                    return@before
                }

                val mediaType = results[0]
                val sourceType = results[1]

                if (mediaType.second == 2) {
                    if (selectedAudioType > 0) {
                        val audioTypeValue = sourceType.second as Int
                        val targetAudioType = selectedAudioType - 1
                        args[sourceType.first] = targetAudioType

                        if (audioTypeValue != targetAudioType && targetAudioType == 1) {
                            Utils.showToast(
                                Utils.getString(R.string.converting_audio),
                                Toast.LENGTH_LONG
                            )
                            val fileMedia = args[2]
                            val fieldFile = ReflectionUtils.getFieldByExtendType(
                                fileMedia!!.javaClass,
                                File::class.java
                            ) ?: run {
                                logDebug("File field not found")
                                return@before
                            }
                            val file = fieldFile.get(fileMedia) as File
                            newFile = AudioOpusConverter.convert(file.absolutePath)
                            if (newFile != null) {
                                file.delete()
                                fieldFile.set(fileMedia, newFile)
                            }
                        }
                    }
                }
            }
        }

        val originFMessageField = Unobfuscator.loadOriginFMessageField(classLoader)
        val forwardAudioTypeMethod = Unobfuscator.loadForwardAudioTypeMethod(classLoader)

        forwardAudioTypeMethod.hook {
            after {
                val fMessage = result
                originFMessageField.isAccessible = true
                originFMessageField.setInt(fMessage, selectedAudioType - 1)
            }
        }
    }

    private fun autoNextStatus() {
        val statusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "StatusPlaybackContactFragment"
        )
        val runNextStatusMethod = Unobfuscator.loadNextStatusRunMethod(classLoader)

        runNextStatusMethod.hook {
            before {
                val obj = ReflectionUtils.getObjectField(instance, "A01")
                if (statusPlaybackContactFragmentClass.isInstance(obj)) {
                    result = null
                }
            }
        }

        val onPlayBackFinished = Unobfuscator.loadOnPlaybackFinished(classLoader)
        onPlayBackFinished.hook {
            replaceUnit { }
        }
    }


    private fun filterItems(filterItems: String) {
        val idsFilter: Set<Int> by lazy {
            filterItems.split("\n").map {
                Utils.getID(it.trim(), "id")
            }.filter {
                it > 0
            }.toSet()
        }
        View::class.java.resolve().firstMethod {
            name = "invalidate"
            superclass()
            parameters(Boolean::class)
        }.hook {
            after {
                val view = instance as View
                val id = view.id
                if (id > 0 && idsFilter.contains(id) && view.isVisible) {
                    view.visibility = View.GONE
                }
            }
        }
    }

    private fun showOnline(showOnline: Boolean) {
        val checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader)
        checkOnlineMethod.hook {
            before {
                val message = args[0] as Message
                if (message.arg1 != 5) return@before
                val baseBundle = message.obj as BaseBundle
                val jid = baseBundle.getString("jid")
                if (TextUtils.isEmpty(jid)) return@before
                val userjid = FMessageWpp.UserJid(jid)
                if (userjid.isGroup) return@before
                val waContact = WaContactWpp.getWaContactFromJid(userjid)
                val name = waContact?.displayName ?: "Unknown"
                if (showOnline)
                    Utils.showToast(
                        String.format(
                            Utils.application.getString(R.string.toast_online),
                            name
                        ), Toast.LENGTH_SHORT
                    )
                Tasker.sendTaskerEvent(name, WppCore.stripJID(jid), "contact_online")
            }
        }
    }

    private fun hookSearchbar(filterChats: String?) {
        if (filterChats.isNullOrEmpty()) return
        val searchbar = Unobfuscator.loadViewAddSearchBarMethod(classLoader)
        val searchBarID = Utils.getID("my_search_bar", "id")

        searchbar.hook {
            before {
                var view: View? = null
                if (args[0] is View) {
                    view = args[0] as View
                } else {
                    val auxFace = (method as Method).parameterTypes[0]
                    val method =
                        ReflectionUtils.findMethodUsingFilter(auxFace) { m -> m.returnType == View::class.java }
                    val currentActivity = WppCore.getCurrentActivity()
                    view = method.invoke(args[0], currentActivity) as View?
                }

                if (view != null && (view.id == searchBarID || view.findViewById<View>(searchBarID) != null) && filterChats != "2") {
                    result = null
                }
            }
        }

        try {
            if (filterChats != "2") {
                val loadMySearchBar = Unobfuscator.loadMySearchBarMethod(classLoader)
                loadMySearchBar.hook {
                    replaceUnit { }
                }
            }
        } catch (_: Exception) {
        }

        val addSeachBar = Unobfuscator.loadAddOptionSearchBarMethod(classLoader)
        val curPageField = Unobfuscator.loadGetCurrentPageInHomeField(classLoader)

        var homeActivity: Any? = null
        var originPageId = 0

        addSeachBar.hook {
            before {
                if (filterChats != "1") return@before
                homeActivity = instanceOrNull
                if (Modifier.isStatic(method.modifiers)) {
                    homeActivity = args[0]
                }
                originPageId = 0
                if (curPageField.type == Int::class.javaPrimitiveType) {
                    originPageId = curPageField.getInt(homeActivity)
                    curPageField.setInt(homeActivity, 1)
                }
            }
            after {
                if (originPageId != 0) {
                    curPageField.setInt(homeActivity, originPageId)
                }
            }
        }

        WppCore.homeActivityClass.resolve().firstMethod {
            name = "onPrepareOptionsMenu"
            superclass()
            parameters(Menu::class.java)
        }.hook {
            after {
                val menu = args[0] as Menu
                val item = menu.findItem(Utils.getID("menuitem_search", "id"))
                item?.isVisible = filterChats == "1"
            }
        }
    }

    override fun getPluginName(): String {
        return "Others"
    }
}
