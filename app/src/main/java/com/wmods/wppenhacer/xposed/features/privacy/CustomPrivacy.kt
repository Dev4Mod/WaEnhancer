package com.wmods.wppenhacer.xposed.features.privacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.adapter.CustomPrivacyAdapter
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

class CustomPrivacy(
    classLoader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(classLoader, preferences) {

    private lateinit var chatUserJidMethod: Method
    private lateinit var groupUserJidMethod: Method

    companion object {
        @JvmStatic
        fun getJSON(number: String?): JSONObject {
            if (Utils.xprefs.getString("custom_privacy_type", "0") == "0" || TextUtils.isEmpty(number)) {
                return JSONObject()
            }
            return WppCore.getPrivJSON("${number}_privacy", JSONObject())
        }
    }

    override fun doHook() {
        if (Utils.xprefs.getString("custom_privacy_type", "0") == "0") return

        val contactInfoActivityClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".ContactInfoActivity"
        )
        val groupInfoActivityClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".GroupChatInfoActivity"
        )
        val userJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "jid.UserJid"
        )
        val groupJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "jid.GroupJid"
        )

        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(contactInfoActivityClass) { method ->
            method.parameterCount == 0 && userJidClass.isAssignableFrom(method.returnType)
        }

        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(groupInfoActivityClass) { method ->
            method.parameterCount == 0 && groupJidClass.isAssignableFrom(method.returnType)
        }

        val type = Utils.xprefs.getString("custom_privacy_type", "0")!!.toInt()

        if (type == 1) {

            WppCore.addListenerActivity(
            object : WppCore.ActivityChangeState {

                @SuppressLint("ResourceType")
                override fun onChange(activity: Activity, type: WppCore.ActivityChangeState.ChangeType) {
                    try {
                        if (type != WppCore.ActivityChangeState.ChangeType.STARTED) return
                        if (!contactInfoActivityClass.isInstance(activity) && !groupInfoActivityClass.isInstance(activity)) {
                            return
                        }
                        if (activity.findViewById<View>(0x7f0a9999) != null) return

                        val id = Utils.getID("contact_info_security_card_layout", "id")
                        val infoLayout = activity.window.findViewById<ViewGroup>(id)
                        val icon = activity.getDrawable(R.drawable.ic_privacy)!!
                        val itemView = createItemView(
                            activity,
                            activity.getString(R.string.custom_privacy),
                            activity.getString(R.string.custom_privacy_sum),
                            icon
                        )

                        itemView.id = 0x7f0a9999
                        itemView.setOnClickListener {
                            showPrivacyDialog(activity, contactInfoActivityClass.isInstance(activity))
                        }

                        infoLayout.addView(itemView)
                    } catch (e: Throwable) {
                        logDebug(e)
                        Utils.showToast(e.message, Toast.LENGTH_SHORT)
                    }
                }
            })
        } else if (type == 2) {
            val hooker = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as Menu
                    val activity = param.thisObject as Activity
                    val customPrivacy = menu.add(0, 0, 0, R.string.custom_privacy)

                    customPrivacy.setIcon(R.drawable.ic_privacy)
                    customPrivacy.setOnMenuItemClickListener {
                        showPrivacyDialog(activity, contactInfoActivityClass.isInstance(activity))
                        true
                    }
                }
            }

            XposedHelpers.findAndHookMethod(
                contactInfoActivityClass,
                "onCreateOptionsMenu",
                Menu::class.java,
                hooker
            )
            XposedHelpers.findAndHookMethod(
                groupInfoActivityClass,
                "onCreateOptionsMenu",
                Menu::class.java,
                hooker
            )
        }

        if (type == 0) return

        val icon = DesignUtils.resizeDrawable(
            DesignUtils.getDrawable(R.drawable.ic_privacy),
            Utils.dipToPixels(24),
            Utils.dipToPixels(24)
        )
        icon.setTint(0xff8696a0.toInt())

        MenuHome.addMenuItem {  menu, activity ->
            menu.add(0, 0, 0, R.string.custom_privacy)
                .setIcon(icon)
                .setOnMenuItemClickListener {
                    showCustomPrivacyList(activity, contactInfoActivityClass, groupInfoActivityClass)
                    true
                }
        }
    }

    private fun createItemView(
        activity: Activity,
        title: String,
        summary: String,
        icon: Drawable
    ): View {
        val mainLayout = LinearLayout(activity)
        mainLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.orientation = LinearLayout.HORIZONTAL
        mainLayout.setPadding(16, 16, 16, 16)

        val imageView = ImageView(activity)
        val imageParams = LinearLayout.LayoutParams(
            Utils.dipToPixels(20),
            Utils.dipToPixels(20)
        )
        imageParams.setMargins(
            Utils.dipToPixels(20),
            0,
            Utils.dipToPixels(16),
            Utils.dipToPixels(20)
        )
        imageView.layoutParams = imageParams
        icon.setTint(0xff8696a0.toInt())
        imageView.setImageDrawable(icon)

        val textContainer = LinearLayout(activity)
        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        containerParams.marginStart = 16
        textContainer.layoutParams = containerParams
        textContainer.orientation = LinearLayout.VERTICAL

        val titleView = TextView(activity)
        titleView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        titleView.text = title
        titleView.setTextColor(DesignUtils.getPrimaryTextColor())

        val summaryView = TextView(activity)
        val summaryParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        summaryParams.marginStart = 4
        summaryView.layoutParams = summaryParams
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        summaryView.text = summary

        textContainer.addView(titleView)
        textContainer.addView(summaryView)

        mainLayout.addView(imageView)
        mainLayout.addView(textContainer)

        return mainLayout
    }

    private fun showCustomPrivacyList(
        activity: Activity,
        contactClass: Class<*>,
        groupClass: Class<*>
    ) {
        val pprefs: SharedPreferences = WppCore.getPrivPrefs()
        val maps = pprefs.all
        val list = ArrayList<CustomPrivacyAdapter.Item>()

        for (key in maps.keys) {
            if (key.endsWith("_privacy")) {
                val number = key.replace("_privacy", "")
                val userJid = FMessageWpp.UserJid(
                    number + if (number.length > 14) "@g.us" else "@s.whatsapp.net"
                )

                var contactName = WppCore.getContactName(userJid)

                if (TextUtils.isEmpty(contactName)) {
                    contactName = number
                }

                val item = CustomPrivacyAdapter.Item()
                item.name = contactName
                item.number = number
                item.key = key
                list.add(item)
            }
        }

        if (list.isEmpty()) {
            Utils.showToast(
                activity.getString(R.string.no_contact_with_custom_privacy),
                Toast.LENGTH_SHORT
            )
            return
        }

        val builder = AlertDialogWpp(activity)
        builder.setTitle(R.string.custom_privacy)

        val listView = ListView(activity)
        listView.adapter = CustomPrivacyAdapter(activity, pprefs, list, contactClass, groupClass)

        builder.setView(listView)
        builder.show()
    }

    private fun showPrivacyDialog(activity: Activity, isChat: Boolean) {
        val userJid = getUserJid(activity, isChat)
        if (userJid.isNull) return

        val builder = createPrivacyDialog(activity, userJid.phoneNumber!!)
        builder.show()
    }

    private fun getUserJid(activity: Activity, isChat: Boolean): FMessageWpp.UserJid {
        return if (isChat) {
            FMessageWpp.UserJid(ReflectionUtils.callMethod(chatUserJidMethod, activity))
        } else {
            FMessageWpp.UserJid(ReflectionUtils.callMethod(groupUserJidMethod, activity))
        }
    }

    private fun createPrivacyDialog(activity: Activity, number: String): AlertDialogWpp {
        val builder = AlertDialogWpp(activity)
        builder.setTitle(R.string.custom_privacy)

        val items = arrayOf(
            activity.getString(R.string.hideread),
            activity.getString(R.string.hidestatusview),
            activity.getString(R.string.hidereceipt),
            activity.getString(R.string.ghostmode),
            activity.getString(R.string.ghostmode_r),
            activity.getString(R.string.block_call)
        )

        val itemsKeys = arrayOf(
            "HideSeen",
            "HideViewStatus",
            "HideReceipt",
            "HideTyping",
            "HideRecording",
            "BlockCall"
        )

        val checkedItems = loadPreferences(number, itemsKeys)

        builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton("OK") { _, _ ->
            savePreferences(number, itemsKeys, checkedItems)
        }
        builder.setNegativeButton(activity.getString(R.string.cancel), null)

        return builder
    }

    private fun loadPreferences(number: String, itemsKeys: Array<String>): BooleanArray {
        val checkedItems = BooleanArray(itemsKeys.size)
        val json = getJSON(number)

        for (i in itemsKeys.indices) {
            val globalKey = getGlobalKey(itemsKeys[i])
            checkedItems[i] = json.optBoolean(itemsKeys[i], getDefaultPreference(globalKey))
        }

        return checkedItems
    }

    private fun getGlobalKey(itemKey: String): String {
        return when (itemKey) {
            "HideSeen" -> "hideread"
            "HideViewStatus" -> "hidestatusview"
            "HideReceipt" -> "hidereceipt"
            "HideTyping" -> "ghostmode_t"
            "HideRecording" -> "ghostmode_r"
            "BlockCall" -> "call_privacy"
            else -> ""
        }
    }

    private fun getDefaultPreference(globalKey: String): Boolean {
        return if (globalKey == "call_privacy") {
            prefs.getString(globalKey, "0") == "1"
        } else {
            prefs.getBoolean(globalKey, false)
        }
    }

    private fun savePreferences(
        number: String,
        itemsKeys: Array<String>,
        checkedItems: BooleanArray
    ) {
        try {
            val jsonObject = JSONObject()

            for (i in itemsKeys.indices) {
                val globalKey = getGlobalKey(itemsKeys[i])

                if (globalKey == "call_privacy") {
                    if ((prefs.getString(globalKey, "0") == "1") != checkedItems[i]) {
                        jsonObject.put(itemsKeys[i], checkedItems[i])
                    }
                } else {
                    if (prefs.getBoolean(globalKey, false) != checkedItems[i]) {
                        jsonObject.put(itemsKeys[i], checkedItems[i])
                    }
                }
            }

            WppCore.setPrivJSON("${number}_privacy", jsonObject)
        } catch (e: Exception) {
            Utils.showToast(e.message, Toast.LENGTH_SHORT)
        }
    }

    override fun getPluginName(): String {
        return "Custom Privacy"
    }
}