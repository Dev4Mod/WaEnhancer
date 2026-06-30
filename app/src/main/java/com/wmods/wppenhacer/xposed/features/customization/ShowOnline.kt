package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.text.TextUtilsCompat
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.listeners.ContactItemListener
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

class ShowOnline(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    private var mStatusUser: Any? = null
    private var mInstancePresence: Any? = null
    private var sendPresenceMethod: Method? = null
    private var tcTokenMethod: Method? = null
    private var getStatusUser: Method? = null
    private var fieldTokenDBInstance: Field? = null
    private var tokenClass: Class<*>? = null

    override fun doHook() {
        val showOnlineText = prefs.getBoolean("showonlinetext", false)
        val showOnlineIcon = prefs.getBoolean("dotonline", false)
        if (!showOnlineText && !showOnlineIcon) return

        val classViewHolder = Unobfuscator.loadViewHolder(classLoader)
        XposedBridge.hookAllConstructors(classViewHolder, object : XC_MethodHook() {
            @SuppressLint("ResourceType")
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.args[1] as View
                val context = param.args[0] as Context
                var content = view.findViewById<LinearLayout>(Utils.getID("conversations_row_content", "id"))
                if (content == null) {
                    content = view.findViewById(Utils.getID("row_content", "id"))
                }
                if (showOnlineText) {
                    val linearLayout = LinearLayout(context)
                    linearLayout.gravity = Gravity.END or Gravity.TOP
                    content.addView(linearLayout)

                    val lastSeenText = TextView(context)
                    lastSeenText.id = 0x7FFF0002
                    lastSeenText.textSize = 12f
                    lastSeenText.text = ""
                    lastSeenText.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    lastSeenText.gravity = Gravity.CENTER_VERTICAL
                    lastSeenText.visibility = View.VISIBLE
                    linearLayout.addView(lastSeenText)
                }
                if (showOnlineIcon) {
                    val contactView = view.findViewById<FrameLayout>(Utils.getID("contact_selector", "id"))
                    val firstChild = contactView.getChildAt(0)
                    val isLeftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR
                    if (firstChild is ImageView) {
                        contactView.removeView(firstChild)

                        val relativeLayout = RelativeLayout(context)
                        relativeLayout.id = 0x7FFF0003
                        val params = RelativeLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.addRule(RelativeLayout.CENTER_IN_PARENT)
                        firstChild.layoutParams = params
                        relativeLayout.addView(firstChild)
                        contactView.addView(relativeLayout)

                        val imageView = ImageView(context)
                        imageView.id = 0x7FFF0001
                        val params2 = RelativeLayout.LayoutParams(
                            Utils.dipToPixels(14), Utils.dipToPixels(14)
                        )
                        params2.addRule(RelativeLayout.ALIGN_TOP, contactView.id)
                        params2.addRule(
                            if (isLeftToRight) RelativeLayout.ALIGN_RIGHT else RelativeLayout.ALIGN_LEFT,
                            firstChild.id
                        )
                        params2.topMargin = Utils.dipToPixels(5)
                        imageView.layoutParams = params2
                        imageView.setImageResource(R.drawable.online)
                        imageView.adjustViewBounds = true
                        imageView.scaleType = ImageView.ScaleType.FIT_XY
                        imageView.visibility = View.INVISIBLE
                        relativeLayout.addView(imageView)
                    } else if (firstChild is RelativeLayout) {
                        val photoView = firstChild.getChildAt(0) as ImageView

                        val params = RelativeLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.addRule(RelativeLayout.CENTER_IN_PARENT)
                        photoView.layoutParams = params

                        val imageView = ImageView(context)
                        imageView.id = 0x7FFF0001
                        val params2 = RelativeLayout.LayoutParams(
                            Utils.dipToPixels(14), Utils.dipToPixels(14)
                        )
                        params2.addRule(RelativeLayout.ALIGN_TOP, contactView.id)
                        params2.addRule(
                            if (isLeftToRight) RelativeLayout.ALIGN_RIGHT else RelativeLayout.ALIGN_LEFT,
                            photoView.id
                        )
                        params2.topMargin = Utils.dipToPixels(5)
                        imageView.layoutParams = params2
                        imageView.setImageResource(R.drawable.online)
                        imageView.adjustViewBounds = true
                        imageView.scaleType = ImageView.ScaleType.FIT_XY
                        imageView.visibility = View.INVISIBLE
                        firstChild.addView(imageView)
                    }
                }
            }
        })

        getStatusUser = Unobfuscator.loadStatusUserMethod(classLoader)
        sendPresenceMethod = Unobfuscator.loadSendPresenceMethod(classLoader)
        tcTokenMethod = Unobfuscator.loadTcTokenMethod(classLoader)

        XposedBridge.hookAllConstructors(getStatusUser!!.declaringClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mStatusUser = param.thisObject
            }
        })

        XposedBridge.hookAllConstructors(sendPresenceMethod!!.declaringClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mInstancePresence = param.thisObject
            }
        })

        tokenClass = sendPresenceMethod!!.parameterTypes[2]
        fieldTokenDBInstance = ReflectionUtils.getFieldByExtendType(
            sendPresenceMethod!!.declaringClass, tcTokenMethod!!.declaringClass
        )

        ContactItemListener.contactListeners.add(object : ContactItemListener.OnContactItemListener() {
            @SuppressLint("ResourceType")
            override fun onBind(waContact: WaContactWpp?, view: View?) {
                try {
                    val userJid = waContact!!.userJid
                    if (userJid.isGroup) return

                    val csDot: ImageView? = if (showOnlineIcon) view?.findViewById(0x7FFF0001) else null
                    if (showOnlineIcon && csDot != null) {
                        csDot.visibility = View.INVISIBLE
                    }
                    val lastSeenText: TextView? = if (showOnlineText) view?.findViewById(0x7FFF0002) else null

                    val tokenDBInstance = fieldTokenDBInstance!!.get(mInstancePresence)
                    val tokenData = ReflectionUtils.callMethod(tcTokenMethod, tokenDBInstance, userJid.userJid)
                    val tokenObj = tokenClass!!.constructors[0].newInstance(
                        if (tokenData == null) null else XposedHelpers.getObjectField(tokenData, "A01")
                    )
                    sendPresenceMethod!!.invoke(null, userJid.userJid, null, tokenObj, mInstancePresence)
                    val status = ReflectionUtils.callMethod(getStatusUser, mStatusUser, waContact.getObject(), false) as String
                    setStatus(status, csDot, lastSeenText)
                } catch (e: Exception) {
                    XposedBridge.log(e)
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Conversation"
    }

    companion object {
        private fun setStatus(status: String?, csDot: ImageView?, lastSeenText: TextView?) {
            if (!TextUtils.isEmpty(status) && status!!.trim { it <= ' ' } == UnobfuscatorCache.getInstance().getString("online")) {
                if (csDot != null) {
                    csDot.visibility = View.VISIBLE
                }
            }

            if (lastSeenText != null) {
                if (!TextUtils.isEmpty(status)) {
                    lastSeenText.text = status
                    if (UnobfuscatorCache.getInstance().getString("online") == status) {
                        lastSeenText.setTextColor(Color.GREEN)
                    } else {
                        lastSeenText.setTextColor(0xffcac100.toInt())
                    }
                } else {
                    lastSeenText.text = ""
                    lastSeenText.setTextColor(Color.GRAY)
                }
            }
        }
    }
}
