package com.wmods.wppenhacer.xposed.features.privacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
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
import androidx.core.content.edit
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.param.HookParam
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
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

class CustomPrivacy(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private lateinit var chatUserJidMethod: Method
    private lateinit var groupUserJidMethod: Method

    companion object {
        private const val KEY_SUFFIX = "_privacy"

        @JvmStatic
        fun getJSON(userJid: FMessageWpp.UserJid?): JSONObject {
            if (Utils.xprefs.getString("custom_privacy_type", "0") == "0") return JSONObject()
            val id = storageId(userJid) ?: return JSONObject()
            val key = id + KEY_SUFFIX
            val privPrefs = WppCore.getPrivPrefs()
            if (privPrefs.contains(key)) return WppCore.getPrivJSON(key, JSONObject())

            val legacyKey = legacyKeys(userJid!!).firstOrNull { it != key && privPrefs.contains(it) }
                ?: return JSONObject()
            val json = WppCore.getPrivJSON(legacyKey, JSONObject())
            if (isStableId(id)) {
                privPrefs.edit {
                    putString(key, json.toString())
                    remove(legacyKey)
                }
            }
            return json
        }

        private fun storageId(userJid: FMessageWpp.UserJid?): String? {
            if (userJid == null || userJid.isNull) return null
            userJid.userRawString?.takeIf { it.endsWith("@lid") }?.let { return it }
            return userJid.phoneRawString
        }

        private fun isStableId(id: String) = id.endsWith("@lid") || id.endsWith("@g.us")

        private fun legacyNumber(userJid: FMessageWpp.UserJid): String? {
            val raw = userJid.phoneRawString ?: return null
            if (!raw.endsWith("@s.whatsapp.net") && !raw.endsWith("@g.us")) return null
            return raw.substringBefore('@').takeIf { it.isNotEmpty() }
        }

        private fun legacyKeys(userJid: FMessageWpp.UserJid): List<String> = buildList {
            userJid.phoneRawString?.let { add(it + KEY_SUFFIX) }
            legacyNumber(userJid)?.let { add(it + KEY_SUFFIX) }
        }

        private fun legacyRawJid(number: String) =
            number + if (number.contains('-') || number.length > 15) "@g.us" else "@s.whatsapp.net"

        private fun migrateLegacyKeys() {
            val privPrefs = WppCore.getPrivPrefs()
            val moves = privPrefs.all.mapNotNull { (key, value) ->
                if (!key.endsWith(KEY_SUFFIX) || value !is String) return@mapNotNull null
                val number = key.removeSuffix(KEY_SUFFIX)
                if (number.isEmpty() || number.contains('@')) return@mapNotNull null
                val id = storageId(FMessageWpp.UserJid(legacyRawJid(number)))
                    ?.takeIf { isStableId(it) } ?: return@mapNotNull null
                Triple(key, id + KEY_SUFFIX, value)
            }
            if (moves.isEmpty()) return
            privPrefs.edit(commit = true) {
                for ((oldKey, newKey, value) in moves) {
                    if (oldKey == newKey) continue
                    if (!privPrefs.contains(newKey)) putString(newKey, value)
                    remove(oldKey)
                }
            }
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

        chatUserJidMethod =
            ReflectionUtils.findMethodUsingFilter(contactInfoActivityClass) { method ->
                method.parameterCount == 0 && userJidClass.isAssignableFrom(method.returnType)
            }

        groupUserJidMethod =
            ReflectionUtils.findMethodUsingFilter(groupInfoActivityClass) { method ->
                method.parameterCount == 0 && groupJidClass.isAssignableFrom(method.returnType)
            }

        val type = Utils.xprefs.getString("custom_privacy_type", "0")!!.toInt()

        if (type == 1) {

            WppCore.addListenerActivity(
                object : WppCore.ActivityChangeState {

                    @SuppressLint("ResourceType")
                    override fun onChange(
                        activity: Activity,
                        type: WppCore.ActivityChangeState.ChangeType
                    ) {
                        try {
                            if (type != WppCore.ActivityChangeState.ChangeType.STARTED) return
                            if (!contactInfoActivityClass.isInstance(activity) && !groupInfoActivityClass.isInstance(
                                    activity
                                )
                            ) {
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
                                showPrivacyDialog(
                                    activity,
                                    contactInfoActivityClass.isInstance(activity)
                                )
                            }

                            infoLayout.addView(itemView)
                        } catch (e: Throwable) {
                            logDebug(e)
                            Utils.showToast(e.message, Toast.LENGTH_SHORT)
                        }
                    }
                })
        } else if (type == 2) {
            val hookerAfter: HookParam.() -> Unit = {
                val menu = args[0] as Menu
                val activity = instance as Activity
                val customPrivacy = menu.add(0, 0, 0, R.string.custom_privacy)

                customPrivacy.setIcon(R.drawable.ic_privacy)
                customPrivacy.setOnMenuItemClickListener {
                    showPrivacyDialog(activity, contactInfoActivityClass.isInstance(activity))
                    true
                }
            }

            contactInfoActivityClass.resolve().firstMethod {
                name = "onCreateOptionsMenu"
                superclass()
                parameters(Menu::class.java)
            }.hook { after(hookerAfter) }
            groupInfoActivityClass.resolve().firstMethod {
                name = "onCreateOptionsMenu"
                superclass()
                parameters(Menu::class.java)
            }.hook { after(hookerAfter) }
        }

        if (type == 0) return

        val icon = DesignUtils.resizeDrawable(
            DesignUtils.getDrawable(R.drawable.ic_privacy),
            Utils.dipToPixels(24),
            Utils.dipToPixels(24)
        )
        icon.setTint(0xff8696a0.toInt())

        MenuHome.addMenuItem { menu, activity ->
            menu.add(0, 0, 0, R.string.custom_privacy)
                .setIcon(icon)
                .setOnMenuItemClickListener {
                    showCustomPrivacyList(
                        activity,
                        contactInfoActivityClass,
                        groupInfoActivityClass
                    )
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
        Utils.databaseExecutor.execute {
            migrateLegacyKeys()
            val list = ArrayList<CustomPrivacyAdapter.Item>()

            for (key in pprefs.all.keys) {
                if (key.endsWith(KEY_SUFFIX)) {
                    val id = key.removeSuffix(KEY_SUFFIX)
                    val userJid = FMessageWpp.UserJid(if (id.contains('@')) id else legacyRawJid(id))
                    val number = legacyNumber(userJid)

                    var contactName = WppCore.getContactName(userJid)
                    if (TextUtils.isEmpty(contactName)) contactName = number ?: id

                    val item = CustomPrivacyAdapter.Item()
                    item.name = contactName
                    item.isGroup = userJid.isGroup
                    item.jid = userJid.phoneRawString ?: userJid.userRawString ?: id
                    item.key = key
                    list.add(item)
                }
            }

            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (list.isEmpty()) {
                    Utils.showToast(
                        activity.getString(R.string.no_contact_with_custom_privacy),
                        Toast.LENGTH_SHORT
                    )
                    return@post
                }

                val builder = AlertDialogWpp(activity)
                builder.setTitle(R.string.custom_privacy)

                val listView = ListView(activity)
                listView.adapter =
                    CustomPrivacyAdapter(activity, pprefs, list, contactClass, groupClass)

                builder.setView(listView)
                builder.show()
            }
        }
    }

    private fun showPrivacyDialog(activity: Activity, isChat: Boolean) {
        val userJid = getUserJid(activity, isChat)
        if (userJid.isNull) return

        val builder = createPrivacyDialog(activity, userJid)
        builder.show()
    }

    private fun getUserJid(activity: Activity, isChat: Boolean): FMessageWpp.UserJid {
        return if (isChat) {
            FMessageWpp.UserJid(ReflectionUtils.callMethod(chatUserJidMethod, activity))
        } else {
            FMessageWpp.UserJid(ReflectionUtils.callMethod(groupUserJidMethod, activity))
        }
    }

    private fun createPrivacyDialog(
        activity: Activity,
        userJid: FMessageWpp.UserJid
    ): AlertDialogWpp {
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

        val checkedItems = loadPreferences(userJid, itemsKeys)

        builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton("OK") { _, _ ->
            savePreferences(userJid, itemsKeys, checkedItems)
        }
        builder.setNegativeButton(activity.getString(R.string.cancel), null)

        return builder
    }

    private fun loadPreferences(
        userJid: FMessageWpp.UserJid,
        itemsKeys: Array<String>
    ): BooleanArray {
        val checkedItems = BooleanArray(itemsKeys.size)
        val json = getJSON(userJid)

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
            xprefs.getString(globalKey, "0") == "1"
        } else {
            xprefs.getBoolean(globalKey, false)
        }
    }

    private fun savePreferences(
        userJid: FMessageWpp.UserJid,
        itemsKeys: Array<String>,
        checkedItems: BooleanArray
    ) {
        try {
            val id = storageId(userJid) ?: return
            val jsonObject = JSONObject()

            for (i in itemsKeys.indices) {
                val globalKey = getGlobalKey(itemsKeys[i])

                if (globalKey == "call_privacy") {
                    if ((xprefs.getString(globalKey, "0") == "1") != checkedItems[i]) {
                        jsonObject.put(itemsKeys[i], checkedItems[i])
                    }
                } else {
                    if (xprefs.getBoolean(globalKey, false) != checkedItems[i]) {
                        jsonObject.put(itemsKeys[i], checkedItems[i])
                    }
                }
            }

            val key = id + KEY_SUFFIX
            WppCore.getPrivPrefs().edit(commit = true) {
                putString(key, jsonObject.toString())
                legacyKeys(userJid).filter { it != key }.forEach { remove(it) }
            }
        } catch (e: Exception) {
            Utils.showToast(e.message, Toast.LENGTH_SHORT)
        }
    }

    override fun getPluginName(): String {
        return "Custom Privacy"
    }
}
