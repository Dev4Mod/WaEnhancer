package com.wmods.wppenhacer.xposed.features.others

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XSharedPreferences
import java.lang.reflect.Method

class GroupAdmin(classLoader: ClassLoader, preferences: XSharedPreferences) : Feature(classLoader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("admin_grp", false)) return

        val jidFactory = Unobfuscator.loadJidFactory(classLoader)
        val grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader)

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {

            override fun onItemBind(fMessage: FMessageWpp, view: ViewGroup, position: Int, convertView: View?) {
                val chatCurrentJid = WppCore.getCurrentUserJid()
                if (chatCurrentJid == null || !chatCurrentJid.isGroup) return

                val grpcheckAdminClass = grpcheckAdmin.declaringClass
                val field = ReflectionUtils.findFieldUsingFilter(view.javaClass) { f ->
                    f.type.isAssignableFrom(grpcheckAdminClass)
                }
                field.isAccessible = true

                val grpParticipants = field.get(view)
                val context = view.context

                var iconAdmin = view.findViewWithTag<ImageView>("admin_icon")
                if (iconAdmin == null) {
                    val nameGroup = view.findViewById<LinearLayout>(Utils.getID("name_in_group", "id")) ?: return

                    val view1 = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val nametv = nameGroup.getChildAt(0)
                    var lpparams =  nametv.layoutParams
                    if (lpparams !is LinearLayout.LayoutParams){
                        lpparams = LinearLayout.LayoutParams(lpparams)
                    }

                    val size = Utils.dipToPixels(16)
                    iconAdmin = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        setImageResource(R.drawable.admin)
                        tag = "admin_icon"
                    }
                    nameGroup.removeView(nametv)
                    nametv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    view1.addView(nametv)
                    view1.addView(iconAdmin)
                    nameGroup.addView(view1, 0, lpparams)
                }

                val groupRawJid = chatCurrentJid.phoneRawString
                if (groupRawJid == null) {
                    iconAdmin.visibility = View.GONE
                    return
                }

                val jidGrp = jidFactory.invoke(null, groupRawJid)
                val participantJid = resolveParticipantJidForAdminCheck(fMessage.userJid, grpcheckAdmin)

                if (participantJid == null) {
                    iconAdmin.visibility = View.GONE
                    return
                }

                val result = grpcheckAdmin.invoke(grpParticipants, jidGrp, participantJid)
                iconAdmin.visibility = if (result != null && result as Boolean) View.VISIBLE else View.GONE
            }
        })
    }

    private fun resolveParticipantJidForAdminCheck(userJid: FMessageWpp.UserJid?, grpcheckAdmin: Method): Any? {
        if (userJid == null) return null

        val expectedType = grpcheckAdmin.parameterTypes[1]

        if (userJid.userJid != null && expectedType.isInstance(userJid.userJid)) {
            return userJid.userJid
        }
        if (userJid.phoneJid != null && expectedType.isInstance(userJid.phoneJid)) {
            return userJid.phoneJid
        }
        if (userJid.userJid != null) {
            return userJid.userJid
        }
        if (userJid.phoneJid != null) {
            return userJid.phoneJid
        }

        return null
    }

    override fun getPluginName(): String {
        return "GroupAdmin"
    }
}