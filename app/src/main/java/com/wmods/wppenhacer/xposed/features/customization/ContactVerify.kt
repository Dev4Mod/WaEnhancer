package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class ContactVerify(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val TAG_CONTACT_CHECKER = "contact_checker"
        private const val STATUS_NOT_ADDED = 401
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
            setText(R.string.checking_if_contact_added)
        }
    }

    @SuppressLint("ResourceType")
    override fun doHook() {
        if (!prefs.getBoolean("verify_blocked_contact", false)) return

        val sendGetProfilePhoto = Unobfuscator.loadGetProfilePhoto(classLoader)
        state.sendGetProfilePhoto = sendGetProfilePhoto
        val profilePhotoProtocolHelper = sendGetProfilePhoto.constructors[0].parameterTypes[2]
        initProfilePhotoProtocolHooks(profilePhotoProtocolHelper)
        val dialerProfilePictureLoader = Unobfuscator.loadDialerProfilePictureLoader(classLoader)
        initProfilePhotoCallbacks(dialerProfilePictureLoader)
        registerActivityListener()
    }

    private fun registerActivityListener() {
        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName == "Conversation" && state == WppCore.ActivityChangeState.ChangeType.STARTED) {
                CompletableFuture.runAsync { onConversationStarted(activity) }
            }
        }
    }

    private fun onConversationStarted(activity: Activity) {
        try {
            val userJid = WppCore.getCurrentUserJid() ?: return
            if (!userJid.isContact) return
            val view =
                activity.findViewById<ViewGroup>(Utils.getID("conversation_contact", "id")) ?: return
            val textView = resolveContactChecker(activity, view)
            showChecking(textView)
            checkContactPhotoProfile(userJid)
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
                val userJid = FMessageWpp.UserJid(fieldUserJid!!.get(param.args[0]))
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val tv = state.checkerView.get() ?: return
                showContactAdded(tv)
            }
        })
        XposedBridge.hookMethod(onError, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val userJid = FMessageWpp.UserJid(param.args[0])
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val tv = state.checkerView.get() ?: return
                val status = ReflectionUtils.getArg(param.args, Int::class.javaObjectType, 0)
                if (status == STATUS_NOT_ADDED) {
                    showProbablyNotAdded(tv)
                } else {
                    showUnavailable(tv)
                }
            }
        })
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
            textView.setText(R.string.checking_if_contact_added)
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
        }
    }

    private fun showContactAdded(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.GREEN)
            textView.setText(R.string.contact_added)
        }
    }

    private fun showProbablyNotAdded(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.YELLOW)
            textView.setText(R.string.contact_probably_not_added)
        }
    }

    private fun showUnavailable(textView: TextView) {
        textView.post {
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
            textView.setText(R.string.not_available)
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
        return "Contact Added Verify"
    }

    private class ContactCheckState {
        @Volatile
        var profilePhotoProtocol: Any? = null
        var sendGetProfilePhoto: Class<*>? = null
        val checkerView = AtomicReference<TextView>()
        val pendingUserJid = AtomicReference<String>()
    }
}
