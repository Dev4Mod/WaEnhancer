package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
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
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.listeners.OnMultiClickListener
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.AnimationUtil
import com.wmods.wppenhacer.xposed.utils.AudioOpusConverter
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.util.DexSignUtil
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Properties
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max

class Others(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    companion object {

        @JvmField
        val propsBoolean = HashMap<Int, Boolean>()
        @JvmField
        val propsInteger = HashMap<Int, Int>()
    }

    private lateinit var properties: Properties

    override fun doHook() {
        properties = Utils.getProperties(prefs, "custom_css", "custom_filters")
        val menuWIcons = prefs.getBoolean("menuwicon", false)
        val newSettings = getNewSettingsVariant()
        val filterChats = prefs.getString("chatfilter", "2")
        val filterSeen = prefs.getBoolean("filterseen", false)
        var statusStyle = prefs.getString("status_style", "0")?.toInt() ?: 0
        val disableMetaAI = prefs.getBoolean("metaai", false)
        val disableSensorProximity = prefs.getBoolean("disable_sensor_proximity", false)
        val proximityAudios = prefs.getBoolean("proximity_audios", false)
        val showOnline = prefs.getBoolean("showonline", false)
        val floatingMenu = prefs.getBoolean("floatingmenu", false)
        val filterItems = prefs.getString("filter_items", null)
        val autonextStatus = prefs.getBoolean("autonext_status", false)
        val audioType = prefs.getString("audio_type", "0")?.toInt() ?: 0
        val audioTranscription = prefs.getBoolean("audio_transcription", false)
        val oldStatus = prefs.getBoolean("oldstatus", false)
        val igstatus = prefs.getBoolean("igstatus", false)
        val animationEmojis = prefs.getBoolean("animation_emojis", false)
        val disableProfileStatus = prefs.getBoolean("disable_profile_status", false)
        val disableExpiration = prefs.getBoolean("disable_expiration", false)
        val disableAd = prefs.getBoolean("disable_ads", false)

        propsInteger[3877] = if (oldStatus) (if (igstatus) 2 else 0) else 2

        propsBoolean[18250] = false
        propsBoolean[11528] = false

        propsBoolean[4497] = menuWIcons
        propsBoolean[4023] = false
        propsBoolean[16250] = false

        if (newSettings == 2) {
            XposedBridge.hookAllMethods(WppCore.homeActivityClass, "onCreateOptionsMenu", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as Menu
                    val menuItem = menu.findItem(Utils.getID("me_tab_menu_item", "id"))
                    menuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            })
        }
        propsBoolean[14862] = newSettings !=0 // WHATS_HAPPENING_SENDING_ENABLED_CODE
        propsInteger[18564] = newSettings // ME_TAB_V2_VARIANTS_CODE

        propsBoolean[2889] = floatingMenu

        // new text composer
        propsBoolean[15708] = true

        // change page id
        propsBoolean[2358] = false

        // disable contact filter
        propsBoolean[7769] = false

        // disable new Media Picker
        propsBoolean[9286] = false

        // Instant Video
        propsBoolean[3354] = true
        propsBoolean[5418] = true
        propsBoolean[9051] = true

        // disable new toolbar
        propsBoolean[11824] = false
        propsBoolean[6481] = false

        // Enable music in Stories
        propsBoolean[13591] = true
        propsBoolean[10024] = true

        // show all status
        propsBoolean[6798] = true

        // auto play emojis settings
        propsBoolean[3575] = animationEmojis
        propsBoolean[9757] = animationEmojis

        // emojis maps
        propsBoolean[10639] = animationEmojis
        propsBoolean[12495] = animationEmojis
        propsBoolean[11066] = animationEmojis

        propsBoolean[7589] = true  // Media select quality
        propsBoolean[6972] = false // Media select quality
        propsBoolean[5625] = true  // Enable option to autodelete channels media

        propsBoolean[8643] = true  // Enable TextStatusComposerActivityV2
//        propsBoolean[3403] = true  // Enable Sticker Suggestion
        propsBoolean[8607] = true  // Enable Dialer keyboard
        propsBoolean[9578] = true  // Enable Privacy Checkup
        propsInteger[8135] = 2  // Call Filters

        // Enable Translate Message
        propsBoolean[9141] = true
        propsBoolean[8925] = true

        propsBoolean[10380] = false // fix crash bug in Settings/Archived

        propsBoolean[0x34b9] = true // Enable Select People in call
        propsBoolean[0x351c] = true // Enable new colors style in Text Composer

        // Enable show count until viewed
        propsBoolean[0x2289] = true
        propsBoolean[0x373f] = true

        // add yours in stories
        propsBoolean[0x2ce2] = true
        propsBoolean[0x2ce3] = true

        propsBoolean[0x345a] = true // new edit profile name

        // new stories selection
        propsBoolean[0x32ca] = true
        propsBoolean[0x32cb] = true

        if (disableMetaAI) {
            propsInteger[15535] = 0
            propsBoolean[8025] = false
            propsBoolean[6251] = false
            propsBoolean[8026] = false
            propsBoolean[14886] = false
        }

        if (audioTranscription) {
            propsBoolean[8632] = true
            propsBoolean[2890] = true
            propsBoolean[9215] = false
            propsBoolean[9216] = true
            propsBoolean[6808] = true
            propsBoolean[10286] = true
            propsBoolean[11596] = true
            propsBoolean[13949] = true
        }

        // Whatsapp Status Style
        val retStatusStyle = Unobfuscator.loadStatusStyleMethod(classLoader)
        XposedBridge.hookMethod(retStatusStyle, XC_MethodReplacement.returnConstant(statusStyle))
        statusStyle = if (oldStatus) 0 else statusStyle
        propsInteger[9973] = 1
        propsBoolean[6285] = true
        propsInteger[8522] = statusStyle
        propsInteger[8521] = statusStyle

        // Status in Group
        propsBoolean[13956] = true
        propsBoolean[13957] = true

        // new popup menu in chat
        propsBoolean[21541] = false

        hookProps()
        hookSearchbar(filterChats)

        if (disableSensorProximity) {
            disableSensorProximity()
        }

        if (proximityAudios) {
            val classes = Unobfuscator.loadProximitySensorListenerClasses(classLoader)
            for (cls in classes) {
                XposedBridge.hookAllMethods(cls, "onSensorChanged", XC_MethodReplacement.DO_NOTHING)
            }
        }

        if (filterItems != null && prefs.getBoolean("custom_filters", true)) {
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
    }


    private fun getNewSettingsVariant(): Int {
        val type = prefs.getString("configui_mode", "-1")?.toInt() ?: -1
        return if (type != -1){
            type
        }else {
            if (prefs.getBoolean("novaconfig", false)) 2 else 0
        }
    }

    private fun disableHomeFilters() {
        propsBoolean[15345] = true
        propsBoolean[13546] = false
        propsBoolean[13408] = true

        val filterView = Unobfuscator.loadChatFilterView(classLoader)
        XposedBridge.hookAllConstructors(filterView, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as View
                view.visibility = View.GONE
                XposedHelpers.findAndHookMethod(View::class.java, "setVisibility", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (view === param.thisObject && param.args[0] as Int != View.GONE) {
                            param.result = View.GONE
                        }
                    }
                })
            }
        })
    }

    private fun disableAds() {
        propsBoolean[22904] = true
        propsBoolean[14306] = false
        try {
            val loadAd = Unobfuscator.loadAdVerifyMethod(classLoader)
            XposedBridge.hookMethod(loadAd, object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val enumParam = param.args[0] as Enum<*>
                    if (enumParam.name == "WAMO") {
                        val retClass = (param.method as Method).returnType as Class<out Enum<*>>
                        val pauseEnum = java.lang.Enum.valueOf(retClass, "PAUSED")
                        param.result = pauseEnum
                    }
                }
            })
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

        XposedBridge.hookMethod(statusProfileMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                isCalledFromProfileStatus.set(true)
            }

            override fun afterHookedMethod(param: MethodHookParam?) {
                isCalledFromProfileStatus.set(false)
            }
        })

        val methods = ReflectionUtils.findAllMethodsUsingFilter(statusDataClass){
            it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        }

        methods.forEach {
            XposedBridge.hookMethod(it, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isCalledFromProfileStatus.get() ?: false)
                        param.result = false
                }
            })
        }

        XposedBridge.hookAllMethods(photoProfileClass, "setStatusIndicatorEnabled", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] as Boolean) {
                    param.result = null
                }
            }
        })
    }

    private fun disableSensorProximity() {
        XposedBridge.hookAllMethods(PowerManager::class.java, "newWakeLock", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) {
                    param.result = null
                }
            }
        })
    }

    private fun callInfo() {
        if (!prefs.getBoolean("call_info", false)) return

        val clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceServiceEventCallback")
        val clsWamCall = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "WamCall")

        XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (clsWamCall.isInstance(param.args[0])) {

                    val callinfo = XposedHelpers.callMethod(param.thisObject, "getCallInfo") ?: return
                    val userJid = FMessageWpp.UserJid(XposedHelpers.callMethod(callinfo, "getPeerJid"))
                    if (userJid.isNull) return
                    CompletableFuture.runAsync {
                        try {
                            showCallInformation(param.args[0], userJid)
                        } catch (e: Exception) {
                            logDebug(e)
                        }
                    }
                }
            }
        })
    }

    private fun showCallInformation(wamCall: Any, userJid: FMessageWpp.UserJid) {
        if (userJid.isGroup) return
        val sb = StringBuilder()
        val contact = WppCore.getContactName(userJid)
        val number = userJid.phoneNumber
        if (!TextUtils.isEmpty(contact))
            sb.append(String.format(Utils.application.getString(R.string.contact_s), contact)).append("\n")
        sb.append(String.format(Utils.application.getString(R.string.phone_number_s), number)).append("\n")
        
        val ip = XposedHelpers.getObjectField(wamCall, "callPeerIpStr") as String?
        if (ip != null) {
            val client = OkHttpClient.Builder().build()
            val url = "http://ip-api.com/json/$ip"
            val request = Request.Builder().url(url).build()
            val content = client.newCall(request).execute().body.string()
            val json = JSONObject(content)
            val country = json.getString("country")
            val city = json.getString("city")
            sb.append(String.format(Utils.application.getString(R.string.country_s), country)).append("\n")
              .append(String.format(Utils.application.getString(R.string.city_s), city)).append("\n")
              .append(String.format(Utils.application.getString(R.string.ip_s), ip)).append("\n")
        }
        val platform = XposedHelpers.getObjectField(wamCall, "callPeerPlatform") as String?
        if (platform != null)
            sb.append(String.format(Utils.application.getString(R.string.platform_s), platform)).append("\n")
        val wppVersion = XposedHelpers.getObjectField(wamCall, "callPeerAppVersion") as String?
        if (wppVersion != null)
            sb.append(String.format(Utils.application.getString(R.string.wpp_version_s), wppVersion)).append("\n")
        
        Utils.showNotification(Utils.application.getString(R.string.call_information), sb.toString())
    }

    private fun alwaysOnline() {
        if (!prefs.getBoolean("always_online", false)) return
        val stateChange = Unobfuscator.loadStateChangeMethod(classLoader)
        XposedBridge.hookMethod(stateChange, XC_MethodReplacement.DO_NOTHING)
    }

    private fun doubleTapReaction() {
        if (!prefs.getBoolean("doubletap2like", false)) return

        val emoji = prefs.getString("doubletap2like_emoji", "👍") ?: "👍"

        val conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader)

        XposedBridge.hookAllConstructors(conversationRowClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val viewGroup = param.thisObject as ViewGroup
                viewGroup.setOnTouchListener(null)
            }
        })

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(fMessage: FMessageWpp, view: ViewGroup, position: Int, convertView: View?) {
                val messageId = fMessage.key.messageID
                val onMultiClickListener = object : OnMultiClickListener(2, 500) {
                    override fun onMultiClick(v: View) {
                        if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return
                        val reactionView = v.findViewById<ViewGroup>(Utils.getID("reactions_bubble_layout", "id"))
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
        if (!prefs.getBoolean("stamp_copied_message", false)) return

        val copiedMessage = Unobfuscator.loadCopiedMessageMethod(classLoader)

        XposedBridge.hookMethod(copiedMessage, object : XC_MethodHook() {
            @Suppress("UNCHECKED_CAST")
            override fun beforeHookedMethod(param: MethodHookParam) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                val collection = param.args.last() as java.util.Collection<*>
                param.args[param.args.lastIndex] = object : ArrayList<Any>(collection as Collection<Any>) {
                    override val size: Int
                        get() = 1
                }
            }
        })
    }

    private fun animationList() {
        val animation = prefs.getString("animation_list", "default") ?: "default"

        val onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus))
        val field1 = Unobfuscator.loadViewHolderField1(classLoader)
        logDebug(Unobfuscator.getFieldDescriptor(field1))
        val absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader)

        XposedBridge.hookMethod(onChangeStatus, object : XC_MethodHook() {
            @SuppressLint("ResourceType")
            override fun afterHookedMethod(param: MethodHookParam) {
                val viewHolder = field1.get(param.thisObject)
                val viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass) { field -> field.type == View::class.java }
                val view = viewField.get(viewHolder) as View
                
                if (animation != "default") {
                    view.startAnimation(AnimationUtil.getAnimation(animation))
                } else if (properties.containsKey("home_list_animation")) {
                    val anim = AnimationUtil.getAnimation(properties.getProperty("home_list_animation"))
                    if (anim != null) {
                        view.startAnimation(anim)
                    }
                }
            }
        })
    }

    private fun customPlayBackSpeed() {
        val voicenoteSpeed = prefs.getFloat("voicenote_speed", 2.0f)
        val playBackSpeed = Unobfuscator.loadPlaybackSpeed(classLoader)
        
        XposedBridge.hookMethod(playBackSpeed, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                super.beforeHookedMethod(param)
                if (param.args[1] as Float == 2.0f) {
                    param.args[1] = voicenoteSpeed
                }
            }
        })
        
        val voicenoteClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceNoteProfileAvatarView")
        val method = ReflectionUtils.findAllMethodsUsingFilter(voicenoteClass) { method1 -> 
            method1.parameterCount == 4 && method1.parameterTypes[0] == Int::class.javaPrimitiveType && method1.returnType == Void.TYPE
        }
        
        XposedBridge.hookMethod(method[method.size - 1], object : XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            override fun afterHookedMethod(param: MethodHookParam) {
                super.afterHookedMethod(param)
                if (param.args[0] as Int == 3) {
                    val view = param.thisObject as View
                    val playback = view.findViewById<TextView>(Utils.getID("fast_playback_overlay", "id"))
                    if (playback != null) {
                        playback.text = voicenoteSpeed.toString().replace(".", ",") + "×"
                    }
                }
            }
        })
    }

    private fun sendAudioType(selectedAudioType: Int) {
        val sendAudioTypeMethod = Unobfuscator.loadSendAudioTypeMethod(classLoader)
        
        XposedBridge.hookMethod(sendAudioTypeMethod, object : XC_MethodHook() {
            private var newFile: File? = null

            override fun beforeHookedMethod(param: MethodHookParam) {
                newFile = null
                val results = ReflectionUtils.findInstancesOfType(param.args, Integer::class.java)
                if (results.size < 2) {
                    return
                }

                val mediaType = results[0]
                val sourceType = results[1]

                if (mediaType.second as Int == 2 || mediaType.second as Int == 9) {
                    if (selectedAudioType > 0) {
                        val audioTypeValue = sourceType.second as Int
                        val targetAudioType = selectedAudioType - 1
                        param.args[sourceType.first as Int] = targetAudioType

                        if (audioTypeValue != targetAudioType && targetAudioType == 1) {
                            Utils.showToast(Utils.getString(R.string.converting_audio), Toast.LENGTH_LONG)
                            val fileMedia = param.args[2]
                            val fieldFile = ReflectionUtils.getFieldByExtendType(fileMedia.javaClass, File::class.java)
                            val file = fieldFile!!.get(fileMedia) as File
                            newFile = AudioOpusConverter.convert(file.absolutePath)
                            if (newFile != null) {
                                file.delete()
                                fieldFile!!.set(fileMedia, newFile)
                            }
                        }
                    }
                }
            }
        })

        val originFMessageField = Unobfuscator.loadOriginFMessageField(classLoader)
        val forwardAudioTypeMethod = Unobfuscator.loadForwardAudioTypeMethod(classLoader)

        XposedBridge.hookMethod(forwardAudioTypeMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fMessage = param.result
                originFMessageField.isAccessible = true
                originFMessageField.setInt(fMessage, selectedAudioType - 1)
            }
        })
    }

    private fun autoNextStatus() {
        val statusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackContactFragment")
        val runNextStatusMethod = Unobfuscator.loadNextStatusRunMethod(classLoader)
        
        XposedBridge.hookMethod(runNextStatusMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val obj = XposedHelpers.getObjectField(param.thisObject, "A01")
                if (statusPlaybackContactFragmentClass.isInstance(obj)) {
                    param.result = null
                }
            }
        })
        
        val onPlayBackFinished = Unobfuscator.loadOnPlaybackFinished(classLoader)
        XposedBridge.hookMethod(onPlayBackFinished, XC_MethodReplacement.DO_NOTHING)
    }




    private fun filterItems(filterItems: String) {
        val idsFilter: List<Int> by lazy {
            filterItems.split("\n").map {
                Utils.getID(it.trim(), "id")
            }.filter {
                it > 0
            }
        }
        XposedHelpers.findAndHookMethod(View::class.java, "invalidate", Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as View
                val id = view.id
                if (id > 0 && idsFilter.contains(id) && view.isVisible) {
                    view.visibility = View.GONE
                }
            }
        })
    }

    private fun showOnline(showOnline: Boolean) {
        val checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader)
        XposedBridge.hookMethod(checkOnlineMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val message = param.args[0] as Message
                if (message.arg1 != 5) return
                val baseBundle = message.obj as BaseBundle
                val jid = baseBundle.getString("jid")
                if (TextUtils.isEmpty(jid)) return
                val userjid = FMessageWpp.UserJid(jid)
                if (userjid.isGroup) return
                val waContact = WaContactWpp.getWaContactFromJid(userjid)
                val name = waContact?.displayName ?: "Unknown"
                if (showOnline)
                    Utils.showToast(String.format(Utils.application.getString(R.string.toast_online), name), Toast.LENGTH_SHORT)
                Tasker.sendTaskerEvent(name, WppCore.stripJID(jid), "contact_online")
            }
        })
    }

    private fun hookProps() {
        val methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean))
        val dataUsageActivityClass = WppCore.dataUsageActivityClass
        
        XposedBridge.hookMethod(methodPropsBoolean, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val list = ReflectionUtils.findInstancesOfType(param.args, Integer::class.java)
                val i = list[0].second.toInt()

                val propValue = propsBoolean[i]
                if (propValue != null) {
                    // Fix Bug in Settings Data Usage
                    if (i == 4023) {
                        if (ReflectionUtils.isCalledFromClass(dataUsageActivityClass)) return
                    }
                    param.result = propValue
                }
            }
        })

        val methodPropsInteger = Unobfuscator.loadPropsIntegerMethod(classLoader)

        XposedBridge.hookMethod(methodPropsInteger, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val list = ReflectionUtils.findInstancesOfType(param.args, Integer::class.java)
                val i = list[0].second.toInt()
                val propValue = propsInteger[i] ?: return
                param.result = propValue
            }
        })
    }

    private fun hookSearchbar(filterChats: String?) {
        if (filterChats.isNullOrEmpty())return
        val searchbar = Unobfuscator.loadViewAddSearchBarMethod(classLoader)
        val searchBarID = Utils.getID("my_search_bar", "id")

        XposedBridge.hookMethod(searchbar, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                var view: View? = null
                if (param.args[0] is View) {
                    view = param.args[0] as View
                } else {
                    val auxFace = (param.method as Method).parameterTypes[0]
                    val method = ReflectionUtils.findMethodUsingFilter(auxFace) { m -> m.returnType == View::class.java }
                    if (method != null) {
                        val currentActivity = WppCore.getCurrentActivity()
                        view = method.invoke(param.args[0], currentActivity) as View?
                    }
                }

                if (view != null && (view.id == searchBarID || view.findViewById<View>(searchBarID) != null) && filterChats != "2") {
                    param.result = null
                }
            }
        })

        try {
            if (filterChats != "2") {
                val loadMySearchBar = Unobfuscator.loadMySearchBarMethod(classLoader)
                XposedBridge.hookMethod(loadMySearchBar, XC_MethodReplacement.DO_NOTHING)
            }
        } catch (_: Exception) {
        }

        val addSeachBar = Unobfuscator.loadAddOptionSearchBarMethod(classLoader)
        val curPageField = Unobfuscator.loadGetCurrentPageInHomeField(classLoader)

        XposedBridge.hookMethod(addSeachBar, object : XC_MethodHook() {
            private var homeActivity: Any? = null
            private var originPageId: Int = 0

            override fun beforeHookedMethod(param: MethodHookParam) {
                if (filterChats != "1") return
                homeActivity = param.thisObject
                if (Modifier.isStatic(param.method.modifiers)) {
                    homeActivity = param.args[0]
                }
                originPageId = 0
                if (curPageField.type == Int::class.javaPrimitiveType) {
                    originPageId = curPageField.getInt(homeActivity)
                    curPageField.setInt(homeActivity, 1)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (originPageId != 0) {
                    curPageField.setInt(homeActivity, originPageId)
                }
            }
        })
        
        XposedHelpers.findAndHookMethod(WppCore.homeActivityClass, "onPrepareOptionsMenu", Menu::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val menu = param.args[0] as Menu
                val item = menu.findItem(Utils.getID("menuitem_search", "id"))
                item?.isVisible = filterChats == "1"
            }
        })
    }

    override fun getPluginName(): String {
        return "Others"
    }
}
