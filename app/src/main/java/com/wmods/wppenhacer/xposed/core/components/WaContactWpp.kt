package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

class WaContactWpp(mInstance: Any?) {

    val instance: Any

    init {
        val type = TYPE ?: throw RuntimeException("WaContactWpp not initialized")
        val obj = mInstance ?: throw RuntimeException("object is null")
        if (!type.isInstance(obj)) {
            throw RuntimeException("object is not a WaContactWpp")
        }
        this.instance = obj
    }

    val userJid: FMessageWpp.UserJid?
        get() {
            return try {
                val fieldContact = fieldContactData
                if (fieldContact != null) {
                    val coreData = fieldContact.get(instance)
                    val userJidObj = fieldUserJid?.get(coreData)
                    userJidObj?.let { FMessageWpp.UserJid(it) }
                } else {
                    val userJidObj = fieldUserJid?.get(instance)
                    userJidObj?.let { FMessageWpp.UserJid(it) }
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val displayName: String?
        get() {
            return try {
                fieldGetDisplayName?.get(fieldContactData?.get(instance)) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val waName: String?
        get() {
            return try {
                val contactData = fieldContactData
                val getWaNameField = fieldGetWaName
                if (contactData != null && getWaNameField != null) {
                    if (contactData.type.isAssignableFrom(getWaNameField.declaringClass)) {
                        return getWaNameField.get(contactData.get(mInstanceGetWaContact)) as? String
                    }
                }
                getWaNameField?.get(instance) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val profilePhoto: File?
        get() {
            try {
                val file =
                    getProfilePhotoHighQuality?.invoke(mInstanceGetProfilePhoto, instance) as? File
                if (file != null && file.exists()) return file
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
            return try {
                getProfilePhoto?.invoke(mInstanceGetProfilePhoto, instance) as? File
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    fun getObject(): Any {
        return instance
    }

    companion object {
        @JvmField
        var TYPE: Class<*>? = null

        private var fieldContactData: Field? = null
        private var fieldUserJid: Field? = null
        private var fieldGetWaName: Field? = null

        private var getWaContactMethod: Method? = null
        private var fieldGetDisplayName: Field? = null
        private var getProfilePhoto: Method? = null
        private var getProfilePhotoHighQuality: Method? = null

        private var mInstanceGetWaContact: Any? = null
        private var mInstanceGetProfilePhoto: Any? = null

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            try {
                TYPE = Unobfuscator.loadWaContactClass(classLoader)
                val classPhoneUserJid = Unobfuscator.findFirstClassUsingName(
                    classLoader, StringMatchType.EndsWith, "jid.PhoneUserJid"
                )
                val classJid = Unobfuscator.findFirstClassUsingName(
                    classLoader, StringMatchType.EndsWith, "jid.Jid"
                )

                val phoneUserJidField =
                    ReflectionUtils.getFieldByExtendType(TYPE, classPhoneUserJid)
                if (phoneUserJidField == null) {
                    val contactDataClass = Unobfuscator.loadWaContactData(classLoader)
                    fieldContactData = ReflectionUtils.getFieldByType(TYPE, contactDataClass)
                    fieldUserJid = ReflectionUtils.getFieldByExtendType(contactDataClass, classJid)
                } else {
                    fieldUserJid = ReflectionUtils.getFieldByExtendType(TYPE, classJid)
                }

                getWaContactMethod = Unobfuscator.loadGetWaContactMethod(classLoader)
                getWaContactMethod?.let { method ->
                    XposedBridge.hookAllConstructors(
                        method.declaringClass,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                mInstanceGetWaContact = param.thisObject
                            }
                        })
                }

                fieldGetDisplayName = Unobfuscator.loadWaContactDataDisplayNameMethod(classLoader)
                fieldGetWaName = Unobfuscator.loadWaContactGetWaNameField(classLoader)

                getProfilePhoto = Unobfuscator.loadGetProfilePhotoMethod(classLoader)
                getProfilePhotoHighQuality =
                    Unobfuscator.loadGetProfilePhotoHighQMethod(classLoader)

                getProfilePhoto?.let { method ->
                    XposedBridge.hookAllConstructors(
                        method.declaringClass,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                mInstanceGetProfilePhoto = param.thisObject
                            }
                        })
                }

            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }

        @JvmStatic
        fun getWaContactFromJid(userJid: FMessageWpp.UserJid): WaContactWpp? {
            return try {
                val method = getWaContactMethod
                val instance = mInstanceGetWaContact
                if (instance != null && method != null) {
                    var jid = userJid.userJid
                    if (!method.parameterTypes[0].isInstance(jid)) {
                        jid = FMessageWpp.UserJid.forceConverter(jid).userJid
                    }
                    val contact = method.invoke(instance, jid)
                    if (contact != null) WaContactWpp(contact) else null
                } else null
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    }
}