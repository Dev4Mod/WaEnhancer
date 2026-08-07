package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
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
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class GroupAdmin(classLoader: ClassLoader, preferences: SharedPreferences) : Feature(classLoader, preferences){

    private val adminFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private var nameInGroupId: Int = -1
    private var nameInGroupTvId: Int = -1

    override fun doHook()  {
        if (!prefs.getBoolean("admin_grp", false)) return

        val jidFactory = Unobfuscator.loadJidFactory(classLoader)
        val grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader)
        nameInGroupId = Utils.getID("name_in_group", "id")
        nameInGroupTvId = Utils.getID("name_in_group_tv", "id")

        ConversationItemListener.conversationListeners.add(object : ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                try {
                    val chatCurrentJid = WppCore.getCurrentUserJid()
                    if (chatCurrentJid == null || !chatCurrentJid.isGroup) return

                    val grpcheckAdminClass = grpcheckAdmin.declaringClass
                    val viewClass = view.javaClass
                    val field = adminFieldCache[viewClass] ?: ReflectionUtils.findFieldUsingFilter(
                        viewClass
                    ) { f ->
                        f.type.isAssignableFrom(grpcheckAdminClass)
                    }.also {
                        it.isAccessible = true
                        adminFieldCache[viewClass] = it
                    }

                    val grpParticipants = field.get(view) ?: return
                    val context = view.context

                    val iconSize = Utils.dipToPixels(14)
                    var iconAdmin = view.findViewWithTag<ImageView>("admin_icon")
                    if (iconAdmin == null) {
                        val nameGroup = view.findViewById<ViewGroup>(nameInGroupId) ?: return

                        val nametv =
                            (if (nameInGroupTvId != -1) nameGroup.findViewById(nameInGroupTvId) else null)
                                ?: nameGroup.getChildAt(0)
                                ?: return

                        val marginStart = Utils.dipToPixels(2)
                        val marginEnd = Utils.dipToPixels(2)

                        val lp = when (val nameLp = nametv.layoutParams) {
                            is LinearLayout.LayoutParams -> {
                                LinearLayout.LayoutParams(nameLp).apply {
                                    width = iconSize
                                    height = iconSize
                                    weight = 0f
                                    leftMargin = marginStart
                                    rightMargin = marginEnd
                                }
                            }

                            is ViewGroup.MarginLayoutParams -> {
                                ViewGroup.MarginLayoutParams(nameLp).apply {
                                    width = iconSize
                                    height = iconSize
                                    leftMargin = marginStart
                                    rightMargin = marginEnd
                                }
                            }

                            else -> {
                                LinearLayout.LayoutParams(iconSize, iconSize).apply {
                                    gravity = Gravity.CENTER_VERTICAL
                                    leftMargin = marginStart
                                    rightMargin = marginEnd
                                }
                            }
                        }
                        iconAdmin = ImageView(context).apply {
                            layoutParams = lp
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageResource(R.drawable.admin)
                            tag = "admin_icon"
                        }

                        val index = nameGroup.indexOfChild(nametv)
                        if (index != -1) {
                            nameGroup.addView(iconAdmin, index + 1)
                        } else {
                            nameGroup.addView(iconAdmin)
                        }
                    }

                    val nametv =
                        (if (nameInGroupTvId != -1) view.findViewById(nameInGroupTvId) else null)
                            ?: (view.findViewById<ViewGroup>(nameInGroupId)?.getChildAt(0))

                    if (nametv != null) {
                        val updateMargin = Runnable {
                            val tvHeight = nametv.measuredHeight
                            val baseTopMargin =
                                (nametv.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
                                    ?: 0
                            val extraTop =
                                if (tvHeight > 0) (tvHeight - iconSize - Utils.dipToPixels(2)) / 2 else 0
                            val targetTopMargin = baseTopMargin + extraTop

                            (iconAdmin.layoutParams as? ViewGroup.MarginLayoutParams)?.let { iconLp ->
                                if (iconLp.topMargin != targetTopMargin) {
                                    iconLp.topMargin = targetTopMargin
                                    iconAdmin.requestLayout()
                                }
                            }
                        }
                        nametv.post(updateMargin)
                    }

                    val groupRawJid = chatCurrentJid.phoneRawString
                    if (groupRawJid == null) {
                        iconAdmin.visibility = View.GONE
                        return
                    }

                    val jidGrp = jidFactory.invoke(null, groupRawJid)
                    val participantJid =
                        resolveParticipantJidForAdminCheck(fMessage.userJid, grpcheckAdmin)

                    if (participantJid == null) {
                        iconAdmin.visibility = View.GONE
                        return
                    }

                    val result = grpcheckAdmin.invoke(grpParticipants, jidGrp, participantJid)
                    iconAdmin.visibility =
                        if (result != null && result as Boolean) View.VISIBLE else View.GONE
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
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