package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ContactBlockedVerify(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val TAG_CONTACT_CHECKER = "contact_checker"
        private const val STATUS_NOT_ADDED = 401
        private const val STATUS_POSSIBLE_BLOCKED = 2
        private const val VERIFY_TIMEOUT_MS = 2000L
        private const val VERIFY_WAIT_MS = 20_000L
    }

    private val state = ContactCheckState()

    @SuppressLint("ResourceType")
    private fun createTextMessageView(activity: Activity): TextView {
        return TextView(activity).apply {
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isSingleLine = true
            marqueeRepeatLimit = -1
            textSize = 10f
            isFocusable = true
            isFocusableInTouchMode = true
            isSelected = true
            setTextColor(DesignUtils.getPrimaryTextColor())
            setText(R.string.checking_if_the_contact_is_blocked)
        }
    }

    @SuppressLint("ResourceType")
    override fun doHook() {
        if (!prefs.getBoolean("verify_blocked_contact", false))
            return

        Others.propsBoolean[2966] = true

        val sendGetProfilePhoto = Unobfuscator.loadGetProfilePhoto(classLoader)
        state.sendGetProfilePhoto = sendGetProfilePhoto
        val profilePhotoProtocolHelper = sendGetProfilePhoto.constructors[0].parameterTypes[2]
        initProfilePhotoProtocolHooks(profilePhotoProtocolHelper)
        val dialerProfilePictureLoader = Unobfuscator.loadDialerProfilePictureLoader(classLoader)
        initProfilePhotoCallbacks(dialerProfilePictureLoader)

        val verifyKeyStrategy = buildVerifyKeyStrategy()
        registerActivityListener(verifyKeyStrategy.callbackInterface, verifyKeyStrategy.invoker)
    }

    private fun buildVerifyKeyStrategy(): VerifyKeyStrategy {
        return try {
            val verifyKeyClass = Unobfuscator.loadVerifyKeyClass(classLoader)
            val callbackInterface = verifyKeyClass.declaredConstructors[0].parameterTypes[0]
            val invoker = VerifyKeyInvoker { proxyInstance, jids ->
                val instance = XposedHelpers.newInstance(verifyKeyClass, proxyInstance, jids)
                XposedHelpers.callMethod(instance, "A00", 1)
            }
            VerifyKeyStrategy(callbackInterface, invoker)
        } catch (_: Exception) {
            val verifyKeyItemConstructor = Unobfuscator.loadVerifyKeyItemConstructor(classLoader)
            val verifyKeyRunnableConstructor =
                Unobfuscator.loadVerifyKeyRunnableConstructor(classLoader)
            val number = Unobfuscator.loadVerifyKeyInt(classLoader)
            val callbackInterface = verifyKeyItemConstructor.parameterTypes[0]
            val invoker = VerifyKeyInvoker { proxyInstance, jids ->
                val instance = verifyKeyItemConstructor.newInstance(proxyInstance, jids)
                val runInstance =
                    verifyKeyRunnableConstructor.newInstance(instance, number) as Runnable
                CompletableFuture.runAsync(runInstance)
            }
            VerifyKeyStrategy(callbackInterface, invoker)
        }
    }

    private fun registerActivityListener(
        callbackInterface: Class<*>,
        invoker: VerifyKeyInvoker
    ) {
        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName == "Conversation" && state == WppCore.ActivityChangeState.ChangeType.STARTED) {
                CompletableFuture.runAsync {
                    onConversationStarted(activity, callbackInterface, invoker)
                }
            }
        }
    }

    private fun onConversationStarted(
        activity: Activity,
        callbackInterface: Class<*>,
        invoker: VerifyKeyInvoker
    ) {
        try {
            val userJid = WppCore.getCurrentUserJid() ?: return
            val meJid = WppCore.getMyUserJid() ?: return
            if (!userJid.isContact) return
            val view =
                activity.findViewById<ViewGroup>(Utils.getID("conversation_contact", "id")) ?: return
            val textView = resolveContactChecker(activity, view)
            showChecking(textView)
            val methodResult = ReflectionUtils.findMethodUsingFilter(callbackInterface) { method ->
                method.parameterCount == 1 && method.parameterTypes[0] == Int::class.java
            } ?: return
            val startTime = System.currentTimeMillis()
            val isloaded = AtomicBoolean(false)
            val clazzProxy = Proxy.newProxyInstance(
                classLoader,
                arrayOf(callbackInterface)
            ) { _, method, objects ->
                if (method.name == methodResult.name) {
                    isloaded.set(true)
                    val value = objects[0] as Int
                    if (view.isAttachedToWindow) {
                        view.post { onVerifyResult(view, userJid, value, startTime) }
                    }
                }
                null
            }
            val jids = listOf(userJid.userJid!!, meJid.phoneJid!!)
            invoker.invoke(clazzProxy, jids)
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isloaded.get()) {
                    showUnverified(textView)
                }
            }, VERIFY_WAIT_MS)

        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    private fun initProfilePhotoProtocolHooks(profilePhotoProtocolHelper: Class<*>) {
        XposedBridge.hookAllConstructors(profilePhotoProtocolHelper, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                state.profilePhotoProtocol = param.thisObject
            }
        })
    }

    private fun initProfilePhotoCallbacks(dialerProfilePictureLoader: Class<*>) {
        val methods = ReflectionUtils.findAllMethodsUsingFilter(dialerProfilePictureLoader) { method ->
            method.name.length == 3 && method.parameterCount > 1
        }
        val onSuccess = methods.first { method ->
            String::class.java !in method.parameterTypes
        }
        val onError = methods.first { method ->
            String::class.java in method.parameterTypes
        }
        XposedBridge.hookMethod(onSuccess, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fieldUserJid = ReflectionUtils.getFieldByExtendType(
                    param.args[0].javaClass,
                    FMessageWpp.UserJid.TYPE_JID
                )
                val userJid = FMessageWpp.UserJid(fieldUserJid.get(param.args[0]))
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val tv = state.checkerView.get() ?: return
                showNotBlocked(tv)
            }
        })
        XposedBridge.hookMethod(onError, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val userJid = FMessageWpp.UserJid(param.args[0])
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val status = ReflectionUtils.getArg(param.args, Int::class.java, 0)
                val tv = state.checkerView.get() ?: return
                if (status == STATUS_NOT_ADDED) {
                    showProbablyNotAdded(tv)
                }
            }
        })
    }

    private fun onVerifyResult(
        view: ViewGroup,
        userJid: FMessageWpp.UserJid,
        value: Int,
        startTime: Long
    ) {
        val textView = view.findViewWithTag(TAG_CONTACT_CHECKER) as? TextView ?: return
        textView.isSelected = true
        if (System.currentTimeMillis() - startTime < VERIFY_TIMEOUT_MS) {
            showUnverified(textView)
            return
        }
        if (value == STATUS_POSSIBLE_BLOCKED) {
            showPossibleBlocked(textView)
        } else {
            checkContactPhotoProfile(userJid)
        }
    }

    private fun resolveContactChecker(activity: Activity, view: ViewGroup): TextView {
        val textView = view.findViewWithTag(TAG_CONTACT_CHECKER) as? TextView
        if (textView != null) {
            state.checkerView.set(textView)
            return textView
        }
        return createTextMessageView(activity).also {
            state.checkerView.set(it)
            it.tag = TAG_CONTACT_CHECKER
            view.post { view.addView(it) }
        }
    }

    private fun showChecking(textView: TextView) {
        textView.post {
            textView.setText(R.string.checking_if_the_contact_is_blocked)
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
        }
    }

    private fun showNotBlocked(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.GREEN)
            textView.setText(R.string.block_not_detected)
        }
    }

    private fun showProbablyNotAdded(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.YELLOW)
            textView.setText(R.string.contact_probably_not_added)
        }
    }

    private fun showPossibleBlocked(textView: TextView) {
        textView.post {
            textView.setText(R.string.possible_block_detected)
            textView.setTextColor(Color.RED)
        }
    }

    private fun showUnverified(textView: TextView) {
        textView.post {
            textView.setText(R.string.block_unverified)
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
        }
    }

    private fun checkContactPhotoProfile(userJid: FMessageWpp.UserJid) {
        try {
            val sendGetProfilePhoto = state.sendGetProfilePhoto ?: return
            val params =
                ReflectionUtils.initArray(sendGetProfilePhoto.constructors[0].parameterTypes)
            params[2] = state.profilePhotoProtocol
            params[3] = userJid.userJid
            val runnable = sendGetProfilePhoto.constructors[0].newInstance(*params) as Runnable
            state.pendingUserJid.set(userJid.userRawString)
            CompletableFuture.runAsync(runnable)
        } catch (e: Exception) {
            logDebug(e)
        }
    }

    override fun getPluginName(): String {
        return "Contact Blocked Verify"
    }

    private fun interface VerifyKeyInvoker {
        @Throws(Exception::class)
        fun invoke(proxyInstance: Any, jids: List<Any>)
    }

    private data class VerifyKeyStrategy(
        val callbackInterface: Class<*>,
        val invoker: VerifyKeyInvoker
    )

    private class ContactCheckState {
        @Volatile
        var profilePhotoProtocol: Any? = null
        var sendGetProfilePhoto: Class<*>? = null
        val checkerView = AtomicReference<TextView>()
        val pendingUserJid = AtomicReference<String>()
    }
}
