package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Pair
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.utils.DebugUtils
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class SeenTick(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {

    private val messageMap = ConcurrentHashMap<String, WeakReference<ImageView>>()
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private var mWaJobManager: Any? = null
        private var mSendReadClass: Class<*>? = null
        private var waJobManagerMethod: Method? = null

        private var cachedSeenDrawable: Drawable? = null
        private var cachedUnseenDrawable: Drawable? = null

        private var sendJobConstructor: Constructor<*>? = null
        private var sendJobParamTypes: Array<Class<*>>? = null
        private var sendJobJidIndexes: List<Pair<Int, Class<*>>>? = null
        private var sendJobMessageIdIndex: Int = -1

        private var sendPlayedClass: Class<*>? = null
        private var sendPlayedConstructor: Constructor<*>? = null
        private var participantInfoConstructor: Constructor<*>? = null

        fun setSeenButton(buttonImage: ImageView, isSeen: Boolean) {
            if (isSeen && cachedSeenDrawable != null) {
                buttonImage.setImageDrawable(cachedSeenDrawable)
                buttonImage.postInvalidate()
                return
            } else if (!isSeen && cachedUnseenDrawable != null) {
                buttonImage.setImageDrawable(cachedUnseenDrawable)
                buttonImage.postInvalidate()
                return
            }

            val originalDrawable = DesignUtils.getDrawableByName("ic_notif_mark_read")
            if (originalDrawable == null) {
                buttonImage.setImageResource(Utils.getID("ic_notif_mark_read", "drawable"))
                if (isSeen) buttonImage.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP)
                else buttonImage.clearColorFilter()
                return
            }

            val clonedDrawable: Drawable = if (originalDrawable is BitmapDrawable) {
                val bitmap = originalDrawable.bitmap
                val config = bitmap.config ?: Bitmap.Config.ARGB_8888
                val clonedBitmap = try {
                    bitmap.copy(config, true)
                } catch (_: Exception) {
                    val fallbackBitmap =
                        createBitmap(bitmap.width, bitmap.height)
                    try {
                        val canvas = Canvas(fallbackBitmap)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    } catch (_: Exception) {
                    }
                    fallbackBitmap
                }
                clonedBitmap.toDrawable(buttonImage.resources)
            } else {
                originalDrawable.constantState?.newDrawable()?.mutate() ?: originalDrawable.mutate()
            }

            if (isSeen) {
                @Suppress("DEPRECATION")
                clonedDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP)
                cachedSeenDrawable = clonedDrawable
            } else {
                clonedDrawable.clearColorFilter()
                cachedUnseenDrawable = clonedDrawable
            }

            buttonImage.setImageDrawable(clonedDrawable)
            buttonImage.postInvalidate()
        }
    }

    private fun registerMessageView(messageId: String?, view: ImageView?) {
        if (messageId == null || view == null) return
        messageMap[messageId] = WeakReference(view)
    }

    private fun getRegisteredView(messageId: String?): ImageView? {
        return messageMap[messageId]?.get()
    }

    override fun doHook() {
        waJobManagerMethod = Unobfuscator.loadBlueOnReplayWaJobManagerMethod(classLoader)
        mSendReadClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "SendReadReceiptJob"
        )

        try {
            mSendReadClass?.let { cls ->
                sendJobConstructor = cls.constructors.firstOrNull()
                sendJobConstructor?.let { constr ->
                    val paramTypes = constr.parameterTypes
                    sendJobParamTypes = paramTypes

                    @Suppress("UNCHECKED_CAST")
                    sendJobJidIndexes = ReflectionUtils.findClassesOfType(
                        paramTypes as Array<Class<*>>,
                        FMessageWpp.UserJid.TYPE_JID
                    ) as List<Pair<Int, Class<*>>>

                    @Suppress("UNCHECKED_CAST")
                    sendJobMessageIdIndex = ReflectionUtils.findIndexOfType(
                        paramTypes as Array<Any?>,
                        Array<String>::class.java
                    )
                }
            }

            sendPlayedClass = Unobfuscator.findFirstClassUsingName(
                classLoader,
                StringMatchType.Contains,
                "SendPlayedReceiptJob"
            )
            sendPlayedClass?.let { cls ->
                sendPlayedConstructor = cls.declaredConstructors.firstOrNull()
                val classParticipantInfo = sendPlayedConstructor?.parameterTypes?.firstOrNull()
                participantInfoConstructor =
                    classParticipantInfo?.declaredConstructors?.firstOrNull()
            }
        } catch (e: Exception) {
            logDebug("Error caching reflection: ${e.message}")
        }

        XposedBridge.hookAllConstructors(
            waJobManagerMethod?.declaringClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    mWaJobManager = param.thisObject
                }
            })

        hookOnSendMessages()

        val ticktype = prefs.getString("seentick", "0")?.toIntOrNull() ?: 0
        if (ticktype == 0) return

        hookConversationScreen(ticktype)
        hookViewOnceScreen(ticktype)
        hookStatusScreen(ticktype)
    }


    private fun hookStatusScreen(ticktype: Int) {
        val viewButtonMethod = Unobfuscator.loadBlueOnReplayViewButtonMethod(classLoader)
        var viewStatusField: Field? = null
        val ifaceKeyStatusItemClass =
            Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader).parameterTypes.first()
        val replyContainerMethod = Unobfuscator.loadStatusPlaybackReplyContainer(classLoader)

        if (ticktype == 1) {
            XposedBridge.hookMethod(viewButtonMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!prefs.getBoolean("hidestatusview", false)) return

                    if (viewStatusField == null) {
                        viewStatusField =
                            ReflectionUtils.findFieldUsingFilter(param.thisObject.javaClass) { f ->
                                f.type == ifaceKeyStatusItemClass
                            }
                    }
                    val ifaceStatusItem = viewStatusField.get(param.thisObject)
                    val fMessage = MenuStatusListener.getFMessageFromStatusData(ifaceStatusItem)

                    if (fMessage == null) {
                        log("FMessage is null")
                        return
                    }

                    val key = fMessage.key
                    if (key.isFromMe) return

                    val fieldViewContainer =
                        ReflectionUtils.findFieldUsingFilter(param.thisObject.javaClass) {
                            replyContainerMethod.declaringClass.isAssignableFrom(it.type)
                        }
                    val replyContainer =
                        replyContainerMethod.invoke(fieldViewContainer.get(param.thisObject))
                    val replyView = XposedHelpers.callMethod(replyContainer, "A01") as View
                    val contentView =
                        replyView.findViewById<LinearLayout>(
                            Utils.getID(
                                "reply_bar_tappable",
                                "id"
                            )
                        )

                    val replyBarBackground =
                        replyView.findViewById<View>(Utils.getID("reply_bar_background", "id"))

                    val buttonImage = ImageView(replyView.context)

                    val iconSize = Utils.dipToPixels(32f)

                    buttonImage.setImageResource(Utils.getID("ic_notif_mark_read", "drawable"))

                    val containerButton = FrameLayout(replyView.context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(DesignUtils.getBackgroundColorFromMap("#ff20272b"))
                        }
                    }

                    containerButton.addView(
                        buttonImage,
                        FrameLayout.LayoutParams(iconSize, iconSize).apply {
                            gravity = Gravity.CENTER
                        }
                    )

                    replyBarBackground.post {
                        val containerSize = replyBarBackground.height

                        containerButton.layoutParams = FrameLayout.LayoutParams(
                            containerSize,
                            containerSize,
                        ).apply {
                            setMargins(0, 0, Utils.dipToPixels(5f), 0)
                        }

                        val position = contentView.indexOfChild(replyBarBackground)
                        contentView.addView(containerButton, position + 1)
                    }

                    registerMessageView(key.messageID, buttonImage)

                    buttonImage.setOnClickListener {
                        scope.launch {
                            Utils.showToast(
                                replyView.context.getString(R.string.sending_read_blue_tick),
                                Toast.LENGTH_SHORT
                            )
                            sendBlueTickStatus(
                                listOf(fMessage)
                            )
                            withContext(Dispatchers.Main) {
                                setSeenButton(buttonImage, true)
                            }
                        }
                    }

                    scope.launch(Dispatchers.IO) {
                        val item = MessageHistoryStore.getInstance().getHideSeenMessage(
                            "status@broadcast",
                            key.messageID,
                            MessageHistoryStore.ReceiptType.READ
                        )
                        withContext(Dispatchers.Main) {
                            setSeenButton(buttonImage, item?.viewed ?: false)
                        }
                    }

                }
            })
        } else {
            MenuStatusListener.menuStatuses.add(object :
                MenuStatusListener.OnMenuItemStatusListener() {

                override fun addMenu(
                    menu: Menu,
                    fMessageList: List<FMessageWpp>,
                    currentIndex: Int
                ): MenuItem? {
                    if (menu.findItem(R.string.send_blue_tick) != null) return null
                    val fMessage = fMessageList[currentIndex]
                    if (fMessage.key.isFromMe) return null
                    return menu.add(0, R.string.send_blue_tick, 0, R.string.send_blue_tick)
                }

                override fun onClick(
                    item: MenuItem,
                    fragmentInstance: Any,
                    fMessageList: List<FMessageWpp>,
                    currentIndex: Int
                ) {
                    val fMessage = fMessageList[currentIndex]
                    DebugUtils.debugObject(fMessage)
                    sendBlueTickStatus(listOf(fMessage))
                    Utils.showToast(
                        Utils.getString(R.string.sending_read_blue_tick),
                        Toast.LENGTH_SHORT
                    )
                }
            })
        }
    }

    private fun hookConversationScreen(ticktype: Int) {
        val onCreateMenuConversationMethod = Unobfuscator.loadOnCreatedMenuConversation(classLoader)

        XposedBridge.hookMethod(onCreateMenuConversationMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val menu = param.args[0] as Menu
                val menuItem = menu.add(0, 0, 0, R.string.send_blue_tick)
                if (ticktype == 1) menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menuItem.setIcon(Utils.getID("ic_notif_mark_read", "drawable"))
                menuItem.setOnMenuItemClickListener {
                    val currentUserJid = WppCore.getCurrentUserJid()
                    currentUserJid?.let { jid -> sendBlueTick(jid) }
                    Utils.showToast(
                        Utils.getString(R.string.sending_read_blue_tick),
                        Toast.LENGTH_SHORT
                    )
                    true
                }
            }
        })

        MenuStatusListener.menuStatuses.add(object : MenuStatusListener.OnMenuItemStatusListener() {
            override fun addMenu(
                menu: Menu,
                fMessageList: List<FMessageWpp>,
                currentIndex: Int
            ): MenuItem? {
                if (menu.findItem(R.string.read_all_mark_as_read) != null) return null
                val fMessage = fMessageList[currentIndex]
                if (fMessage.key.isFromMe) return null
                return menu.add(
                    0,
                    R.string.read_all_mark_as_read,
                    0,
                    R.string.read_all_mark_as_read
                )
            }

            override fun onClick(
                item: MenuItem,
                fragmentInstance: Any,
                fMessageList: List<FMessageWpp>,
                currentIndex: Int
            ) {
                MenuStatusListener.currentStatusList.forEach { fMessage ->
                    val view = getRegisteredView(fMessage.key.messageID)
                    view?.post {
                        setSeenButton(view, true)
                    }
                }
                sendBlueTickStatus(MenuStatusListener.currentStatusList)
                Utils.showToast(
                    Utils.getString(R.string.sending_read_blue_tick),
                    Toast.LENGTH_SHORT
                )
            }
        })
    }

    private fun hookViewOnceScreen(ticktype: Int) {
        val menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(classLoader)

        XposedBridge.hookMethod(menuMethod, object : XC_MethodHook() {
            @SuppressLint("DiscouragedApi")
            override fun afterHookedMethod(param: MethodHookParam) {
                val fmessageObj = ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0) ?: return
                val fMessage = FMessageWpp(fmessageObj)
                if (!fMessage.isViewOnce) return

                val menu = ReflectionUtils.getArg(param.args, Menu::class.java, 0)
                if (menu == null) {
                    logDebug("Menu is null")
                    return
                }

                val item = menu.add(0, 0, 0, R.string.send_blue_tick)
                    .setIcon(Utils.getID("ic_notif_mark_read", "drawable"))
                if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                item.setOnMenuItemClickListener {
                    val userJid = fMessage.key.remoteJid
                    val messageID = fMessage.key.messageID
                    MessageHistoryStore.getInstance().updateViewedMessage(
                        userJid.phoneRawString,
                        messageID,
                        MessageHistoryStore.ReceiptType.PLAYED,
                        true
                    )
                    MessageHistoryStore.getInstance().updateViewedMessage(
                        userJid.phoneRawString,
                        messageID,
                        MessageHistoryStore.ReceiptType.READ,
                        true
                    )
                    sendBlueTickMedia(fMessage)
                    Utils.showToast(
                        Utils.getString(R.string.sending_read_blue_tick),
                        Toast.LENGTH_SHORT
                    )
                    true
                }
            }
        })

        XposedHelpers.findAndHookMethod(
            WppCore.getViewOnceViewerActivityClass(classLoader),
            "onCreateOptionsMenu",
            Menu::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as Menu
                    val item = menu.add(0, 0, 0, R.string.send_blue_tick)
                        .setIcon(Utils.getID("ic_notif_mark_read", "drawable"))
                    if (ticktype == 1) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                    item.setOnMenuItemClickListener {
                        scope.launch(Dispatchers.IO) {
                            val keyClass = FMessageWpp.Key.TYPE
                            val fieldType =
                                ReflectionUtils.getFieldByType(param.thisObject.javaClass, keyClass)
                            val keyMessage =
                                ReflectionUtils.getObjectField(fieldType, param.thisObject)
                            val fMessage = FMessageWpp.Key(keyMessage).fMessage ?: return@launch
                            val rawJid = fMessage.key.remoteJid.phoneRawString
                            val messageID = fMessage.key.messageID

                            MessageHistoryStore.getInstance().updateViewedMessage(
                                rawJid,
                                messageID,
                                MessageHistoryStore.ReceiptType.PLAYED,
                                true
                            )
                            MessageHistoryStore.getInstance().updateViewedMessage(
                                rawJid,
                                messageID,
                                MessageHistoryStore.ReceiptType.READ,
                                true
                            )
                            sendBlueTickMedia(fMessage)
                            Utils.showToast(
                                Utils.getString(R.string.sending_read_blue_tick),
                                Toast.LENGTH_SHORT
                            )
                        }
                        true
                    }
                }
            }
        )
    }

    private fun hookOnSendMessages() {

        val messageJobMethod = Unobfuscator.loadBlueOnReplayMessageJobMethod(classLoader)
        val messageSendClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.Contains,
            "SendE2EMessageJob"
        )
        val blueOnReplayEnabled = prefs.getBoolean("blueonreply", false)

        XposedBridge.hookMethod(messageJobMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!blueOnReplayEnabled) return
                val obj = messageSendClass.cast(param.thisObject)
                val rawJid = XposedHelpers.getObjectField(obj, "jid") as String
                val userJid = FMessageWpp.UserJid(rawJid)

                if (userJid.isStatus) {
                    MenuStatusListener.currentStatusList.forEach { fMessage ->
                        val view = getRegisteredView(fMessage.key.messageID)
                        view?.post {
                            setSeenButton(view, true)
                        }
                    }
                    sendBlueTickStatus(MenuStatusListener.currentStatusList)
                } else {
                    sendBlueTick(userJid)
                }
            }
        })
    }

    private fun sendBlueTick(userJid: FMessageWpp.UserJid) {

        scope.launch {
            val phoneNumber = userJid.phoneNumber
            val userRaw = userJid.userRawString ?: ""
            if (phoneNumber == Utils.getMyNumber() || userRaw.contains("lid_me")) return@launch

            val messages = ArrayList<FMessageWpp>()
            val hiddenMessages = MessageHistoryStore.getInstance()
                .getHideSeenMessages(
                    userJid.phoneRawString,
                    MessageHistoryStore.ReceiptType.READ,
                    false
                )

            hiddenMessages?.forEach { message ->
                message.fMessage?.let { messages.add(it) }
            }

            if (messages.isEmpty()) return@launch

            messages.forEach { m ->
                if (m.mediaType == 2) {
                    MessageHistoryStore.getInstance().updateViewedMessage(
                        userJid.phoneRawString,
                        m.key.messageID,
                        MessageHistoryStore.ReceiptType.PLAYED,
                        true
                    )
                    sendBlueTickMedia(m)
                }
            }

            messages.forEach { msg ->
                MessageHistoryStore.getInstance().updateViewedMessage(
                    userJid.phoneRawString,
                    msg.key.messageID,
                    MessageHistoryStore.ReceiptType.READ,
                    true
                )
            }

            sendBlueTickMsg(userJid, messages)
        }
    }

    private fun sendBlueTickMsg(userJid: FMessageWpp.UserJid, messages: ArrayList<FMessageWpp>) {

        if (messages.isEmpty()) return

        val constr = sendJobConstructor ?: return
        val jidIndexes = sendJobJidIndexes ?: return

        val paramTypes = sendJobParamTypes ?: return

        if (jidIndexes.size < 2 || sendJobMessageIdIndex == -1) return

        @Suppress("UNCHECKED_CAST")
        val args = ReflectionUtils.initArray(paramTypes)
        args[jidIndexes[0].first] = userJid.userJid

        val groupedMap = HashMap<FMessageWpp.UserJid, MutableList<FMessageWpp>>(4)
        val isGroup = userJid.isGroup

        for (message in messages) {
            val userJidMsg = (if (isGroup) message.userJid else message.key.remoteJid)
            groupedMap.computeIfAbsent(userJidMsg) { ArrayList(if (isGroup) 4 else messages.size) }
                .add(message)
        }

        for ((userJidMsg, groupMessages) in groupedMap) {
            try {
                val groupSize = groupMessages.size
                val messageIds = Array(groupSize) { i -> groupMessages[i].key.messageID }

                args[jidIndexes[1].first] = if (isGroup) userJidMsg.userJid else null
                args[sendJobMessageIdIndex] = messageIds

                val sendJob = constr.newInstance(*args)
                XposedHelpers.setAdditionalInstanceField(sendJob, "blue_on_reply", true)
                waJobManagerMethod?.invoke(mWaJobManager, sendJob)
            } catch (ex: Exception) {
                logDebug(ex)
            }
        }
    }

    private fun sendBlueTickStatus(
        fMessageList: List<FMessageWpp>
    ) {

        if (fMessageList.isEmpty()) return

        val currentJidTarget = fMessageList.first().userJid

        scope.launch {
            try {
                val size = fMessageList.size

                val constr = sendJobConstructor ?: return@launch
                val jidIndexes = sendJobJidIndexes ?: return@launch

                val paramTypes = sendJobParamTypes ?: return@launch

                if (jidIndexes.size < 2 || sendJobMessageIdIndex == -1) return@launch

                val arrS = Array(size) { "" }
                val messageHistory = MessageHistoryStore.getInstance()

                for (i in 0 until size) {
                    val msgId = fMessageList[i].key.messageID
                    arrS[i] = msgId
                    messageHistory.updateViewedMessage(
                        "status@broadcast",
                        msgId,
                        MessageHistoryStore.ReceiptType.READ,
                        true
                    )
                }

                val userJidSender = WppCore.createUserJid("status@broadcast")

                @Suppress("UNCHECKED_CAST")
                val args = ReflectionUtils.initArray(paramTypes)

                args[jidIndexes[0].first] = userJidSender
                args[jidIndexes[1].first] = currentJidTarget.userJid
                args[sendJobMessageIdIndex] = arrS

                val sendJob2 = constr.newInstance(*args)
                XposedHelpers.setAdditionalInstanceField(sendJob2, "blue_on_reply", true)
                waJobManagerMethod?.invoke(mWaJobManager, sendJob2)
            } catch (e: Exception) {
                logDebug(e)
            }
        }

    }

    private fun sendBlueTickMedia(fMessage: FMessageWpp) {
        scope.launch {
            try {
                val userJid = fMessage.key.remoteJid
                val participant = if (userJid.isGroup) fMessage.userJid.userJid else null

                val sPlayedClass = sendPlayedClass ?: return@launch
                val pInfoConstructor = participantInfoConstructor ?: return@launch

                val rowsId = arrayOf(fMessage.rowId)
                val messageId = fMessage.key.messageID

                val participantInfo = pInfoConstructor.newInstance(
                    userJid.userJid,
                    participant,
                    rowsId,
                    arrayOf(messageId)
                )
                val sendJob = XposedHelpers.newInstance(sPlayedClass, participantInfo, false)

                waJobManagerMethod?.invoke(mWaJobManager, sendJob)
            } catch (e: Throwable) {
                logDebug(e)
            }
        }
    }

    override fun getPluginName(): String {
        return "Seen Tick"
    }
}