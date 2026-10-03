package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.model.ContactPickerResult
import com.wmods.wppenhacer.preference.ContactPickerPreference
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.ActivityChangeState
import com.wmods.wppenhacer.xposed.core.WppCore.addListenerActivity
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadLockedAuthCheckMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.atomic.AtomicBoolean


class ActivityController(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {
    private val disableAuth = AtomicBoolean(false)

    override fun doHook() {
        val clazz =
            findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".SettingsNotifications")

        val authCheckMethod = loadLockedAuthCheckMethod(classLoader)

        authCheckMethod.hook {
            before {
                if (disableAuth.get()) result = (false)
            }
        }

        addListenerActivity { activity, type ->
            if (clazz.isAssignableFrom(activity.javaClass) && type == ActivityChangeState.ChangeType.ENDED) {
                disableAuth.set(false)
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            before {
                if (clazz != instance.javaClass) return@before
                val activity = instance as Activity
                val intent = activity.intent
                if (intent.getBooleanExtra("contact_mode", false)) {
                    disableAuth.set(true)
                    contactController(intent, activity)
                }
            }
        }


        Activity::class.java.resolve().firstMethod {
            name = "onActivityResult"
            superclass()
            parameters(Int::class, Int::class, Intent::class.java)
        }.hook {
            after {
                disableAuth.set(false)
                if (clazz != instance.javaClass) return@after
                val activity = instance as Activity
                val id = args[0] as Int
                val intent = args[2] as Intent?
                if (id == ContactPickerPreference.REQUEST_CONTACT_PICKER && intent != null) {
                    processResultContact(intent, activity)
                }
                activity.finish()
            }
        }
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