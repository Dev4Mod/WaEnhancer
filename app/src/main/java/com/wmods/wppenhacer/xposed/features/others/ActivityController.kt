package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wmods.wppenhacer.model.ContactPickerResult
import com.wmods.wppenhacer.preference.ContactPickerPreference
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.ActivityChangeState
import com.wmods.wppenhacer.xposed.core.WppCore.addListenerActivity
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadLockedAuthCheckMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.atomic.AtomicBoolean


class ActivityController(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {
    private val disableAuth = AtomicBoolean(false)

    override fun doHook() {
        val clazz = findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".SettingsNotifications")

        val authCheckMethod = loadLockedAuthCheckMethod(classLoader)

        XposedBridge.hookMethod(authCheckMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (disableAuth.get()) param.setResult(false)
            }
        })

        addListenerActivity{ activity, type ->
                if (clazz.isAssignableFrom(activity.javaClass) && type == ActivityChangeState.ChangeType.ENDED) {
                    disableAuth.set(false)
                }
        }

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (clazz != param.thisObject.javaClass) return
                    val activity = param.thisObject as Activity
                    val intent = activity.intent
                    if (intent.getBooleanExtra("contact_mode", false)) {
                        disableAuth.set(true)
                        contactController(intent, activity)
                    }
                }
            })


        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    disableAuth.set(false)
                    if (clazz != param.thisObject.javaClass) return
                    val activity = param.thisObject as Activity
                    val id = param.args[0] as Int
                    val intent = param.args[2] as Intent?
                    if (id == ContactPickerPreference.REQUEST_CONTACT_PICKER && intent != null) {
                        processResultContact(intent, activity)
                    }
                    activity.finish()
                }
            })
    }

    override fun getPluginName(): String {
        return "Activity Controller"
    }

    companion object {
        private var Key: String? = null
        private fun processResultContact(intent: Intent, activity: Activity) {
            if (!intent.hasExtra("key") && Key != null) {
                intent.putExtra("key", Key)
            }
            if (!intent.hasExtra("contacts")) {
                intent.putStringArrayListExtra("contacts", ArrayList<String?>())
            }
            if (!intent.hasExtra("picker_contacts")) {
                intent.putExtra("picker_contacts", ArrayList<ContactPickerResult?>())
            }
            activity.setResult(Activity.RESULT_OK, intent)
        }


        @Throws(Exception::class)
        private fun contactController(intent: Intent, activity: Activity) {
            Key = intent.getStringExtra("key")
            val contacts = intent.getStringArrayListExtra("contacts")
            val pickerIntent = WhatsAppContactPickerLauncher.createAboutPickerIntent(
                activity,
                activity.packageName,
                (if (Key == null) "" else Key)!!,
                contacts
            )
            activity.startActivityForResult(
                pickerIntent,
                ContactPickerPreference.REQUEST_CONTACT_PICKER
            )
        }
    }
}